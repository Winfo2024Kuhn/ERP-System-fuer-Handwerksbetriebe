package org.example.kalkulationsprogramm.service.dokumentstand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentAuditService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.DatensatzLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DokumentStandWiederherstellenServiceTest {

    @Mock AusgangsDokumentStandRepository standRepository;
    @Mock AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @Mock FrontendUserProfileRepository benutzerRepository;
    @Mock DokumentStandService standService;
    @Mock AusgangsGeschaeftsDokumentService dokumentService;
    @Mock AusgangsGeschaeftsDokumentAuditService auditService;
    @Mock DatensatzLockService lockService;

    private DokumentStandWiederherstellenService service;
    private AusgangsGeschaeftsDokument dokument;
    private AusgangsDokumentStand stand;

    @BeforeEach
    void setUp() {
        service = new DokumentStandWiederherstellenService(standRepository, dokumentRepository, benutzerRepository,
                standService, dokumentService, auditService, lockService);
        dokument = new AusgangsGeschaeftsDokument();
        dokument.setId(7L);
        dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        dokument.setBetreff("Jetzt");
        dokument.setDatum(LocalDate.of(2026, 11, 17));
        dokument.setZahlungszielTage(30);
        dokument.setPositionenJson("{\"blocks\":[]}");

        stand = new AusgangsDokumentStand();
        stand.setId(11L);
        stand.setDokumentId(7L);
        stand.setNummer(2);
        stand.setArt(AusgangsDokumentStandArt.EXPORT);
        stand.setFestgehaltenAm(LocalDateTime.of(2026, 11, 3, 14, 5));
        stand.setBetreff("Früher");
        stand.setBetragNetto(new BigDecimal("95.00"));
        stand.setMwstSatz(new BigDecimal("0.19"));
        stand.setZahlungszielTage(null);
        stand.setRechnungsadresseOverride("Max Mustermann\nMusterweg 1");
        stand.setPositionenJson("{\"blocks\":[{\"id\":\"a\",\"type\":\"SERVICE\"}]}");
        stand.setHtmlInhalt("<p>früher</p>");
        stand.setDatum(LocalDate.of(2026, 11, 3));
        stand.setInhaltFingerabdruck(DokumentStandInhalt.von(stand).fingerabdruck());
    }

    private void standGefunden() {
        when(dokumentRepository.findById(7L)).thenReturn(Optional.of(dokument));
        when(standRepository.findByIdAndDokumentId(11L, 7L)).thenReturn(Optional.of(stand));
    }

    @Test
    @DisplayName("Sichert offene Änderungen, übernimmt den Inhalt (ohne Datum), merkt sich die Nummer, schreibt den Audit")
    void wiederherstellen() {
        standGefunden();
        FrontendUserProfile max = new FrontendUserProfile();
        max.setId(42L);
        when(benutzerRepository.findById(42L)).thenReturn(Optional.of(max));
        when(dokumentService.aktualisieren(eq(7L), any())).thenReturn(dokument);
        AusgangsGeschaeftsDokumentResponseDto antwort = new AusgangsGeschaeftsDokumentResponseDto();
        when(dokumentService.findById(7L)).thenReturn(antwort);

        assertThat(service.wiederherstellen(7L, 11L, 42L, "127.0.0.1")).isSameAs(antwort);

        InOrder reihenfolge = inOrder(standService, dokumentService, auditService);
        reihenfolge.verify(standService).festhalten(dokument, AusgangsDokumentStandArt.VOR_WIEDERHERSTELLEN, 42L, null);
        ArgumentCaptor<AusgangsGeschaeftsDokumentUpdateDto> dto = ArgumentCaptor.forClass(AusgangsGeschaeftsDokumentUpdateDto.class);
        reihenfolge.verify(dokumentService).aktualisieren(eq(7L), dto.capture());
        reihenfolge.verify(auditService).protokolliereAenderung(dokument, max,
                "Stand 2 vom 03.11.2026 14:05 wiederhergestellt", "127.0.0.1");
        verify(standService).merkeWiederherstellung(7L, 42L, 2);

        assertThat(dto.getValue().getDatum()).isNull();
        assertThat(dto.getValue().getBetreff()).isEqualTo("Früher");
        assertThat(dto.getValue().getBetragNetto()).isEqualByComparingTo("95.00");
        assertThat(dto.getValue().getHtmlInhalt()).isEqualTo("<p>früher</p>");
        assertThat(dto.getValue().getRechnungsadresseOverride()).isEqualTo("Max Mustermann\nMusterweg 1");
        // Leere Felder des Stands werden auch leer übernommen (aktualisieren übergeht null).
        assertThat(dokument.getZahlungszielTage()).isNull();
        assertThat(dokument.getDatum()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(dokument.getWiederherstellungsZaehler()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fehler beim Übernehmen: 400 mit verständlicher Meldung, ohne technische Details")
    void fehlerBeimUebernehmen() {
        standGefunden();
        when(dokumentService.aktualisieren(eq(7L), any())).thenThrow(new RuntimeException("SQL state 23505 geheim"));
        assertThatThrownBy(() -> service.wiederherstellen(7L, 11L, 42L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).doesNotContain("SQL").startsWith("Dieser Stand ließ sich nicht übernehmen");
                });
        verify(auditService, never()).protokolliereAenderung(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Fachliche Meldungen aus aktualisieren (z. B. Gutschrift) bleiben erhalten")
    void fachlicheMeldungBleibt() {
        standGefunden();
        when(dokumentService.aktualisieren(eq(7L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gutschrift zu hoch."));
        assertThatThrownBy(() -> service.wiederherstellen(7L, 11L, 42L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getReason()).isEqualTo("Gutschrift zu hoch."));
    }

    @Test
    @DisplayName("Stand gehört nicht zum Dokument oder Dokument fehlt: 404 „Diesen Stand gibt es nicht.“")
    void nichtGefunden() {
        when(dokumentRepository.findById(7L)).thenReturn(Optional.of(dokument));
        when(standRepository.findByIdAndDokumentId(11L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.wiederherstellen(7L, 11L, 42L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).isEqualTo("Diesen Stand gibt es nicht.");
                });
        when(dokumentRepository.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.wiederherstellen(8L, 11L, 42L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Ungültige IDs: 400")
    void ungueltigeIds() {
        for (long[] ids : new long[][] { { 0, 1 }, { -1, 1 }, { 1, 0 }, { 1, -3 } }) {
            assertThatThrownBy(() -> service.wiederherstellen(ids[0], ids[1], 42L, null))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(dokumentService, never()).aktualisieren(anyLong(), any());
    }

    @Test
    @DisplayName("Andere Person hält die Sperre: 409, nichts geändert")
    void fremdeSperre() {
        standGefunden();
        when(lockService.istVonAnderemGesperrt(SperrbarerTyp.AUSGANG, 7L, 42L)).thenReturn(true);
        assertThatThrownBy(() -> service.wiederherstellen(7L, 11L, 42L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("Dieses Dokument wird gerade von einer anderen Person "
                            + "bearbeitet. Bitte warten, bis sie es schließt.");
                });
        verify(standService, never()).festhalten(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Gebuchtes, angenommenes oder storniertes Dokument: 400")
    void nichtBearbeitbar() {
        standGefunden();
        dokument.setDigitalAngenommen(true);
        assertThatThrownBy(() -> service.wiederherstellen(7L, 11L, 42L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("Dieses Dokument ist gebucht, angenommen oder storniert und "
                            + "lässt sich nicht mehr ändern. Ältere Stände können Sie nur ansehen.");
                });
        verify(dokumentService, never()).aktualisieren(anyLong(), any());
    }

    @Test
    @DisplayName("Stand ist schon der aktuelle Inhalt: 400")
    void schonAktuell() {
        standGefunden();
        dokument.setBetreff(stand.getBetreff());
        dokument.setBetragNetto(stand.getBetragNetto());
        dokument.setMwstSatz(stand.getMwstSatz());
        dokument.setZahlungszielTage(null);
        dokument.setRechnungsadresseOverride(stand.getRechnungsadresseOverride());
        dokument.setPositionenJson(stand.getPositionenJson());
        assertThatThrownBy(() -> service.wiederherstellen(7L, 11L, 42L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("Dieser Stand ist bereits der aktuelle Inhalt.");
                });
        verify(auditService, never()).protokolliereAenderung(any(), any(), anyString(), any());
    }
}
