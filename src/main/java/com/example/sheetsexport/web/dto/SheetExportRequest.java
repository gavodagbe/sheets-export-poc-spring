package com.example.sheetsexport.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de la requête POST /api/exports/sheet.
 *
 * <pre>
 * // mode DOM (historique)
 * { "sheetTitle": "...", "source": { "type": "inline",
 *     "rows": [...], "dropdownOptions": [...], "dropdownColumnIndex": 2 } }
 *
 * // mode base de données (données résolues côté serveur)
 * { "sheetTitle": "...", "source": { "type": "dataset",
 *     "datasetId": "inventory", "filters": { "warehouse": "PARIS-01" } } }
 * </pre>
 *
 * @param sheetTitle     titre du spreadsheet à créer
 * @param shareWithEmail OPTIONNEL : email d'un tiers avec qui partager le fichier (role writer).
 * @param source         d'où viennent les données ({@link InlineSource} ou {@link DatasetSource}).
 */
public record SheetExportRequest(

        @NotBlank
        String sheetTitle,

        @Email
        String shareWithEmail,

        @NotNull
        @Valid
        ExportSource source
) {
}
