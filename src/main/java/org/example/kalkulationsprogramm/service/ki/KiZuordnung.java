package org.example.kalkulationsprogramm.service.ki;

/**
 * Über wen eine KI-Funktion gerade läuft – und warum, wenn nicht über den
 * gewählten Anbieter.
 *
 * @param anbieter      {@code null} bei {@link Status#AUS}
 * @param modellVorgabe eigenes Modell aus der Wahl je Funktion, sonst {@code null} (dann gilt die Stufe)
 * @param hinweis       {@code null} bei {@link Status#AKTIV}, sonst ein Satz für die Oberfläche
 */
public record KiZuordnung(KiFunktion funktion, Status status, KiAnbieter anbieter, String modellVorgabe,
                          String hinweis) {

    public enum Status {
        /** Läuft über den gewählten Anbieter (Standard oder eigene Wahl der Funktion). */
        AKTIV,
        /** Der gewählte Anbieter kann es nicht oder hat keinen Schlüssel; ein anderer springt ein. */
        ERSATZ,
        /** Kein passender, eingerichteter Anbieter. */
        AUS
    }

    public boolean verfuegbar() {
        return status != Status.AUS;
    }
}
