package org.example.kalkulationsprogramm.service.ki;

/**
 * Für eine KI-Funktion ist gerade kein passender Anbieter eingerichtet. Die
 * Meldung ist derselbe Hinweis, den die Einstellungsseite bei der Funktion zeigt.
 */
public class KiNichtVerfuegbar extends RuntimeException {

    private final KiFunktion funktion;

    public KiNichtVerfuegbar(KiFunktion funktion, String hinweis) {
        super(hinweis);
        this.funktion = funktion;
    }

    public KiFunktion funktion() {
        return funktion;
    }
}
