package org.example.kalkulationsprogramm.service;

/**
 * Fachliche Ablehnung beim Zuordnen, Abhängen oder Hochladen von Belegen
 * ("Nur PDF, JPG oder PNG erlaubt."). Die Meldung ist für den Nutzer gedacht
 * und darf unverändert angezeigt werden – anders als technische Meldungen.
 */
public class BelegAbgelehntException extends IllegalArgumentException {

    public BelegAbgelehntException(String meldung) {
        super(meldung);
    }
}
