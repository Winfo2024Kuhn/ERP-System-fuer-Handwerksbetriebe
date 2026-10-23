package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.Email.EmailSignaturDto;
import org.example.kalkulationsprogramm.dto.Email.EmailSignaturSpeichernRequest;
import org.example.kalkulationsprogramm.service.EmailSignatureService;
import org.example.kalkulationsprogramm.service.EmailSignaturVerwaltungService;
import org.example.kalkulationsprogramm.service.EmailSignaturVerwaltungService.Benutzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signatur-Endpoints mit echter Security-Filterkette: Anmeldung, CSRF, Admin-Recht für die
 * System-Signatur und 404 für Signaturen, die der Benutzer nicht sehen darf.
 */
@WebMvcTest(controllers = EmailSignatureController.class)
@Import({ SecurityConfig.class, EmailSignatureControllerTest.EchteFilterBeans.class })
class EmailSignatureControllerTest {

    /** Echter Cloudflare-Filter: MockMvc initialisiert jeden Filter der Kette. */
    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    private static final Benutzer MAX = new Benutzer(5L, false);
    private static final Benutzer CHEF = new Benutzer(1L, true);

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private EmailSignatureService service;
    @MockitoBean private EmailSignaturVerwaltungService verwaltung;

    @BeforeEach
    void setup() {
        given(verwaltung.benutzer(any())).willReturn(MAX);
    }

    private static EmailSignaturDto dto(Long id, String name, boolean systemDefault) {
        return new EmailSignaturDto(id, name, "<p>Max Mustermann</p>", null, false, systemDefault,
                5L, "Max Mustermann", true, null, null, List.of(), false, null, false);
    }

    // ---------- Anmeldung, CSRF, Rechte ----------

