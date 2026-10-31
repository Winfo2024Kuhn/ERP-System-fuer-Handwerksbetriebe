package org.example.kalkulationsprogramm.service.spam;

/**
 * Unabhängige Hinweise auf Spam. Jede Gruppe zählt höchstens einmal; Punkte gibt es erst,
 * wenn mehrere zusammenkommen (ein einzelnes Signal allein trifft zu oft echte Kunden).
 * Die Kürzel landen in {@code Email.spamGruende} und im Bayes-Zerleger ({@code signal_<kürzel>}).
 */
public enum SpamSignal {

    /** Der Anzeigename nennt eine Marke oder fremde Domain, die nicht zur Absender-Adresse passt. */
    ABSENDER_GEFAELSCHT,

    /** Antworten gehen an eine andere Haupt-Domain als der Absender (Reply-To). */
    ANTWORT_UMGELEITET,

    /** Anhang, der Programme oder Makros ausführen kann (.exe, .js, .lnk, .docm …). */
    GEFAEHRLICHER_ANHANG,

    /** Der empfangende Server meldet {@code dmarc=fail}. */
    ECHTHEIT_FEHLGESCHLAGEN,

    /** Der Mail-Anbieter hat die Mail selbst als Spam markiert ({@code X-Spam-Flag: YES}). */
    ANBIETER_SPAM,

    /** Die Mail hat kein Sendedatum. */
    DATUM_FEHLT,

    /** Die Mail besteht fast nur aus Bild und Link. */
    NUR_BILD_MIT_LINK,

    /** Typische Spam-Wörter im Betreff oder Text. */
    SPAM_WOERTER
}
