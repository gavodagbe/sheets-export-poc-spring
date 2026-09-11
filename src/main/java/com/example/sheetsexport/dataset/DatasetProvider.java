package com.example.sheetsexport.dataset;

import java.util.List;
import java.util.Map;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  LA COUTURE ("seam") entre l'export et vos données.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Un {@code DatasetProvider} = un jeu de données exportable. Déclarez-le comme
 * {@code @Component} : {@link DatasetCatalog} le découvre automatiquement.
 *
 * Dans ce POC, l'unique implémentation ({@code InventoryDatasetProvider}) renvoie des
 * données EN DUR. Pour une vraie appli, l'équipe remplace le corps de {@link #fetchRows}
 * par un appel à son repository (Spring Data JPA, JdbcTemplate, MyBatis…) — la signature
 * et tout le reste du dispositif (contrôleur, résolveur, service Google, front) ne bougent pas.
 *
 * Exemple d'implémentation JPA :
 * <pre>
 * &#64;Component
 * class InventoryDatasetProvider implements DatasetProvider {
 *     private final InventoryRepository repo;   // extends JpaRepository&lt;InventoryItem, Long&gt;
 *
 *     public List&lt;Map&lt;String, Object&gt;&gt; fetchRows(Map&lt;String, String&gt; filters) {
 *         String warehouse = filters.get("warehouse");   // whitelist : on ne lit QUE les clés connues
 *         List&lt;InventoryItem&gt; items = (warehouse != null)
 *                 ? repo.findByWarehouseCode(warehouse)
 *                 : repo.findAll();
 *         return items.stream().map(this::toRow).toList();
 *     }
 * }
 * </pre>
 */
public interface DatasetProvider {

    /** Métadonnées (id, colonnes, config dropdown). L'{@code id} doit être unique dans l'appli. */
    DatasetDefinition definition();

    /**
     * Renvoie les lignes à exporter. Chaque {@code Map} = une ligne ; les clés doivent
     * correspondre à {@code definition().columnKeys()} (l'ordre des colonnes est réappliqué
     * ensuite, une clé absente devient une cellule vide).
     *
     * @param filters filtres bruts venus du client. N'utilisez QUE les clés que vous
     *                connaissez ({@code definition().filterKeys()}) ; ignorez le reste.
     */
    List<Map<String, Object>> fetchRows(Map<String, String> filters);
}
