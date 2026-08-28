package com.example.sheetsexport.web.dto;

/**
 * Réponse de POST /api/exports/sheet.
 */
public record SheetExportResponse(
        String spreadsheetId,
        String spreadsheetUrl
) {
}
