package com.example.sheetsexport.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Mode {@code "dataset"} : le front ne transmet PAS les données, seulement une référence.
 * Le serveur résout les lignes ET la configuration du dropdown à partir du catalogue
 * ({@code GET /api/datasets}).
 *
 * @param datasetId identifiant d'un jeu de données déclaré dans le catalogue (whitelist).
 * @param filters   filtres optionnels (clé → valeur). Les clés non reconnues par le provider
 *                  sont ignorées. Ne jamais construire de SQL directement à partir de ça :
 *                  le provider mappe chaque clé sur une condition connue.
 */
public record DatasetSource(

        @NotBlank
        String datasetId,

        Map<String, String> filters

) implements ExportSource {

    public Map<String, String> filtersOrEmpty() {
        return filters == null ? Map.of() : filters;
    }
}
