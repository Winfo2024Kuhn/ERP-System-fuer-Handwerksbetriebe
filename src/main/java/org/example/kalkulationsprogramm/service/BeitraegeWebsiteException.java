package org.example.kalkulationsprogramm.service;

/**
 * Wird von {@link BeitraegeWebsiteClient} geworfen, wenn der Aufruf der
 * internen Beiträge-API der Website fehlschlägt: Netzwerkfehler
 * (IOException, InterruptedException) oder ein HTTP-Status >= 400.
 * Der {@code statusCode} ist bei Netzwerkfehlern {@code null}, bei einer
 * fehlerhaften HTTP-Antwort der zurückgegebene Statuscode, damit der
 * aufrufende Controller gezielt darauf reagieren kann (z. B. 401 vom
 * eigenen Endpunkt weiterreichen statt pauschal 500).
 */
public class BeitraegeWebsiteException extends RuntimeException {

    private final Integer statusCode;

    public BeitraegeWebsiteException(String message) {
        super(message);
        this.statusCode = null;
    }

    public BeitraegeWebsiteException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public BeitraegeWebsiteException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
