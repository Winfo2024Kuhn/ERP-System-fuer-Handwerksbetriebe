package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelDokument;
import org.example.kalkulationsprogramm.domain.ArtikelDokumentTyp;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumentDto;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelDokumentRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testdaten sind ausschliesslich Dummy-Daten (DSGVO) - "Max Mustermann" und
 * frei erfundene Artikel/Dokumente, keine echten Kunden- oder Lieferantendaten.
 */
@ExtendWith(MockitoExtension.class)
class ArtikelDokumentServiceTest {

    @Mock
    private ArtikelDokumentRepository dokumentRepository;
    @Mock
    private ArtikelRepository artikelRepository;

    @InjectMocks
    private ArtikelDokumentService service;

    private static Artikel artikel(Long id) {
        Artikel artikel = new Artikel();
        artikel.setId(id);
        artikel.setProduktname("Rundrohr 42,4x2");
        return artikel;
    }

    @Nested
    @DisplayName("ladeHoch")
    class LadeHoch {

        @Test
        @DisplayName("Happy Path: Datei wird auf der Platte abgelegt und das Dokument gespeichert")
        void speichertDateiUndDokument(@TempDir Path tempDir) throws IOException {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

            Artikel artikel = artikel(1L);
            given(artikelRepository.findById(1L)).willReturn(Optional.of(artikel));
            given(dokumentRepository.save(any(ArtikelDokument.class))).willAnswer(inv -> {
                ArtikelDokument d = inv.getArgument(0);
                d.setId(100L);
                return d;
            });

            MockMultipartFile datei = new MockMultipartFile(
                    "datei", "zulassung.pdf", "application/pdf", "Pruefzeugnis-Inhalt".getBytes());

            ArtikelDokumentDto ergebnis = service.ladeHoch(1L, datei, ArtikelDokumentTyp.ZULASSUNG,
                    "Pruefzeugnis 2026");

            assertThat(ergebnis.getId()).isEqualTo(100L);
            assertThat(ergebnis.getOriginalDateiname()).isEqualTo("zulassung.pdf");
            assertThat(ergebnis.getTyp()).isEqualTo(ArtikelDokumentTyp.ZULASSUNG);
            assertThat(ergebnis.getBeschreibung()).isEqualTo("Pruefzeugnis 2026");
            assertThat(ergebnis.getDateigroesseBytes()).isEqualTo(datei.getSize());
            assertThat(ergebnis.getUrl()).isEqualTo("/api/artikel/dokumente/100/datei");

            // Physische Datei liegt unter uploads/artikel/{artikelId}/ mit
            // UUID-Praefix, der Originalname bleibt erhalten.
            Path artikelDir = tempDir.resolve("artikel").resolve("1");
            try (var dateien = Files.list(artikelDir)) {
                Path gespeicherteDatei = dateien.findFirst().orElseThrow();
                assertThat(gespeicherteDatei.getFileName().toString()).endsWith("_zulassung.pdf");
                assertThat(Files.readString(gespeicherteDatei)).isEqualTo("Pruefzeugnis-Inhalt");
            }
        }

        @Test
        @DisplayName("Path-Traversal im Dateinamen wird abgelehnt")
        void lehntPathTraversalAb(@TempDir Path tempDir) {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
            given(artikelRepository.findById(1L)).willReturn(Optional.of(artikel(1L)));

            MockMultipartFile datei = new MockMultipartFile(
                    "datei", "../../../../etc/passwd.pdf", "application/pdf", "boese".getBytes());

            assertThatThrownBy(() -> service.ladeHoch(1L, datei, ArtikelDokumentTyp.SONSTIGES, null))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(dokumentRepository, never()).save(any());
            // Es darf auch kein Verzeichnis ausserhalb des Artikel-Ordners entstanden sein.
            assertThat(Files.exists(tempDir.resolve("etc"))).isFalse();
        }

