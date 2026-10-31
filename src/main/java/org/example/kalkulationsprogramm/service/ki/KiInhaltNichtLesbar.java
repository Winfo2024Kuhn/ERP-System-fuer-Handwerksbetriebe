package org.example.kalkulationsprogramm.service.ki;

/**
 * Der Anbieter kann eine mitgeschickte Datei nicht lesen (Bild- oder Aufnahmeformat).
 * Erkannt vor dem Senden; ein zweiter Versuch ändert nichts. Die Meldung nennt nur
 * Anbieter und Dateiart und darf so zu den Nutzern.
 */
public class KiInhaltNichtLesbar extends KiAnbieterFehler {

    public KiInhaltNichtLesbar(String meldung) {
        super(meldung, 0, false);
    }
}
