package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;

/**
 * SOAP-Fehler der FRITZ!Box mit UPnP-Fehlercode (z.B. 713 = Index gibt es nicht,
 * 501 = Aktion fehlgeschlagen). Nach außen eine gewöhnliche
 * {@link TelefonAnlageException} mit Grund UNERWARTETE_ANTWORT; die
 * FRITZ!Box-Umsetzung kann über den Code gezielt reagieren. Das Detail nennt
 * Aktion und Code, damit man im Log und auf der Einstellungsseite sieht, woran es lag.
 */
class SoapFehler extends TelefonAnlageException {

    private final int code;

    SoapFehler(int code, String detail) {
        super(Grund.UNERWARTETE_ANTWORT, detail, null);
        this.code = code;
    }

    /** UPnP-Fehlercode oder 0, wenn die Box keinen mitgeschickt hat. */
    int code() {
        return code;
    }
}
