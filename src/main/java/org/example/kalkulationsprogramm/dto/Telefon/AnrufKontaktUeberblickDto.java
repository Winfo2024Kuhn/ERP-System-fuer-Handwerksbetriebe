package org.example.kalkulationsprogramm.dto.Telefon;

import java.util.List;

/**
 * Alles, was das Anruf-Fenster über den Anrufer zeigt: Stammdaten sowie
 * Projekte und Anfragen, in die man direkt springen kann.
 *
 * @param nummer          Kundennummer (nur bei Kunden)
 * @param ansprechpartner beim Kunden der Ansprechpartner, beim Lieferanten der Vertreter
 * @param projekte        offene zuerst, dann die neuesten; höchstens {@code MAX_EINTRAEGE} (nur bei Kunden)
 * @param projekteGesamt  alle Projekte des Kunden, auch die nicht mitgelieferten
 * @param anfragen        offene zuerst, dann die neuesten; höchstens {@code MAX_EINTRAEGE} (nur bei Kunden)
 * @param anfragenGesamt  alle Anfragen des Kunden, auch die nicht mitgelieferten
 */
public record AnrufKontaktUeberblickDto(String typ,
                                        Long id,
                                        String name,
                                        String nummer,
                                        String ansprechpartner,
                                        String strasse,
                                        String plz,
                                        String ort,
                                        List<Projekt> projekte,
                                        long projekteGesamt,
                                        List<Anfrage> anfragen,
                                        long anfragenGesamt) {

    public static final int MAX_EINTRAEGE = 50;

    public record Projekt(Long id, String bauvorhaben, String auftragsnummer, String ort, boolean abgeschlossen) {
    }

    /** @param angebotsnummer Nummer des Angebots zur Anfrage, falls schon eins geschrieben ist */
    public record Anfrage(Long id, String bauvorhaben, String angebotsnummer, String ort, boolean abgeschlossen) {
    }
}
