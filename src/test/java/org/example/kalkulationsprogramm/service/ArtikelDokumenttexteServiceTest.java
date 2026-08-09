package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumenttexteRequest;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtikelDokumenttexteServiceTest {

    @Mock
    private ArtikelRepository artikelRepository;

    @InjectMocks
    private ArtikelService artikelService;

    /** Sendet alle drei Felder ausdruecklich mit - auch wenn ein Wert null ist. */
    private static ArtikelDokumenttexteRequest request(String kurz, String beschreibung, String aufschlag) {
        ArtikelDokumenttexteRequest r = new ArtikelDokumenttexteRequest();
        r.setKurzbeschreibung(kurz);
        r.setBeschreibung(beschreibung);
        r.setVerkaufsaufschlagProzent(aufschlag == null ? null : new BigDecimal(aufschlag));
        return r;
    }

    @Test
    void schreibtDieDreiFelder() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        Artikel ergebnis = artikelService.aktualisiereDokumenttexte(
                7L, request("Rundrohr 42,4 Lager", "<p>Rundrohr, Edelstahl</p>", "40.00"));

        assertThat(ergebnis.getKurzbeschreibung()).isEqualTo("Rundrohr 42,4 Lager");
        assertThat(ergebnis.getBeschreibung()).isEqualTo("<p>Rundrohr, Edelstahl</p>");
        assertThat(ergebnis.getVerkaufsaufschlagProzent()).isEqualByComparingTo("40.00");
    }

    // ==================================================================
    // Echtes Teil-Update: nicht mitgesendete Felder bleiben unangetastet,
    // ausdruecklich mitgesendetes null loescht.
    // ==================================================================

    @Test
    void nichtMitgesendeteFelderBleibenUnveraendert() {
        Artikel artikel = new Artikel();
        artikel.setKurzbeschreibung("Alter Kurztext");
        artikel.setBeschreibung("<p>Alter Kundentext</p>");
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        // Nur der Aufschlag wird gesetzt - kurzbeschreibung/beschreibung werden
        // nie angefasst, duerfen also nicht geloescht werden.
        ArtikelDokumenttexteRequest nurAufschlag = new ArtikelDokumenttexteRequest();
        nurAufschlag.setVerkaufsaufschlagProzent(new BigDecimal("45.00"));

        Artikel ergebnis = artikelService.aktualisiereDokumenttexte(7L, nurAufschlag);

        assertThat(ergebnis.getKurzbeschreibung()).isEqualTo("Alter Kurztext");
        assertThat(ergebnis.getBeschreibung()).isEqualTo("<p>Alter Kundentext</p>");
        assertThat(ergebnis.getVerkaufsaufschlagProzent()).isEqualByComparingTo("45.00");
    }

    @Test
    void ausdruecklichMitgesendetesNullLoeschtDasFeld() {
        Artikel artikel = new Artikel();
        artikel.setKurzbeschreibung("Alter Kurztext");
        artikel.setBeschreibung("<p>Alter Kundentext</p>");
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        // kurzbeschreibung wird ausdruecklich auf null gesetzt (Setter wird
        // aufgerufen), beschreibung wird gar nicht angefasst.
        ArtikelDokumenttexteRequest nurKurzLoeschen = new ArtikelDokumenttexteRequest();
        nurKurzLoeschen.setKurzbeschreibung(null);

        Artikel ergebnis = artikelService.aktualisiereDokumenttexte(7L, nurKurzLoeschen);

        assertThat(ergebnis.getKurzbeschreibung()).isNull();
        assertThat(ergebnis.getBeschreibung()).isEqualTo("<p>Alter Kundentext</p>");
    }

    @Test
    void entferntSkriptMarkupAusDerKundenbeschreibung() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        Artikel ergebnis = artikelService.aktualisiereDokumenttexte(
                7L, request(null, "<p>Rohr</p><script>alert(1)</script>", null));

        assertThat(ergebnis.getBeschreibung()).doesNotContain("script");
        assertThat(ergebnis.getBeschreibung()).contains("Rohr");
    }

    @Test
    void behaeltErlaubteFormatierung() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        Artikel ergebnis = artikelService.aktualisiereDokumenttexte(
                7L, request(null, "<p><strong>Rundrohr</strong><br>Edelstahl</p>", null));

        assertThat(ergebnis.getBeschreibung()).contains("<strong>");
        assertThat(ergebnis.getBeschreibung()).contains("<br>");
    }

    // ==================================================================
    // Enge Safelist: kein Link, kein ungeprueftes CSS
    // ==================================================================

    @Test
    void entferntLinks() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        Artikel ergebnis = artikelService.aktualisiereDokumenttexte(
                7L, request(null, "<a href=\"https://boese.example\">Klick</a>", null));

        assertThat(ergebnis.getBeschreibung()).doesNotContain("href");
        assertThat(ergebnis.getBeschreibung()).doesNotContain("<a");
        assertThat(ergebnis.getBeschreibung()).contains("Klick");
    }

    @Test
    void entferntGefaehrlicheCssEigenschaftAusDemStyle() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        Artikel ergebnis = artikelService.aktualisiereDokumenttexte(
                7L, request(null, "<span style=\"position:fixed;inset:0\">x</span>", null));

        assertThat(ergebnis.getBeschreibung()).doesNotContain("position");
        assertThat(ergebnis.getBeschreibung()).doesNotContain("inset");
    }

    @Test
    void entferntTrackingPixelAusDemStyle() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        Artikel ergebnis = artikelService.aktualisiereDokumenttexte(
                7L, request(null, "<span style=\"background:url(https://tracker.example/p.gif)\">x</span>", null));

        assertThat(ergebnis.getBeschreibung()).doesNotContain("url(");
        assertThat(ergebnis.getBeschreibung()).doesNotContain("background");
    }

    @Test
    void behaeltErlaubteCssEigenschaften() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        Artikel ergebnis = artikelService.aktualisiereDokumenttexte(
                7L, request(null, "<span style=\"font-size: 12pt; color: #333\">x</span>", null));

        assertThat(ergebnis.getBeschreibung()).contains("font-size");
        assertThat(ergebnis.getBeschreibung()).contains("color");
    }

    // ==================================================================
    // Fehlerfaelle
    // ==================================================================

    @Test
    void meldetUnbekannteId() {
        when(artikelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artikelService.aktualisiereDokumenttexte(999L, request("x", null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void lehntUeberlangeKurzbeschreibungAb() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));

        assertThatThrownBy(() -> artikelService.aktualisiereDokumenttexte(
                7L, request("x".repeat(256), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lehntUeberlangeBeschreibungAb() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));

        assertThatThrownBy(() -> artikelService.aktualisiereDokumenttexte(
                7L, request(null, "x".repeat(10_001), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lehntUnsinnigenAufschlagAb() {
        Artikel artikel = new Artikel();
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));

        assertThatThrownBy(() -> artikelService.aktualisiereDokumenttexte(
                7L, request(null, null, "-5.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
