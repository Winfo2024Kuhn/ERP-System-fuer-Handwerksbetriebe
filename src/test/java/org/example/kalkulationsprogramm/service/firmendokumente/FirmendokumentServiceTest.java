package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.ANSEHEN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.firmendokumente.Firmendokument;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdnerRecht;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAendernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRechtRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentRepository;
import org.example.kalkulationsprogramm.service.AbteilungsRechtPruefung;
import org.example.kalkulationsprogramm.service.AbteilungsRechtPruefung.Zugehoerigkeit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dokumente mit echter Rechteprüfung ({@link FirmendokumentZugriff}): „Prüfungen“ ist offen, „Versicherungen“
 * eingeschränkt – Max Mustermann aus der Werkstatt (Abteilung 11) darf dort höchstens ansehen.
 */
@ExtendWith(MockitoExtension.class)
class FirmendokumentServiceTest {

    private static final byte[] PDF = "%PDF-1.7\nInhalt".getBytes(StandardCharsets.US_ASCII);
    private static final String VERBOTEN = "Du darfst in diesem Ordner nichts ändern.";

    @TempDir Path tmp;
    @Mock FirmendokumentRepository dokumentRepository;
    @Mock FirmendokumentOrdnerRepository ordnerRepository;
    @Mock FirmendokumentOrdnerRechtRepository rechtRepository;
    @Mock AbteilungsRechtPruefung rechtPruefung;

