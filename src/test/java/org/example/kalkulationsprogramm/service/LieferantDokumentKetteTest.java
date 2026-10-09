package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.Bestellung.Verbindung;
import org.junit.jupiter.api.Test;

class LieferantDokumentKetteTest {

    private static final Set<LieferantDokumentTyp> ALLE = EnumSet.allOf(LieferantDokumentTyp.class);

    private static LieferantDokument dokument(long id, LieferantDokumentTyp typ, LocalDate datum) {
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setTyp(typ);
        if (datum != null) {
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setDokument(d);
            gd.setDokumentDatum(datum);
            d.setGeschaeftsdaten(gd);
        }
        return d;
    }

    /** Wie JPA: beide Seiten der Verknüpfung (Nachfolger -> Vorgänger). */
    private static void verknuepfe(LieferantDokument nachfolger, LieferantDokument vorgaenger) {
        nachfolger.getVerknuepfteDokumente().add(vorgaenger);
        vorgaenger.getVerknuepftVon().add(nachfolger);
    }

    @Test
    void sammeltUeberUmwegeInBeideRichtungen() {
        // Rechnung -> AB <- Lieferschein <- Zeugnis; das Angebot hängt an keinem
        LieferantDokument ab = dokument(1, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, null);
        LieferantDokument lieferschein = dokument(2, LieferantDokumentTyp.LIEFERSCHEIN, null);
        LieferantDokument zeugnis = dokument(3, LieferantDokumentTyp.WERKSTOFFZEUGNIS, null);
        LieferantDokument rechnung = dokument(4, LieferantDokumentTyp.RECHNUNG, null);
        LieferantDokument fremd = dokument(5, LieferantDokumentTyp.ANGEBOT, null);
        verknuepfe(rechnung, ab);
        verknuepfe(lieferschein, ab);
        verknuepfe(zeugnis, lieferschein);

        assertThat(LieferantDokumentKette.sammle(rechnung))
                .containsExactlyInAnyOrder(ab, lieferschein, zeugnis, rechnung)
                .doesNotContain(fremd);
        assertThat(LieferantDokumentKette.sammle(fremd)).containsExactly(fremd);
        assertThat(LieferantDokumentKette.sammle(null)).isEmpty();
    }

    @Test
    void sichtbarSortiertNachAblaufUndDatum() {
        LieferantDokument rechnung = dokument(10, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2026, 4, 2));
        LieferantDokument ab = dokument(11, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, LocalDate.of(2026, 3, 14));
        LieferantDokument ls2 = dokument(12, LieferantDokumentTyp.LIEFERSCHEIN, LocalDate.of(2026, 3, 27));
        LieferantDokument ls1 = dokument(13, LieferantDokumentTyp.LIEFERSCHEIN, LocalDate.of(2026, 3, 20));
        LieferantDokument ohneDatum = dokument(14, LieferantDokumentTyp.LIEFERSCHEIN, null);
        LieferantDokument sonstiges = dokument(15, LieferantDokumentTyp.SONSTIG, null);
        LieferantDokument gutschrift = dokument(16, LieferantDokumentTyp.GUTSCHRIFT, null);
        verknuepfe(rechnung, ab);
        verknuepfe(ls2, ab);
        verknuepfe(ls1, ab);
        verknuepfe(ohneDatum, ab);
        verknuepfe(sonstiges, ab);
        verknuepfe(gutschrift, rechnung);

        assertThat(LieferantDokumentKette.sichtbar(rechnung, ALLE))
                .containsExactly(ab, ls1, ls2, ohneDatum, sonstiges, rechnung, gutschrift);
    }

    @Test
    void sichtbarLaesstVersteckteTypenWegAuchInDerMitte() {
        // Rechnung -> AB (nicht sichtbar) <- Lieferschein: die Kette läuft durch die AB,
        // die AB selbst und ihre Kanten erscheinen aber nicht
        LieferantDokument rechnung = dokument(20, LieferantDokumentTyp.RECHNUNG, null);
        LieferantDokument ab = dokument(21, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, null);
        LieferantDokument lieferschein = dokument(22, LieferantDokumentTyp.LIEFERSCHEIN, null);
        verknuepfe(rechnung, ab);
        verknuepfe(lieferschein, ab);

        List<LieferantDokument> kette = LieferantDokumentKette.sichtbar(rechnung,
                EnumSet.of(LieferantDokumentTyp.RECHNUNG, LieferantDokumentTyp.LIEFERSCHEIN));

        assertThat(kette).containsExactly(lieferschein, rechnung);
        assertThat(LieferantDokumentKette.verbindungen(kette)).isEmpty();
    }

    @Test
    void verbindungenNurInnerhalbDerKetteUndJedeEinmal() {
        LieferantDokument lieferschein = dokument(2, LieferantDokumentTyp.LIEFERSCHEIN, null);
        LieferantDokument zeugnis = dokument(3, LieferantDokumentTyp.WERKSTOFFZEUGNIS, null);
        LieferantDokument draussen = dokument(9, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, null);
        verknuepfe(zeugnis, lieferschein);
        // doppelt gespeichert (beide Richtungen) zählt nur einmal
        verknuepfe(lieferschein, zeugnis);
        verknuepfe(lieferschein, draussen);

        List<Verbindung> verbindungen = LieferantDokumentKette.verbindungen(List.of(lieferschein, zeugnis));

        assertThat(verbindungen).hasSize(1);
        assertThat(Set.of(verbindungen.get(0).vonId(), verbindungen.get(0).zuId())).containsExactlyInAnyOrder(2L, 3L);
    }
}
