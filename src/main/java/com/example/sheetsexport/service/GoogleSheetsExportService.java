package com.example.sheetsexport.service;

import com.example.sheetsexport.exception.InvalidAccessTokenException;
import com.example.sheetsexport.exception.QuotaExceededException;
import com.example.sheetsexport.exception.SheetsExportException;
import com.example.sheetsexport.export.ResolvedExport;
import com.example.sheetsexport.web.dto.SheetExportResponse;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.BooleanCondition;
import com.google.api.services.sheets.v4.model.ConditionValue;
import com.google.api.services.sheets.v4.model.DataValidationRule;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SetDataValidationRequest;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Génère un nouveau Google Sheet à chaque appel :
 * <ol>
 *   <li>{@code spreadsheets.create} avec 2 onglets définis d'un coup :
 *       "Data" (visible) et "RefData" (hidden)</li>
 *   <li>{@code values.batchUpdate} pour écrire les données dans les 2 onglets</li>
 *   <li>{@code spreadsheets.batchUpdate} avec {@code setDataValidation}
 *       (condition ONE_OF_RANGE pointant vers RefData) sur la colonne du dropdown</li>
 *   <li>{@code drive.permissions.create} pour partager le fichier (role writer)</li>
 * </ol>
 * Service stateless : aucun ID n'est persisté.
 */
