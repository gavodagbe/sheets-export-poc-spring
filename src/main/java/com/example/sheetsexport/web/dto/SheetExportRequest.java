package com.example.sheetsexport.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Corps de la requête POST /api/exports/sheet.
 *
 * @param sheetTitle          titre du spreadsheet à créer
 * @param shareWithEmail      OPTIONNEL : email d'un tiers avec qui partager le fichier (role writer).
 *                            Le fichier appartient déjà à l'utilisateur authentifié ; laisser vide
 *                            si l'export est pour lui seul.
 * @param rows                lignes de données ; chaque map = une ligne (clé = nom de colonne).
 *                            L'ordre des clés de la 1re ligne définit l'ordre des colonnes / l'entête.
 * @param dropdownOptions     valeurs source du dropdown (écrites dans l'onglet caché RefData)
 * @param dropdownColumnIndex index (0-based) de la colonne de l'onglet Data qui reçoit le dropdown
 */
public record SheetExportRequest(

        @NotBlank
        String sheetTitle,

        @Email
        String shareWithEmail,

        @NotNull
        List<Map<String, Object>> rows,

        @NotEmpty
        List<String> dropdownOptions,

        @NotNull
        @Min(0)
        Integer dropdownColumnIndex
) {
}
