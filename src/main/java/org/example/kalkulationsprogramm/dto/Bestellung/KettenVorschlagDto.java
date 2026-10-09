package org.example.kalkulationsprogramm.dto.Bestellung;

import java.util.List;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;

/**
 * Ein Dokument, das wahrscheinlich noch in eine Kette gehört – für das Fenster
 * „Dokument zur Kette hinzufügen“.
 *
 * @param dokument           das vorgeschlagene Dokument (beliebige Art)
 * @param kettenDokumentId   an dieses Dokument der Kette wird es gehängt
 * @param trefferquote       0–100 %
 * @param eindeutig          {@code false}, wenn ein zweites Dokument genauso gut passt
 * @param gehoertSchonZu     {@code null} oder das Dokument einer anderen Kette, an dem
 *                           es schon hängt, z. B. „Lieferschein LS-4711“
 */
public record KettenVorschlagDto(
        DokumentRef dokument,
        String lieferantName,
        Long kettenDokumentId,
        LieferantDokumentTyp kettenDokumentTyp,
        String kettenDokumentNummer,
        int trefferquote,
        boolean sicher,
        boolean eindeutig,
        List<String> gruende,
        String gehoertSchonZu) {
}
