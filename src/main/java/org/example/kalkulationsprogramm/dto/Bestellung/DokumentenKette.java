package org.example.kalkulationsprogramm.dto.Bestellung;

import java.util.List;

/**
 * Eine Bestellung als Kette zusammenhängender Lieferanten-Dokumente.
 *
 * @param verbindungen alle Verknüpfungen innerhalb der Kette, jede Kante einmal
 */
public record DokumentenKette(
        String id,
        Long lieferantId,
        String lieferantName,
        List<DokumentRef> dokumente,
        List<Verbindung> verbindungen,
        /** Nur bei laufenden Bestellungen: die wahrscheinlichste Rechnung. */
        RechnungsVorschlagDto rechnungsVorschlag) {

    public DokumentenKette(String id, Long lieferantId, String lieferantName, List<DokumentRef> dokumente) {
        this(id, lieferantId, lieferantName, dokumente, List.of(), null);
    }

    public DokumentenKette(String id, Long lieferantId, String lieferantName, List<DokumentRef> dokumente,
            List<Verbindung> verbindungen) {
        this(id, lieferantId, lieferantName, dokumente, verbindungen, null);
    }

    public DokumentenKette mitVorschlag(RechnungsVorschlagDto vorschlag) {
        return new DokumentenKette(id, lieferantId, lieferantName, dokumente, verbindungen, vorschlag);
    }
}
