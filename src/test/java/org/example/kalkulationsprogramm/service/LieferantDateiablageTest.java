package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.example.kalkulationsprogramm.domain.LieferantBild;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class LieferantDateiablageTest {

    private static final long LIEFERANT_ID = 7L;
    private static final String UUID_PRAEFIX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_";
    private static final byte[] INHALT = "%PDF-1.4 Musterrechnung".getBytes(StandardCharsets.ISO_8859_1);

    @TempDir
    Path tmp;

    /** Bewusst kein Ordner namens {@code uploads}: Die Ablage muss dem konfigurierten Ordner folgen. */
    private Path uploadPath;
    private LieferantDateiablage ablage;

    @BeforeEach
    void setUp() {
        uploadPath = tmp.resolve("eigene-ablage");
        ablage = new LieferantDateiablage(uploadPath.toString());
    }

    private Path lieferantOrdner() {
        return uploadPath.resolve("lieferanten").resolve(String.valueOf(LIEFERANT_ID));
    }

    private Path bilderOrdner() {
        return lieferantOrdner().resolve("bilder");
    }

    private static MockMultipartFile datei(String name) {
        return new MockMultipartFile("datei", name, "application/pdf", INHALT);
    }

    private static Lieferanten lieferant(Long id) {
        var lieferant = new Lieferanten();
        lieferant.setId(id);
        lieferant.setLieferantenname("Musterlieferant GmbH");
        return lieferant;
    }

    private static LieferantBild bild(Long lieferantId, String gespeicherterName) {
        var bild = new LieferantBild();
        bild.setLieferant(lieferant(lieferantId));
        bild.setGespeicherterDateiname(gespeicherterName);
        return bild;
    }

    @Nested
    @DisplayName("Dokumente")
    class Dokumente {

        @Test
        @DisplayName("landen unter <upload.path>/lieferanten/<id>/ mit UUID vor dem Namen")
        void speichertImLieferantenOrdner() throws Exception {
            var ergebnis = ablage.speichereDokument(LIEFERANT_ID, datei("Rechnung_Muster.pdf"));

            assertThat(ergebnis.originalDateiname()).isEqualTo("Rechnung_Muster.pdf");
            assertThat(ergebnis.gespeicherterDateiname()).matches(UUID_PRAEFIX + "Rechnung_Muster\\.pdf");
            assertThat(lieferantOrdner().resolve(ergebnis.gespeicherterDateiname())).hasBinaryContent(INHALT);
        }

        @Test
        @DisplayName("Zwei Uploads mit gleichem Namen überschreiben sich nicht")
        void gleicherNameZweimal() throws Exception {
            var erste = ablage.speichereDokument(LIEFERANT_ID, datei("Lieferschein.pdf"));
            var zweite = ablage.speichereDokument(LIEFERANT_ID, datei("Lieferschein.pdf"));

            assertThat(erste.gespeicherterDateiname()).isNotEqualTo(zweite.gespeicherterDateiname());
            assertThat(lieferantOrdner().resolve(erste.gespeicherterDateiname())).exists();
            assertThat(lieferantOrdner().resolve(zweite.gespeicherterDateiname())).exists();
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(delimiterString = "=>", value = {
                "../../etc/passwd => passwd",
                "..\\..\\boese.pdf => boese.pdf",
                "C:\\Users\\Max Mustermann\\scan.pdf => scan.pdf",
                "a*b?c\"d<e>f|g.pdf => a_b_c_d_e_f_g.pdf",
                "/ => dokument",
                ".. => dokument",
                "'   ' => dokument",
        })
        @DisplayName("Path-Traversal und verbotene Zeichen im Namen bleiben im Lieferanten-Ordner")
        void bereinigtDenNamen(String rohName, String erwartet) throws Exception {
            var ergebnis = ablage.speichereDokument(LIEFERANT_ID, datei(rohName));

            assertThat(ergebnis.originalDateiname()).isEqualTo(erwartet);
            Path gespeichert = lieferantOrdner().resolve(ergebnis.gespeicherterDateiname());
            assertThat(gespeichert).exists();
            assertThat(gespeichert.getParent()).isEqualTo(lieferantOrdner());
            assertThat(tmp.resolve("etc").resolve("passwd")).doesNotExist();
        }

        @Test
        @DisplayName("Lieferant fehlt: kein Speichern")
        void ohneLieferantId() {
            assertThatThrownBy(() -> ablage.speichereDokument(null, datei("Rechnung.pdf")))
                    .isInstanceOf(NullPointerException.class);
        }

        /**
         * Der eigentliche Fehler: Mit einem abweichend gesetzten {@code upload.path} lag die Datei
         * im Arbeitsverzeichnis, die Dateisuche schaute aber im konfigurierten Ordner.
         */
        @Test
        @DisplayName("Die Dateisuche findet das Dokument auch bei abweichendem upload.path")
        void dateisucheFindetDasDokument() throws Exception {
            var ergebnis = ablage.speichereDokument(LIEFERANT_ID, datei("Rechnung_Muster.pdf"));
            var dateisuche = new LieferantDokumentDateiService(uploadPath.toString(),
                    tmp.resolve("anderer-upload-dir").toString(), tmp.resolve("mail").toString(),
                    tmp.resolve("lieferanten-mail").toString());
            var dokument = new LieferantDokument();
            dokument.setLieferant(lieferant(LIEFERANT_ID));
            dokument.setGespeicherterDateiname(ergebnis.gespeicherterDateiname());

            assertThat(dateisuche.findeDatei(dokument))
                    .contains(lieferantOrdner().resolve(ergebnis.gespeicherterDateiname()).toAbsolutePath().normalize());
        }
    }

    @Nested
    @DisplayName("Dateinamen bereinigen")
    class DateinamenBereinigen {

        @Test
        void ohneNamenGiltDerErsatz() {
            assertThat(LieferantDateiablage.bereinigterDateiname(null, "foto")).isEqualTo("foto");
        }

        @Test
        @DisplayName("Steuer- und Formatzeichen (z. B. Schreibrichtungs-Umkehr) werden ersetzt")
        void steuerzeichen() {
            assertThat(LieferantDateiablage.bereinigterDateiname("Rechnung\u202Efdp.exe", "x"))
                    .isEqualTo("Rechnung_fdp.exe");
            assertThat(LieferantDateiablage.bereinigterDateiname("a\u0000b.pdf", "x")).isEqualTo("a_b.pdf");
        }
    }

    @Nested
    @DisplayName("Bilder")
    class Bilder {

        @Test
        @DisplayName("landen unter <upload.path>/lieferanten/<id>/bilder/ und werden wiedergefunden")
        void speichernUndFinden() throws Exception {
            var ergebnis = ablage.speichereBild(LIEFERANT_ID, datei("Kratzer.jpg"));

            assertThat(ergebnis.originalDateiname()).isEqualTo("Kratzer.jpg");
            assertThat(ergebnis.gespeicherterDateiname()).matches(UUID_PRAEFIX + "Kratzer\\.jpg");
            Path erwartet = bilderOrdner().resolve(ergebnis.gespeicherterDateiname());
            assertThat(erwartet).hasBinaryContent(INHALT);
            assertThat(ablage.findeBild(bild(LIEFERANT_ID, ergebnis.gespeicherterDateiname())))
                    .contains(erwartet.toAbsolutePath().normalize());
        }

        @ParameterizedTest(name = "{0} + {1} → {2}")
        @CsvSource({
                "Kratzer, .jpg, Kratzer.jpg",
                "Kratzer.JPG, .jpg, Kratzer.JPG",
                "angriff.svg, .png, angriff.svg.png",
                "'', .jpg, foto.jpg",
        })
        @DisplayName("Die Endung aus dem geprüften Bildtyp wird bei Bedarf angehängt")
        void endungAusDemBildtyp(String name, String endung, String erwartet) throws Exception {
            var ergebnis = ablage.speichereBild(LIEFERANT_ID, datei(name), endung);

            assertThat(ergebnis.gespeicherterDateiname()).matches(UUID_PRAEFIX + java.util.regex.Pattern.quote(erwartet));
            assertThat(bilderOrdner().resolve(ergebnis.gespeicherterDateiname())).exists();
        }

        @Test
        @DisplayName("Fehlende Datei: nicht gefunden")
        void fehlendeDatei() {
            assertThat(ablage.findeBild(bild(LIEFERANT_ID, "gibt-es-nicht.jpg"))).isEmpty();
        }

        @Test
        @DisplayName("Ein Ordner mit dem Namen des Bildes zählt nicht als Bild")
        void ordnerStattDatei() throws Exception {
            Files.createDirectories(bilderOrdner().resolve("ordner.jpg"));

            assertThat(ablage.findeBild(bild(LIEFERANT_ID, "ordner.jpg"))).isEmpty();
        }

        @Test
        @DisplayName("Das Bild eines anderen Lieferanten liegt nicht in diesem Ordner")
        void andererLieferant() throws Exception {
            var ergebnis = ablage.speichereBild(LIEFERANT_ID, datei("Kratzer.jpg"));

            assertThat(ablage.findeBild(bild(8L, ergebnis.gespeicherterDateiname()))).isEmpty();
        }

        @Test
        @DisplayName("Path-Traversal im gespeicherten Namen liefert keine Datei außerhalb des Bilder-Ordners")
        void traversalImGespeichertenNamen() throws Exception {
            Path geheim = uploadPath.resolve("lieferanten").resolve("geheim.txt");
            Files.createDirectories(geheim.getParent());
            Files.writeString(geheim, "streng vertraulich");

            assertThat(ablage.findeBild(bild(LIEFERANT_ID, "../../geheim.txt"))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = { "", "  ", "/", "..", "a\u0000b.jpg" })
        @DisplayName("Leere, ungültige und Ordner-Namen: nicht gefunden")
        void ungueltigeNamen(String name) {
            assertThat(ablage.findeBild(bild(LIEFERANT_ID, name))).isEmpty();
        }

        @Test
        @DisplayName("Ohne Bild, Lieferant, Lieferanten-ID oder Namen: nicht gefunden")
        void ohneAngaben() {
            var ohneLieferant = new LieferantBild();
            ohneLieferant.setGespeicherterDateiname("Kratzer.jpg");

            assertThat(ablage.findeBild(null)).isEmpty();
            assertThat(ablage.findeBild(ohneLieferant)).isEmpty();
            assertThat(ablage.findeBild(bild(null, "Kratzer.jpg"))).isEmpty();
            assertThat(ablage.findeBild(bild(LIEFERANT_ID, null))).isEmpty();
        }

        @Test
        @DisplayName("Löschen entfernt die Datei")
        void loeschen() throws Exception {
            var ergebnis = ablage.speichereBild(LIEFERANT_ID, datei("Kratzer.jpg"));
            Path pfad = bilderOrdner().resolve(ergebnis.gespeicherterDateiname());

            ablage.loescheBild(bild(LIEFERANT_ID, ergebnis.gespeicherterDateiname()));

            assertThat(pfad).doesNotExist();
        }

        @Test
        @DisplayName("Löschen mit Path-Traversal im Namen lässt Dateien außerhalb des Bilder-Ordners stehen")
        void loeschenMitTraversal() throws Exception {
            Path daneben = lieferantOrdner().resolve("Rechnung_Muster.pdf");
            Files.createDirectories(daneben.getParent());
            Files.write(daneben, INHALT);

            ablage.loescheBild(bild(LIEFERANT_ID, "../Rechnung_Muster.pdf"));

            assertThat(daneben).exists();
        }

        @Test
        @DisplayName("Löschen einer fehlenden Datei ist kein Fehler")
        void loeschenOhneDatei() {
            ablage.loescheBild(bild(LIEFERANT_ID, "gibt-es-nicht.jpg"));

            assertThat(bilderOrdner()).doesNotExist();
        }
    }
}
