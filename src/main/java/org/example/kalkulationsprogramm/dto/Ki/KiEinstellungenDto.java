package org.example.kalkulationsprogramm.dto.Ki;

import java.util.List;

/**
 * Kompletter Stand der KI-Einstellungen für Einstellungen → KI-Funktionen.
 * Jede Änderung liefert ihn neu, damit die Oberfläche nie raten muss.
 *
 * @param anbieter               gewählter Anbieter, z. B. {@code MISTRAL}
 * @param ersatzErlaubt          darf ein anderer Anbieter einspringen?
 * @param verschluesselungBereit kann der Server Schlüssel verschlüsselt ablegen?
 */
public record KiEinstellungenDto(
        String anbieter,
        boolean ersatzErlaubt,
        boolean verschluesselungBereit,
        List<Anbieter> anbieterListe,
        List<Funktion> funktionen) {

    /**
     * @param datenstandort {@code EU} oder {@code WELTWEIT}
     * @param faehigkeiten  z. B. {@code TEXT}, {@code BILD}, {@code PDF}
     */
    public record Anbieter(
            String typ,
            String name,
            String beschreibung,
            String datenstandort,
            boolean schluesselGesetzt,
            boolean schluesselVerschluesselt,
            List<String> faehigkeiten,
            List<Modell> modelle) {
    }

    /**
     * @param stufe          {@code SCHNELL}, {@code STANDARD} oder {@code GRUENDLICH}
     * @param modell         wirksames Modell
     * @param standardModell Modell, wenn nichts eingestellt ist
     */
    public record Modell(String stufe, String modell, String standardModell) {
    }

    /**
     * @param status      {@code AKTIV}, {@code ERSATZ} oder {@code AUS}
     * @param anbieter    {@code null} bei {@code AUS}
     * @param anbieterName {@code null} bei {@code AUS}
     * @param modell      {@code null} bei {@code AUS}
     * @param hinweis     bei {@code AKTIV} meist {@code null}; dort nur ein Zusatz wie „Läuft ohne Internet-Suche“
     * @param eigeneWahl  {@code null}, wenn die Funktion dem Standard-Anbieter folgt
     */
    public record Funktion(
            String funktion,
            String name,
            List<String> braucht,
            String status,
            String anbieter,
            String anbieterName,
            String modell,
            String hinweis,
            EigeneWahl eigeneWahl) {
    }

    /** @param modell {@code null} = Standard-Modell des Anbieters */
    public record EigeneWahl(String anbieter, String modell) {
    }
}
