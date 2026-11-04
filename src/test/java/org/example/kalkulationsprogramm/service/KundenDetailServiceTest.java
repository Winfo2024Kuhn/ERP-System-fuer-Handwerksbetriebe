package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailReiterRepository;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KundenDetailServiceTest {

    @Mock private KundeRepository kundeRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private AnfrageRepository anfrageRepository;
    @Mock private EmailReiterRepository emailReiterRepository;
    @Mock private DateiSpeicherService dateiSpeicherService;
    @Mock private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;
    @Mock private KundeNotizRepository kundeNotizRepository;

    private KundenDetailService service;

    @BeforeEach
    void setUp() {
        service = new KundenDetailService(kundeRepository, projektRepository, anfrageRepository,
                emailReiterRepository, dateiSpeicherService, ausgangsGeschaeftsDokumentService, kundeNotizRepository);
    }

    private Kunde erstelleKunde(Long id) {
        Kunde kunde = new Kunde();
        kunde.setId(id);
        kunde.setName("Max Mustermann");
        kunde.setKundennummer("K-001");
        kunde.setStrasse("Musterstraße 1");
        kunde.setPlz("12345");
        kunde.setOrt("Musterstadt");
        kunde.setKundenEmails(List.of("test@example.com"));
        return kunde;
    }

    private Projekt erstelleProjekt(Long id, Kunde kunde) {
        Projekt projekt = new Projekt();
        projekt.setId(id);
        projekt.setKundenId(kunde);
        return projekt;
    }

    // ═══════════════════════════════════════════════════════════════
    // loadDetails
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class LoadDetails {

        /**
         * Sets up the common mocks needed for loadDetails to work.
         * loadAnfragenForKunde uses findByProjektIdIn and findByKunde_KundennummerIgnoreCase.
         */
        private void setupAnfrageMocks(Kunde kunde, List<Projekt> projekte, List<Anfrage> anfragen) {
            List<Long> projektIds = projekte.stream().map(Projekt::getId).toList();
            if (!projektIds.isEmpty()) {
                lenient().when(anfrageRepository.findByProjektIdIn(projektIds)).thenReturn(anfragen);
            }
            lenient().when(anfrageRepository.findByKunde_KundennummerIgnoreCase(kunde.getKundennummer()))
                    .thenReturn(anfragen);
        }

        @Test
        void gibtEmptyWennKundeNichtGefunden() {
            when(kundeRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<KundeDetailDto> result = service.loadDetails(999L);

            assertThat(result).isEmpty();
        }

        @Test
        void mapptKundeGrunddaten() {
            Kunde kunde = erstelleKunde(1L);
            kunde.setZahlungsziel(30);
            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of());
            setupAnfrageMocks(kunde, List.of(), List.of());

            Optional<KundeDetailDto> result = service.loadDetails(1L);

            assertThat(result).isPresent();
            KundeDetailDto dto = result.get();
            assertThat(dto.getZahlungsziel()).isEqualTo(30);
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Max Mustermann");
            assertThat(dto.getKundennummer()).isEqualTo("K-001");
        }

        @Test
        void mapptGeschaeftsdokumenteUndNotizen() {
            Kunde kunde = erstelleKunde(1L);
            Projekt projekt = erstelleProjekt(10L, kunde);

            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(projekt));
            setupAnfrageMocks(kunde, List.of(projekt), List.of());

            var dok = new org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto();
            dok.setId(77L);
            dok.setDokumentNummer("2026/06/00123");
            dok.setDatum(java.time.LocalDate.of(2026, 6, 23));
            when(ausgangsGeschaeftsDokumentService.findByProjekt(10L)).thenReturn(List.of(dok));

            org.example.kalkulationsprogramm.domain.KundeNotiz notiz =
                    new org.example.kalkulationsprogramm.domain.KundeNotiz();
            notiz.setId(5L);
            notiz.setText("Rückruf mit Max Mustermann");
            notiz.setErstelltAm(LocalDateTime.now());
            when(kundeNotizRepository.findByKundeIdOrderByErstelltAmDesc(1L)).thenReturn(List.of(notiz));

            Optional<KundeDetailDto> result = service.loadDetails(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getGeschaeftsdokumente()).hasSize(1);
            assertThat(result.get().getGeschaeftsdokumente().get(0).getDokumentNummer()).isEqualTo("2026/06/00123");
            assertThat(result.get().getNotizen()).hasSize(1);
            assertThat(result.get().getNotizen().get(0).getText()).isEqualTo("Rückruf mit Max Mustermann");
        }

        @Test
        void zeigtDokumentOhneProjektUndAnfrageDasDirektAmKundenHaengt() {
            // Bug: Akte zeigte „Dokumente 0“, die Datenschutz-Löschung zählte aber „1 Angebot/Rechnung“.
            Kunde kunde = erstelleKunde(1L);
            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of());
            setupAnfrageMocks(kunde, List.of(), List.of());
            when(ausgangsGeschaeftsDokumentService.findByKunde(1L)).thenReturn(List.of(dokument(88L, "AN-2026-001")));

            KundeDetailDto dto = service.loadDetails(1L).orElseThrow();

            assertThat(dto.getGeschaeftsdokumente())
                    .extracting(d -> d.getDokumentNummer())
                    .containsExactly("AN-2026-001");
        }

        @Test
        void zeigtDokumentAmProjektUndAmKundenNurEinmal() {
            Kunde kunde = erstelleKunde(1L);
            Projekt projekt = erstelleProjekt(10L, kunde);
            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(projekt));
            setupAnfrageMocks(kunde, List.of(projekt), List.of());
            when(ausgangsGeschaeftsDokumentService.findByProjekt(10L)).thenReturn(List.of(dokument(77L, "RE-2026-001")));
            when(ausgangsGeschaeftsDokumentService.findByKunde(1L)).thenReturn(List.of(dokument(77L, "RE-2026-001")));

            KundeDetailDto dto = service.loadDetails(1L).orElseThrow();

            assertThat(dto.getGeschaeftsdokumente()).hasSize(1);
        }

        private org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto dokument(
                Long id, String nummer) {
            var dok = new org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto();
            dok.setId(id);
            dok.setDokumentNummer(nummer);
            dok.setDatum(java.time.LocalDate.of(2026, 10, 1));
            return dok;
        }

        @Test
        void kundeOhneKundennummerBekommtSeineDirektenAnfragenEinmal() {
            Kunde kunde = erstelleKunde(1L);
            kunde.setKundennummer(" ");
            Projekt projekt = erstelleProjekt(10L, kunde);
            Anfrage imProjekt = new Anfrage();
            imProjekt.setId(20L);
            Anfrage direkt = new Anfrage();
            direkt.setId(21L);
            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(projekt));
            when(anfrageRepository.findByProjektIdIn(List.of(10L))).thenReturn(List.of(imProjekt));
            when(anfrageRepository.findByKundeId(1L)).thenReturn(List.of(direkt, imProjekt));

            KundeDetailDto dto = service.loadDetails(1L).orElseThrow();

            assertThat(dto.getAnfragen()).extracting(a -> a.getId()).containsExactlyInAnyOrder(20L, 21L);
            assertThat(dto.getStatistik().getAnfrageAnzahl()).isEqualTo(2);
            org.mockito.Mockito.verify(anfrageRepository, org.mockito.Mockito.never())
                    .findByKunde_KundennummerIgnoreCase(org.mockito.ArgumentMatchers.any());
            org.mockito.Mockito.verify(ausgangsGeschaeftsDokumentService).findByAnfrage(21L);
        }

        @Test
        void zaehltEmailAdressenOhneSieMitzuschicken() {
            Kunde kunde = erstelleKunde(1L);
            kunde.setKundenEmails(List.of("max.mustermann@example.com", "MAX.MUSTERMANN@example.com ", "info@example.com"));
            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of());
            setupAnfrageMocks(kunde, List.of(), List.of());

            KundeDetailDto dto = service.loadDetails(1L).orElseThrow();

            assertThat(dto.getStatistik().getEmailAdresseAnzahl()).isEqualTo(2);
        }

        @Test
        void zeigtNurDieZahlDerEmailVerlaeufe() {
            Kunde kunde = erstelleKunde(1L);
            Projekt projekt = erstelleProjekt(10L, kunde);
            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(projekt));
            setupAnfrageMocks(kunde, List.of(projekt), List.of());
            when(emailReiterRepository.anzahlVerlaeufe(EmailBezug.KUNDE, 1L)).thenReturn(140L);

            Optional<KundeDetailDto> result = service.loadDetails(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getEmailAnzahl()).isEqualTo(140L);
        }
    }
}
