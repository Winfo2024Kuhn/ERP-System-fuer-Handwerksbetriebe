package org.example.kalkulationsprogramm.dto.Bestellung;

import java.util.List;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;

/**
 * @param rechnung          die vorgeschlagene Rechnung
 * @param bestellDokumentId an dieses Dokument der Bestellung wird sie gehängt
 * @param trefferquote      0–100 %
 * @param eindeutig         {@code false}, wenn eine zweite Rechnung genauso gut passt
 * @param gehoertSchonZu    {@code null} oder das Bestelldokument, an dem die Rechnung
 *                          schon hängt, z. B. „Lieferschein LS-4711“
 */
public record RechnungsVorschlagDto(
        DokumentRef rechnung,
        String lieferantName,
        Long bestellDokumentId,
        LieferantDokumentTyp bestellDokumentTyp,
        String bestellDokumentNummer,
        int trefferquote,
        boolean sicher,
        boolean eindeutig,
        List<String> gruende,
        String gehoertSchonZu) {
}
