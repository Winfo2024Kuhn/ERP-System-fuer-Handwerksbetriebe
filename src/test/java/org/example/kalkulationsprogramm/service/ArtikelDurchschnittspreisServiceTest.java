package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-Tests fuer den gewichteten Durchschnittspreis-Service.
 * Kein Spring-Context, reine Mathematik + null-Guards.
 */
@ExtendWith(MockitoExtension.class)
class ArtikelDurchschnittspreisServiceTest {

    @Mock
    private ArtikelRepository artikelRepository;

    private ArtikelDurchschnittspreisService service;

    @BeforeEach
    void setUp() {
        service = new ArtikelDurchschnittspreisService(artikelRepository);
    }

    private static Artikel artikel(Long id) {
        Artikel a = new Artikel();
        a.setId(id);
        return a;
    }

    private static LieferantenArtikelPreise preisEintrag(BigDecimal preis) {
        LieferantenArtikelPreise l = new LieferantenArtikelPreise();
        l.setPreis(preis);
        return l;
    }

    @Nested
    class Aktualisiere {

        @Test
        void erstBefuellung_setztWerteUnveraendert() {
            Artikel a = artikel(1L);

            service.aktualisiere(a, new BigDecimal("100"), new BigDecimal("1.50"));

            assertThat(a.getDurchschnittspreisNetto()).isEqualByComparingTo("1.5000");
            assertThat(a.getDurchschnittspreisMenge()).isEqualByComparingTo("100.000");
            assertThat(a.getDurchschnittspreisAktualisiertAm()).isNotNull();
            verify(artikelRepository).save(a);
        }

        @Test
        void zweiterDatenpunkt_mitteltGewichtet() {
            Artikel a = artikel(1L);
            a.setDurchschnittspreisNetto(new BigDecimal("1.00"));
            a.setDurchschnittspreisMenge(new BigDecimal("100"));

            // 100kg @ 1,00 + 300kg @ 2,00 = 700€ / 400kg = 1,75€/kg
            service.aktualisiere(a, new BigDecimal("300"), new BigDecimal("2.00"));

            assertThat(a.getDurchschnittspreisNetto()).isEqualByComparingTo("1.7500");
            assertThat(a.getDurchschnittspreisMenge()).isEqualByComparingTo("400.000");
        }

        @Test
        void grosseMenge_dominiertKleine() {
            Artikel a = artikel(1L);
            a.setDurchschnittspreisNetto(new BigDecimal("5.00"));
            a.setDurchschnittspreisMenge(new BigDecimal("1"));

            // 1kg @ 5,00 + 999kg @ 1,00 = 5 + 999 = 1004€ / 1000kg = 1,004
            service.aktualisiere(a, new BigDecimal("999"), new BigDecimal("1.00"));

            assertThat(a.getDurchschnittspreisNetto()).isEqualByComparingTo("1.0040");
            assertThat(a.getDurchschnittspreisMenge()).isEqualByComparingTo("1000.000");
        }

        @Test
        void nullArtikel_wirdIgnoriert() {
            service.aktualisiere(null, BigDecimal.TEN, BigDecimal.ONE);
            verify(artikelRepository, never()).save(any());
        }

        @Test
        void nullMenge_wirdIgnoriert() {
            Artikel a = artikel(1L);
            service.aktualisiere(a, null, BigDecimal.ONE);
            verify(artikelRepository, never()).save(any());
            assertThat(a.getDurchschnittspreisNetto()).isNull();
        }

        @Test
        void nullPreis_wirdIgnoriert() {
            Artikel a = artikel(1L);
            service.aktualisiere(a, BigDecimal.TEN, null);
            verify(artikelRepository, never()).save(any());
        }

        @Test
        void negativeMenge_wirdIgnoriert() {
            Artikel a = artikel(1L);
            service.aktualisiere(a, new BigDecimal("-5"), BigDecimal.ONE);
            verify(artikelRepository, never()).save(any());
        }

