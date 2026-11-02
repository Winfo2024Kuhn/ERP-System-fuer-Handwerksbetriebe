package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.firmendokumente.Firmendokument;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAendernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FirmendokumentServiceTest {

    private static final byte[] PDF = "%PDF-1.7\nInhalt".getBytes(StandardCharsets.US_ASCII);

    @TempDir Path tmp;
    @Mock FirmendokumentRepository dokumentRepository;
    @Mock FirmendokumentOrdnerRepository ordnerRepository;

    private FirmendokumentDateiablage ablage;
    private FirmendokumentService service;
    private FirmendokumentOrdner pruefungen;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T08:00:00Z"), ZoneId.of("Europe/Berlin"));
        ablage = new FirmendokumentDateiablage(tmp.toString());
        service = new FirmendokumentService(dokumentRepository, ordnerRepository, ablage, clock);
        pruefungen = new FirmendokumentOrdner();
        pruefungen.setId(1L);
        pruefungen.setName("Prüfungen");
    }

    private Firmendokument dokument(long id, String gespeichert) {
        Firmendokument d = new Firmendokument();
        d.setId(id);
        d.setOrdner(pruefungen);
        d.setName("Torprüfung Halle 2");
        d.setOriginalDateiname("scan.pdf");
        d.setGespeicherterDateiname(gespeichert);
        d.setGroesseBytes(PDF.length);
        d.setHochgeladenAm(LocalDateTime.of(2026, 11, 2, 9, 0));
        return d;
    }

    private static int status(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode().value();
    }

    @Test
    @DisplayName("Hochladen: Datei liegt unter UUID, Eintrag mit Name, Ordner, Datum und Hochlader")
    void hochladen() throws Exception {
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        given(dokumentRepository.saveAndFlush(any())).willAnswer(a -> {
            Firmendokument d = a.getArgument(0);
            d.setId(7L);
            return d;
        });
        MockMultipartFile datei = new MockMultipartFile("datei", "../../scan_0042.pdf", "application/pdf", PDF);

        FirmendokumentDto dto = service.hochladen(datei, 1L, "  Torprüfung Halle 2 – 2026 ", LocalDate.of(2027, 10, 31),
                "Max Mustermann");

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.ordnerId()).isEqualTo(1L);
        assertThat(dto.name()).isEqualTo("Torprüfung Halle 2 – 2026");
        assertThat(dto.originalDateiname()).isEqualTo("scan_0042.pdf");
        assertThat(dto.groesseBytes()).isEqualTo(PDF.length);
        assertThat(dto.hochgeladenAm()).isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 0));
        assertThat(dto.hochgeladenVon()).isEqualTo("Max Mustermann");
        assertThat(dto.faelligAm()).isEqualTo(LocalDate.of(2027, 10, 31));
        assertThat(dto.url()).isEqualTo("/api/firmendokumente/7/datei");
        try (var dateien = Files.list(tmp.resolve("firmendokumente"))) {
            List<Path> liste = dateien.toList();
            assertThat(liste).hasSize(1);
            assertThat(Files.readAllBytes(liste.get(0))).isEqualTo(PDF);
        }
    }

    @Test
    @DisplayName("Hochladen: kein PDF, fehlender Name oder Ordner → 400, nichts wird gespeichert")
    void hochladenFehler() {
        MockMultipartFile pdf = new MockMultipartFile("datei", "a.pdf", "application/pdf", PDF);
        MockMultipartFile exe = new MockMultipartFile("datei", "a.pdf", "application/pdf", "MZ".getBytes(StandardCharsets.US_ASCII));
        given(ordnerRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.hochladen(exe, 1L, "x", null, null)).hasMessageContaining("nur PDF-Dateien");
        assertThatThrownBy(() -> service.hochladen(pdf, 1L, " ", null, null))
                .hasMessageContaining("Bitte einen Namen für das Dokument eingeben.");
        assertThatThrownBy(() -> service.hochladen(pdf, null, "x", null, null)).hasMessageContaining("Bitte einen Ordner auswählen.");
        assertThatThrownBy(() -> service.hochladen(pdf, Long.MAX_VALUE, "x", null, null))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("Den Ordner gibt es nicht mehr.");
        assertThat(tmp.resolve("firmendokumente")).doesNotExist();
        verify(dokumentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Hochladen: scheitert das Speichern in der Datenbank, wird die Datei wieder entfernt")
    void hochladenAufraeumen() throws Exception {
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        given(dokumentRepository.saveAndFlush(any())).willThrow(new IllegalStateException("DB weg"));
        MockMultipartFile pdf = new MockMultipartFile("datei", "a.pdf", "application/pdf", PDF);

        assertThatThrownBy(() -> service.hochladen(pdf, 1L, "x", null, null)).isInstanceOf(IllegalStateException.class);
        try (var dateien = Files.list(tmp.resolve("firmendokumente"))) {
            assertThat(dateien).isEmpty();
        }
    }

    @Test
    @DisplayName("Liste im Ordner: Seite wird begrenzt (page < 0 → 0, size > 50 → 50); unbekannter Ordner → 404")
    void imOrdner() {
        given(ordnerRepository.existsById(1L)).willReturn(true);
        given(dokumentRepository.findeImOrdner(1L, PageRequest.of(0, 50)))
                .willReturn(new PageImpl<>(List.of(dokument(7, "x")), PageRequest.of(0, 50), 120));

        FirmendokumentSeiteDto seite = service.imOrdner(1L, -1, 100_000);

        assertThat(seite.gesamt()).isEqualTo(120);
        assertThat(seite.seite()).isZero();
        assertThat(seite.seitenGroesse()).isEqualTo(50);
        assertThat(seite.eintraege()).extracting(FirmendokumentDto::id).containsExactly(7L);

        assertThatThrownBy(() -> service.imOrdner(-1L, 0, 50)).satisfies(t -> assertThat(status(t)).isEqualTo(404));
    }

    @Test
    @DisplayName("Suche: Muster klein und maskiert; zu kurz oder zu lang → 400")
    void suche() {
        given(dokumentRepository.suche("%50\\% tor%", PageRequest.of(1, 1)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1), 0));

        assertThat(service.suche("  50% TOR ", 1, 0).gesamt()).isZero();
        assertThatThrownBy(() -> service.suche("a", 0, 50)).hasMessageContaining("mindestens 2 Zeichen");
        assertThatThrownBy(() -> service.suche(null, 0, 50)).hasMessageContaining("mindestens 2 Zeichen");
        assertThatThrownBy(() -> service.suche("x".repeat(10_001), 0, 50)).hasMessageContaining("höchstens 100 Zeichen");
    }

    @Test
    @DisplayName("Fällig: alles bis heute + 30 Tage")
    void faellig() {
        given(dokumentRepository.findeFaelligBis(LocalDate.of(2026, 12, 2), PageRequest.of(0, 50)))
                .willReturn(new PageImpl<>(List.of(dokument(7, "x")), PageRequest.of(0, 50), 1));

        assertThat(service.faellig(0, 50).eintraege()).hasSize(1);
    }

    @Test
    @DisplayName("Ändern: Name, Ordner und Datum; Datum lässt sich auch entfernen")
    void aendern() {
        Firmendokument d = dokument(7, "x");
        d.setFaelligAm(LocalDate.of(2026, 1, 1));
        FirmendokumentOrdner arbeitsschutz = new FirmendokumentOrdner();
        arbeitsschutz.setId(2L);
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(d));
        given(ordnerRepository.findById(2L)).willReturn(Optional.of(arbeitsschutz));

        FirmendokumentDto dto = service.aendern(7L, new FirmendokumentAendernDto(" Neu ", 2L, null));

        assertThat(dto.name()).isEqualTo("Neu");
        assertThat(dto.ordnerId()).isEqualTo(2L);
        assertThat(dto.faelligAm()).isNull();
    }

    @Test
    @DisplayName("Ändern/Löschen/Datei: unbekanntes Dokument → 404")
    void nichtGefunden() {
        given(dokumentRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.aendern(0L, new FirmendokumentAendernDto("x", 1L, null)))
                .satisfies(t -> assertThat(status(t)).isEqualTo(404));
        assertThatThrownBy(() -> service.loeschen(-1L)).satisfies(t -> assertThat(status(t)).isEqualTo(404));
        assertThatThrownBy(() -> service.datei(Long.MAX_VALUE)).satisfies(t -> assertThat(status(t)).isEqualTo(404));
    }

    @Test
    @DisplayName("Löschen entfernt Eintrag und Datei")
    void loeschen() {
        String gespeichert = ablage.speichere(PDF);
        Firmendokument d = dokument(7, gespeichert);
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(d));

        service.loeschen(7L);

        verify(dokumentRepository).delete(d);
        assertThat(ablage.pfad(gespeichert)).doesNotExist();
    }

    @Test
    @DisplayName("Löschen in einer Transaktion: Datei verschwindet erst nach dem Commit, bei Rollback bleibt sie")
    void loeschenNachCommit() {
        String gespeichert = ablage.speichere(PDF);
        Firmendokument d = dokument(7, gespeichert);
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(d));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.loeschen(7L);
            assertThat(ablage.pfad(gespeichert)).exists();
            List<TransactionSynchronization> nachher = TransactionSynchronizationManager.getSynchronizations();
            nachher.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(ablage.pfad(gespeichert)).exists();
            nachher.forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(ablage.pfad(gespeichert)).doesNotExist();
    }

    @Test
    @DisplayName("Löschen: lässt sich die Datei nach dem Commit nicht entfernen, bleibt das Löschen trotzdem gültig")
    void loeschenDateiNichtEntfernbar() throws Exception {
        String gespeichert = "0a4f113b-1111-2222-3333-444455556666.pdf";
        Path verzeichnis = Files.createDirectories(tmp.resolve("firmendokumente").resolve(gespeichert));
        Files.writeString(verzeichnis.resolve("drin.txt"), "x");
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(dokument(7, gespeichert)));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.loeschen(7L);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(dokumentRepository).delete(any());
        assertThat(verzeichnis).exists();
    }

    @Test
    @DisplayName("Riesige Seitennummer wird so begrenzt, dass der Versatz nicht überläuft")
    void riesigeSeitennummer() {
        int maxSeite = Integer.MAX_VALUE / 50 - 1;
        given(dokumentRepository.findeFaelligBis(LocalDate.of(2026, 12, 2), PageRequest.of(maxSeite, 50)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(maxSeite, 50), 0));

        assertThat(service.faellig(Integer.MAX_VALUE, 50).seite()).isEqualTo(maxSeite);
        assertThat(PageRequest.of(maxSeite, 50).getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Datei: Pfad und Download-Name; fehlt die Datei auf der Platte → 404")
    void datei() {
        String gespeichert = ablage.speichere(PDF);
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(dokument(7, gespeichert)));
        given(dokumentRepository.findById(8L)).willReturn(Optional.of(dokument(8, "0a4f113b-0000-0000-0000-000000000000.pdf")));

        FirmendokumentService.Datei datei = service.datei(7L);

        assertThat(datei.pfad()).isEqualTo(ablage.pfad(gespeichert));
        assertThat(datei.dateiname()).isEqualTo("Torprüfung Halle 2.pdf");
        assertThatThrownBy(() -> service.datei(8L)).hasMessageContaining("Datei zu diesem Dokument wurde nicht gefunden");
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "Torprüfung;Torprüfung.pdf",
            "Bericht.PDF;Bericht.PDF",
            "a/b\\c:d;a_b_c_d.pdf",
            "'  ';dokument.pdf",
            "<script>;_script_.pdf"
    })
    @DisplayName("Download-Name ohne Pfad- und Sonderzeichen, immer mit .pdf")
    void downloadName(String name, String erwartet) {
        assertThat(FirmendokumentService.downloadName(name)).isEqualTo(erwartet);
    }
}
