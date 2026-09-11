package com.example.sheetsexport.dataset;

import java.util.List;

/**
 * Configuration du menu déroulant (data validation) d'un jeu de données.
 * En mode {@code dataset}, c'est le SERVEUR qui la fournit — pas le front.
 *
 * @param columnIndex       index (0-based) de la colonne qui reçoit le dropdown
 * @param options           valeurs autorisées (écrites dans l'onglet caché RefData)
 * @param dependentDropdown configuration optionnelle d'un menu déroulant dépendant
 */
public record DropdownConfig(
        int columnIndex,
        List<String> options,
        DependentDropdownConfig dependentDropdown
) {
    public DropdownConfig(int columnIndex, List<String> options) {
        this(columnIndex, options, null);
    }
}

