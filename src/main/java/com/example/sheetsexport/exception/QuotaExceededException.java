package com.example.sheetsexport.exception;

/**
 * Levée quand l'API Google renvoie 429 même après épuisement des retries
 * (mappée en HTTP 429 par le ControllerAdvice).
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
