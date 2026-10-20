package org.example.kalkulationsprogramm.dto.Bestellung;

import java.util.List;

/**
 * Die Bestellübersicht: Dokumenten-Ketten, gruppiert nach Status, neueste zuerst. Jede Liste
 * enthält höchstens so viele Ketten wie angefordert; {@link #gesamt()} sagt, wie viele es
 * (nach der Suche) insgesamt sind – für Reiter-Zähler und „Mehr laden“.
 */
public record BestellungsUebersichtDto(
        List<DokumentenKette> offeneAnfragen,
        List<DokumentenKette> laufendeBestellungen,
        List<DokumentenKette> abgeschlossen,
        List<DokumentenKette> zugeordnet,
        List<DokumentenKette> ausgeblendet,
        Gesamt gesamt) {

    /** Ohne Kürzung: Gesamtzahlen = Listenlängen (alle offenen Anfragen zählen als aktuell). */
    public BestellungsUebersichtDto(List<DokumentenKette> offeneAnfragen, List<DokumentenKette> laufendeBestellungen,
            List<DokumentenKette> abgeschlossen, List<DokumentenKette> zugeordnet, List<DokumentenKette> ausgeblendet) {
        this(offeneAnfragen, laufendeBestellungen, abgeschlossen, zugeordnet, ausgeblendet,
                new Gesamt(offeneAnfragen.size(), offeneAnfragen.size(), laufendeBestellungen.size(),
                        laufendeBestellungen.size(), abgeschlossen.size(), zugeordnet.size(), ausgeblendet.size()));
    }

    /**
     * Anzahl Ketten je Gruppe nach der Suche, vor dem Kürzen.
     *
     * @param offeneAnfragenAktuell       offene Anfragen, die sich in den letzten 60 Tagen bewegt haben
     *                                    (Zähler am Reiter „Angebote“; ältere stehen eingeklappt darunter)
     * @param laufendeBestellungenAktuell laufende Bestellungen der letzten 60 Tage; die übrigen stehen
     *                                    unter „Seit über 2 Monaten keine Rechnung“
     */
    public record Gesamt(int offeneAnfragen, int offeneAnfragenAktuell, int laufendeBestellungen,
            int laufendeBestellungenAktuell, int abgeschlossen, int zugeordnet, int ausgeblendet) {
    }
}
