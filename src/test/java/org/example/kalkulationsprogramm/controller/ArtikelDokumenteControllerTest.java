package org.example.kalkulationsprogramm.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.ArtikelDokumentTyp;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumentDto;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelDokumentService;
import org.example.kalkulationsprogramm.service.ArtikelImportService;
import org.example.kalkulationsprogramm.service.ArtikelMatchingService;
import org.example.kalkulationsprogramm.service.ArtikelPositionsPreisService;
import org.example.kalkulationsprogramm.service.ArtikelServiceContract;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-Verdrahtung der vier Artikel-Dokumente-Endpoints: Statuscodes,
 * Multipart-Binding, Header. Die eigentliche Datei-, Pruef- und Loeschlogik
 * ist bereits in {@link org.example.kalkulationsprogramm.service.ArtikelDokumentServiceTest}
 * abgedeckt - hier wird {@link ArtikelDokumentService} gemockt und nur die
 * Uebersetzung HTTP <-> Service geprueft. Testdaten sind Dummy-Daten (DSGVO).
 */
@WebMvcTest(ArtikelController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtikelDokumenteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtikelServiceContract artikelService;
    @MockBean
    private ArtikelImportService artikelImportService;
    @MockBean
    private LieferantenRepository lieferantenRepository;
    @MockBean
    private WerkstoffRepository werkstoffRepository;
    @MockBean
    private ArtikelMatchingService artikelMatchingService;
    @MockBean
    private KategorieService kategorieService;
    @MockBean
    private ArtikelPositionsPreisService artikelPositionsPreisService;
    @MockBean
    private ArtikelDokumentService artikelDokumentService;

    /** Ein einfaches Multipart-Textfeld (kein Datei-Anhang) fuer @RequestPart-String-Bindings. */
    private static MockMultipartFile teilfeld(String name, String wert) {
        return new MockMultipartFile(name, "", "text/plain", wert.getBytes());
    }

    @Nested
    @DisplayName("POST /api/artikel/{id}/dokumente")
    class Upload {

        @Test
        @DisplayName("Erfolg: 201 mit dem gespeicherten Dokument im Antwortkoerper")
        void erfolg_antwortetMit201UndDto() throws Exception {
            ArtikelDokumentDto dto = new ArtikelDokumentDto();
            dto.setId(42L);
            dto.setOriginalDateiname("zulassung.pdf");
            dto.setTyp(ArtikelDokumentTyp.ZULASSUNG);
            dto.setBeschreibung("Pruefzeugnis 2026");
            dto.setErstelltAm(LocalDateTime.of(2026, 8, 15, 10, 0));
            dto.setUrl("/api/artikel/dokumente/42/datei");

            given(artikelDokumentService.ladeHoch(
                    eq(7L), any(), eq(ArtikelDokumentTyp.ZULASSUNG), eq("Pruefzeugnis 2026")))
                    .willReturn(dto);

            MockMultipartFile datei = new MockMultipartFile(
                    "datei", "zulassung.pdf", "application/pdf", "Inhalt".getBytes());

            mockMvc.perform(multipart("/api/artikel/7/dokumente")
                            .file(datei)
                            .file(teilfeld("typ", "ZULASSUNG"))
                            .file(teilfeld("beschreibung", "Pruefzeugnis 2026")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.originalDateiname").value("zulassung.pdf"))
                    .andExpect(jsonPath("$.typ").value("ZULASSUNG"))
                    .andExpect(jsonPath("$.url").value("/api/artikel/dokumente/42/datei"));
        }

        @Test
        @DisplayName("Verbotene Endung: 400 mit der Handwerker-deutschen Meldung des Service im Antwortkoerper")
        void verboteneEndung_antwortetMit400UndMeldungImBody() throws Exception {
            given(artikelDokumentService.ladeHoch(eq(7L), any(), eq(ArtikelDokumentTyp.SONSTIGES), any()))
                    .willThrow(new IllegalArgumentException(
                            "Dieser Dateityp wird nicht unterstützt. Erlaubt sind PDF, PNG, JPG, JPEG, WEBP und GIF."));

            MockMultipartFile datei = new MockMultipartFile(
                    "datei", "schadcode.exe", "application/octet-stream", "x".getBytes());

            mockMvc.perform(multipart("/api/artikel/7/dokumente")
                            .file(datei)
                            .file(teilfeld("typ", "SONSTIGES")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Dieser Dateityp wird nicht unterstützt. Erlaubt sind PDF, PNG, JPG, JPEG, WEBP und GIF."));
        }

        @Test
        @DisplayName("Zu grosse Datei: 400 mit der Handwerker-deutschen Meldung des Service im Antwortkoerper")
        void zuGrosseDatei_antwortetMit400UndMeldungImBody() throws Exception {
            given(artikelDokumentService.ladeHoch(eq(7L), any(), eq(ArtikelDokumentTyp.DATENBLATT), any()))
                    .willThrow(new IllegalArgumentException("Die Datei ist zu groß. Erlaubt sind höchstens 10 MB."));

            MockMultipartFile datei = new MockMultipartFile(
                    "datei", "riesig.pdf", "application/pdf", "x".getBytes());

            mockMvc.perform(multipart("/api/artikel/7/dokumente")
                            .file(datei)
                            .file(teilfeld("typ", "DATENBLATT")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Die Datei ist zu groß. Erlaubt sind höchstens 10 MB."));
        }

        @Test
        @DisplayName("Unbekannte Artikel-ID: 404")
        void unbekannteArtikelId_antwortetMit404() throws Exception {
            given(artikelDokumentService.ladeHoch(eq(999L), any(), eq(ArtikelDokumentTyp.DATENBLATT), any()))
                    .willThrow(new NotFoundException("Diesen Artikel gibt es nicht."));

            MockMultipartFile datei = new MockMultipartFile(
                    "datei", "plan.pdf", "application/pdf", "x".getBytes());

            mockMvc.perform(multipart("/api/artikel/999/dokumente")
                            .file(datei)
                            .file(teilfeld("typ", "DATENBLATT")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Unbekannter Dokumenttyp-String: 400 mit Meldung, Service wird gar nicht erst aufgerufen")
        void unbekannterTyp_antwortetMit400UndMeldungImBody() throws Exception {
            MockMultipartFile datei = new MockMultipartFile(
                    "datei", "plan.pdf", "application/pdf", "x".getBytes());

            mockMvc.perform(multipart("/api/artikel/7/dokumente")
                            .file(datei)
                            .file(teilfeld("typ", "GIBT_ES_NICHT")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/artikel/{id}/dokumente")
    class Liste {

        @Test
        @DisplayName("Antwortet mit der Dokumentliste des Artikels")
        void antwortetMitListe() throws Exception {
            ArtikelDokumentDto dto = new ArtikelDokumentDto();
            dto.setId(1L);
            dto.setOriginalDateiname("datenblatt.pdf");
            dto.setTyp(ArtikelDokumentTyp.DATENBLATT);
            dto.setUrl("/api/artikel/dokumente/1/datei");

            given(artikelDokumentService.listeDokumente(7L)).willReturn(List.of(dto));

            mockMvc.perform(get("/api/artikel/7/dokumente"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].originalDateiname").value("datenblatt.pdf"));
        }

        @Test
        @DisplayName("Unbekannte Artikel-ID: 404")
        void unbekannteId_antwortetMit404() throws Exception {
            given(artikelDokumentService.listeDokumente(999L))
                    .willThrow(new NotFoundException("Diesen Artikel gibt es nicht."));

            mockMvc.perform(get("/api/artikel/999/dokumente"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/artikel/dokumente/{dokumentId}/datei")
    class DateiAuslieferung {

        @Test
        @DisplayName("Liefert Bild/PDF mit korrektem Content-Type inline aus, inkl. nosniff-Header")
        void liefertDateiMitKorrektemContentType() throws Exception {
            var datei = new ArtikelDokumentService.ArtikelDokumentDatei(
                    new ByteArrayResource("PDF-Inhalt".getBytes()), "plan.pdf", "application/pdf", true);
            given(artikelDokumentService.ladeDatei(10L)).willReturn(datei);

            // Spring's ContentDisposition.builder() liefert bei einem reinen ASCII-Namen sowohl
            // das klassische filename= (RFC-2047-kodiert, fuer aeltere Clients) als auch das
            // RFC-5987-konforme filename*=UTF-8''... - lokal verifiziert, nicht geraten.
            mockMvc.perform(get("/api/artikel/dokumente/10/datei"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Disposition",
                            "inline; filename=\"=?UTF-8?Q?plan.pdf?=\"; filename*=UTF-8''plan.pdf"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(content().bytes("PDF-Inhalt".getBytes()));
        }

        @Test
        @DisplayName("Alles ausser Bild/PDF liefert der Service als attachment - nicht inline mit "
                + "eigenem Content-Type. Deckt den Direktweg ab (siehe SCRAPING_ARTIKEL_CSV.md), bei dem "
                + "eine .html oder .svg an der Upload-Whitelist vorbei ins Verzeichnis gelangen koennte")
        void nichtBildOderPdf_wirdAlsAttachmentAusgeliefert() throws Exception {
            var datei = new ArtikelDokumentService.ArtikelDokumentDatei(
                    new ByteArrayResource("<script>alert(1)</script>".getBytes()),
                    "geschmuggelt.html", "application/octet-stream", false);
            given(artikelDokumentService.ladeDatei(11L)).willReturn(datei);

            mockMvc.perform(get("/api/artikel/dokumente/11/datei"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"=?UTF-8?Q?geschmuggelt.html?=\"; filename*=UTF-8''geschmuggelt.html"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }

        @Test
        @DisplayName("Dateiname mit eingebettetem Anfuehrungszeichen: RFC-5987-Name (filename*=) ist "
                + "korrekt prozent-kodiert und von modernen Clients bevorzugt - keine neue Kopfzeile")
        void dateinameMitAnfuehrungszeichen_rfc5987NameKorrektKodiert() throws Exception {
            var datei = new ArtikelDokumentService.ArtikelDokumentDatei(
                    new ByteArrayResource("Inhalt".getBytes()), "a\";x=\"b.pdf", "application/pdf", true);
            given(artikelDokumentService.ladeDatei(12L)).willReturn(datei);

            // Tatsaechliches Verhalten von ContentDisposition.builder() lokal verifiziert (nicht
            // geraten): Das legacy filename= (RFC-2047-Q-Encoding, fuer sehr alte Clients) laesst
            // das eingebettete Anfuehrungszeichen unveraendert stehen - das ist Spring-eigenes
            // Verhalten, identisch zu BelegController#downloadDatei. Sicherheitsrelevant ist das
            // nicht: es entsteht keine zusaetzliche Kopfzeile (kein CR/LF, siehe naechster Test),
            // und alle RFC-6266-konformen Clients nutzen bevorzugt filename*=, das hier korrekt
            // RFC-5987-prozent-kodiert ist.
            mockMvc.perform(get("/api/artikel/dokumente/12/datei"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            "inline; filename=\"=?UTF-8?Q?a\";x=3D\"b.pdf?=\"; filename*=UTF-8''a%22%3Bx%3D%22b.pdf"));
        }

        @Test
        @DisplayName("Dateiname mit CR/LF kann keinen zusaetzlichen Response-Header mehr einschleusen")
        void dateinameMitCrLf_schleustKeinenHeaderEin() throws Exception {
            var datei = new ArtikelDokumentService.ArtikelDokumentDatei(
                    new ByteArrayResource("Inhalt".getBytes()), "a\r\nX-Evil: 1.pdf", "application/pdf", true);
            given(artikelDokumentService.ladeDatei(13L)).willReturn(datei);

            mockMvc.perform(get("/api/artikel/dokumente/13/datei"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("X-Evil"));
        }

        @Test
        @DisplayName("Unbekannte Dokument-ID: 404")
        void unbekannteId_antwortetMit404() throws Exception {
            given(artikelDokumentService.ladeDatei(999L))
                    .willThrow(new NotFoundException("Dieses Dokument gibt es nicht."));

            mockMvc.perform(get("/api/artikel/dokumente/999/datei"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/artikel/dokumente/{dokumentId}")
    class Loeschen {

        @Test
        @DisplayName("Loescht das Dokument und antwortet mit 204")
        void antwortetMit204UndRuftServiceAuf() throws Exception {
            mockMvc.perform(delete("/api/artikel/dokumente/20"))
                    .andExpect(status().isNoContent());

            verify(artikelDokumentService).loescheDokument(20L);
        }

        @Test
        @DisplayName("Unbekannte Dokument-ID: 404")
        void unbekannteId_antwortetMit404() throws Exception {
            doThrow(new NotFoundException("Dieses Dokument gibt es nicht."))
                    .when(artikelDokumentService).loescheDokument(999L);

            mockMvc.perform(delete("/api/artikel/dokumente/999"))
                    .andExpect(status().isNotFound());
        }
    }
}
