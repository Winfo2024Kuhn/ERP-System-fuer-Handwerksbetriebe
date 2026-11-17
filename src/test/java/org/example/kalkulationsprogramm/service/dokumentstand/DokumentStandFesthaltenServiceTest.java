package org.example.kalkulationsprogramm.service.dokumentstand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandFesthaltenDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DokumentStandFesthaltenServiceTest {

    @Mock DokumentStandService standService;
    @Mock FrontendUserProfileRepository benutzerRepository;
    @TempDir Path uploads;

    private DokumentStandFesthaltenService service;

    @BeforeEach
    void setUp() {
        service = new DokumentStandFesthaltenService(standService,
                new DokumentStandEintragMapper(benutzerRepository, new DokumentStandPdfAblage(uploads.toString())));
    }

    @Test
    @DisplayName("Neuer Stand ohne PDF: festgehalten, Eintrag aktuell, pdfVorhanden false")
    void neuerStand() {
        AusgangsDokumentStand s = new AusgangsDokumentStand();
        s.setId(11L);
        s.setDokumentId(7L);
        s.setNummer(3);
        s.setArt(AusgangsDokumentStandArt.EXPORT);
        s.setFestgehaltenAm(LocalDateTime.of(2026, 11, 17, 10, 0));
        s.setFestgehaltenVonId(42L);
        s.setBearbeitetVonIds("[42]");
        s.setBetragNetto(new BigDecimal("10"));
        s.setAenderungen("[\"Rabatt 5 % gegeben\"]");
        when(standService.festhaltenNachExport(7L, 42L, null)).thenReturn(Optional.of(s));
        FrontendUserProfile max = new FrontendUserProfile();
        max.setId(42L);
        max.setDisplayName("Max Mustermann");
        when(benutzerRepository.findAllById(any())).thenReturn(List.of(max));

        StandFesthaltenDto ergebnis = service.festhalten(7L, 42L);

        assertThat(ergebnis.festgehalten()).isTrue();
        assertThat(ergebnis.stand().nummer()).isEqualTo(3);
        assertThat(ergebnis.stand().aktuell()).isTrue();
        assertThat(ergebnis.stand().pdfVorhanden()).isFalse();
        assertThat(ergebnis.stand().festgehaltenVon()).isEqualTo("Max Mustermann");
        assertThat(ergebnis.stand().aenderungen()).containsExactly("Rabatt 5 % gegeben");
        verify(standService).festhaltenNachExport(7L, 42L, null);
    }

    @Test
    @DisplayName("Gleicher Inhalt: festgehalten false, stand null")
    void keinNeuerStand() {
        when(standService.festhaltenNachExport(7L, 42L, null)).thenReturn(Optional.empty());
        StandFesthaltenDto ergebnis = service.festhalten(7L, 42L);
        assertThat(ergebnis.festgehalten()).isFalse();
        assertThat(ergebnis.stand()).isNull();
    }

    @Test
    @DisplayName("Doppelte Nummer (gleichzeitiger Export): kein neuer Stand statt Fehler")
    void doppelteNummer() {
        when(standService.festhaltenNachExport(7L, 42L, null))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_ausgangs_dokument_stand_nummer"));
        StandFesthaltenDto ergebnis = service.festhalten(7L, 42L);
        assertThat(ergebnis.festgehalten()).isFalse();
        assertThat(ergebnis.stand()).isNull();
    }

    @Test
    @DisplayName("Ungültige ID: 400, ohne Datenbank")
    void ungueltigeId() {
        assertThatThrownBy(() -> service.festhalten(0L, 42L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
