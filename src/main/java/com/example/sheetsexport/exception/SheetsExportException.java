package com.example.sheetsexport.exception;

/**
 * Erreur générique lors de la génération du Sheet (mappée en HTTP 502 par le ControllerAdvice).
 */
public class SheetsExportException extends RuntimeException {

    public SheetsExportException(String message, Throwable cause) {
        super(message, cause);
    }

    public SheetsExportException(String message) {
        super(message);
    }
}
