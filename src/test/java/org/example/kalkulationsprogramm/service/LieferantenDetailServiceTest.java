package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.repository.EmailReiterRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantNotizRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantStatistikDto;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LieferantenDetailServiceTest {

    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private EmailReiterRepository emailReiterRepository;
    @Mock private LieferantArtikelpreisMapper artikelpreisMapper;
    @Mock private LieferantDokumentService lieferantDokumentService;
    @Mock private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Mock private LieferantNotizRepository notizRepository;
    @Mock private LieferantenArtikelPreiseRepository artikelpreisRepository;

    private LieferantenDetailService service;

    @BeforeEach
    void setUp() {
        service = new LieferantenDetailService(lieferantenRepository, emailRepository, emailReiterRepository,
                artikelpreisMapper, lieferantDokumentService, geschaeftsdokumentRepository, notizRepository,
                artikelpreisRepository);
    }

    private Lieferanten erstelleLieferant(Long id) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(id);
        lieferant.setLieferantenname("Test Lieferant GmbH");
        lieferant.setIstAktiv(true);
        lieferant.setKundenEmails(List.of("lieferant@example.com"));
        lieferant.setArtikelpreise(new ArrayList<>());
        return lieferant;
    }

    // ═══════════════════════════════════════════════════════════════
    // Schlankes Laden für die Detailseite
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class NachladenFuerDetailseite {

        @Test
        void stammdatenLadenKeineListenSondernNurZaehler() {
            Lieferanten lieferant = erstelleLieferant(1L);
            when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(lieferant));
            when(emailReiterRepository.anzahlVerlaeufe(EmailBezug.LIEFERANT, 1L)).thenReturn(12L);
            when(lieferantDokumentService.zaehleDokumente(1L)).thenReturn(3L);
            when(notizRepository.countByLieferantId(1L)).thenReturn(2L);

            LieferantDetailDto result = service.loadStammdaten(1L);

            assertThat(result.getLieferantenname()).isEqualTo("Test Lieferant GmbH");
            assertThat(result.getEmailAnzahl()).isEqualTo(12L);
            assertThat(result.getDokumenteAnzahl()).isEqualTo(3L);
            assertThat(result.getNotizenAnzahl()).isEqualTo(2L);
            assertThat(result.getArtikelpreise()).isNull();
            assertThat(result.getDokumente()).isNull();
            assertThat(result.getNotizen()).isNull();
            verifyNoInteractions(emailRepository);
            verify(lieferantDokumentService, never()).getDokumenteByLieferant(anyLong(), any());
            verify(artikelpreisMapper, never()).toDtoList(any());
        }

        @Test
        void stammdatenGebenNullFuerUnbekanntenLieferanten() {
            when(lieferantenRepository.findById(99L)).thenReturn(Optional.empty());

            assertThat(service.loadStammdaten(99L)).isNull();
        }

        @Test
        void statistikZaehltArtikelPerAbfrage() {
            Lieferanten lieferant = erstelleLieferant(1L);
            when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(lieferant));
            when(artikelpreisRepository.zaehleArtikelVonLieferant(1L)).thenReturn(1500L);
            when(emailRepository.countByLieferantId(1L)).thenReturn(4L);

            LieferantStatistikDto result = service.loadStatistik(1L);

            assertThat(result.getArtikelAnzahl()).isEqualTo(1500);
            assertThat(result.getEmailAnzahl()).isEqualTo(4L);
        }

        @Test
        void statistikGibtNullFuerUnbekanntenLieferanten() {
            when(lieferantenRepository.findById(99L)).thenReturn(Optional.empty());

            assertThat(service.loadStatistik(99L)).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // loadDetails
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class LoadDetails {

        @Test
        void gibtNullWennLieferantNichtGefunden() {
            when(lieferantenRepository.findById(999L)).thenReturn(Optional.empty());

            LieferantDetailDto result = service.loadDetails(999L);

            assertThat(result).isNull();
        }

        @Test
        void mapptGrunddatenKorrekt() {
            Lieferanten lieferant = erstelleLieferant(1L);
            when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(lieferant));
            when(lieferantDokumentService.getDokumenteByLieferant(1L, null)).thenReturn(List.of());
            when(notizRepository.findByLieferantIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

            LieferantDetailDto result = service.loadDetails(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getLieferantenname()).isEqualTo("Test Lieferant GmbH");
        }

        @Test
        void zeigtNurDieZahlDerEmailVerlaeufeOhneMailsZuLaden() {
            Lieferanten lieferant = erstelleLieferant(1L);
            when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(lieferant));
            when(lieferantDokumentService.getDokumenteByLieferant(1L, null)).thenReturn(List.of());
            when(notizRepository.findByLieferantIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());
            when(emailReiterRepository.anzahlVerlaeufe(EmailBezug.LIEFERANT, 1L)).thenReturn(140L);

            LieferantDetailDto result = service.loadDetails(1L);

            assertThat(result.getEmailAnzahl()).isEqualTo(140L);
        }
    }
}
