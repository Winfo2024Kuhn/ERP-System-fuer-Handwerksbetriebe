package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;

/**
 * SOAP-Fehler der FRITZ!Box mit UPnP-Fehlercode (z.B. 713 = Index gibt es nicht,
 * 501 = Aktion fehlgeschlagen). Nach außen eine gewöhnliche
 * {@link TelefonAnlageException} mit Grund UNERWARTETE_ANTWORT; die
 * FRITZ!Box-Umsetzung kann über den Code gezielt reagieren.
 */
class SoapFehler extends TelefonAnlageException {

    private final int code;

    SoapFehler(int code) {
        super(Grund.UNERWARTETE_ANTWORT);
        this.code = code;
    }

    /** UPnP-Fehlercode oder 0, wenn die Box keinen mitgeschickt hat. */
    int code() {
        return code;
    }
}
