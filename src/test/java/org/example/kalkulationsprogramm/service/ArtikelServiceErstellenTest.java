package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Artikel neu anlegen speichert richtig (Issue #265). */
@ExtendWith(MockitoExtension.class)
class ArtikelServiceErstellenTest {

    @Mock private ArtikelRepository artikelRepository;
    @Mock private KategorieRepository kategorieRepository;
    @Mock private WerkstoffRepository werkstoffRepository;
    @Mock private LieferantenRepository lieferantenRepository;

    @InjectMocks
    private ArtikelService service;

    private static ArtikelCreateDto dto() {
        ArtikelCreateDto dto = new ArtikelCreateDto();
        dto.setProduktname("Flachstahl 40x5");
        return dto;
    }

    private Lieferanten lieferant() {
        Lieferanten l = new Lieferanten();
        l.setId(7L);
        l.setLieferantenname("Muster Stahlhandel GmbH");
        when(lieferantenRepository.findById(7L)).thenReturn(Optional.of(l));
        return l;
    }

    @Test
    void mitLieferant_preisBekommtDatumHerkunftUndNummer() {
        Lieferanten l = lieferant();
        when(artikelRepository.existsByArtikelnummerIgnoreCase("FS-40-5")).thenReturn(false);
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(inv -> inv.getArgument(0));
        ArtikelCreateDto dto = dto();
        dto.setArtikelnummer("  FS-40-5 ");
        dto.setLieferantId(7L);
        dto.setPreis(new BigDecimal("4.20"));
        dto.setExterneArtikelnummer(" 4711 ");

        Artikel artikel = service.erstelleArtikel(dto);

        assertThat(artikel.getArtikelnummer()).isEqualTo("FS-40-5");
        assertThat(artikel.getArtikelpreis()).hasSize(1);
        LieferantenArtikelPreise preis = artikel.getArtikelpreis().getFirst();
        assertThat(preis.getLieferant()).isSameAs(l);
        assertThat(preis.getPreis()).isEqualByComparingTo("4.20");
        assertThat(preis.getExterneArtikelnummer()).isEqualTo("4711");
        assertThat(preis.getPreisAenderungsdatum()).isNotNull();
        assertThat(preis.getQuelle()).isEqualTo(PreisQuelle.MANUELL);
        assertThat(preis.isAktuell()).isTrue();
    }

    @Test
    void ohnePreisUndNummer_entstehtKeinPreisstand() {
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(inv -> inv.getArgument(0));
        ArtikelCreateDto dto = dto();
        dto.setExterneArtikelnummer("  ");

        Artikel artikel = service.erstelleArtikel(dto);

        assertThat(artikel.getArtikelpreis()).isEmpty();
        assertThat(artikel.getArtikelnummer()).isNull();
        verify(artikelRepository, times(1)).save(any(Artikel.class));
    }

    @Test
    void preisOderNummerOhneLieferant_gibt400() {
        ArtikelCreateDto mitPreis = dto();
        mitPreis.setPreis(new BigDecimal("4.20"));
        assertThatThrownBy(() -> service.erstelleArtikel(mitPreis))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Für Preis und Lieferanten-Artikelnummer bitte auch den Lieferanten wählen.");

        ArtikelCreateDto mitNummer = dto();
        mitNummer.setExterneArtikelnummer("4711");
        assertThatThrownBy(() -> service.erstelleArtikel(mitNummer)).isInstanceOf(ResponseStatusException.class);
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void artikelnummerVergeben_zuLang_negativerPreis_unbekannterLieferant() {
        when(artikelRepository.existsByArtikelnummerIgnoreCase("4711")).thenReturn(true);
        ArtikelCreateDto doppelt = dto();
        doppelt.setArtikelnummer("4711");
        assertThatThrownBy(() -> service.erstelleArtikel(doppelt))
                .hasMessageContaining("Die Artikelnummer „4711“ ist schon vergeben.");

        ArtikelCreateDto zuLang = dto();
        zuLang.setArtikelnummer("x".repeat(10_001));
        assertThatThrownBy(() -> service.erstelleArtikel(zuLang)).hasMessageContaining("höchstens 64 Zeichen");

        ArtikelCreateDto negativ = dto();
        negativ.setLieferantId(7L);
        negativ.setPreis(new BigDecimal("-1"));
        assertThatThrownBy(() -> service.erstelleArtikel(negativ)).hasMessageContaining("nicht negativ");

        when(lieferantenRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());
        ArtikelCreateDto fremd = dto();
        fremd.setLieferantId(Long.MAX_VALUE);
        assertThatThrownBy(() -> service.erstelleArtikel(fremd)).hasMessageContaining("Lieferant wurde nicht gefunden");

        verify(artikelRepository, never()).save(any());
    }

    @Test
    void sqlTextAlsArtikelnummer_wirdNurAlsParameterGeprueft() {
        when(artikelRepository.existsByArtikelnummerIgnoreCase("'; DROP TABLE artikel; --")).thenReturn(false);
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(inv -> inv.getArgument(0));
        ArtikelCreateDto dto = dto();
        dto.setArtikelnummer("'; DROP TABLE artikel; --");

        assertThat(service.erstelleArtikel(dto).getArtikelnummer()).isEqualTo("'; DROP TABLE artikel; --");
    }
}
