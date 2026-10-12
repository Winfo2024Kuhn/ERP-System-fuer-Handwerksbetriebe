package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die vier konfigurierten Ordner liegen hier bewusst getrennt nebeneinander statt ineinander
 * (wie im Standard-Setup). So zeigt jeder Test, in welchem Basis-Ordner gesucht wird.
 */
class LieferantDokumentDateiServiceTest {

    private static final long LIEFERANT_ID = 7L;
    private static final long EMAIL_ID = 55L;
    private static final String NAME = "0b6f-uuid_Rechnung_Muster.pdf";

    @TempDir
    Path tmp;

    private Path uploadPath;
    private Path uploadDir;
    private Path mailAnhangDir;
    private Path lieferantenMailAnhangDir;
    private LieferantDokumentDateiService service;

    @BeforeEach
    void setUp() {
        uploadPath = tmp.resolve("upload-path");
        uploadDir = tmp.resolve("upload-dir");
        mailAnhangDir = tmp.resolve("mail");
        lieferantenMailAnhangDir = tmp.resolve("lieferanten-mail");
        service = new LieferantDokumentDateiService(uploadPath.toString(), uploadDir.toString(),
                mailAnhangDir.toString(), lieferantenMailAnhangDir.toString());
    }

    private static LieferantDokument dokument(String gespeicherterName) {
        var lieferant = new Lieferanten();
        lieferant.setId(LIEFERANT_ID);
        lieferant.setLieferantenname("Musterlieferant GmbH");
        var dokument = new LieferantDokument();
        dokument.setId(1L);
        dokument.setLieferant(lieferant);
        dokument.setGespeicherterDateiname(gespeicherterName);
        return dokument;
    }

    private static EmailAttachment anhang(String gespeicherterName) {
        var email = new Email();
        email.setId(EMAIL_ID);
        var anhang = new EmailAttachment();
        anhang.setEmail(email);
        anhang.setStoredFilename(gespeicherterName);
        anhang.setOriginalFilename("Rechnung.pdf");
        return anhang;
    }

    private static Path datei(Path pfad, String inhalt) throws Exception {
        Files.createDirectories(pfad.getParent());
        Files.writeString(pfad, inhalt, StandardCharsets.ISO_8859_1);
        return pfad;
    }

    private static Path pdf(Path pfad) throws Exception {
        return datei(pfad, "%PDF-1.4 Dummy-Rechnung");
    }

    // ------------------------------------------------------------------ Suchorte

    /** Ein Ordner relativ zu einem der vier Basis-Ordner des Tests. */
    private record Ort(String beschreibung, Function<LieferantDokumentDateiServiceTest, Path> ordner) {
        @Override
        public String toString() {
            return beschreibung;
        }
    }

