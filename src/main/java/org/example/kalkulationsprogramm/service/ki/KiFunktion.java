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
            KiModellStufe.STANDARD, EnumSet.of(KiFaehigkeit.PDF, KiFaehigkeit.BILD)),
    EMAIL_ZUORDNUNG("E-Mails Projekten und Anfragen zuordnen",
            KiModellStufe.SCHNELL, EnumSet.of(KiFaehigkeit.TEXT)),
    EMAIL_TEXT("E-Mail-Texte verbessern",
            KiModellStufe.SCHNELL, EnumSet.of(KiFaehigkeit.TEXT)),
    SCAN_BENENNUNG("Namen für gescannte Dokumente vorschlagen",
            KiModellStufe.STANDARD, EnumSet.of(KiFaehigkeit.BILD)),
    BEITRAG_ENTWURF("Beiträge für die Website entwerfen",
            KiModellStufe.GRUENDLICH, EnumSet.of(KiFaehigkeit.TEXT, KiFaehigkeit.BILD)),
    KOSTENKONTO_VORSCHLAG("Kostenkonto für Belege vorschlagen",
            KiModellStufe.STANDARD, EnumSet.of(KiFaehigkeit.WERKZEUGE, KiFaehigkeit.PDF, KiFaehigkeit.BILD)),
    /**
     * Die Internet-Suche ist ein Zusatz: Ohne sie beantwortet die Hilfe weiter Fragen zum
     * Programm. Ihr Programm-Wissen sucht sie nur über Gemini auch nach Bedeutung
     * ({@link KiDienst#geminiEinbettungsSchluessel(KiFunktion)}), sonst nach Wörtern.
     */
    PROGRAMM_HILFE("Programm-Hilfe (Fragen zum Programm)",
            KiModellStufe.STANDARD, EnumSet.of(KiFaehigkeit.WERKZEUGE), EnumSet.of(KiFaehigkeit.WEBSUCHE)),
    SPRACHEINGABE("Spracheingabe in der Handy-App",
            KiModellStufe.STANDARD, EnumSet.of(KiFaehigkeit.AUDIO));

    private final String bezeichnung;
    private final KiModellStufe stufe;
    private final Set<KiFaehigkeit> braucht;
    private final Set<KiFaehigkeit> nutztWennMoeglich;

    KiFunktion(String bezeichnung, KiModellStufe stufe, Set<KiFaehigkeit> braucht) {
        this(bezeichnung, stufe, braucht, EnumSet.noneOf(KiFaehigkeit.class));
    }

    KiFunktion(String bezeichnung, KiModellStufe stufe, Set<KiFaehigkeit> braucht,
               Set<KiFaehigkeit> nutztWennMoeglich) {
        this.bezeichnung = bezeichnung;
        this.stufe = stufe;
        this.braucht = braucht;
        this.nutztWennMoeglich = nutztWennMoeglich;
    }

    public String bezeichnung() {
        return bezeichnung;
    }

    /** Modellstufe, die die Funktion ohne besonderen Grund nutzt. */
    public KiModellStufe stufe() {
        return stufe;
    }

    /** Ohne diese Fähigkeiten läuft die Funktion nicht über einen Anbieter. */
    public Set<KiFaehigkeit> braucht() {
        return EnumSet.copyOf(braucht);
    }

    /** Zusätze: kann der Anbieter sie nicht, läuft die Funktion ohne – mit Hinweis in den Einstellungen. */
    public Set<KiFaehigkeit> nutztWennMoeglich() {
        return nutztWennMoeglich.isEmpty() ? EnumSet.noneOf(KiFaehigkeit.class) : EnumSet.copyOf(nutztWennMoeglich);
    }
}
