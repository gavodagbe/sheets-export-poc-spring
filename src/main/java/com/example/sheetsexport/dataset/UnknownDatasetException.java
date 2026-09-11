package com.example.sheetsexport.dataset;

/**
 * {@code datasetId} inconnu du catalogue → renvoyé en {@code 400} au client.
 */
public class UnknownDatasetException extends RuntimeException {
    public UnknownDatasetException(String datasetId) {
        super("Jeu de données inconnu : '" + datasetId + "'");
    }
}
