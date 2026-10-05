package org.example.kalkulationsprogramm.exception;

import java.util.List;

/**
 * Die Firmenstammdaten reichen nicht aus, um ein Dokument rechtssicher zu erzeugen
 * (z. B. E-Rechnung ohne Anschrift oder Steuernummer des Betriebs). Die Meldung
 * ist für Anwender gedacht und nennt die fehlenden Angaben in Klartext.
 */
public class FirmenstammdatenUnvollstaendigException extends RuntimeException {

    private final List<String> fehlendeAngaben;

    public FirmenstammdatenUnvollstaendigException(String verwendungszweck, List<String> fehlendeAngaben) {
        super("Für " + verwendungszweck + " fehlen Angaben zu Ihrem Betrieb: "
                + String.join(", ", fehlendeAngaben)
                + ". Bitte unter „Firma“ ergänzen.");
        this.fehlendeAngaben = List.copyOf(fehlendeAngaben);
    }

    public List<String> getFehlendeAngaben() {
        return fehlendeAngaben;
    }
}