@Service
public class GoogleSheetsExportService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsExportService.class);

    private static final String DATA_SHEET_TITLE = "Data";
    private static final String REF_SHEET_TITLE = "RefData";
    private static final int DATA_SHEET_ID = 0;
    private static final int REF_SHEET_ID = 1;

    private final GoogleClientFactory clientFactory;
    private final RetryExecutor retry;

    public GoogleSheetsExportService(GoogleClientFactory clientFactory, RetryExecutor retry) {
        this.clientFactory = clientFactory;
        this.retry = retry;
    }

    /**
     * @param accessToken access token OAuth de l'utilisateur (scopes spreadsheets + drive)
     * @param request     export résolu à plat (voir {@code ExportSourceResolver})
     */
    public SheetExportResponse export(String accessToken, ResolvedExport request) {
        // Clients construits pour CE token : le fichier appartiendra à l'utilisateur.
        Sheets sheets = clientFactory.sheets(accessToken);
        Drive drive = clientFactory.drive(accessToken);
        try {
            // --- 1. Création du spreadsheet avec les 2 onglets d'un seul appel ------------------
            Spreadsheet toCreate = new Spreadsheet()
                    .setProperties(new SpreadsheetProperties().setTitle(request.sheetTitle()))
                    .setSheets(List.of(
                            new Sheet().setProperties(new SheetProperties()
                                    .setSheetId(DATA_SHEET_ID)
                                    .setTitle(DATA_SHEET_TITLE)
                                    .setHidden(false)),
                            new Sheet().setProperties(new SheetProperties()
                                    .setSheetId(REF_SHEET_ID)
                                    .setTitle(REF_SHEET_TITLE)
                                    .setHidden(true))   // <-- onglet source du dropdown, masqué
                    ));

            Spreadsheet created = retry.execute("spreadsheets.create", () ->
                    sheets.spreadsheets().create(toCreate)
                            .setFields("spreadsheetId,spreadsheetUrl")
                            .execute());

            String spreadsheetId = created.getSpreadsheetId();
            String spreadsheetUrl = created.getSpreadsheetUrl();
            log.info("Spreadsheet créé : {}", spreadsheetId);

            // --- 2. Remplissage des 2 onglets -------------------------------------------------
            List<List<Object>> dataValues = buildDataValues(request.rows());
            List<List<Object>> refValues = buildRefValues(request, dataValues.size());

            // On n'envoie que les plages effectivement remplies (l'API rejette un ValueRange vide).
            List<ValueRange> valueRanges = new ArrayList<>();
            if (!dataValues.isEmpty()) {
                valueRanges.add(new ValueRange().setRange(DATA_SHEET_TITLE + "!A1").setValues(dataValues));
            }
            if (!refValues.isEmpty()) {
                valueRanges.add(new ValueRange().setRange(REF_SHEET_TITLE + "!A1").setValues(refValues));
            }

            if (!valueRanges.isEmpty()) {
                BatchUpdateValuesRequest valuesBody = new BatchUpdateValuesRequest()
                        .setValueInputOption("USER_ENTERED")
                        .setData(valueRanges);
                retry.execute("values.batchUpdate", () ->
                        sheets.spreadsheets().values().batchUpdate(spreadsheetId, valuesBody).execute());
            }

            // --- 3. Data validation (dropdown) sur la colonne demandée de l'onglet Data ------
            retry.execute("spreadsheets.batchUpdate(setDataValidation)", () ->
                    sheets.spreadsheets().batchUpdate(spreadsheetId,
                            buildDataValidationBatch(request, dataValues.size())).execute());

            // --- 4. Partage optionnel du fichier via Drive --------------------------------
            // Le fichier appartient déjà à l'utilisateur authentifié ; on ne partage que si
            // un email tiers est fourni.
            String shareWith = request.shareWithEmail();
            if (shareWith != null && !shareWith.isBlank()) {
                Permission permission = new Permission()
                        .setType("user")
                        .setRole("writer")
                        .setEmailAddress(shareWith);

                retry.execute("drive.permissions.create", () ->
                        drive.permissions().create(spreadsheetId, permission)
                                .setSendNotificationEmail(true)
                                .setFields("id")
                                .execute());
                log.info("Spreadsheet {} partagé avec {}", spreadsheetId, shareWith);
            }
            return new SheetExportResponse(spreadsheetId, spreadsheetUrl);

        } catch (GoogleJsonResponseException e) {
            int status = e.getStatusCode();
            if (status == 401) {
                throw new InvalidAccessTokenException(
                        "Access token Google invalide ou expiré", e);
            }
            if (status == 429) {
                throw new QuotaExceededException("Quota Google dépassé (429) après retries", e);
            }
            throw new SheetsExportException(
                    "Erreur API Google (" + status + ") : " + e.getStatusMessage(), e);
        } catch (IOException e) {
            throw new SheetsExportException("Erreur d'I/O lors de l'appel aux APIs Google", e);
        }
    }

    /**
     * Transforme les lignes JSON en matrice de valeurs.
     * La 1re ligne du résultat est l'entête (clés de la 1re map, ordre préservé par Jackson).
     */
    private List<List<Object>> buildDataValues(List<Map<String, Object>> rows) {
        List<List<Object>> values = new ArrayList<>();
        if (rows.isEmpty()) {
            return values;
        }
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        values.add(new ArrayList<>(headers));
        for (Map<String, Object> row : rows) {
            List<Object> line = new ArrayList<>(headers.size());
            for (String header : headers) {
                line.add(row.get(header));   // null si la clé est absente de cette ligne
            }
            values.add(line);
        }
        return values;
    }

    /**
     * Construit les données de l'onglet caché RefData :
     * Col A : Options parentes
     * Col B : Formule dynamique TRANSPOSE(FILTER(...)) pour chaque ligne de Data (si dropdown dépendant)
     * Col B à Z : Espaces vides réservés à l'expansion de TRANSPOSE
     * Col AA & AB : Table de mapping (Parent, Enfant)
     */
    private List<List<Object>> buildRefValues(ResolvedExport request, int dataRowCount) {
        List<String> parentOptions = request.dropdownOptions();
        boolean hasDep = request.hasDependentDropdown();

        List<Map.Entry<String, String>> mappingPairs = new ArrayList<>();
        if (hasDep) {
            for (Map.Entry<String, List<String>> entry : request.dependentOptionsMap().entrySet()) {
                String parent = entry.getKey();
                if (entry.getValue() != null) {
                    for (String child : entry.getValue()) {
                        mappingPairs.add(Map.entry(parent, child));
                    }
                }
            }
        }

        int mappingCount = mappingPairs.size();
        int parentCount = parentOptions != null ? parentOptions.size() : 0;
        int maxRows = Math.max(parentCount, mappingCount + 1);
        maxRows = Math.max(maxRows, dataRowCount);
        if (maxRows == 0) {
            maxRows = 1;
        }

        String parentColLetter = getColumnLetter(request.dropdownColumnIndex());
        int mapEndRow = Math.max(2, mappingCount + 1);

        List<List<Object>> refRows = new ArrayList<>(maxRows);
        for (int r = 0; r < maxRows; r++) {
            List<Object> row = new ArrayList<>();
            // Col A (index 0): Parent Option
            String parentOpt = (parentOptions != null && r < parentOptions.size()) ? parentOptions.get(r) : "";
            row.add(parentOpt);

            // Col B (index 1): Formule pour la ligne r+1 dans Data (pour r >= 1)
            if (hasDep && r >= 1 && r < dataRowCount) {
                int dataRowIndex = r + 1; // 1-based row index dans Sheet (Row 2, 3...)
                String formula = "=IFERROR(TRANSPOSE(FILTER($AB$2:$AB$" + mapEndRow + ", $AA$2:$AA$" + mapEndRow + " = Data!" + parentColLetter + dataRowIndex + ")), \"\")";
                row.add(formula);
            } else {
                row.add("");
            }

            // Col C à Z (indices 2 à 25) : vides pour laisser TRANSPOSE s'étendre
            if (hasDep) {
                for (int c = 2; c < 26; c++) {
                    row.add("");
                }

                // Col AA & AB (indices 26 et 27) : Table de mapping
                if (r == 0) {
                    row.add("Parent");
                    row.add("Child");
                } else if (r - 1 < mappingCount) {
                    Map.Entry<String, String> pair = mappingPairs.get(r - 1);
                    row.add(pair.getKey());
                    row.add(pair.getValue());
                } else {
                    row.add("");
                    row.add("");
                }
            }

            refRows.add(row);
        }
        return refRows;
    }


    private static String getColumnLetter(int colIndex) {
        StringBuilder sb = new StringBuilder();
        int col = colIndex;
        while (col >= 0) {
            sb.insert(0, (char) ('A' + (col % 26)));
            col = (col / 26) - 1;
        }
        return sb.toString();
    }

    /**
     * Construit la requête batchUpdate qui pose les règles de data validation.
     */
    private BatchUpdateSpreadsheetRequest buildDataValidationBatch(ResolvedExport request, int dataRowCount) {
        List<Request> requests = new ArrayList<>();

        int startRow = 1;
        int endRow = Math.max(dataRowCount, startRow + 1);

        // 1. Validation du Dropdown Parent
        if (request.dropdownOptions() != null && !request.dropdownOptions().isEmpty()) {
            int parentCol = request.dropdownColumnIndex();
            int parentOptionCount = request.dropdownOptions().size();

            GridRange parentTargetRange = new GridRange()
                    .setSheetId(DATA_SHEET_ID)
                    .setStartRowIndex(startRow)
                    .setEndRowIndex(endRow)
                    .setStartColumnIndex(parentCol)
                    .setEndColumnIndex(parentCol + 1);

            ConditionValue parentRangeRef = new ConditionValue()
                    .setUserEnteredValue("=" + REF_SHEET_TITLE + "!A1:A" + parentOptionCount);

            BooleanCondition parentCondition = new BooleanCondition()
                    .setType("ONE_OF_RANGE")
                    .setValues(List.of(parentRangeRef));

            DataValidationRule parentRule = new DataValidationRule()
                    .setCondition(parentCondition)
                    .setShowCustomUi(true)
                    .setStrict(false);

            requests.add(new Request().setSetDataValidation(new SetDataValidationRequest()
                    .setRange(parentTargetRange)
                    .setRule(parentRule)));
        }

        // 2. Validation du Dropdown Dépendant (par ligne)
        if (request.hasDependentDropdown()) {
            int depCol = request.dependentColumnIndex();
            for (int r = startRow; r < endRow; r++) {
                int sheetRowNumber = r + 1; // 1-based row number (e.g. 2, 3...)
                GridRange depTargetRange = new GridRange()
                        .setSheetId(DATA_SHEET_ID)
                        .setStartRowIndex(r)
                        .setEndRowIndex(r + 1)
                        .setStartColumnIndex(depCol)
                        .setEndColumnIndex(depCol + 1);

                ConditionValue depRangeRef = new ConditionValue()
                        .setUserEnteredValue("=" + REF_SHEET_TITLE + "!B" + sheetRowNumber + ":Z" + sheetRowNumber);

                BooleanCondition depCondition = new BooleanCondition()
                        .setType("ONE_OF_RANGE")
                        .setValues(List.of(depRangeRef));

                DataValidationRule depRule = new DataValidationRule()
                        .setCondition(depCondition)
                        .setShowCustomUi(true)
                        .setStrict(false);

                requests.add(new Request().setSetDataValidation(new SetDataValidationRequest()
                        .setRange(depTargetRange)
                        .setRule(depRule)));
            }
        }

        return new BatchUpdateSpreadsheetRequest().setRequests(requests);
    }
}

