package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumenttexteRequest;
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

    @Test
    void meldetUnbekannteId() {
        when(artikelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artikelService.aktualisiereDokumenttexte(999L, request("x", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
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
