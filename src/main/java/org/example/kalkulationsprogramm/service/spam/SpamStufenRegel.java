package org.example.kalkulationsprogramm.service.spam;

import java.util.Set;

import org.example.kalkulationsprogramm.domain.SpamStufe;

/**
 * Entscheidet aus Lernfilter-Wert B (0..1) und Regelpunkten R (0..100), wie sicher eine Mail
 * Spam ist. Spam wandert auch auf dem Mail-Server in den Spam-Ordner – deshalb wird nur bei sehr
 * sicheren Fällen {@link SpamStufe#SPAM} vergeben. Ein Fehlalarm ist schlimmer als
 * durchgelassener Spam.
 *
 * <ul>
 *   <li><b>SPAM:</b> (B ≥ 0,95 und R ≥ 25 und mindestens ein Signal außer Spam-Wörtern) oder (R ≥ 85 und B fehlt oder B &gt; 0,20). Der Lernfilter
 *       allein reicht nie: Im Probelauf gegen eine Prod-Kopie gab er sogar zugeordneten, also sicher
 *       echten Mails ≥ 0,99 (er ist großteils mit englischen SMS angelernt).</li>
 *   <li><b>UNSICHER:</b> gefährlicher Anhang (immer, auch bei harmlosem B).</li>
 *   <li><b>KEIN_SPAM:</b> B ≤ 0,20 und R &lt; 40.</li>
 *   <li><b>UNSICHER:</b> B ≥ 0,60 oder R ≥ 40.</li>
 *   <li>Sonst KEIN_SPAM.</li>
 * </ul>
 * Reine Logik ohne Datenbank.
 */
public final class SpamStufenRegel {

    /** Lernfilter sehr sicher – zusammen mit {@link #REGEL_MIT_LERNFILTER} reicht das für Spam. */
    public static final double LERNFILTER_SICHER = 0.95;

    /** Bis hierhin hält der Lernfilter die Mail für harmlos. */
    public static final double LERNFILTER_HARMLOS = 0.20;

    /** Ab hier ist der Lernfilter misstrauisch. */
    public static final double LERNFILTER_VERDACHT = 0.60;

    /** Mindestpunkte der Regeln, damit ein sehr sicherer Lernfilter Spam ergibt. */
    public static final int REGEL_MIT_LERNFILTER = 25;

    /** Ab hier sind die Regeln misstrauisch. */
    public static final int REGEL_VERDACHT = 40;

    /** Ab hier sind die Regeln sicher. */
    public static final int REGEL_SICHER = 85;

    /** Punkte, wenn zwei unabhängige Signale zusammenkommen. */
    public static final int PUNKTE_ZWEI_SIGNALE = 40;

    /** Punkte, wenn drei oder mehr unabhängige Signale zusammenkommen. */
    public static final int PUNKTE_DREI_SIGNALE = 70;

    /** Punkte für einen gefährlichen Anhang mit mindestens zwei weiteren Signalen. */
    public static final int PUNKTE_ANHANG_MIT_SIGNAL = 85;

    private static final double ANTEIL_REGELN = 0.4;
    private static final double ANTEIL_LERNFILTER = 60.0;

    private SpamStufenRegel() {
    }

    /**
     * @param lernfilter  B zwischen 0 und 1; {@code null}, wenn das Modell noch nicht bereit ist
     * @param regelPunkte R zwischen 0 und 100
     * @param signale     erkannte Signalgruppen der Mail
     */
    public static SpamStufe bestimme(Double lernfilter, int regelPunkte, Set<SpamSignal> signale) {
        Double b = lernfilter == null || lernfilter.isNaN() ? null : lernfilter;
        Set<SpamSignal> s = signale == null ? Set.of() : signale;

        // Der Lernfilter braucht einen zweiten, unabhängigen Zeugen: ein echtes Signal. Spam-Wörter
        // zählen dafür nicht – sie sind dieselbe Art Beleg wie der Lernfilter (Wörter im Text).
        if (b != null && b >= LERNFILTER_SICHER && regelPunkte >= REGEL_MIT_LERNFILTER && hatBelegSignal(s)) {
            return SpamStufe.SPAM;
        }
        if (regelPunkte >= REGEL_SICHER && (b == null || b > LERNFILTER_HARMLOS)) {
            return SpamStufe.SPAM;
        }
        // Inhaber-Entscheidung: Ein Anhang, der Programme starten kann, bekommt immer den Hinweis –
        // auch wenn der Lernfilter die Mail für harmlos hält. Verschoben wird dadurch nichts.
        if (s.contains(SpamSignal.GEFAEHRLICHER_ANHANG)) {
            return SpamStufe.UNSICHER;
        }
        if (b != null && b <= LERNFILTER_HARMLOS && regelPunkte < REGEL_VERDACHT) {
            return SpamStufe.KEIN_SPAM;
        }
        if ((b != null && b >= LERNFILTER_VERDACHT) || regelPunkte >= REGEL_VERDACHT) {
            return SpamStufe.UNSICHER;
        }
        return SpamStufe.KEIN_SPAM;
    }

    private static boolean hatBelegSignal(Set<SpamSignal> signale) {
        return signale.stream().anyMatch(signal -> signal != SpamSignal.SPAM_WOERTER);
    }

    /**
     * Punkte für zusammentreffende Signale: ein Signal allein gibt nichts (es trifft zu oft echte
     * Kunden), zwei geben {@value #PUNKTE_ZWEI_SIGNALE}, ab drei {@value #PUNKTE_DREI_SIGNALE}.
     * Ein gefährlicher Anhang mit mindestens zwei weiteren Signalen gibt {@value #PUNKTE_ANHANG_MIT_SIGNAL}
     * – mit nur einem nicht, denn Makro-Dateien (Aufmaß, LV-Kalkulation) sind im Handwerk üblich und
     * weitergeleitete Mails scheitern oft an DMARC.
     */
    public static int gruppenPunkte(Set<SpamSignal> signale) {
        int anzahl = signale == null ? 0 : signale.size();
        if (anzahl >= 3 && signale.contains(SpamSignal.GEFAEHRLICHER_ANHANG)) {
            return PUNKTE_ANHANG_MIT_SIGNAL;
        }
        if (anzahl >= 3) {
            return PUNKTE_DREI_SIGNALE;
        }
        return anzahl == 2 ? PUNKTE_ZWEI_SIGNALE : 0;
    }

    /**
     * Der angezeigte Spam-Wert (0..100): 0,4 · R + 60 · B, ohne Lernfilter nur R.
     * Er dient nur der Anzeige; entschieden wird über {@link #bestimme}.
     */
    public static int anzeigeWert(int regelPunkte, Double lernfilter) {
        double wert = lernfilter == null || lernfilter.isNaN()
                ? regelPunkte
                : ANTEIL_REGELN * regelPunkte + ANTEIL_LERNFILTER * lernfilter;
        return (int) Math.max(0, Math.min(100, Math.round(wert)));
    }
}
