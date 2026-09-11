package com.example.sheetsexport.dataset;

/**
 * Description d'une colonne d'un jeu de données (métadonnée exposée par {@code GET /api/datasets}).
 *
 * @param key   nom technique = clé présente dans chaque ligne renvoyée par le provider
 * @param label libellé affiché (entête du Sheet / UI du front)
 */
public record DatasetColumn(String key, String label) {
}