        @Test
        @DisplayName("Ein eingebetteter Pfadtrenner im Dateinamen wird abgelehnt")
        void lehntPfadtrennerImDateinamenAb(@TempDir Path tempDir) {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
            given(artikelRepository.findById(1L)).willReturn(Optional.of(artikel(1L)));

            MockMultipartFile datei = new MockMultipartFile(
                    "datei", "unterordner/versteckt.pdf", "application/pdf", "boese".getBytes());

            assertThatThrownBy(() -> service.ladeHoch(1L, datei, ArtikelDokumentTyp.SONSTIGES, null))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(dokumentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Verbotene Dateiendungen werden abgelehnt")
        void lehntVerboteneEndungenAb(@TempDir Path tempDir) {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
            given(artikelRepository.findById(1L)).willReturn(Optional.of(artikel(1L)));

            for (String dateiname : List.of("schadcode.exe", "skript.bat", "angriff.js", "shell.sh")) {
                MockMultipartFile datei = new MockMultipartFile(
                        "datei", dateiname, "application/octet-stream", "inhalt".getBytes());

                assertThatThrownBy(() -> service.ladeHoch(1L, datei, ArtikelDokumentTyp.SONSTIGES, null))
                        .as("Endung von %s muss abgelehnt werden", dateiname)
                        .isInstanceOf(IllegalArgumentException.class);
            }
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Erlaubte Bild- und PDF-Endungen werden akzeptiert")
        void akzeptiertErlaubteEndungen(@TempDir Path tempDir) throws IOException {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
            given(artikelRepository.findById(1L)).willReturn(Optional.of(artikel(1L)));
            given(dokumentRepository.save(any(ArtikelDokument.class))).willAnswer(inv -> inv.getArgument(0));

            for (String dateiname : List.of("plan.pdf", "bild.png", "foto.jpg", "foto2.jpeg", "bild.webp",
                    "animation.gif")) {
                MockMultipartFile datei = new MockMultipartFile(
                        "datei", dateiname, "application/octet-stream", "inhalt".getBytes());

                assertThatCode(() -> service.ladeHoch(1L, datei, ArtikelDokumentTyp.DATENBLATT, null))
                        .as("Endung von %s muss akzeptiert werden", dateiname)
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("Eine Datei ueber 10 MB wird abgelehnt")
        void lehntZuGrosseDateiAb(@TempDir Path tempDir) {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
            given(artikelRepository.findById(1L)).willReturn(Optional.of(artikel(1L)));

            MultipartFile zuGross = mock(MultipartFile.class);
            when(zuGross.isEmpty()).thenReturn(false);
            when(zuGross.getSize()).thenReturn(10L * 1024 * 1024 + 1);

            assertThatThrownBy(() -> service.ladeHoch(1L, zuGross, ArtikelDokumentTyp.DATENBLATT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("10 MB");

            verify(dokumentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Unbekannte Artikel-ID liefert NotFoundException (404)")
        void wirftNotFound_beiUnbekannterArtikelId() {
            given(artikelRepository.findById(999L)).willReturn(Optional.empty());
            MockMultipartFile datei = new MockMultipartFile("datei", "plan.pdf", "application/pdf", "x".getBytes());

            assertThatThrownBy(() -> service.ladeHoch(999L, datei, ArtikelDokumentTyp.DATENBLATT, null))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Negative und Null-Artikel-ID liefern NotFoundException (404)")
        void wirftNotFound_beiNegativerOderNullId() {
            given(artikelRepository.findById(-1L)).willReturn(Optional.empty());
            given(artikelRepository.findById(0L)).willReturn(Optional.empty());
            MockMultipartFile datei = new MockMultipartFile("datei", "plan.pdf", "application/pdf", "x".getBytes());

            assertThatThrownBy(() -> service.ladeHoch(-1L, datei, ArtikelDokumentTyp.DATENBLATT, null))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> service.ladeHoch(0L, datei, ArtikelDokumentTyp.DATENBLATT, null))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Ein neues Vorschaubild ersetzt ein vorhandenes - alter Eintrag und alte Datei verschwinden")
        void ersetztVorhandenesVorschaubild(@TempDir Path tempDir) throws IOException {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

            Artikel artikel = artikel(5L);
            // Vorschaubild-Uploads sperren den Artikel-Datensatz (findByIdForUpdate),
            // nicht das gewoehnliche findById - siehe ladeHoch-Javadoc.
            given(artikelRepository.findByIdForUpdate(5L)).willReturn(Optional.of(artikel));

            // Vorhandenes Vorschaubild samt physischer Datei anlegen.
            Path artikelDir = tempDir.resolve("artikel").resolve("5");
            Files.createDirectories(artikelDir);
            Path altesFile = artikelDir.resolve("alt-uuid_altesbild.png");
            Files.writeString(altesFile, "altes-bild");

            ArtikelDokument altesDokument = new ArtikelDokument();
            altesDokument.setId(50L);
            altesDokument.setArtikel(artikel);
            altesDokument.setTyp(ArtikelDokumentTyp.VORSCHAUBILD);
            altesDokument.setGespeicherterDateiname("alt-uuid_altesbild.png");

            given(dokumentRepository.findFirstByArtikelIdAndTyp(5L, ArtikelDokumentTyp.VORSCHAUBILD))
                    .willReturn(Optional.of(altesDokument));
            given(dokumentRepository.save(any(ArtikelDokument.class))).willAnswer(inv -> {
                ArtikelDokument d = inv.getArgument(0);
                d.setId(51L);
                return d;
            });

            MockMultipartFile neuesBild = new MockMultipartFile(
                    "datei", "neuesbild.png", "image/png", "neues-bild".getBytes());

            ArtikelDokumentDto ergebnis = service.ladeHoch(5L, neuesBild, ArtikelDokumentTyp.VORSCHAUBILD, null);

            assertThat(ergebnis.getId()).isEqualTo(51L);
            verify(dokumentRepository).delete(altesDokument);
            assertThat(Files.exists(altesFile)).isFalse();

            // Die neue Datei existiert weiterhin.
            try (var dateien = Files.list(artikelDir)) {
                assertThat(dateien.anyMatch(p -> p.getFileName().toString().endsWith("_neuesbild.png"))).isTrue();
            }
        }

        @Test
        @DisplayName("Kein Dokumenttyp angegeben wird abgelehnt")
        void lehntFehlendenTypAb() {
            given(artikelRepository.findById(1L)).willReturn(Optional.of(artikel(1L)));
            MockMultipartFile datei = new MockMultipartFile("datei", "plan.pdf", "application/pdf", "x".getBytes());

            assertThatThrownBy(() -> service.ladeHoch(1L, datei, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Auch der allererste Vorschaubild-Upload eines Artikels sperrt den Artikel-Datensatz")
        void erstesVorschaubildSperrtDenArtikelDatensatz(@TempDir Path tempDir) throws IOException {
            // Regression: Wuerde hier das gewoehnliche findById genutzt statt
            // findByIdForUpdate, koennten zwei gleichzeitige Erst-Uploads (noch
            // kein vorhandenes Vorschaubild zum Sperren) je eine eigene
            // VORSCHAUBILD-Zeile anlegen - genau die Race, die der Lock verhindert.
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

            given(artikelRepository.findByIdForUpdate(6L)).willReturn(Optional.of(artikel(6L)));
            given(dokumentRepository.findFirstByArtikelIdAndTyp(6L, ArtikelDokumentTyp.VORSCHAUBILD))
                    .willReturn(Optional.empty());
            given(dokumentRepository.save(any(ArtikelDokument.class))).willAnswer(inv -> inv.getArgument(0));

            MockMultipartFile erstesBild = new MockMultipartFile(
                    "datei", "erstesbild.png", "image/png", "bild".getBytes());

            service.ladeHoch(6L, erstesBild, ArtikelDokumentTyp.VORSCHAUBILD, null);

            verify(artikelRepository).findByIdForUpdate(6L);
            verify(artikelRepository, never()).findById(6L);
        }

        @Test
        @DisplayName("Uploads ohne Vorschaubild-Typ sperren den Artikel nicht (kein unnoetiges Serialisieren)")
        void nichtVorschaubildUploadsSperrenDenArtikelNicht(@TempDir Path tempDir) throws IOException {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
            given(artikelRepository.findById(1L)).willReturn(Optional.of(artikel(1L)));
            given(dokumentRepository.save(any(ArtikelDokument.class))).willAnswer(inv -> inv.getArgument(0));

            MockMultipartFile datei = new MockMultipartFile("datei", "plan.pdf", "application/pdf", "x".getBytes());

            service.ladeHoch(1L, datei, ArtikelDokumentTyp.DATENBLATT, null);

            verify(artikelRepository, never()).findByIdForUpdate(any());
        }
    }

    @Nested
    @DisplayName("listeDokumente")
    class ListeDokumente {

        @Test
        @DisplayName("Unbekannte Artikel-ID liefert NotFoundException (404)")
        void wirftNotFound_beiUnbekannterId() {
            given(artikelRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> service.listeDokumente(999L))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Gibt alle Dokumente eines Artikels als DTO zurueck")
        void gibtDokumenteZurueck() {
            given(artikelRepository.existsById(1L)).willReturn(true);

            ArtikelDokument dokument = new ArtikelDokument();
            dokument.setId(10L);
            dokument.setOriginalDateiname("datenblatt.pdf");
            dokument.setTyp(ArtikelDokumentTyp.DATENBLATT);

            given(dokumentRepository.findByArtikelIdOrderBySortierungAscIdAsc(1L))
                    .willReturn(List.of(dokument));

            List<ArtikelDokumentDto> ergebnis = service.listeDokumente(1L);

            assertThat(ergebnis).hasSize(1);
            assertThat(ergebnis.get(0).getId()).isEqualTo(10L);
            assertThat(ergebnis.get(0).getUrl()).isEqualTo("/api/artikel/dokumente/10/datei");
        }
    }

    @Nested
    @DisplayName("ladeDatei")
    class LadeDatei {

        @Test
        @DisplayName("Unbekannte Dokument-ID liefert NotFoundException (404)")
        void wirftNotFound_beiUnbekannterId() {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.ladeDatei(999L))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Fehlt die physische Datei trotz DB-Eintrag, liefert NotFoundException (404)")
        void wirftNotFound_wennDateiFehlt(@TempDir Path tempDir) {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

            ArtikelDokument dokument = new ArtikelDokument();
            dokument.setId(10L);
            dokument.setArtikel(artikel(1L));
            dokument.setGespeicherterDateiname("nicht-vorhanden.pdf");
            given(dokumentRepository.findById(10L)).willReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.ladeDatei(10L))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Liefert die physische Datei mit korrektem Content-Type aus")
        void liefertDateiMitContentType(@TempDir Path tempDir) throws IOException {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

            Path artikelDir = tempDir.resolve("artikel").resolve("1");
            Files.createDirectories(artikelDir);
            Files.writeString(artikelDir.resolve("uuid_plan.pdf"), "PDF-Inhalt");

            ArtikelDokument dokument = new ArtikelDokument();
            dokument.setId(10L);
            dokument.setArtikel(artikel(1L));
            dokument.setOriginalDateiname("plan.pdf");
            dokument.setGespeicherterDateiname("uuid_plan.pdf");
            given(dokumentRepository.findById(10L)).willReturn(Optional.of(dokument));

            ArtikelDokumentService.ArtikelDokumentDatei ergebnis = service.ladeDatei(10L);

            assertThat(ergebnis.originalDateiname()).isEqualTo("plan.pdf");
            assertThat(ergebnis.contentType()).isEqualTo("application/pdf");
            assertThat(ergebnis.resource().exists()).isTrue();
        }
    }

    @Nested
    @DisplayName("loescheDokument")
    class LoescheDokument {

        @Test
        @DisplayName("Unbekannte Dokument-ID liefert NotFoundException (404)")
        void wirftNotFound_beiUnbekannterId() {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.loescheDokument(999L))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Loescht Datenbankeintrag und physische Datei")
        void loeschtDatenbankUndDatei(@TempDir Path tempDir) throws IOException {
            ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

            Path artikelDir = tempDir.resolve("artikel").resolve("1");
            Files.createDirectories(artikelDir);
            Path datei = artikelDir.resolve("uuid_zeichnung.pdf");
            Files.writeString(datei, "Zeichnung");

            ArtikelDokument dokument = new ArtikelDokument();
            dokument.setId(20L);
            dokument.setArtikel(artikel(1L));
            dokument.setGespeicherterDateiname("uuid_zeichnung.pdf");
            given(dokumentRepository.findById(20L)).willReturn(Optional.of(dokument));

            service.loescheDokument(20L);

            verify(dokumentRepository).delete(dokument);
            assertThat(Files.exists(datei)).isFalse();
        }
    }

    @Nested
    @DisplayName("ladeVorschaubildUrls (Bulk fuer die Trefferliste)")
    class LadeVorschaubildUrls {

        @Test
        @DisplayName("Leere Artikel-Liste fragt das Repository gar nicht erst an")
        void leereListeOhneRepositoryZugriff() {
            Map<Long, String> ergebnis = service.ladeVorschaubildUrls(List.of());

            assertThat(ergebnis).isEmpty();
            verify(dokumentRepository, never()).findByArtikelIdInAndTyp(anyList(), any());
        }

        @Test
        @DisplayName("Laedt Vorschaubilder mehrerer Artikel in genau einem Aufruf (keine N+1)")
        void laedtVorschaubilderInEinemRutsch() {
            ArtikelDokument bild1 = new ArtikelDokument();
            bild1.setId(1L);
            bild1.setArtikel(artikel(10L));
            bild1.setTyp(ArtikelDokumentTyp.VORSCHAUBILD);

            ArtikelDokument bild2 = new ArtikelDokument();
            bild2.setId(2L);
            bild2.setArtikel(artikel(20L));
            bild2.setTyp(ArtikelDokumentTyp.VORSCHAUBILD);

            given(dokumentRepository.findByArtikelIdInAndTyp(List.of(10L, 20L, 30L), ArtikelDokumentTyp.VORSCHAUBILD))
                    .willReturn(List.of(bild1, bild2));

            Map<Long, String> ergebnis = service.ladeVorschaubildUrls(List.of(10L, 20L, 30L));

            assertThat(ergebnis).hasSize(2);
            assertThat(ergebnis.get(10L)).isEqualTo("/api/artikel/dokumente/1/datei");
            assertThat(ergebnis.get(20L)).isEqualTo("/api/artikel/dokumente/2/datei");
            assertThat(ergebnis).doesNotContainKey(30L);
            // Genau ein Aufruf fuer die ganze Seite - nicht einer je Artikel.
            verify(dokumentRepository, times(1)).findByArtikelIdInAndTyp(anyList(), any());
        }

        @Test
        @DisplayName("Zwei VORSCHAUBILD-Zeilen am selben Artikel lassen die Suche nicht mit 500 scheitern - der juengere Eintrag gewinnt")
        void duplikatVorschaubildLaesstDieSucheNichtAbstuerzen() {
            // Regression: Collectors.toMap ohne Merge-Function wirft
            // IllegalStateException bei doppeltem Key - das haette FRUEHER die
            // GESAMTE Artikelsuche mit 500 abgeschossen, nicht nur den
            // betroffenen Artikel.
            ArtikelDokument aelterePreview = new ArtikelDokument();
            aelterePreview.setId(1L);
            aelterePreview.setArtikel(artikel(10L));
            aelterePreview.setTyp(ArtikelDokumentTyp.VORSCHAUBILD);
            aelterePreview.setErstelltAm(java.time.LocalDateTime.of(2026, 8, 1, 10, 0));

            ArtikelDokument juengerePreview = new ArtikelDokument();
            juengerePreview.setId(2L);
            juengerePreview.setArtikel(artikel(10L));
            juengerePreview.setTyp(ArtikelDokumentTyp.VORSCHAUBILD);
            juengerePreview.setErstelltAm(java.time.LocalDateTime.of(2026, 8, 2, 10, 0));

            given(dokumentRepository.findByArtikelIdInAndTyp(List.of(10L), ArtikelDokumentTyp.VORSCHAUBILD))
                    .willReturn(List.of(aelterePreview, juengerePreview));

            Map<Long, String> ergebnis = service.ladeVorschaubildUrls(List.of(10L));

            assertThat(ergebnis).hasSize(1);
            assertThat(ergebnis.get(10L)).isEqualTo("/api/artikel/dokumente/2/datei");
        }
    }
}
