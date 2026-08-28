package com.example.sheetsexport.service;

import com.example.sheetsexport.config.GoogleProperties;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import org.springframework.stereotype.Component;

/**
 * Construit des clients Sheets / Drive "à la volée" pour un access token OAuth donné.
 *
 * Le token appartient à l'utilisateur final (obtenu via son consentement Google) :
 * le Sheet créé lui appartient, dans son propre Drive. Rien n'est stocké côté serveur.
 */
@Component
public class GoogleClientFactory {

    private final HttpTransport transport;
    private final JsonFactory jsonFactory;
    private final String applicationName;

    public GoogleClientFactory(HttpTransport transport, JsonFactory jsonFactory, GoogleProperties props) {
        this.transport = transport;
        this.jsonFactory = jsonFactory;
        this.applicationName = props.getApplicationName();
    }

    public Sheets sheets(String accessToken) {
        return new Sheets.Builder(transport, jsonFactory, bearerInitializer(accessToken))
                .setApplicationName(applicationName)
                .build();
    }

    public Drive drive(String accessToken) {
        return new Drive.Builder(transport, jsonFactory, bearerInitializer(accessToken))
                .setApplicationName(applicationName)
                .build();
    }

    /**
     * Pose simplement l'entête {@code Authorization: Bearer <token>} sur chaque requête.
     * Pas de logique de refresh : le token est utilisé tel quel (stateless). S'il est
     * expiré/invalide, Google répond 401 → mappé en 401 par le service.
     */
    private HttpRequestInitializer bearerInitializer(String accessToken) {
        return request -> request.getHeaders().setAuthorization("Bearer " + accessToken);
    }
}
