package com.example.sheetsexport.dataset;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jeu de données « inventory » — implémentation <b>STATIQUE</b> (données en dur).
 *
 * C'est le point à remplacer par un vrai accès base de données :
 * <ul>
 *   <li>{@link #ALL_ROWS} → une requête {@code repository.findAll()} / {@code findByWarehouseCode(...)} ;</li>
 *   <li>le filtrage en Java ci-dessous → une clause {@code WHERE} ;</li>
 *   <li>{@link #toRow} → le mapping entité → ligne.</li>
 * </ul>
 * Tout le reste du dispositif (résolveur, contrôleur, service Google, front) est agnostique.
 */
@Component
public class InventoryDatasetProvider implements DatasetProvider {

    private static final String FILTER_WAREHOUSE = "warehouse";

    private static final DatasetDefinition DEFINITION = new DatasetDefinition(
            "inventory",
            "Inventaire (entrepôts)",
            List.of(
                    new DatasetColumn("produit", "Produit"),
                    new DatasetColumn("quantite", "Quantité"),
                    new DatasetColumn("entrepot", "Entrepôt"),
                    new DatasetColumn("statut", "Statut")
            ),
            List.of(FILTER_WAREHOUSE),
            new DropdownConfig(3, List.of("À commander", "En stock", "Rupture"))
    );

    /** "Table" en dur. Dans une vraie appli : lignes d'une table SQL. */
    private static final List<Map<String, Object>> ALL_ROWS = List.of(
            row("Café en grains 1kg", 12, "PARIS-01", "En stock"),
            row("Thé vert (boîte 50)", 5, "PARIS-01", "À commander"),
            row("Sucre blanc 1kg", 30, "PARIS-01", "En stock"),
            row("Filtres n°4 (x100)", 3, "PARIS-01", "Rupture"),
            row("Lait demi-écrémé 1L", 18, "LYON-02", "En stock"),
            row("Gobelets carton 25cl (x50)", 7, "LYON-02", "À commander"),
            row("Chocolat en poudre 800g", 0, "LYON-02", "Rupture"),
            row("Touillettes bois (x1000)", 42, "LILLE-03", "En stock")
    );

    @Override
    public DatasetDefinition definition() {
        return DEFINITION;
    }

    @Override
    public List<Map<String, Object>> fetchRows(Map<String, String> filters) {
        // Whitelist : on ne lit QUE les clés de filtre connues, le reste est ignoré.
        String warehouse = trimToNull(filters.get(FILTER_WAREHOUSE));

        return ALL_ROWS.stream()
                .filter(r -> warehouse == null || warehouse.equalsIgnoreCase(String.valueOf(r.get("entrepot"))))
                .toList();
    }

    private static Map<String, Object> row(String produit, int quantite, String entrepot, String statut) {
        return toRow(produit, quantite, entrepot, statut);
    }

    /** Mapping "entité" → ligne. Les clés = {@link DatasetDefinition#columnKeys()}. */
    private static Map<String, Object> toRow(String produit, int quantite, String entrepot, String statut) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("produit", produit);
        r.put("quantite", quantite);
        r.put("entrepot", entrepot);
        r.put("statut", statut);
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