    @Test
    @DisplayName("Ohne Anmeldung kein Zugriff auf die Signaturen")
    void anonymWirdAbgewiesen() throws Exception {
        mockMvc.perform(get("/api/email/signatures")).andExpect(status().isUnauthorized());
        verifyNoInteractions(verwaltung);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Speichern ohne CSRF-Token wird abgewiesen")
    void speichernOhneCsrf() throws Exception {
        mockMvc.perform(post("/api/email/signatures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Standard\",\"html\":\"<p>x</p>\"}"))
                .andExpect(status().isForbidden());
        verify(verwaltung, never()).speichern(any(), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Benutzer darf keine System-Signatur setzen")
    void systemSignaturNurAdmin() throws Exception {
        mockMvc.perform(put("/api/email/signatures/42/system-default").with(csrf()))
                .andExpect(status().isForbidden());
        verify(verwaltung, never()).alsSystemSignatur(anyLong(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin setzt die System-Signatur – Antwort hat das Feld isSystemDefault")
    void systemSignaturAdmin() throws Exception {
        given(verwaltung.benutzer(any())).willReturn(CHEF);
        given(verwaltung.alsSystemSignatur(42L, CHEF)).willReturn(dto(42L, "Firma", true));

        mockMvc.perform(put("/api/email/signatures/42/system-default").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.isSystemDefault").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("System-Signatur für unbekannte ID: 404")
    void systemSignaturUnbekannt() throws Exception {
        given(verwaltung.benutzer(any())).willReturn(CHEF);
        given(verwaltung.alsSystemSignatur(99L, CHEF)).willThrow(new NoSuchElementException());

        mockMvc.perform(put("/api/email/signatures/99/system-default").with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------- Liste und Laden ----------

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Liste kommt aus dem Rechte-Service für den angemeldeten Benutzer")
    void listeFuerBenutzer() throws Exception {
        given(verwaltung.liste(MAX)).willReturn(List.of(dto(1L, "Meine", false)));

        mockMvc.perform(get("/api/email/signatures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Meine"))
                .andExpect(jsonPath("$[0].besitzerName").value("Max Mustermann"))
                .andExpect(jsonPath("$[0].eigene").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Fremde oder unbekannte Signatur: 404")
    void fremdeSignatur404() throws Exception {
        given(verwaltung.laden(eq(7L), any())).willReturn(Optional.empty());
        given(verwaltung.sichtbare(anyLong(), any())).willReturn(Optional.empty());
        given(verwaltung.bearbeitbare(anyLong(), any())).willThrow(new NoSuchElementException());

        mockMvc.perform(get("/api/email/signatures/7")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/email/signatures/7/preview")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/email/signatures/7/images/3")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/email/signatures/7/images/3").with(csrf())).andExpect(status().isNotFound());
        verify(service, never()).deleteImage(anyLong(), anyLong());
        verify(service, never()).findImage(anyLong(), anyLong());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ungültige IDs: kein Treffer bzw. 400")
    void ungueltigeIds() throws Exception {
        given(verwaltung.laden(anyLong(), any())).willReturn(Optional.empty());

        mockMvc.perform(get("/api/email/signatures/-1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/email/signatures/0")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/email/signatures/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/email/signatures/'; DROP TABLE email_signature; --"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Speichern ----------

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Speichern reicht alle Felder an den Service weiter")
    void speichernReichtFelderWeiter() throws Exception {
        given(verwaltung.speichern(any(), eq(MAX))).willReturn(dto(3L, "Neu", false));

        mockMvc.perform(post("/api/email/signatures").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Neu\",\"html\":\"<table></table>\",\"vorlageDaten\":\"{\\\"vorlage\\\":\\\"klassisch\\\"}\","
                                + "\"besitzerId\":9,\"besitzerFestlegen\":true,\"alsStandard\":true,\"firmenVorlageId\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));

        ArgumentCaptor<EmailSignaturSpeichernRequest> captor = ArgumentCaptor.forClass(EmailSignaturSpeichernRequest.class);
        verify(verwaltung).speichern(captor.capture(), eq(MAX));
        assertThat(captor.getValue().name()).isEqualTo("Neu");
        assertThat(captor.getValue().vorlageDaten()).isEqualTo("{\"vorlage\":\"klassisch\"}");
        assertThat(captor.getValue().besitzerId()).isEqualTo(9L);
        assertThat(captor.getValue().besitzerFestlegen()).isTrue();
        assertThat(captor.getValue().alsStandard()).isTrue();
        assertThat(captor.getValue().firmenVorlageId()).isEqualTo(4L);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ungültige Eingaben: 400 mit verständlicher Meldung")
    void speichernUngueltig() throws Exception {
        given(verwaltung.speichern(any(), any())).willThrow(new IllegalArgumentException("Bitte gib der Signatur einen Namen."));

        mockMvc.perform(post("/api/email/signatures").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<script>alert(1)</script>\",\"html\":\"'; DROP TABLE x; --\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte gib der Signatur einen Namen."));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Fremde Signatur ändern: 404")
    void speichernFremd() throws Exception {
        given(verwaltung.speichern(any(), any())).willThrow(new NoSuchElementException());

        mockMvc.perform(post("/api/email/signatures").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":7,\"name\":\"Fremd\",\"html\":\"<p>x</p>\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- Standard-Signatur, Löschen ----------

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Eigene Standard-Signatur setzen und entfernen")
    void standardSetzenUndEntfernen() throws Exception {
        EmailSignaturDto standard = new EmailSignaturDto(3L, "Meine", "<p>x</p>", null, true, false,
                5L, "Max Mustermann", true, null, null, List.of(), false, null, false);
        given(verwaltung.alsStandard(3L, MAX)).willReturn(standard);

        mockMvc.perform(put("/api/email/signatures/3/standard").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultSignature").value(true));
        mockMvc.perform(delete("/api/email/signatures/standard").with(csrf()))
                .andExpect(status().isNoContent());
        verify(verwaltung).standardEntfernen(MAX);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Fremde Signatur als Standard: 404, ohne Profil: 400")
    void standardFehlerfaelle() throws Exception {
        given(verwaltung.alsStandard(7L, MAX)).willThrow(new NoSuchElementException());
        given(verwaltung.alsStandard(8L, MAX)).willThrow(new IllegalArgumentException("Zu deiner Anmeldung gibt es kein Benutzerprofil."));

        mockMvc.perform(put("/api/email/signatures/7/standard").with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/email/signatures/8/standard").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("System-Signatur löschen: 400 mit Meldung")
    void systemSignaturLoeschen() throws Exception {
        given(verwaltung.benutzer(any())).willReturn(CHEF);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Die System-Signatur kann nicht gelöscht werden."))
                .when(verwaltung).loeschen(9L, CHEF);

        mockMvc.perform(delete("/api/email/signatures/9").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Die System-Signatur kann nicht gelöscht werden."));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Alter Bild-Upload gibt es nicht mehr")
    void bildUploadEntfernt() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/email/signatures/3/images")
                        .file(new org.springframework.mock.web.MockMultipartFile("files", "x.html", "text/html", "<script>".getBytes()))
                        .with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Bilder alter Signaturen: Bild nur mit erlaubtem Typ, sonst Download – immer nosniff + Sandbox")
    void bildAuslieferungAbgesichert(@org.junit.jupiter.api.io.TempDir java.nio.file.Path ordner) throws Exception {
        java.nio.file.Path datei = java.nio.file.Files.write(ordner.resolve("bild"), new byte[] {1, 2, 3});
        org.example.kalkulationsprogramm.domain.EmailSignature sig = new org.example.kalkulationsprogramm.domain.EmailSignature();
        sig.setId(3L);
        given(verwaltung.sichtbare(eq(3L), any())).willReturn(Optional.of(sig));
        org.example.kalkulationsprogramm.domain.EmailSignatureImage png = new org.example.kalkulationsprogramm.domain.EmailSignatureImage();
        png.setContentType("image/png");
        org.example.kalkulationsprogramm.domain.EmailSignatureImage svg = new org.example.kalkulationsprogramm.domain.EmailSignatureImage();
        svg.setContentType("image/svg+xml");
        given(service.findImage(3L, 1L)).willReturn(Optional.of(png));
        given(service.findImage(3L, 2L)).willReturn(Optional.of(svg));
        given(service.resolveImagePath(any())).willReturn(Optional.of(datei));

        mockMvc.perform(get("/api/email/signatures/3/images/1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "image/png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("Content-Disposition"));
        mockMvc.perform(get("/api/email/signatures/3/images/2"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "application/octet-stream"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Löschen: eigene 204, fremde 404")
    void loeschen() throws Exception {
        org.mockito.Mockito.doThrow(new NoSuchElementException()).when(verwaltung).loeschen(7L, MAX);

        mockMvc.perform(delete("/api/email/signatures/3").with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/email/signatures/7").with(csrf())).andExpect(status().isNotFound());
        verify(verwaltung).loeschen(3L, MAX);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Vom Betrieb festgelegte Signatur: Löschen und Bild entfernen 400 mit Meldung")
    void nurLesenNichtAenderbar() throws Exception {
        String meldung = "Diese Signatur hat der Betrieb für Sie festgelegt. Ändern kann sie nur ein Admin.";
        org.mockito.Mockito.doThrow(new IllegalArgumentException(meldung)).when(verwaltung).loeschen(8L, MAX);
        given(verwaltung.bearbeitbare(8L, MAX)).willThrow(new IllegalArgumentException(meldung));

        mockMvc.perform(delete("/api/email/signatures/8").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(meldung));
        mockMvc.perform(delete("/api/email/signatures/8/images/1").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(meldung));
        verify(service, never()).deleteImage(anyLong(), anyLong());
    }

    // ---------- Firmen-Vorlagen ----------

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /firmen-vorlagen: jeder angemeldete Benutzer sieht die Vorlagen")
    void firmenVorlagenFuerBenutzer() throws Exception {
        EmailSignaturDto vorlage = new EmailSignaturDto(4L, "Firmen-Design", "<table></table>", "{\"vorlage\":\"kompakt\"}",
                false, false, null, null, false, null, null, List.of(), true, null, true);
        given(verwaltung.firmenVorlagen(MAX)).willReturn(List.of(vorlage));

        mockMvc.perform(get("/api/email/signatures/firmen-vorlagen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].firmenVorlage").value(true))
                .andExpect(jsonPath("$[0].nurLesen").value(true))
                .andExpect(jsonPath("$[0].vorlageDaten").value("{\"vorlage\":\"kompakt\"}"))
                .andExpect(jsonPath("$[0].besitzerId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("GET /firmen-vorlagen ohne Anmeldung: 401")
    void firmenVorlagenAnonym() throws Exception {
        mockMvc.perform(get("/api/email/signatures/firmen-vorlagen")).andExpect(status().isUnauthorized());
        verifyNoInteractions(verwaltung);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Benutzer darf keine Firmen-Vorlage festlegen oder zurücknehmen")
    void firmenVorlageNurAdmin() throws Exception {
        mockMvc.perform(put("/api/email/signatures/4/firmen-vorlage").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/email/signatures/4/firmen-vorlage").with(csrf())).andExpect(status().isForbidden());
        verify(verwaltung, never()).alsFirmenVorlage(anyLong(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin legt Firmen-Vorlage fest und nimmt sie zurück; ohne CSRF 403, unbekannt 404")
    void firmenVorlageAdmin() throws Exception {
        given(verwaltung.benutzer(any())).willReturn(CHEF);
        EmailSignaturDto alsVorlage = new EmailSignaturDto(4L, "Firmen-Design", "<p>x</p>", null, false, false,
                1L, "Chef Mustermann", true, null, null, List.of(), true, null, false);
        given(verwaltung.alsFirmenVorlage(4L, true, CHEF)).willReturn(alsVorlage);
        given(verwaltung.alsFirmenVorlage(4L, false, CHEF)).willReturn(dto(4L, "Firmen-Design", false));
        given(verwaltung.alsFirmenVorlage(eq(404L), org.mockito.ArgumentMatchers.anyBoolean(), eq(CHEF)))
                .willThrow(new NoSuchElementException());

        mockMvc.perform(put("/api/email/signatures/4/firmen-vorlage").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmenVorlage").value(true));
        mockMvc.perform(delete("/api/email/signatures/4/firmen-vorlage").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmenVorlage").value(false));
        mockMvc.perform(put("/api/email/signatures/404/firmen-vorlage").with(csrf())).andExpect(status().isNotFound());
        given(verwaltung.alsFirmenVorlage(5L, true, CHEF))
                .willThrow(new IllegalArgumentException("Firmen-Vorlage kann nur eine Signatur werden, die mit dem Formular gestaltet wurde."));
        mockMvc.perform(put("/api/email/signatures/5/firmen-vorlage").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Firmen-Vorlage kann nur eine Signatur werden, die mit dem Formular gestaltet wurde."));
        mockMvc.perform(put("/api/email/signatures/4/firmen-vorlage")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/email/signatures/abc/firmen-vorlage").with(csrf())).andExpect(status().isBadRequest());
    }

    // ---------- Standard zum Vorbelegen, System-Signatur ----------

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /default reicht Parameter an den Rechte-Service (der sie bei Benutzern ignoriert)")
    void defaultSignatur() throws Exception {
        given(verwaltung.standardSignatur(MAX, 99L, null)).willReturn(Optional.of(dto(3L, "Meine", false)));

        mockMvc.perform(get("/api/email/signatures/default").param("frontendUserId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
        mockMvc.perform(get("/api/email/signatures/default"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /system-default: 204 ohne sichtbare System-Signatur")
    void systemDefaultLeer() throws Exception {
        given(verwaltung.systemSignatur(MAX)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/email/signatures/system-default")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /system-default liefert Signatur + Platzhalter-Flag")
    void systemDefaultMitPlatzhalter() throws Exception {
        given(verwaltung.benutzer(any())).willReturn(CHEF);
        EmailSignaturDto platzhalter = new EmailSignaturDto(99L, "System", "<div data-system-placeholder=\"1\">Bitte eintragen</div>",
                null, false, true, null, null, false, null, null, List.of(), false, null, false);
        given(verwaltung.systemSignatur(CHEF)).willReturn(Optional.of(platzhalter));

        mockMvc.perform(get("/api/email/signatures/system-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature.id").value(99))
                .andExpect(jsonPath("$.signature.isSystemDefault").value(true))
                .andExpect(jsonPath("$.isPlatzhalter").value(true));
    }
}
