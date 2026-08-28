package com.example.sheetsexport.exception;

/**
 * Access token OAuth manquant, mal formé, invalide, expiré ou aux scopes insuffisants
 * (mappée en HTTP 401 par le ControllerAdvice).
 */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException(String message) {
        super(message);
    }

    public InvalidAccessTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
