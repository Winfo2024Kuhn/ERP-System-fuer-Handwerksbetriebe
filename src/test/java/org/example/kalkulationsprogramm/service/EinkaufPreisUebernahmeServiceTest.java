package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisScope;
import org.example.kalkulationsprogramm.repository.AngebotVersionRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.service.LieferantArtikelpreisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufPreisUebernahmeService;

class EinkaufPreisUebernahmeServiceTest {
    @Test
    void hundertStueckBasisWirdAufEinzelpreisNormalisiert() {
        assertThat(EinkaufPreisUebernahmeService.normalisiereEinzelpreis(new java.math.BigDecimal("1.83"), "100 STUECK"))
                .isEqualByComparingTo("0.0183");
    }

    @Test
    void projektSonderpreisIstKeinAllgemeinerArtikelpreis() {
        Artikel artikel = new Artikel();
        LieferantenArtikelPreise standard = new LieferantenArtikelPreise();
        standard.setAktuell(true);
        standard.setPreis(new java.math.BigDecimal("10.00"));
        LieferantenArtikelPreise projekt = new LieferantenArtikelPreise();
        projekt.setAktuell(true);
        projekt.setScope(org.example.kalkulationsprogramm.domain.PreisScope.PROJEKT);
        artikel.getArtikelpreis().add(standard);
        artikel.getArtikelpreis().add(projekt);

        assertThat(artikel.getAktuellePreise()).containsExactly(standard);
        assertThat(artikel.getGuenstigsterPreis()).contains(standard);
    }

    @Test
    void abgelaufenerPreisBleibtNurAlsHinweisVerfuegbar() {
        var repository = mock(LieferantenArtikelPreiseRepository.class);
        var price = new LieferantenArtikelPreise();
        price.setScope(PreisScope.STANDARD);
        price.setPreis(new java.math.BigDecimal("12.50"));
        price.setWaehrung("EUR");
        price.setEinheit("STUECK");
        price.setGueltigBis(LocalDate.of(2026, 8, 31));
        when(repository.findAllByArtikel_IdAndLieferant_IdAndAktuellTrue(1L, 2L)).thenReturn(List.of(price));
        var service = new EinkaufPreisUebernahmeService(mock(AngebotVersionRepository.class),
                mock(LieferantArtikelpreisService.class), repository,
                mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufAuditService.class), new ObjectMapper());

        var suggestion = service.letzterBestaetigterPreis(1L, 2L, null, null, LocalDate.of(2026, 9, 1));

        assertThat(suggestion).isPresent();
        assertThat(suggestion.orElseThrow().hinweis()).contains("abgelaufen");
    }
}
