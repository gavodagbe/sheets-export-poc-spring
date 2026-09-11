package com.example.sheetsexport.dataset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Métadonnées d'un jeu de données : ce que le front a besoin de connaître pour proposer
 * un bouton « Exporter depuis la base » et, éventuellement, une UI de filtres.
 *
 * @param id           identifiant stable, utilisé dans {@link com.example.sheetsexport.web.dto.DatasetSource}
 * @param label        libellé lisible
 * @param columns      colonnes, dans l'ordre voulu pour le Sheet (définit l'entête)
 * @param filterKeys   clés de filtres acceptées par le provider (pour info / UI ; whitelist)
 * @param dropdown     configuration du menu déroulant appliqué au Sheet
 */
public record DatasetDefinition(
        String id,
        String label,
        List<DatasetColumn> columns,
        List<String> filterKeys,
        DropdownConfig dropdown
) {
    /** Clés de colonnes dans l'ordre — sert à ordonner les lignes renvoyées par le provider. */
    public List<String> columnKeys() {
        return columns.stream().map(DatasetColumn::key).toList();
    }

    /** Libellés de colonnes dans l'ordre — entête du Sheet / de la table HTML. */
    public List<String> columnLabels() {
        return columns.stream().map(DatasetColumn::label).toList();
    }

    /**
     * Réordonne / relibelle chaque ligne brute du provider selon les colonnes déclarées :
     * clé = libellé, ordre = celui du catalogue, clé absente → cellule vide.
     * Utilisé aussi bien pour l'export que pour l'aperçu ({@code GET /api/datasets/{id}/rows}).
     */
    public List<Map<String, Object>> toLabeledRows(List<Map<String, Object>> rawRows) {
        List<Map<String, Object>> out = new ArrayList<>(rawRows.size());
        for (Map<String, Object> raw : rawRows) {
            Map<String, Object> line = new LinkedHashMap<>();
            for (DatasetColumn column : columns) {
                line.put(column.label(), raw.get(column.key()));
            }
            out.add(line);
        }
        return out;
    }
}
