package com.example.sheetsexport.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Aperçu d'un jeu de données ({@code GET /api/datasets/{id}/rows}).
 * Le front s'en sert pour afficher la table ET la renvoyer en mode {@code inline}.
 *
 * @param columns libellés de colonnes, dans l'ordre (entête)
 * @param rows    lignes ; chaque map est keyée par libellé de colonne (ordre préservé)
 */
public record DatasetRowsResponse(
        List<String> columns,
        List<Map<String, Object>> rows
) {
}
