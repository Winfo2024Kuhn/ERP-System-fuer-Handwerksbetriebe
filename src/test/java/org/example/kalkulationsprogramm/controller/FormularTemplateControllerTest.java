package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateCopyRequest;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateDto;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateListDto;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateRenameRequest;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateSaveRequest;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateSelectionRequest;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateUpdateRequest;
import org.example.kalkulationsprogramm.service.FormularTemplateService;
import org.example.kalkulationsprogramm.service.FormularTemplateService.NamedTemplateData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FormularTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class FormularTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FormularTemplateService formularTemplateService;

    @Nested
    @DisplayName("GET /api/formulare/template")
    class GetTemplate {

        @Test
        @DisplayName("Gibt Template mit Platzhaltern zurück")
        void gibtTemplateZurueck() throws Exception {
            given(formularTemplateService.loadTemplate()).willReturn("<h1>Rechnung</h1>");
            given(formularTemplateService.getLastModifiedIso()).willReturn("2024-01-01T10:00:00");
            given(formularTemplateService.getSupportedPlaceholders()).willReturn(List.of("{{kunde}}", "{{betrag}}"));

            mockMvc.perform(get("/api/formulare/template"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.html").value("<h1>Rechnung</h1>"))
                    .andExpect(jsonPath("$.placeholders[0]").value("{{kunde}}"));
        }

        @Test
        @DisplayName("Gibt Default-Template zurück wenn preset=default")
        void gibtDefaultTemplateZurueck() throws Exception {
            given(formularTemplateService.defaultTemplate()).willReturn("<h1>Default</h1>");
            given(formularTemplateService.getSupportedPlaceholders()).willReturn(List.of());

            mockMvc.perform(get("/api/formulare/template").param("preset", "default"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.html").value("<h1>Default</h1>"))
                    .andExpect(jsonPath("$.lastModified").doesNotExist());
        }
    }

    @Nested
    @DisplayName("PUT /api/formulare/template")
    class SaveTemplate {

        @Test
        @DisplayName("Speichert Template erfolgreich")
        void speichertTemplateErfolgreich() throws Exception {
            given(formularTemplateService.saveTemplate("<h1>Neu</h1>")).willReturn("<h1>Neu</h1>");
            given(formularTemplateService.getLastModifiedIso()).willReturn("2024-06-01T12:00:00");
            given(formularTemplateService.getSupportedPlaceholders()).willReturn(List.of());

            FormularTemplateUpdateRequest request = new FormularTemplateUpdateRequest();
            request.setHtml("<h1>Neu</h1>");

            mockMvc.perform(put("/api/formulare/template")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.html").value("<h1>Neu</h1>"));
        }

        @Test
        @DisplayName("Leerer HTML-Body gibt 400 zurück (@Valid)")
        void leererHtmlBodyGibt400() throws Exception {
            FormularTemplateUpdateRequest request = new FormularTemplateUpdateRequest();
            request.setHtml("");

            mockMvc.perform(put("/api/formulare/template")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("SQL Injection im HTML wird als String gespeichert")
        void sqlInjectionImHtml() throws Exception {
            String sqlPayload = "'; DROP TABLE templates; --";
            given(formularTemplateService.saveTemplate(sqlPayload)).willReturn(sqlPayload);
            given(formularTemplateService.getLastModifiedIso()).willReturn("2024-01-01T10:00:00");
            given(formularTemplateService.getSupportedPlaceholders()).willReturn(List.of());

            FormularTemplateUpdateRequest request = new FormularTemplateUpdateRequest();
            request.setHtml(sqlPayload);

            mockMvc.perform(put("/api/formulare/template")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(put("/api/formulare/template")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<template />"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("POST /api/formulare/template/logo")
    class UploadLogo {

        @Test
        @DisplayName("Logo-Upload gibt URL und Dateiname zurück")
        void uploadLogoErfolgreich() throws Exception {
            given(formularTemplateService.storeLogo(any())).willReturn("/api/formulare/template/assets/logo.png");

            MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3});

            mockMvc.perform(multipart("/api/formulare/template/logo").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.filename").value("logo.png"))
                    .andExpect(jsonPath("$.url").value("/api/formulare/template/assets/logo.png"));
        }
    }

    @Nested
    @DisplayName("Named Templates")
    class NamedTemplates {

        private NamedTemplateData buildData(String name) {
            return new NamedTemplateData(name, "<h1>" + name + "</h1>", List.of(), List.of(), "2024-01-01", "2024-06-01");
        }

        @Test
        @DisplayName("GET /api/formulare/templates listet alle Templates")
        void listTemplates() throws Exception {
            given(formularTemplateService.listNamedTemplates())
                    .willReturn(List.of(new FormularTemplateListDto("Standard", "2024-01-01", "2024-06-01", List.of())));

            mockMvc.perform(get("/api/formulare/templates"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Standard"));
        }

        @Test
        @DisplayName("POST /api/formulare/templates erstellt benanntes Template")
        void saveNamedTemplate() throws Exception {
            given(formularTemplateService.saveNamedTemplate(eq("Rechnung"), any(), any()))
                    .willReturn(buildData("Rechnung"));
            given(formularTemplateService.getSupportedPlaceholders()).willReturn(List.of());

            FormularTemplateSaveRequest request = new FormularTemplateSaveRequest();
            request.setName("Rechnung");
            request.setHtml("<h1>Rechnung</h1>");

            mockMvc.perform(post("/api/formulare/templates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Rechnung"));
        }

        @Test
        @DisplayName("POST mit leerem Namen gibt 400 zurück (@Valid)")
        void leererNameGibt400() throws Exception {
            FormularTemplateSaveRequest request = new FormularTemplateSaveRequest();
            request.setName("");
            request.setHtml("<h1>Test</h1>");

            mockMvc.perform(post("/api/formulare/templates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST mit überlangem Namen gibt 400 zurück")
        void ueberlangerNameGibt400() throws Exception {
            FormularTemplateSaveRequest request = new FormularTemplateSaveRequest();
            request.setName("A".repeat(101));
            request.setHtml("<h1>Test</h1>");

            mockMvc.perform(post("/api/formulare/templates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/formulare/templates/{name} lädt benanntes Template")
        void loadNamedTemplate() throws Exception {
            given(formularTemplateService.loadNamedTemplate("Standard"))
                    .willReturn(buildData("Standard"));
            given(formularTemplateService.getSupportedPlaceholders()).willReturn(List.of());

            mockMvc.perform(get("/api/formulare/templates/Standard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Standard"));
        }

        @Test
        @DisplayName("POST /api/formulare/templates/{name}/copy kopiert Template")
        void copyTemplate() throws Exception {
            given(formularTemplateService.copyNamedTemplate("Standard", "Standard-Kopie"))
                    .willReturn(buildData("Standard-Kopie"));
            given(formularTemplateService.getSupportedPlaceholders()).willReturn(List.of());

            FormularTemplateCopyRequest request = new FormularTemplateCopyRequest();
            request.setNewName("Standard-Kopie");

            mockMvc.perform(post("/api/formulare/templates/Standard/copy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Standard-Kopie"));
        }

        @Test
        @DisplayName("PUT /api/formulare/templates/{name}/rename benennt Template um")
        void renameTemplate() throws Exception {
            given(formularTemplateService.renameNamedTemplate("Alt", "Neu"))
                    .willReturn(buildData("Neu"));
            given(formularTemplateService.getSupportedPlaceholders()).willReturn(List.of());

            FormularTemplateRenameRequest request = new FormularTemplateRenameRequest();
            request.setNewName("Neu");

            mockMvc.perform(put("/api/formulare/templates/Alt/rename")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Neu"));
        }

        @Test
        @DisplayName("DELETE /api/formulare/templates/{name} löscht Template (204)")
        void deleteTemplate() throws Exception {
            doNothing().when(formularTemplateService).deleteNamedTemplate("Alt");

            mockMvc.perform(delete("/api/formulare/templates/Alt"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("XSS im Template-Name")
        void xssImTemplateName() throws Exception {
            String xss = "<script>alert('xss')</script>";
            given(formularTemplateService.saveNamedTemplate(eq(xss), any(), any()))
                    .willReturn(new NamedTemplateData(xss, "<h1>XSS</h1>", List.of(), List.of(), "2024-01-01", "2024-06-01"));
            given(formularTemplateService.getSupportedPlaceholders()).willReturn(List.of());

            FormularTemplateSaveRequest request = new FormularTemplateSaveRequest();
            request.setName(xss);
            request.setHtml("<h1>XSS</h1>");

            mockMvc.perform(post("/api/formulare/templates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Template Selection")
    class Selection {

        @Test
        @DisplayName("GET /api/formulare/templates/selection gibt Template-Name zurück")
        void getSelection() throws Exception {
            given(formularTemplateService.getPreferredTemplateForDokumenttyp("RECHNUNG", null))
                    .willReturn(Optional.of("Standard"));

            mockMvc.perform(get("/api/formulare/templates/selection")
                            .param("dokumenttyp", "RECHNUNG"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Standard"));
        }

        @Test
        @DisplayName("GET /api/formulare/templates/selection gibt 204 wenn keine Auswahl")
        void getSelectionLeer() throws Exception {
            given(formularTemplateService.getPreferredTemplateForDokumenttyp("ANGEBOT", null))
                    .willReturn(Optional.empty());

            mockMvc.perform(get("/api/formulare/templates/selection")
                            .param("dokumenttyp", "ANGEBOT"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("POST /api/formulare/templates/selection setzt Auswahl (204)")
        void setSelection() throws Exception {
            doNothing().when(formularTemplateService).setPreferredTemplateForDokumenttyp(any(String.class), any(String.class), any(List.class));

            FormularTemplateSelectionRequest request = new FormularTemplateSelectionRequest();
            request.setDokumenttyp("RECHNUNG");
            request.setTemplateName("Standard");

            mockMvc.perform(post("/api/formulare/templates/selection")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Platzhalter & Dokumentnummer")
    class PlatzhalterUndNummer {

        @Test
        @DisplayName("GET /api/formulare/placeholders gibt Map zurück")
        void resolvePlaceholders() throws Exception {
            given(formularTemplateService.resolvePlaceholders(null, false))
                    .willReturn(Map.of("{{firma}}", "Handwerk GmbH"));

            mockMvc.perform(get("/api/formulare/placeholders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.['{{firma}}']").value("Handwerk GmbH"));
        }

        @Test
        @DisplayName("POST /api/formulare/dokumentnummer generiert Nummer")
        void generateDokumentnummer() throws Exception {
            given(formularTemplateService.generateDokumentnummer()).willReturn("DOK-2024-001");

            mockMvc.perform(post("/api/formulare/dokumentnummer"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("DOK-2024-001"));
        }
    }
}
