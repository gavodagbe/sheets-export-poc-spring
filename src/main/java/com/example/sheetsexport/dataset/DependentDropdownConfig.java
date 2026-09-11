package com.example.sheetsexport.dataset;

import java.util.List;
import java.util.Map;

/**
 * Configuration d'un menu déroulant dépendant (en cascade).
 *
 * @param parentColumnIndex    index (0-based) de la colonne parent
 * @param parentOptions        liste des options parentes
 * @param dependentColumnIndex index (0-based) de la colonne dépendante
 * @param dependentOptionsMap  mapping parent -> liste des options enfants autorisées
 */
public record DependentDropdownConfig(
        int parentColumnIndex,
        List<String> parentOptions,
        int dependentColumnIndex,
        Map<String, List<String>> dependentOptionsMap
) {
}
