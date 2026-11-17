package org.example.kalkulationsprogramm.service.dokumentstand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandDetailDto;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandSeiteDto;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandRepository;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandZeile;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DokumentStandAbfrageServiceTest {

    @Mock AusgangsDokumentStandRepository standRepository;
    @Mock AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @Mock FrontendUserProfileRepository benutzerRepository;
    @TempDir Path uploads;

    private DokumentStandAbfrageService service;
    private AusgangsGeschaeftsDokument dokument;

    @BeforeEach
    void setUp() {
        service = new DokumentStandAbfrageService(standRepository, dokumentRepository,
                new DokumentStandEintragMapper(benutzerRepository, new DokumentStandPdfAblage(uploads.toString())));
        dokument = new AusgangsGeschaeftsDokument();
        dokument.setId(7L);
        dokument.setBetreff("Tor");
        dokument.setBetragNetto(new BigDecimal("100"));
        dokument.setPositionenJson("{\"blocks\":[]}");
    }

    private static FrontendUserProfile benutzer(long id, String name) {
        FrontendUserProfile p = new FrontendUserProfile();
        p.setId(id);
        p.setDisplayName(name);
        return p;
    }

    private AusgangsDokumentStandZeile zeile(long id, int nummer, String fingerabdruck, String pdf) {
        return new AusgangsDokumentStandZeile(id, nummer, AusgangsDokumentStandArt.EXPORT,
                LocalDateTime.of(2026, 11, 17, 10, 0), 1L, "[2]", new BigDecimal("100"),
                "[\"Erste Fassung\"]", null, fingerabdruck, pdf);
    }

    private AusgangsDokumentStand neuesterStand(String betreff) {
        AusgangsDokumentStand s = new AusgangsDokumentStand();
        s.setId(11L);
        s.setDokumentId(7L);
        s.setNummer(2);
        s.setBetreff(betreff);
        s.setBetragNetto(new BigDecimal("100"));
        s.setPositionenJson("{\"blocks\":[]}");
        s.setInhaltFingerabdruck(DokumentStandInhalt.von(s).fingerabdruck());
        return s;
    }

    @Test
    @DisplayName("Seite: Namen gesammelt geladen, aktuell und PDF erkannt, offene Änderungen live")
    void seite() throws Exception {
        Files.write(uploads.resolve("da.pdf"), new byte[] { 1 });
        String aktuellerAbdruck = DokumentStandInhalt.von(dokument).fingerabdruck();
        when(dokumentRepository.findById(7L)).thenReturn(Optional.of(dokument));
        when(standRepository.findeSeite(eq(7L), any())).thenReturn(new PageImpl<>(
                List.of(zeile(12L, 2, "x".repeat(64), "fehlt.pdf"), zeile(11L, 1, aktuellerAbdruck, "da.pdf")),
                PageRequest.of(0, 2), 3));
        when(benutzerRepository.findAllById(any())).thenReturn(
                List.of(benutzer(1L, "Max Mustermann"), benutzer(2L, "Erika Mustermann")));
        when(standRepository.findFirstByDokumentIdOrderByNummerDesc(7L)).thenReturn(Optional.of(neuesterStand("Zaun")));

        StandSeiteDto seite = service.seite(7L, 0, 2);

        assertThat(seite.gesamt()).isEqualTo(3);
        assertThat(seite.dokumentBearbeitbar()).isTrue();
        assertThat(seite.hatMehr()).isTrue();
        assertThat(seite.staende()).hasSize(2);
        assertThat(seite.staende().get(0).aktuell()).isFalse();
        assertThat(seite.staende().get(0).pdfVorhanden()).isFalse();
        assertThat(seite.staende().get(1).aktuell()).isTrue();
        assertThat(seite.staende().get(1).pdfVorhanden()).isTrue();
        assertThat(seite.staende().get(1).festgehaltenVon()).isEqualTo("Max Mustermann");
        assertThat(seite.staende().get(1).bearbeitetVon()).containsExactly("Erika Mustermann");
        assertThat(seite.staende().get(1).aenderungen()).containsExactly("Erste Fassung");
        assertThat(seite.offeneAenderungen()).containsExactly("Betreff: „Zaun“ → „Tor“");
        verify(benutzerRepository).findAllById(any());
    }

    @Test
    @DisplayName("Seitengröße und Seite werden begrenzt (size 1..50, page >= 0)")
    void grenzen() {
        when(dokumentRepository.findById(7L)).thenReturn(Optional.of(dokument));
        when(standRepository.findeSeite(eq(7L), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(standRepository.findFirstByDokumentIdOrderByNummerDesc(7L)).thenReturn(Optional.empty());

        StandSeiteDto seite = service.seite(7L, -1, 100000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(standRepository).findeSeite(eq(7L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(seite.seitenGroesse()).isEqualTo(50);
        assertThat(seite.offeneAenderungen()).isNull();
        verify(benutzerRepository, never()).findAllById(any());

        service.seite(7L, 0, 0);
        verify(standRepository).findeSeite(eq(7L), eq(PageRequest.of(0, 1)));
    }

    @Test
    @DisplayName("Inhalt gleicht dem neuesten Stand: offeneAenderungen leer")
    void keineOffenenAenderungen() {
        when(dokumentRepository.findById(7L)).thenReturn(Optional.of(dokument));
        when(standRepository.findeSeite(eq(7L), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        when(standRepository.findFirstByDokumentIdOrderByNummerDesc(7L)).thenReturn(Optional.of(neuesterStand("Tor")));

        assertThat(service.seite(7L, 0, 25).offeneAenderungen()).isEmpty();
        dokument.setDigitalAngenommen(true);
        assertThat(service.seite(7L, 0, 25).dokumentBearbeitbar()).isFalse();
    }

    @Test
    @DisplayName("Ungültige oder unbekannte Dokument-ID: 400 bzw. 404")
    void ungueltigeIds() {
        assertThatThrownBy(() -> service.seite(0L, 0, 25)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.seite(-5L, 0, 25)).isInstanceOf(ResponseStatusException.class);
        when(dokumentRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.seite(Long.MAX_VALUE, 0, 25)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Detail: Inhalt des Stands; Stand eines anderen Dokuments → 404")
    void detail() {
        AusgangsDokumentStand stand = neuesterStand("Zaun");
        stand.setFestgehaltenVonId(1L);
        stand.setHtmlInhalt("<p>Zaun</p>");
        stand.setAenderungen("[\"Erste Fassung\"]");
        stand.setArt(AusgangsDokumentStandArt.EXPORT);
        stand.setFestgehaltenAm(LocalDateTime.of(2026, 11, 17, 10, 0));
        when(dokumentRepository.findById(7L)).thenReturn(Optional.of(dokument));
        when(standRepository.findByIdAndDokumentId(11L, 7L)).thenReturn(Optional.of(stand));
        when(benutzerRepository.findAllById(any())).thenReturn(List.of(benutzer(1L, "Max Mustermann")));

        StandDetailDto detail = service.detail(7L, 11L);
        assertThat(detail.betreff()).isEqualTo("Zaun");
        assertThat(detail.htmlInhalt()).isEqualTo("<p>Zaun</p>");
        assertThat(detail.festgehaltenVon()).isEqualTo("Max Mustermann");
        assertThat(detail.aktuell()).isFalse();
        assertThat(detail.dokumentBearbeitbar()).isTrue();
        dokument.setStorniert(true);
        assertThat(service.detail(7L, 11L).dokumentBearbeitbar()).isFalse();

        when(standRepository.findByIdAndDokumentId(99L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(7L, 99L)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getReason()).isEqualTo("Diesen Stand gibt es nicht.");
        });
    }

    @Test
    @DisplayName("PDF: liefert die Datei; fehlend oder Pfad-Trick → 404")
    void pdf() throws Exception {
        Files.write(uploads.resolve("da.pdf"), new byte[] { 37, 80 });
        AusgangsDokumentStand stand = neuesterStand("Zaun");
        when(dokumentRepository.findById(7L)).thenReturn(Optional.of(dokument));
        when(standRepository.findByIdAndDokumentId(11L, 7L)).thenReturn(Optional.of(stand));

        stand.setPdfDateiname("da.pdf");
        assertThat(service.pdf(7L, 11L)).containsExactly(37, 80);

        for (String name : new String[] { null, "fehlt.pdf", "../../etc/passwd", "..\\geheim.pdf", "/etc/x.pdf", "da.exe" }) {
            stand.setPdfDateiname(name);
            assertThatThrownBy(() -> service.pdf(7L, 11L)).isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}
