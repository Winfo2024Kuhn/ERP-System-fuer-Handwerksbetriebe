package org.example.kalkulationsprogramm.domain;

/**
 * Warum ein Preisvorschlag unvollstaendig ist. Der DocumentEditor macht daraus
 * einen sichtbaren Hinweis an der Position - eingefuegt wird sie in jedem Fall,
 * und der Bediener kann den Preis immer selbst eintragen.
 *
 * <p>Wird bewusst NICHT als Datenbankspalte gespeichert: Der Hinweis beschreibt
 * den Pflegezustand der Stammdaten im Moment des Einfuegens, nicht die Position.
 */
public enum ArtikelPreisHinweis {
    /** Alles gepflegt, der Vorschlag ist belastbar. */
    OK,
    /** Kein Verkaufsaufschlag hinterlegt - der Vorschlag ist der reine Einkaufspreis. */
    KEIN_AUFSCHLAG,
    /** Kein aktueller Lieferantenpreis vorhanden - betrifft planmaessig alle lieferantenneutralen Werkstoffe. */
    KEIN_PREIS,
    /** Kilogramm-Artikel ohne Gewicht je Meter und ohne Gewicht je Quadratmeter. */
    KEIN_GEWICHT
}
