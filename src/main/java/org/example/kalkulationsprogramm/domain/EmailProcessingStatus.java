package org.example.kalkulationsprogramm.domain;

/**
 * Verarbeitungsstatus einer Email in der Queue.
 */
public enum EmailProcessingStatus {
    /**
     * Email wurde erkannt, wartet auf Verarbeitung.
     */
    QUEUED,
    
    /**
     * Email wird gerade verarbeitet.
     */
    PROCESSING,
    
    /**
     * Email wurde erfolgreich verarbeitet.
     */
    DONE,
    
    /**
     * Verarbeitung ist fehlgeschlagen.
     */
    ERROR
}
