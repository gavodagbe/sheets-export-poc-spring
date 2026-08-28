package com.example.sheetsexport.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Infrastructure Google partagée (transport HTTP + fabrique JSON).
 *
 * Il n'y a plus de bean Sheets/Drive singleton : chaque requête crée ses clients
 * à partir de l'access token de l'utilisateur (voir {@link com.example.sheetsexport.service.GoogleClientFactory}).
 */
@Configuration
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleApiConfig {

    @Bean
    public HttpTransport googleHttpTransport() throws GeneralSecurityException, IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public JsonFactory googleJsonFactory() {
        return GsonFactory.getDefaultInstance();
    }
}