    private final Authentication max = mock(Authentication.class);
    private FirmendokumentDateiablage ablage;
    private FirmendokumentService service;
    private FirmendokumentOrdner pruefungen;
    private FirmendokumentOrdner versicherungen;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T08:00:00Z"), ZoneId.of("Europe/Berlin"));
        ablage = new FirmendokumentDateiablage(tmp.toString());
        FirmendokumentZugriff zugriff = new FirmendokumentZugriff(rechtPruefung, ordnerRepository, rechtRepository);
        service = new FirmendokumentService(dokumentRepository, ordnerRepository, ablage, zugriff, clock);
        pruefungen = new FirmendokumentOrdner();
        pruefungen.setId(1L);
        pruefungen.setName("Prüfungen");
        versicherungen = new FirmendokumentOrdner();
        versicherungen.setId(3L);
        versicherungen.setName("Versicherungen");
        versicherungen.setRechteEingeschraenkt(true);
    }

    private Firmendokument dokument(long id, String gespeichert) {
        return dokument(id, gespeichert, pruefungen, "scan.pdf");
    }

    private Firmendokument dokument(long id, String gespeichert, FirmendokumentOrdner ordner, String original) {
        Firmendokument d = new Firmendokument();
        d.setId(id);
        d.setOrdner(ordner);
        d.setName("Torprüfung Halle 2");
        d.setOriginalDateiname(original);
        d.setGespeicherterDateiname(gespeichert);
        d.setGroesseBytes(PDF.length);
        d.setHochgeladenAm(LocalDateTime.of(2026, 11, 2, 9, 0));
        return d;
    }

    /** Werkstatt (11) darf „Versicherungen“ nur ansehen. */
    private void werkstattDarfVersicherungenAnsehen() {
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of(11L)));
        given(rechtRepository.findeFuerOrdnerUndAbteilungen(3L, Set.of(11L))).willReturn(List.of(recht(ANSEHEN)));
    }

    /** Ab jetzt hat Werkstatt (11) an „Versicherungen“ keinen Eintrag mehr → sieht den Zweig nicht. */
    private void werkstattSiehtVersicherungenNicht() {
        given(rechtRepository.findeFuerOrdnerUndAbteilungen(3L, Set.of(11L))).willReturn(List.of());
    }

    private FirmendokumentOrdnerRecht recht(FirmendokumentZugriffsStufe stufe) {
        Abteilung werkstatt = new Abteilung();
        werkstatt.setId(11L);
        FirmendokumentOrdnerRecht r = new FirmendokumentOrdnerRecht();
        r.setOrdner(versicherungen);
        r.setAbteilung(werkstatt);
        r.setStufe(stufe);
        return r;
    }

    private static int status(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode().value();
    }

    private void speichernGibtId7() {
        given(dokumentRepository.saveAndFlush(any())).willAnswer(a -> {
            Firmendokument d = a.getArgument(0);
            d.setId(7L);
            return d;
        });
    }

    @Test
    @DisplayName("Hochladen: Datei liegt als <UUID>.bin, Eintrag mit Name, Ordner, Datum, Hochlader, Dateiart und Ändern-Recht")
    void hochladen() throws Exception {
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        speichernGibtId7();
        MockMultipartFile datei = new MockMultipartFile("datei", "../../scan_0042.pdf", "application/pdf", PDF);

        FirmendokumentDto dto = service.hochladen(datei, 1L, "  Torprüfung Halle 2 – 2026 ", LocalDate.of(2027, 10, 31),
                "Max Mustermann", max);

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.ordnerId()).isEqualTo(1L);
        assertThat(dto.name()).isEqualTo("Torprüfung Halle 2 – 2026");
        assertThat(dto.originalDateiname()).isEqualTo("scan_0042.pdf");
        assertThat(dto.groesseBytes()).isEqualTo(PDF.length);
        assertThat(dto.hochgeladenAm()).isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 0));
        assertThat(dto.hochgeladenVon()).isEqualTo("Max Mustermann");
        assertThat(dto.faelligAm()).isEqualTo(LocalDate.of(2027, 10, 31));
        assertThat(dto.url()).isEqualTo("/api/firmendokumente/7/datei");
        assertThat(dto.dateiart()).isEqualTo("PDF");
        assertThat(dto.darfAendern()).isTrue();
        try (var dateien = Files.list(tmp.resolve("firmendokumente"))) {
            List<Path> liste = dateien.toList();
            assertThat(liste).hasSize(1);
            assertThat(liste.get(0).getFileName().toString()).endsWith(".bin");
            assertThat(Files.readAllBytes(liste.get(0))).isEqualTo(PDF);
        }
    }

    @Test
    @DisplayName("Hochladen: Word-Datei und Foto gehen auch – Dateiart SONSTIGE bzw. BILD")
    void hochladenAndereFormate() {
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        speichernGibtId7();

        FirmendokumentDto word = service.hochladen(new MockMultipartFile("datei", "Haftpflicht.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "PK".getBytes(StandardCharsets.US_ASCII)), 1L, "Haftpflicht 2026", null, null, max);
        FirmendokumentDto foto = service.hochladen(new MockMultipartFile("datei", "IMG_0001.JPG", "image/jpeg",
                new byte[] { (byte) 0xFF, (byte) 0xD8 }), 1L, "Foto Tor", null, null, max);

        assertThat(word.dateiart()).isEqualTo("SONSTIGE");
        assertThat(word.originalDateiname()).isEqualTo("Haftpflicht.docx");
        assertThat(foto.dateiart()).isEqualTo("BILD");
    }

    @Test
    @DisplayName("Hochladen: Programmdatei, fehlender Name oder Ordner → 400, nichts wird gespeichert")
    void hochladenFehler() {
        MockMultipartFile pdf = new MockMultipartFile("datei", "a.pdf", "application/pdf", PDF);
        MockMultipartFile exe = new MockMultipartFile("datei", "rechnung.pdf.exe", "application/pdf",
                "MZ".getBytes(StandardCharsets.US_ASCII));
        given(ordnerRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.hochladen(exe, 1L, "x", null, null, max))
                .hasMessageContaining("Programmdateien (z. B. .exe oder .bat)");
        assertThatThrownBy(() -> service.hochladen(pdf, 1L, " ", null, null, max))
                .hasMessageContaining("Bitte einen Namen für das Dokument eingeben.");
        assertThatThrownBy(() -> service.hochladen(pdf, null, "x", null, null, max))
                .hasMessageContaining("Bitte einen Ordner auswählen.");
        assertThatThrownBy(() -> service.hochladen(pdf, Long.MAX_VALUE, "x", null, null, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("Den Ordner gibt es nicht mehr.");
        assertThat(tmp.resolve("firmendokumente")).doesNotExist();
        verify(dokumentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Hochladen: nur Ansehen-Recht → 403, unsichtbarer Ordner → 400 „gibt es nicht mehr“; nichts gespeichert")
    void hochladenOhneRecht() {
        MockMultipartFile pdf = new MockMultipartFile("datei", "a.pdf", "application/pdf", PDF);
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        werkstattDarfVersicherungenAnsehen();

        assertThatThrownBy(() -> service.hochladen(pdf, 3L, "Police", null, null, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403)).hasMessageContaining(VERBOTEN);

        werkstattSiehtVersicherungenNicht();
        assertThatThrownBy(() -> service.hochladen(pdf, 3L, "Police", null, null, max))
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

        assertThatThrownBy(() -> service.hochladen(pdf, 1L, "x", null, null, max))
                .isInstanceOf(IllegalStateException.class);
        try (var dateien = Files.list(tmp.resolve("firmendokumente"))) {
            assertThat(dateien).isEmpty();
        }
    }

    @Test
    @DisplayName("Liste im Ordner: Seite wird begrenzt (page < 0 → 0, size > 50 → 50); unbekannter Ordner → 404")
    void imOrdner() {
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        given(dokumentRepository.findeImOrdner(1L, PageRequest.of(0, 50)))
                .willReturn(new PageImpl<>(List.of(dokument(7, "x")), PageRequest.of(0, 50), 120));

        FirmendokumentSeiteDto seite = service.imOrdner(1L, -1, 100_000, max);

        assertThat(seite.gesamt()).isEqualTo(120);
        assertThat(seite.seite()).isZero();
        assertThat(seite.seitenGroesse()).isEqualTo(50);
        assertThat(seite.eintraege()).extracting(FirmendokumentDto::id).containsExactly(7L);
        assertThat(seite.eintraege().get(0).darfAendern()).isTrue();

        assertThatThrownBy(() -> service.imOrdner(-1L, 0, 50, max)).satisfies(t -> assertThat(status(t)).isEqualTo(404));
    }

    @Test
    @DisplayName("Liste im Ordner: nur ansehen → darfAendern=false; unsichtbarer Ordner → 404 wie „gibt es nicht“")
    void imOrdnerRechte() {
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        werkstattDarfVersicherungenAnsehen();
        given(dokumentRepository.findeImOrdner(3L, PageRequest.of(0, 50))).willReturn(new PageImpl<>(
                List.of(dokument(8, "x", versicherungen, "Police.docx")), PageRequest.of(0, 50), 1));

        FirmendokumentDto dto = service.imOrdner(3L, 0, 50, max).eintraege().get(0);
        assertThat(dto.darfAendern()).isFalse();
        assertThat(dto.dateiart()).isEqualTo("SONSTIGE");

        werkstattSiehtVersicherungenNicht();
        assertThatThrownBy(() -> service.imOrdner(3L, 0, 50, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(404))
                .hasMessageContaining("Den Ordner gibt es nicht mehr.");
    }

    @Test
    @DisplayName("Suche: Muster klein und maskiert; zu kurz oder zu lang → 400; ohne Einschränkung die Abfrage über alles")
    void suche() {
        given(dokumentRepository.suche("%50\\% tor%", PageRequest.of(1, 1)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1), 0));

        assertThat(service.suche("  50% TOR ", 1, 0, max).gesamt()).isZero();
        assertThatThrownBy(() -> service.suche("a", 0, 50, max)).hasMessageContaining("mindestens 2 Zeichen");
        assertThatThrownBy(() -> service.suche(null, 0, 50, max)).hasMessageContaining("mindestens 2 Zeichen");
        assertThatThrownBy(() -> service.suche("x".repeat(10_001), 0, 50, max))
                .hasMessageContaining("höchstens 100 Zeichen");
        verify(dokumentRepository, never()).sucheInOrdnern(any(), anyCollection(), any());
    }

    @Test
    @DisplayName("Suche mit eingeschränkten Ordnern: nur in sichtbaren Ordnern, mit Ändern-Recht je Dokument")
    void sucheEingeschraenkt() {
        FirmendokumentOrdner geheim = new FirmendokumentOrdner();
        geheim.setId(6L);
        geheim.setName("Geheim");
        geheim.setRechteEingeschraenkt(true);
        given(ordnerRepository.findeSortiert(PageRequest.of(0, FirmendokumentZugriff.MAX_ORDNER)))
                .willReturn(List.of(pruefungen, versicherungen, geheim));
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of(11L)));
        given(rechtRepository.findeFuerAbteilungen(Set.of(11L))).willReturn(List.of(recht(ANSEHEN)));
        given(dokumentRepository.sucheInOrdnern("%tor%", Set.of(1L, 3L), PageRequest.of(0, 50))).willReturn(
                new PageImpl<>(List.of(dokument(7, "x"), dokument(8, "y", versicherungen, "tor.pdf")),
                        PageRequest.of(0, 50), 2));

        List<FirmendokumentDto> treffer = service.suche("tor", 0, 50, max).eintraege();

        assertThat(treffer).extracting(FirmendokumentDto::id, FirmendokumentDto::darfAendern)
                .containsExactly(tuple(7L, true), tuple(8L, false));
        verify(dokumentRepository, never()).suche(any(), any());
    }

    @Test
    @DisplayName("Suche und Fällige: sieht man keinen Ordner, kommt eine leere Seite ohne Abfrage")
    void nichtsSichtbar() {
        given(ordnerRepository.findeSortiert(PageRequest.of(0, FirmendokumentZugriff.MAX_ORDNER)))
                .willReturn(List.of(versicherungen));
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of()));

        FirmendokumentSeiteDto suche = service.suche("tor", 2, 20, max);
        FirmendokumentSeiteDto faellig = service.faellig(0, 50, max);

        assertThat(suche.eintraege()).isEmpty();
        assertThat(suche.gesamt()).isZero();
        assertThat(suche.seite()).isEqualTo(2);
        assertThat(suche.seitenGroesse()).isEqualTo(20);
        assertThat(faellig.eintraege()).isEmpty();
        verifyNoInteractions(dokumentRepository);
    }

    @Test
    @DisplayName("Fällig: alles bis heute + 30 Tage")
    void faellig() {
        given(dokumentRepository.findeFaelligBis(LocalDate.of(2026, 12, 2), PageRequest.of(0, 50)))
                .willReturn(new PageImpl<>(List.of(dokument(7, "x")), PageRequest.of(0, 50), 1));

        assertThat(service.faellig(0, 50, max).eintraege()).hasSize(1);
    }

    @Test
    @DisplayName("Fällig mit eingeschränkten Ordnern: nur aus sichtbaren Ordnern")
    void faelligEingeschraenkt() {
        given(ordnerRepository.findeSortiert(PageRequest.of(0, FirmendokumentZugriff.MAX_ORDNER)))
                .willReturn(List.of(pruefungen, versicherungen));
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of(11L)));
        given(rechtRepository.findeFuerAbteilungen(Set.of(11L))).willReturn(List.of());
        given(dokumentRepository.findeFaelligBisInOrdnern(LocalDate.of(2026, 12, 2), Set.of(1L), PageRequest.of(0, 50)))
                .willReturn(new PageImpl<>(List.of(dokument(7, "x")), PageRequest.of(0, 50), 1));

        assertThat(service.faellig(0, 50, max).eintraege()).extracting(FirmendokumentDto::id).containsExactly(7L);
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

        FirmendokumentDto dto = service.aendern(7L, new FirmendokumentAendernDto(" Neu ", 2L, null), max);

        assertThat(dto.name()).isEqualTo("Neu");
        assertThat(dto.ordnerId()).isEqualTo(2L);
        assertThat(dto.faelligAm()).isNull();
        assertThat(dto.darfAendern()).isTrue();
    }

    @Test
    @DisplayName("Ändern: nur ansehen → 403; Ziel nur ansehen → 403, Ziel unsichtbar → 400; unsichtbares Dokument → 404")
    void aendernRechte() {
        Firmendokument police = dokument(8, "x", versicherungen, "police.pdf");
        given(dokumentRepository.findById(8L)).willReturn(Optional.of(police));
        werkstattDarfVersicherungenAnsehen();

        assertThatThrownBy(() -> service.aendern(8L, new FirmendokumentAendernDto("Neu", 3L, null), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403)).hasMessageContaining(VERBOTEN);

        Firmendokument torpruefung = dokument(7, "x");
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(torpruefung));
        given(ordnerRepository.findById(3L)).willReturn(Optional.of(versicherungen));
        assertThatThrownBy(() -> service.aendern(7L, new FirmendokumentAendernDto("Neu", 3L, null), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403)).hasMessageContaining(VERBOTEN);
        assertThat(torpruefung.getOrdner()).isSameAs(pruefungen);
        assertThat(torpruefung.getName()).isEqualTo("Torprüfung Halle 2");

        werkstattSiehtVersicherungenNicht();
        assertThatThrownBy(() -> service.aendern(7L, new FirmendokumentAendernDto("Neu", 3L, null), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400))
                .hasMessageContaining("Den Ordner gibt es nicht mehr.");
        assertThatThrownBy(() -> service.aendern(8L, new FirmendokumentAendernDto("Neu", 1L, null), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(404))
                .hasMessageContaining("Das Dokument gibt es nicht mehr.");
    }

    @Test
    @DisplayName("Ändern: Dokument aus eingeschränktem Zweig in anderen Hauptordner nur als Admin; im selben Zweig erlaubt")
    void aendernVerschiebenAusEingeschraenktemZweig() {
        FirmendokumentOrdner haftpflicht = new FirmendokumentOrdner();
        haftpflicht.setId(4L);
        haftpflicht.setParent(versicherungen);
        Firmendokument police = dokument(8, "x", versicherungen, "police.pdf");
        given(dokumentRepository.findById(8L)).willReturn(Optional.of(police));
        given(ordnerRepository.findById(1L)).willReturn(Optional.of(pruefungen));
        given(ordnerRepository.findById(4L)).willReturn(Optional.of(haftpflicht));
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of(11L)));
        given(rechtRepository.findeFuerOrdnerUndAbteilungen(3L, Set.of(11L)))
                .willReturn(List.of(recht(FirmendokumentZugriffsStufe.AENDERN)));

        assertThatThrownBy(() -> service.aendern(8L, new FirmendokumentAendernDto("Police", 1L, null), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(403))
                .hasMessageContaining("Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.");
        assertThat(police.getOrdner()).isSameAs(versicherungen);

        assertThat(service.aendern(8L, new FirmendokumentAendernDto("Police", 4L, null), max).ordnerId()).isEqualTo(4L);

        police.setOrdner(versicherungen);
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(true, Set.of()));
        assertThat(service.aendern(8L, new FirmendokumentAendernDto("Police", 1L, null), max).ordnerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Hochladen und Ändern: Dokumentname mit Programmendung → 400, nichts gespeichert oder geändert")
    void nameMitProgrammendung() {
        String meldung = "Der Name darf nicht auf eine Programmendung wie .exe enden.";
        MockMultipartFile pdf = new MockMultipartFile("datei", "a.pdf", "application/pdf", PDF);
        Firmendokument d = dokument(7, "x");
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(d));

        assertThatThrownBy(() -> service.hochladen(pdf, 1L, "Rechnung.exe", null, null, max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400)).hasMessageContaining(meldung);
        assertThatThrownBy(() -> service.aendern(7L, new FirmendokumentAendernDto("Start.BAT. ", 1L, null), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(400)).hasMessageContaining(meldung);
        assertThat(tmp.resolve("firmendokumente")).doesNotExist();
        assertThat(d.getName()).isEqualTo("Torprüfung Halle 2");
    }

    @Test
    @DisplayName("Ändern/Löschen/Datei: unbekanntes Dokument → 404")
    void nichtGefunden() {
        given(dokumentRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.aendern(0L, new FirmendokumentAendernDto("x", 1L, null), max))
                .satisfies(t -> assertThat(status(t)).isEqualTo(404));
        assertThatThrownBy(() -> service.loeschen(-1L, max)).satisfies(t -> assertThat(status(t)).isEqualTo(404));
        assertThatThrownBy(() -> service.datei(Long.MAX_VALUE, max)).satisfies(t -> assertThat(status(t)).isEqualTo(404));
    }

    @Test
    @DisplayName("Löschen entfernt Eintrag und Datei")
    void loeschen() {
        String gespeichert = ablage.speichere(PDF);
        Firmendokument d = dokument(7, gespeichert);
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(d));

        service.loeschen(7L, max);

        verify(dokumentRepository).delete(d);
        assertThat(ablage.pfad(gespeichert)).doesNotExist();
    }

    @Test
    @DisplayName("Löschen: nur ansehen → 403, unsichtbar → 404; Eintrag und Datei bleiben")
    void loeschenRechte() {
        String gespeichert = ablage.speichere(PDF);
        given(dokumentRepository.findById(8L)).willReturn(Optional.of(dokument(8, gespeichert, versicherungen, "a.pdf")));
        werkstattDarfVersicherungenAnsehen();

        assertThatThrownBy(() -> service.loeschen(8L, max)).satisfies(t -> assertThat(status(t)).isEqualTo(403));
        werkstattSiehtVersicherungenNicht();
        assertThatThrownBy(() -> service.loeschen(8L, max)).satisfies(t -> assertThat(status(t)).isEqualTo(404));

        verify(dokumentRepository, never()).delete(any());
        assertThat(ablage.pfad(gespeichert)).exists();
    }

    @Test
    @DisplayName("Löschen in einer Transaktion: Datei verschwindet erst nach dem Commit, bei Rollback bleibt sie")
    void loeschenNachCommit() {
        String gespeichert = ablage.speichere(PDF);
        Firmendokument d = dokument(7, gespeichert);
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(d));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.loeschen(7L, max);
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
    @DisplayName("Löschen: lässt sich die (alte <UUID>.pdf-)Datei nach dem Commit nicht entfernen, bleibt das Löschen gültig")
    void loeschenDateiNichtEntfernbar() throws Exception {
        String gespeichert = "0a4f113b-1111-2222-3333-444455556666.pdf";
        Path verzeichnis = Files.createDirectories(tmp.resolve("firmendokumente").resolve(gespeichert));
        Files.writeString(verzeichnis.resolve("drin.txt"), "x");
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(dokument(7, gespeichert)));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.loeschen(7L, max);
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

        assertThat(service.faellig(Integer.MAX_VALUE, 50, max).seite()).isEqualTo(maxSeite);
        assertThat(PageRequest.of(maxSeite, 50).getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Datei: Pfad, Download-Name und PDF-Typ; alte <UUID>.pdf lesbar; fehlt die Datei auf der Platte → 404")
    void datei() throws Exception {
        String gespeichert = ablage.speichere(PDF);
        String alt = "0a4f113b-0000-0000-0000-00000000000a.pdf";
        Files.write(tmp.resolve("firmendokumente").resolve(alt), PDF);
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(dokument(7, gespeichert)));
        given(dokumentRepository.findById(6L)).willReturn(Optional.of(dokument(6, alt, pruefungen, "scan")));
        given(dokumentRepository.findById(8L)).willReturn(Optional.of(dokument(8, "0a4f113b-0000-0000-0000-000000000000.pdf")));

        FirmendokumentService.Datei datei = service.datei(7L, max);
        FirmendokumentService.Datei altDatei = service.datei(6L, max);

        assertThat(datei.pfad()).isEqualTo(ablage.pfad(gespeichert));
        assertThat(datei.dateiname()).isEqualTo("Torprüfung Halle 2.pdf");
        assertThat(datei.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(datei.immerAlsAnhang()).isFalse();
        assertThat(altDatei.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(altDatei.dateiname()).isEqualTo("Torprüfung Halle 2.pdf");
        assertThatThrownBy(() -> service.datei(8L, max)).hasMessageContaining("Datei zu diesem Dokument wurde nicht gefunden");
    }

    @Test
    @DisplayName("Datei: Word-Datei als octet-stream und immer als Anhang mit Endung .docx; Bild als image/png")
    void dateiAndereFormate() {
        String word = ablage.speichere(new byte[] { 1 });
        String bild = ablage.speichere(new byte[] { 2 });
        given(dokumentRepository.findById(7L)).willReturn(Optional.of(dokument(7, word, pruefungen, "Police.docx")));
        given(dokumentRepository.findById(9L)).willReturn(Optional.of(dokument(9, bild, pruefungen, "foto.png")));

        FirmendokumentService.Datei w = service.datei(7L, max);
        FirmendokumentService.Datei b = service.datei(9L, max);

        assertThat(w.dateiname()).isEqualTo("Torprüfung Halle 2.docx");
        assertThat(w.mediaType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(w.immerAlsAnhang()).isTrue();
        assertThat(b.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(b.immerAlsAnhang()).isFalse();
    }

    @Test
    @DisplayName("Datei: ansehen reicht; unsichtbares Dokument → 404 wie „gibt es nicht“")
    void dateiRechte() {
        String gespeichert = ablage.speichere(PDF);
        given(dokumentRepository.findById(8L)).willReturn(Optional.of(dokument(8, gespeichert, versicherungen, "a.pdf")));
        werkstattDarfVersicherungenAnsehen();

        assertThat(service.datei(8L, max).pfad()).isEqualTo(ablage.pfad(gespeichert));

        werkstattSiehtVersicherungenNicht();
        assertThatThrownBy(() -> service.datei(8L, max)).satisfies(t -> assertThat(status(t)).isEqualTo(404))
                .hasMessageContaining("Das Dokument gibt es nicht mehr.");
    }
}
