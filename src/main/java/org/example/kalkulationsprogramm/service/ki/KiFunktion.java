package org.example.kalkulationsprogramm.service.ki;

import java.util.EnumSet;
import java.util.Set;

/**
 * Die KI-Funktionen des Programms, so wie die Einstellungsseite sie zeigt.
 * Jede Funktion nennt, welche Fähigkeiten ein Anbieter dafür braucht; daraus
 * ergibt sich, über wen sie läuft ({@link KiDienst#zuordnung(KiFunktion)}).
 */
public enum KiFunktion {
    DOKUMENT_ANALYSE("Rechnungen, Lieferscheine und Belege auslesen",
            KiModellStufe.STANDARD, KiFaehigkeit.PDF, KiFaehigkeit.BILD),
    EMAIL_ZUORDNUNG("E-Mails Projekten und Anfragen zuordnen",
            KiModellStufe.SCHNELL, KiFaehigkeit.TEXT),
    EMAIL_TEXT("E-Mail-Texte verbessern",
            KiModellStufe.SCHNELL, KiFaehigkeit.TEXT),
    SCAN_BENENNUNG("Namen für gescannte Dokumente vorschlagen",
            KiModellStufe.STANDARD, KiFaehigkeit.BILD),
    BEITRAG_ENTWURF("Beiträge für die Website entwerfen",
            KiModellStufe.GRUENDLICH, KiFaehigkeit.TEXT, KiFaehigkeit.BILD),
    KOSTENKONTO_VORSCHLAG("Kostenkonto für Belege vorschlagen",
            KiModellStufe.STANDARD, KiFaehigkeit.WERKZEUGE, KiFaehigkeit.PDF, KiFaehigkeit.BILD),
    PROGRAMM_HILFE("Programm-Hilfe (Fragen zum Programm)",
            KiModellStufe.STANDARD, KiFaehigkeit.WERKZEUGE, KiFaehigkeit.WISSENSSUCHE),
    SPRACHEINGABE("Spracheingabe in der Handy-App",
            KiModellStufe.STANDARD, KiFaehigkeit.AUDIO);

    private final String bezeichnung;
    private final KiModellStufe stufe;
    private final Set<KiFaehigkeit> braucht;

    KiFunktion(String bezeichnung, KiModellStufe stufe, KiFaehigkeit erste, KiFaehigkeit... weitere) {
        this.bezeichnung = bezeichnung;
        this.stufe = stufe;
        this.braucht = EnumSet.of(erste, weitere);
    }

    public String bezeichnung() {
        return bezeichnung;
    }

    /** Modellstufe, die die Funktion ohne besonderen Grund nutzt. */
    public KiModellStufe stufe() {
        return stufe;
    }

    public Set<KiFaehigkeit> braucht() {
        return EnumSet.copyOf(braucht);
    }
}
