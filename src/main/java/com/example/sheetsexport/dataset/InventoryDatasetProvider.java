package com.example.sheetsexport.dataset;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jeu de données « inventory » — implémentation <b>STATIQUE</b> (données en dur).
 * Inclut un menu déroulant en cascade : Catégorie -> Sous-Catégorie.
 */
@Component
public class InventoryDatasetProvider implements DatasetProvider {

    private static final String FILTER_WAREHOUSE = "warehouse";

    private static final DependentDropdownConfig DEPENDENT_DROPDOWN = new DependentDropdownConfig(
            2, // Index 2 = Catégorie
            List.of("Boissons", "Épicerie", "Fournitures"),
            3, // Index 3 = Sous-Catégorie
            Map.of(
                    "Boissons", List.of("Café", "Thé", "Lait"),
                    "Épicerie", List.of("Sucre", "Chocolat"),
                    "Fournitures", List.of("Filtres", "Gobelets", "Touillettes")
            )
    );

    private static final DatasetDefinition DEFINITION = new DatasetDefinition(
            "inventory",
            "Inventaire (avec catégories en cascade)",
            List.of(
                    new DatasetColumn("produit", "Produit"),
                    new DatasetColumn("quantite", "Quantité"),
                    new DatasetColumn("categorie", "Catégorie"),
                    new DatasetColumn("sousCategorie", "Sous-Catégorie"),
                    new DatasetColumn("entrepot", "Entrepôt")
            ),
            List.of(FILTER_WAREHOUSE),
            new DropdownConfig(2, List.of("Boissons", "Épicerie", "Fournitures"), DEPENDENT_DROPDOWN)
    );

    /** "Table" en dur. Dans une vraie appli : lignes d'une table SQL. */
    private static final List<Map<String, Object>> ALL_ROWS = List.of(
            row("Café en grains 1kg", 12, "Boissons", "Café", "PARIS-01"),
            row("Thé vert (boîte 50)", 5, "Boissons", "Thé", "PARIS-01"),
            row("Sucre blanc 1kg", 30, "Épicerie", "Sucre", "PARIS-01"),
            row("Filtres n°4 (x100)", 3, "Fournitures", "Filtres", "PARIS-01"),
            row("Lait demi-écrémé 1L", 18, "Boissons", "Lait", "LYON-02"),
            row("Gobelets carton 25cl (x50)", 7, "Fournitures", "Gobelets", "LYON-02"),
            row("Chocolat en poudre 800g", 0, "Épicerie", "Chocolat", "LYON-02"),
            row("Touillettes bois (x1000)", 42, "Fournitures", "Touillettes", "LILLE-03")
    );

    @Override
    public DatasetDefinition definition() {
        return DEFINITION;
    }

    @Override
    public List<Map<String, Object>> fetchRows(Map<String, String> filters) {
        String warehouse = trimToNull(filters.get(FILTER_WAREHOUSE));

        return ALL_ROWS.stream()
                .filter(r -> warehouse == null || warehouse.equalsIgnoreCase(String.valueOf(r.get("entrepot"))))
                .toList();
    }

    private static Map<String, Object> row(String produit, int quantite, String categorie, String sousCategorie, String entrepot) {
        return toRow(produit, quantite, categorie, sousCategorie, entrepot);
    }

    private static Map<String, Object> toRow(String produit, int quantite, String categorie, String sousCategorie, String entrepot) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("produit", produit);
        r.put("quantite", quantite);
        r.put("categorie", categorie);
        r.put("sousCategorie", sousCategorie);
        r.put("entrepot", entrepot);
        return r;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