    static Stream<Arguments> suchorte() {
        return Stream.of(
                new Ort("file.upload-dir direkt", t -> t.uploadDir),
                new Ort("upload.path direkt", t -> t.uploadPath),
                new Ort("Mail-Ordner email/<lieferantId>", t -> t.mailAnhangDir.resolve("email").resolve("7")),
                new Ort("upload.path attachments/email/<lieferantId>",
                        t -> t.uploadPath.resolve("attachments").resolve("email").resolve("7")),
                new Ort("file.upload-dir lieferanten/<lieferantId>",
                        t -> t.uploadDir.resolve("lieferanten").resolve("7")),
                new Ort("upload.path lieferanten/<lieferantId>",
                        t -> t.uploadPath.resolve("lieferanten").resolve("7")),
                new Ort("Lieferanten-Mail-Ordner <lieferantId>", t -> t.lieferantenMailAnhangDir.resolve("7")),
                new Ort("file.upload-dir attachments/lieferanten/<lieferantId>",
                        t -> t.uploadDir.resolve("attachments").resolve("lieferanten").resolve("7")),
                new Ort("upload.path attachments/lieferanten/<lieferantId>",
                        t -> t.uploadPath.resolve("attachments").resolve("lieferanten").resolve("7")),
                new Ort("Mail-Ordner vendor-invoices", t -> t.mailAnhangDir.resolve("vendor-invoices")),
                new Ort("file.upload-dir attachments/vendor-invoices",
                        t -> t.uploadDir.resolve("attachments").resolve("vendor-invoices")),
                new Ort("upload.path attachments/vendor-invoices",
                        t -> t.uploadPath.resolve("attachments").resolve("vendor-invoices")),
                new Ort("Mail-Ordner direkt", t -> t.mailAnhangDir),
                new Ort("upload.path attachments", t -> t.uploadPath.resolve("attachments")),
                new Ort("Mail-Ordner <emailId>", t -> t.mailAnhangDir.resolve("55")),
                new Ort("upload.path attachments/<emailId>",
                        t -> t.uploadPath.resolve("attachments").resolve("55")),
                new Ort("Mail-Ordner <lieferantId>", t -> t.mailAnhangDir.resolve("7")),
                new Ort("upload.path lieferant-emails", t -> t.uploadPath.resolve("lieferant-emails")),
                new Ort("upload.path email", t -> t.uploadPath.resolve("email")))
                .map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("suchorte")
    @DisplayName("Findet die Datei an jedem Ablageort")
    void findetDateiAnJedemOrt(Ort ort) throws Exception {
        Path erwartet = pdf(ort.ordner().apply(this).resolve(NAME));
        var dokument = dokument(NAME);
        // Der Ordner je E-Mail braucht den Anhang, um die E-Mail-ID zu kennen.
        dokument.setAttachment(anhang(null));

        assertThat(service.findeDatei(dokument)).contains(erwartet.toAbsolutePath().normalize());
        assertThat(service.findePdf(dokument)).contains(erwartet.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("Beleg aus „Belege & Kasse“ (Name belege/<datei>) wird im Upload-Ordner gefunden")
    void findetBelegMitUnterordnerImNamen() throws Exception {
        Path beleg = pdf(uploadDir.resolve("belege").resolve("scan.pdf"));

        assertThat(service.findeDatei(dokument("belege/scan.pdf"))).contains(beleg.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("Liegt dieselbe Datei an zwei Orten, gewinnt der frühere Ort")
    void frueherOrtGewinnt() throws Exception {
        pdf(mailAnhangDir.resolve(NAME));
        Path manuell = pdf(uploadDir.resolve("lieferanten").resolve("7").resolve(NAME));

        assertThat(service.findeDatei(dokument(NAME))).contains(manuell.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("Ohne Lieferant wird in den gemeinsamen Ordnern weitergesucht")
    void ohneLieferantSuchtInGemeinsamenOrdnern() throws Exception {
        Path flach = pdf(mailAnhangDir.resolve(NAME));
        var dokument = dokument(NAME);
        dokument.setLieferant(null);

        assertThat(service.findeDatei(dokument)).contains(flach.toAbsolutePath().normalize());
    }

    // ------------------------------------------------------------------ Dateinamen

    @Nested
    @DisplayName("Dateiname aus Dokument und E-Mail-Anhang")
    class Dateinamen {

        @Test
        @DisplayName("Anhang ohne eigenen Dateinamen: gesucht wird mit dem Namen des Anhangs")
        void anhangOhneEigenenDateinamen() throws Exception {
            Path datei = pdf(mailAnhangDir.resolve("anhang.pdf"));
            var dokument = dokument(null);
            dokument.setAttachment(anhang("anhang.pdf"));

            assertThat(service.findeDatei(dokument)).contains(datei.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("Anhang ohne gespeicherten Namen: der eigene Name des Dokuments zählt")
        void anhangOhneGespeichertenNamen() throws Exception {
            Path datei = pdf(uploadDir.resolve("lieferanten").resolve("7").resolve(NAME));
            var dokument = dokument(NAME);
            dokument.setAttachment(anhang("  "));

            assertThat(service.findeDatei(dokument)).contains(datei.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("Der Name des Anhangs geht vor – auch wenn der eigene Name an einem früheren Ort liegt")
        void anhangsnameGehtVor() throws Exception {
            pdf(uploadDir.resolve("eigener-name.pdf"));
            Path anhangsDatei = pdf(mailAnhangDir.resolve("anhang.pdf"));
            var dokument = dokument("eigener-name.pdf");
            dokument.setAttachment(anhang("anhang.pdf"));

            assertThat(service.findeDatei(dokument)).contains(anhangsDatei.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("Fehlt die Datei des Anhangs, wird der eigene Name genommen")
        void eigenerNameAlsErsatz() throws Exception {
            Path eigene = pdf(uploadDir.resolve("lieferanten").resolve("7").resolve("eigener-name.pdf"));
            var dokument = dokument("eigener-name.pdf");
            dokument.setAttachment(anhang("gibt-es-nicht.pdf"));

            assertThat(service.findeDatei(dokument)).contains(eigene.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("Dokument ohne jeden Dateinamen: nichts gefunden")
        void ohneDateinamen() {
            assertThat(service.findeDatei(dokument(null))).isEmpty();
            assertThat(service.findeDatei(dokument(" "))).isEmpty();
            assertThat(service.findeDatei(null)).isEmpty();
        }
    }

    // ------------------------------------------------------------------ fehlende Datei

    @Test
    @DisplayName("Fehlende Datei: leeres Ergebnis statt Fehler")
    void fehlendeDatei() {
        var dokument = dokument("gibt-es-nicht.pdf");
        dokument.setAttachment(anhang("auch-nicht.pdf"));

        assertThat(service.findeDatei(dokument)).isEmpty();
        assertThat(service.findePdf(dokument)).isEmpty();
    }

    @Test
    @DisplayName("Ein Ordner mit dem gesuchten Namen ist keine Datei")
    void ordnerStattDatei() throws Exception {
        Files.createDirectories(mailAnhangDir.resolve(NAME));

        assertThat(service.findeDatei(dokument(NAME))).isEmpty();
    }

    // ------------------------------------------------------------------ Path Traversal

    @Nested
    @DisplayName("Path Traversal")
    class PathTraversal {

        @ParameterizedTest
        @ValueSource(strings = { "../draussen.pdf", "../../draussen.pdf", "../../../draussen.pdf",
                "lieferanten/../../draussen.pdf" })
        @DisplayName("../ im gespeicherten Namen führt weder aus der Ablage noch aus dem Suchordner heraus")
        void punktPunktFuehrtNichtHeraus(String name) throws Exception {
            // Neben den Ablage-Ordnern: z. B. <mail>/email/7/../../../draussen.pdf
            pdf(tmp.resolve("draussen.pdf"));
            // In der Ablage, aber außerhalb des Suchordners: <upload-dir>/lieferanten/7/../../draussen.pdf
            pdf(uploadDir.resolve("draussen.pdf"));

            assertThat(service.findeDatei(dokument(name))).isEmpty();
        }

        @Test
        @DisplayName("../ im Namen des Anhangs führt nicht aus der Ablage heraus")
        void punktPunktImAnhang() throws Exception {
            pdf(tmp.resolve("draussen.pdf"));
            var dokument = dokument(null);
            dokument.setAttachment(anhang("../draussen.pdf"));

            assertThat(service.findeDatei(dokument)).isEmpty();
        }

        @Test
        @DisplayName("../ führt auch nicht in den Ordner eines anderen Lieferanten")
        void keinSprungZuAnderemLieferanten() throws Exception {
            pdf(uploadDir.resolve("lieferanten").resolve("8").resolve("fremd.pdf"));

            assertThat(service.findeDatei(dokument("../8/fremd.pdf"))).isEmpty();
        }

        @Test
        @DisplayName("Ein absoluter Pfad im gespeicherten Namen wird nicht gelesen")
        void absoluterPfad() throws Exception {
            Path draussen = pdf(tmp.resolve("draussen.pdf"));

            assertThat(service.findeDatei(dokument(draussen.toAbsolutePath().toString()))).isEmpty();
        }

        @Test
        @DisplayName("Ein ungültiger Name (Null-Byte) gilt als nicht vorhanden")
        void ungueltigerName() {
            assertThat(service.findeDatei(dokument("rechnung\0.pdf"))).isEmpty();
        }
    }

    // ------------------------------------------------------------------ nur PDFs

    @Nested
    @DisplayName("findePdf")
    class NurPdf {

        @Test
        @DisplayName("Liegt unter dem einen Namen eine XML und unter dem anderen die PDF, wird die PDF gefunden")
        void pdfStattXml() throws Exception {
            datei(mailAnhangDir.resolve("e-rechnung.xml"), "<?xml version=\"1.0\"?><Invoice/>");
            Path pdf = pdf(uploadDir.resolve("lieferanten").resolve("7").resolve("e-rechnung.pdf"));
            var dokument = dokument("e-rechnung.pdf");
            dokument.setAttachment(anhang("e-rechnung.xml"));

            assertThat(service.findeDatei(dokument)).contains(mailAnhangDir.resolve("e-rechnung.xml")
                    .toAbsolutePath().normalize());
            assertThat(service.findePdf(dokument)).contains(pdf.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("Liegt unter beiden Namen eine PDF, gewinnt die des Anhangs")
        void zweiPdfsAnhangGewinnt() throws Exception {
            pdf(uploadDir.resolve("lieferanten").resolve("7").resolve("eigene.pdf"));
            Path anhangsPdf = pdf(mailAnhangDir.resolve("anhang.pdf"));
            var dokument = dokument("eigene.pdf");
            dokument.setAttachment(anhang("anhang.pdf"));

            assertThat(service.findePdf(dokument)).contains(anhangsPdf.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("Ein Foto mit .pdf-Endung ist keine PDF – entscheidend ist der Inhalt")
        void fotoMitPdfEndung() throws Exception {
            Path foto = uploadDir.resolve(NAME);
            Files.createDirectories(foto.getParent());
            Files.write(foto, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 });

            assertThat(service.findeDatei(dokument(NAME))).isPresent();
            assertThat(service.findePdf(dokument(NAME))).isEmpty();
        }

        @Test
        @DisplayName("Die PDF-Kennung darf nach ein paar Bytes Vorspann stehen")
        void pdfKennungNachVorspann() throws Exception {
            Path pdf = datei(uploadDir.resolve(NAME), "\r\n\r\n%PDF-1.7 Dummy");

            assertThat(service.findePdf(dokument(NAME))).contains(pdf.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("Steht die PDF-Kennung erst nach 1024 Bytes, ist es keine PDF")
        void pdfKennungZuSpaet() throws Exception {
            datei(uploadDir.resolve(NAME), " ".repeat(1024) + "%PDF-1.7");

            assertThat(service.findePdf(dokument(NAME))).isEmpty();
        }
    }

    // ------------------------------------------------------------------ liegtInAblage

    @Nested
    @DisplayName("liegtInAblage")
    class LiegtInAblage {

        @Test
        @DisplayName("Pfade in jedem der vier Ordner liegen in der Ablage")
        void jederOrdnerGehoertDazu() {
            assertThat(service.liegtInAblage(uploadPath.resolve("a.pdf"))).isTrue();
            assertThat(service.liegtInAblage(uploadDir.resolve("lieferanten").resolve("7").resolve("a.pdf")))
                    .isTrue();
            assertThat(service.liegtInAblage(mailAnhangDir.resolve("a.pdf"))).isTrue();
            assertThat(service.liegtInAblage(lieferantenMailAnhangDir.resolve("7").resolve("a.pdf"))).isTrue();
        }

        @Test
        @DisplayName("Pfade daneben – auch über ../ – liegen nicht in der Ablage")
        void danebenGehoertNichtDazu() {
            assertThat(service.liegtInAblage(tmp.resolve("draussen.pdf"))).isFalse();
            assertThat(service.liegtInAblage(uploadDir.resolve("..").resolve("draussen.pdf"))).isFalse();
            assertThat(service.liegtInAblage(tmp.resolve("upload-dir-nachbar").resolve("a.pdf"))).isFalse();
        }

        @Test
        @DisplayName("Leer konfigurierte Ordner fallen auf uploads/ im Arbeitsverzeichnis zurück")
        void leereKonfigurationNimmtStandard() {
            var standard = new LieferantDokumentDateiService("", " ", null, "");

            assertThat(standard.liegtInAblage(Path.of("uploads", "a.pdf"))).isTrue();
            assertThat(standard.liegtInAblage(tmp.resolve("a.pdf"))).isFalse();
        }
    }
}
