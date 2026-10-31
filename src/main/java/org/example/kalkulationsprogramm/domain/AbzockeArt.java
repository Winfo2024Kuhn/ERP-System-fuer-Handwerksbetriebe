package org.example.kalkulationsprogramm.domain;

/**
 * Bekannte Betrugsmaschen gegen Handwerksbetriebe. Eine erkannte Masche zeigt nur einen
 * Warnhinweis an der Mail; sie verschiebt nie etwas.
 */
public enum AbzockeArt {

    /** Kostenpflichtiger Eintrag in einem Branchenbuch, getarnt als Korrekturabzug oder Rechnung. */
    BRANCHENBUCH,

    /** Angebliche Google-Mail zum Unternehmensprofil (Verifizierung, Löschung, Gebühr). */
    GOOGLE_PROFIL,

    /** Angebliche Post von Gericht, Behörde oder Zoll mit Zahlungsaufforderung. */
    BEHOERDE_GERICHT,

    /** Mitteilung einer neuen Bankverbindung – typische Masche bei gefälschten Lieferantenrechnungen. */
    BANKVERBINDUNG_GEAENDERT
}
