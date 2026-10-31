package org.example.kalkulationsprogramm.service.ki;

/**
 * Ein Aufruf beim KI-Anbieter ist gescheitert. Die Meldung ist ein deutscher
 * Satz ohne Inhalte der Anfrage oder Antwort (DSGVO, Geschäftsdaten) und ohne
 * Schlüssel – sie darf ins Log und zu den Nutzern.
 */
public class KiAnbieterFehler extends RuntimeException {

    private final int status;
    private final boolean voruebergehend;

    public KiAnbieterFehler(String meldung, int status, boolean voruebergehend) {
        super(meldung);
        this.status = status;
        this.voruebergehend = voruebergehend;
    }

    public KiAnbieterFehler(String meldung, boolean voruebergehend, Throwable ursache) {
        super(meldung, ursache);
        this.status = 0;
        this.voruebergehend = voruebergehend;
    }

    /** HTTP-Status der Antwort, 0 ohne Antwort (Netzwerk, Zeitlimit). */
    public int status() {
        return status;
    }

    /** Lohnt ein zweiter Versuch (Überlastung, Störung, Zeitlimit)? */
    public boolean voruebergehend() {
        return voruebergehend;
    }
}
