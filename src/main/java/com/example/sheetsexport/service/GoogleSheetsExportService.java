package com.example.sheetsexport.service;

import com.example.sheetsexport.exception.InvalidAccessTokenException;
import com.example.sheetsexport.exception.QuotaExceededException;
import com.example.sheetsexport.exception.SheetsExportException;
import com.example.sheetsexport.web.dto.SheetExportRequest;
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
     * @param request     paramètres de l'export
     */
    public SheetExportResponse export(String accessToken, SheetExportRequest request) {
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
            List<List<Object>> refValues = buildRefValues(request.dropdownOptions());

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
                        .setValueInputOption("RAW")
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

    /** Une valeur du dropdown par ligne dans la colonne A de RefData. */
    private List<List<Object>> buildRefValues(List<String> options) {
        List<List<Object>> values = new ArrayList<>(options.size());
        for (String option : options) {
            values.add(List.of(option));
        }
        return values;
    }

    /**
     * Construit la requête batchUpdate qui pose la règle de data validation.
     * Condition ONE_OF_RANGE : la valeur autorisée est n'importe quelle cellule de
     * la plage {@code RefData!A1:A<n>} (onglet caché).
     *
     * @param dataRowCount nombre de lignes écrites dans Data (entête incluse)
     */
    private BatchUpdateSpreadsheetRequest buildDataValidationBatch(SheetExportRequest request, int dataRowCount) {
        int col = request.dropdownColumnIndex();
        int optionCount = request.dropdownOptions().size();

        // Plage cible : de la 1re ligne de données (sous l'entête) jusqu'à la dernière ligne.
        // Si aucune donnée, on applique quand même la validation sur une ligne pour la démo.
        int startRow = 1;
        int endRow = Math.max(dataRowCount, startRow + 1);

        GridRange targetRange = new GridRange()
                .setSheetId(DATA_SHEET_ID)
                .setStartRowIndex(startRow)
                .setEndRowIndex(endRow)
                .setStartColumnIndex(col)
                .setEndColumnIndex(col + 1);

        // Référence de plage vers l'onglet caché. Le "=" est requis pour ONE_OF_RANGE.
        ConditionValue rangeRef = new ConditionValue()
                .setUserEnteredValue("=" + REF_SHEET_TITLE + "!A1:A" + optionCount);

        BooleanCondition condition = new BooleanCondition()
                .setType("ONE_OF_RANGE")
                .setValues(List.of(rangeRef));

        DataValidationRule rule = new DataValidationRule()
                .setCondition(condition)
                .setShowCustomUi(true)   // affiche la flèche de dropdown dans la cellule
                .setStrict(false);       // n'empêche pas une saisie hors liste (mise en garde seulement)

        SetDataValidationRequest setDataValidation = new SetDataValidationRequest()
                .setRange(targetRange)
                .setRule(rule);

        return new BatchUpdateSpreadsheetRequest()
                .setRequests(List.of(new Request().setSetDataValidation(setDataValidation)));
    }
}
