package com.example.sheetsexport.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Mode {@code "inline"} : les données viennent du front (lecture du tableau HTML).
 *
 * @param rows                lignes ; chaque map = une ligne (clé = nom de colonne). L'ordre des
 *                            clés de la 1re ligne définit l'ordre des colonnes / l'entête.
 * @param dropdownOptions     valeurs source du dropdown (écrites dans l'onglet caché RefData)
 * @param dropdownColumnIndex index (0-based) de la colonne de l'onglet Data qui reçoit le dropdown
 */
public record InlineSource(

        @NotNull
        List<Map<String, Object>> rows,

        @NotEmpty
        List<String> dropdownOptions,

        @NotNull
        @Min(0)
        Integer dropdownColumnIndex

) implements ExportSource {
}