        @Test
        void negativerPreis_wirdIgnoriert() {
            Artikel a = artikel(1L);
            service.aktualisiere(a, BigDecimal.TEN, new BigDecimal("-1"));
            verify(artikelRepository, never()).save(any());
        }

        @Test
        void nullMenge_beiAltesSchnittLeer_setztNeuenWert() {
            Artikel a = artikel(1L);
            a.setDurchschnittspreisNetto(new BigDecimal("2.00"));
            a.setDurchschnittspreisMenge(null);

            service.aktualisiere(a, new BigDecimal("50"), new BigDecimal("3.00"));

            assertThat(a.getDurchschnittspreisNetto()).isEqualByComparingTo("3.0000");
            assertThat(a.getDurchschnittspreisMenge()).isEqualByComparingTo("50.000");
        }
    }

    @Nested
    class Backfill {

        @Test
        void artikelOhnePreise_wirdUebersprungen() {
            Artikel a = artikel(1L);
            when(artikelRepository.findAll()).thenReturn(List.of(a));

            var result = service.backfillAlle();

            assertThat(result.verarbeitet()).isZero();
            assertThat(result.uebersprungen()).isEqualTo(1);
            verify(artikelRepository, never()).save(any());
        }

        @Test
        void einzelnerPreis_wirdUebernommen() {
            Artikel a = artikel(1L);
            a.setArtikelpreis(new ArrayList<>(List.of(preisEintrag(new BigDecimal("2.50")))));
            when(artikelRepository.findAll()).thenReturn(List.of(a));

            var result = service.backfillAlle();

            assertThat(result.verarbeitet()).isEqualTo(1);
            assertThat(a.getDurchschnittspreisNetto()).isEqualByComparingTo("2.5000");
            assertThat(a.getDurchschnittspreisMenge()).isEqualByComparingTo("0");
        }

        @Test
        void mehrereLieferanten_werdenGemittelt() {
            Artikel a = artikel(1L);
            a.setArtikelpreis(new ArrayList<>(Arrays.asList(
                    preisEintrag(new BigDecimal("1.00")),
                    preisEintrag(new BigDecimal("2.00")),
                    preisEintrag(new BigDecimal("3.00"))
            )));
            when(artikelRepository.findAll()).thenReturn(List.of(a));

            var result = service.backfillAlle();

            assertThat(result.verarbeitet()).isEqualTo(1);
            assertThat(a.getDurchschnittspreisNetto()).isEqualByComparingTo("2.0000");
        }

        @Test
        void nullUndNegativePreise_werdenIgnoriert() {
            Artikel a = artikel(1L);
            a.setArtikelpreis(new ArrayList<>(Arrays.asList(
                    preisEintrag(null),
                    preisEintrag(new BigDecimal("-1.00")),
                    preisEintrag(new BigDecimal("4.00"))
            )));
            when(artikelRepository.findAll()).thenReturn(List.of(a));

            var result = service.backfillAlle();

            assertThat(result.verarbeitet()).isEqualTo(1);
            assertThat(a.getDurchschnittspreisNetto()).isEqualByComparingTo("4.0000");
        }

        @Test
        void ergebnis_enthaeltAnzahlenUndDauer() {
            Artikel mitPreis = artikel(1L);
            mitPreis.setArtikelpreis(new ArrayList<>(List.of(preisEintrag(new BigDecimal("1.00")))));
            Artikel ohne = artikel(2L);
            Artikel leer = artikel(3L);
            leer.setArtikelpreis(new ArrayList<>());
            when(artikelRepository.findAll()).thenReturn(List.of(mitPreis, ohne, leer));

            var result = service.backfillAlle();

            assertThat(result.verarbeitet()).isEqualTo(1);
            assertThat(result.uebersprungen()).isEqualTo(2);
            assertThat(result.dauerMs()).isGreaterThanOrEqualTo(0);
        }
    }
}
