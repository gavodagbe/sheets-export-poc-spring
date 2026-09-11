package com.example.sheetsexport.web;

import com.example.sheetsexport.dataset.DatasetCatalog;
import com.example.sheetsexport.dataset.DatasetDefinition;
import com.example.sheetsexport.dataset.DatasetProvider;
import com.example.sheetsexport.web.dto.DatasetRowsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Expose le catalogue des jeux de données exportables ET leur contenu.
 *
 * <p>Routes publiques (comme {@code /api/session}) : le POC affichait déjà les données dans le
 * HTML. Dans une vraie appli, protéger {@code /api/datasets/**} au même titre que l'export
 * si les données sont sensibles.</p>
 */
@RestController
public class DatasetController {

    private final DatasetCatalog catalog;

    public DatasetController(DatasetCatalog catalog) {
        this.catalog = catalog;
    }

    /** Métadonnées de tous les jeux de données (id, libellé, colonnes, config dropdown). */
    @GetMapping("/api/datasets")
    public List<DatasetDefinition> datasets() {
        return catalog.definitions();
    }

    /** Métadonnées d'un jeu de données. */
    @GetMapping("/api/datasets/{id}")
    public DatasetDefinition dataset(@PathVariable String id) {
        return catalog.require(id).definition();
    }

    /**
     * Contenu d'un jeu de données (colonnes + lignes), résolu côté serveur.
     * Tous les query params sont passés comme filtres au {@link DatasetProvider}.
     */
    @GetMapping("/api/datasets/{id}/rows")
    public DatasetRowsResponse rows(@PathVariable String id,
                                    @RequestParam(required = false) Map<String, String> filters) {
        DatasetProvider provider = catalog.require(id);
        DatasetDefinition def = provider.definition();
        List<Map<String, Object>> raw = provider.fetchRows(filters == null ? Map.of() : filters);
        return new DatasetRowsResponse(def.columnLabels(), def.toLabeledRows(raw));
    }
}
