package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentRef;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentenKette;
import org.junit.jupiter.api.Test;

/** Gleiche Regeln wie {@code passtZurSuche}/{@code istAlt} im Frontend (bestellungenListe.ts). */
class BestellungsKettenSucheTest {

    private static DokumentRef dok(LieferantDokumentTyp typ, String nummer, LocalDate datum, Double betrag) {
        DokumentRef d = new DokumentRef();
        d.typ = typ;
        d.dokumentNummer = nummer;
        d.dokumentDatum = datum;
        d.betragBrutto = betrag;
        d.dateiname = "ab_4711.pdf";
        return d;
    }

    private static DokumentenKette kette(DokumentRef... doks) {
        return new DokumentenKette("k1", 1L, "Max Mustermann Stahlhandel", List.of(doks));
    }

    private final DokumentenKette kette = kette(
            dok(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-2026-17", LocalDate.of(2026, 3, 5), 1234.5));

    @Test
    void findetLieferantNummerArtDateiBetragUndDatum() {
        for (String suche : List.of("mustermann", "ab-2026-17", "AB stahlhandel", "4711", "1.234,50", "1234,50 €",
                "05.03.2026", "")) {
            assertThat(BestellungsKettenSuche.passt(kette, suche)).as(suche).isTrue();
        }
        assertThat(BestellungsKettenSuche.passt(kette, null)).isTrue();
        // geschütztes Leerzeichen trennt Wörter wie im Browser
        assertThat(BestellungsKettenSuche.passt(kette, "mustermann\u00A0stahlhandel")).isTrue();
    }

    @Test
    void betragRundetHalbeCentsAufWieDerBrowser() {
        DokumentenKette k = kette(dok(LieferantDokumentTyp.RECHNUNG, null, null, 0.125));
        assertThat(BestellungsKettenSuche.passt(k, "0,13")).isTrue();
    }

    @Test
    void alleWoerterMuessenPassen() {
        assertThat(BestellungsKettenSuche.passt(kette, "mustermann rechnung")).isFalse();
        assertThat(BestellungsKettenSuche.passt(kette, "<script>alert(1)</script>")).isFalse();
        assertThat(BestellungsKettenSuche.passt(kette, "'; DROP TABLE lieferant_dokument; --")).isFalse();
    }

    @Test
    void ohneAngabenKeinFehler() {
        DokumentRef leer = new DokumentRef();
        DokumentenKette ohne = new DokumentenKette("k2", null, null, List.of(leer));
        assertThat(BestellungsKettenSuche.passt(ohne, "x")).isFalse();
        assertThat(BestellungsKettenSuche.istAlt(ohne, LocalDate.of(2026, 10, 20))).isFalse();
    }

    @Test
    void altAbSechzigTagenOhneBewegung() {
        DokumentRef eingang = dok(LieferantDokumentTyp.ANGEBOT, null, null, null);
        eingang.eingangsDatum = LocalDate.of(2026, 8, 21);
        DokumentenKette angebot = kette(eingang);
        assertThat(BestellungsKettenSuche.istAlt(angebot, LocalDate.of(2026, 10, 20))).isFalse(); // 60 Tage
        assertThat(BestellungsKettenSuche.istAlt(angebot, LocalDate.of(2026, 10, 21))).isTrue();  // 61 Tage
    }
}
