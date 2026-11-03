package org.example.kalkulationsprogramm.domain.firmendokumente;

/**
 * Was eine Abteilung in einem eingeschränkten Zweig der Firmendokumente darf. Die Reihenfolge ist die Rangfolge:
 * Ist jemand in mehreren Abteilungen, gilt die höchste Stufe ({@link #hoechste}).
 */
public enum FirmendokumentZugriffsStufe {
    /** Sieht den Zweig gar nicht (wie „gibt es nicht“). */
    KEINE,
    /** Sieht Ordner und Dokumente und darf Dateien öffnen, aber nichts ändern. */
    ANSEHEN,
    /** Darf zusätzlich hochladen, umbenennen, verschieben und löschen. */
    AENDERN;

    public boolean darfAnsehen() {
        return this != KEINE;
    }

    public boolean darfAendern() {
        return this == AENDERN;
    }

    /** Die höhere von zwei Stufen; {@code null} zählt wie {@link #KEINE}. */
    public static FirmendokumentZugriffsStufe hoechste(FirmendokumentZugriffsStufe a, FirmendokumentZugriffsStufe b) {
        if (a == null) {
            return b == null ? KEINE : b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }
}
