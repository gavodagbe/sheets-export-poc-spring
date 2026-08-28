package com.example.sheetsexport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration Google mappée depuis application.yml (préfixe "google").
 *
 * Les identifiants OAuth ne sont plus ici : c'est Spring Security qui les porte
 * (spring.security.oauth2.client.registration.google).
 */
@ConfigurationProperties(prefix = "google")
public class GoogleProperties {

    /** Nom d'application transmis aux clients Google (visible dans les logs/quotas). */
    private String applicationName = "sheets-export-poc";

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}
