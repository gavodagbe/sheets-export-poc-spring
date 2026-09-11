package com.example.sheetsexport.export;

import java.util.List;
import java.util.Map;

/**
 * Export « à plat », prêt à être poussé vers Google — quelle que soit la source d'origine
 * ({@code inline} ou {@code dataset}). C'est ce que consomme {@code GoogleSheetsExportService}.
 *
 * @param sheetTitle          titre du spreadsheet
 * @param shareWithEmail      email tiers ou {@code null}
 * @param rows                lignes ; chaque map = une ligne (clé = entête de colonne, ordre préservé)
 * @param dropdownOptions     valeurs du menu déroulant
 * @param dropdownColumnIndex index (0-based) de la colonne recevant le menu déroulant
 */
public record ResolvedExport(
        String sheetTitle,
        String shareWithEmail,
        List<Map<String, Object>> rows,
        List<String> dropdownOptions,
        int dropdownColumnIndex
) {
}
