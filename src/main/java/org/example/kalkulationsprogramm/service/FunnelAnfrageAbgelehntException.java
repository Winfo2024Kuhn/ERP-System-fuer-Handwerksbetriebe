package org.example.kalkulationsprogramm.service;

/**
 * Wird vom {@link AnfrageFunnelSpamFilterService} bzw. {@link AnfrageFunnelService}
 * geworfen, wenn eine eingehende Webseiten-Anfrage als Spaß-/Spam-Anfrage erkannt
 * wurde. Der Controller wandelt das in HTTP 422 mit einer kurzen Begründung um,
 * damit die Webseite dem Absender eine sinnvolle Fehlermeldung zeigen kann.
 */
public class FunnelAnfrageAbgelehntException extends RuntimeException {
    public FunnelAnfrageAbgelehntException(String grund) {
        super(grund);
    }
}
