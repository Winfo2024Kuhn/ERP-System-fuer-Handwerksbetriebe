package org.example.kalkulationsprogramm.domain;

/**
 * Zustell-Ergebnis einer versendeten E-Mail, ermittelt aus eingehenden
 * Unzustellbarkeits-Meldungen (DSN nach RFC 3464).
 *
 * <p>Es gibt bewusst kein {@code ZUGESTELLT}: SMTP kennt keine positive
 * Zustellbestaetigung. Dass der Relay des Providers die Mail annimmt, heisst
 * nur, dass er sie weiterleiten wird — ob sie beim Empfaenger ankommt, erfaehrt
 * der Absender ausschliesslich im Fehlerfall. {@link #OFFEN} bedeutet daher
 * "kein Fehler bekannt", nicht "nachweislich angekommen".</p>
 */
public enum ZustellStatus
{
    /** Kein Rueckläufer eingegangen — Regelfall fuer jede versendete Mail. */
    OFFEN,

    /**
     * Der Zielserver hat die Mail dauerhaft abgelehnt (z.B. Postfach existiert
     * nicht). Der Grund steht in {@code Email#zustellFehler}.
     */
    UNZUSTELLBAR
}
