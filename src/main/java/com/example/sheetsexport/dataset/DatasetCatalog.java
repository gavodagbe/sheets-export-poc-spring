package com.example.sheetsexport.dataset;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registre des jeux de données exportables.
 *
 * Il indexe tous les {@link DatasetProvider} déclarés dans le contexte Spring par leur
 * {@code definition().id()}. Un {@code datasetId} absent de ce registre est refusé
 * ({@link UnknownDatasetException}) — c'est la <b>whitelist</b> : le client ne peut cibler
 * que des jeux de données explicitement déclarés.
 */
@Component
public class DatasetCatalog {

    private final Map<String, DatasetProvider> providersById = new LinkedHashMap<>();

    public DatasetCatalog(List<DatasetProvider> providers) {
        for (DatasetProvider provider : providers) {
            String id = provider.definition().id();
            DatasetProvider previous = providersById.put(id, provider);
            if (previous != null) {
                throw new IllegalStateException("Deux DatasetProvider partagent l'id '" + id + "'");
            }
        }
    }

    /** Toutes les définitions, pour {@code GET /api/datasets}. */
    public List<DatasetDefinition> definitions() {
        return providersById.values().stream().map(DatasetProvider::definition).toList();
    }

    /** @throws UnknownDatasetException si l'id n'est pas déclaré */
    public DatasetProvider require(String datasetId) {
        DatasetProvider provider = providersById.get(datasetId);
        if (provider == null) {
            throw new UnknownDatasetException(datasetId);
        }
        return provider;
    }
}
