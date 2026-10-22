package org.example.kalkulationsprogramm.dto.Telefon;

/**
 * Ergebnis eines Laufs mit dem FRITZ!Box-Telefonbuch (Abgleich oder Löschen nach dem Ausschalten).
 *
 * @param erfolgreich  alles übertragen bzw. gelöscht
 * @param laeuftSchon  es lief gerade schon ein Lauf – nichts getan
 * @param meldung      Ergebnis bzw. Fehler in Klartext
 * @param neu          neu angelegte Einträge
 * @param geaendert    geänderte Einträge
 * @param geloescht    gelöschte Einträge
 * @param abgelehnt    Einträge, die die FRITZ!Box abgelehnt hat
 * @param eintraege    Einträge, die jetzt im Telefonbuch stehen
 */
public record TelefonbuchAbgleichErgebnisDto(boolean erfolgreich,
                                             boolean laeuftSchon,
                                             String meldung,
                                             int neu,
                                             int geaendert,
                                             int geloescht,
                                             int abgelehnt,
                                             int eintraege) {

    public static TelefonbuchAbgleichErgebnisDto nichtGelaufen(String meldung) {
        return new TelefonbuchAbgleichErgebnisDto(false, false, meldung, 0, 0, 0, 0, 0);
    }

    public static TelefonbuchAbgleichErgebnisDto beschaeftigt() {
        return new TelefonbuchAbgleichErgebnisDto(false, true,
                "Der Abgleich läuft gerade schon. Bitte gleich noch einmal versuchen.", 0, 0, 0, 0, 0);
    }
}
