package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.EmailTextTemplateRepository;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.EmailTextTemplateService;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code POST /api/email/template}: {@code anrede} ist der Rohwert der Anrede-Art (HERR, FRAU, FAMILIE,
 * FIRMA, DAMEN_HERREN oder leer). Die Standardvorlage {@code {{ANREDE}} {{KUNDENNAME}},} wird zur
 * Briefanrede – mit echtem {@link EmailTextTemplateService}, nur Datenbank und Firmendaten sind gemockt.
 */
class EmailTemplateControllerBriefanredeTest {

    private final EmailTextTemplateRepository repository = mock(EmailTextTemplateRepository.class);
    private final FirmeninformationService firma = mock(FirmeninformationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EmailTextTemplateService vorlagen = new EmailTextTemplateService(repository, firma);
        mvc = MockMvcBuilders.standaloneSetup(
                new EmailTemplateController(vorlagen, mock(DokumentFreigabeService.class), firma)).build();
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
    }

    private void standardVorlage() {
        EmailTextTemplate vorlage = new EmailTextTemplate();
        vorlage.setDokumentTyp("RECHNUNG");
        vorlage.setSubjectTemplate("Rechnung {{DOKUMENTNUMMER}}");
        vorlage.setHtmlBody("<p>{{ANREDE}} {{KUNDENNAME}},</p>");
        vorlage.setAktiv(true);
        when(repository.findByDokumentTyp("RECHNUNG")).thenReturn(Optional.of(vorlage));
    }

    private ResultActions sende(String json) throws Exception {
        return mvc.perform(post("/api/email/template").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource(delimiter = '|', value = {
            "DAMEN_HERREN | Landkreis Musterstadt | <p>Sehr geehrte Damen und Herren,</p>",
            "FIRMA        | Landkreis Musterstadt | <p>Sehr geehrte Damen und Herren,</p>",
            "HERR         | Max Mustermann        | <p>Sehr geehrter Herr Max Mustermann,</p>",
            "frau         | Erika Musterfrau      | <p>Sehr geehrte Frau Erika Musterfrau,</p>",
            "FRAU         | ''                    | <p>Sehr geehrte Damen und Herren,</p>",
            "''           | Max Mustermann        | <p>Sehr geehrte Damen und Herren,</p>",
            "Sehr geehrter Herr | Max Mustermann  | <p>Sehr geehrte Damen und Herren,</p>"
    })
    @DisplayName("DB-Vorlage: Anrede-Rohwert + Kundenname ergeben die Briefanrede")
    void dbVorlageBriefanrede(String anrede, String kundenName, String erwartet) throws Exception {
        standardVorlage();
        sende("{\"dokumentTyp\":\"RECHNUNG\",\"anrede\":\"" + anrede + "\",\"kundenName\":\"" + kundenName + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(erwartet));
    }

    @Test
    @DisplayName("DB-Vorlage: ohne anrede-Feld gilt Damen und Herren")
    void dbVorlageOhneAnrede() throws Exception {
        standardVorlage();
        sende("{\"dokumentTyp\":\"RECHNUNG\",\"kundenName\":\"Landkreis Musterstadt\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("<p>Sehr geehrte Damen und Herren,</p>"));
    }

    @Test
    @DisplayName("DB-Vorlage: Skript und SQL im Namen landen maskiert bzw. als Text in der Mail")
    void dbVorlageBoeseNamen() throws Exception {
        standardVorlage();
        sende("{\"dokumentTyp\":\"RECHNUNG\",\"anrede\":\"HERR\",\"kundenName\":\"<script>alert(1)</script>\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("<p>Sehr geehrter Herr &lt;script&gt;alert(1)&lt;/script&gt;,</p>"));
        sende("{\"dokumentTyp\":\"RECHNUNG\",\"anrede\":\"'; DROP TABLE kunde; --\",\"kundenName\":\"Max Mustermann\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("<p>Sehr geehrte Damen und Herren,</p>"));
    }

    @Test
    @DisplayName("Ohne DB-Vorlage: Fallback nutzt dieselbe Briefanrede, nie den Rohwert")
    void fallbackBriefanrede() throws Exception {
        when(repository.findByDokumentTyp(anyString())).thenReturn(Optional.empty());

        sende("{\"dokumentTyp\":\"ANGEBOT\",\"anrede\":\"DAMEN_HERREN\",\"kundenName\":\"Landkreis Musterstadt\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body", Matchers.startsWith("Sehr geehrte Damen und Herren,<br><br>")));
        sende("{\"dokumentTyp\":\"AUFTRAGSBESTAETIGUNG\",\"anrede\":\"HERR\",\"kundenName\":\"Max Mustermann\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body", Matchers.startsWith("Sehr geehrter Herr Max Mustermann,<br><br>")));
        sende("{\"dokumentTyp\":\"ZEICHNUNG\",\"anrede\":\"FRAU\",\"kundenName\":\"Erika Musterfrau\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body", Matchers.startsWith("Sehr geehrte Frau Erika Musterfrau,<br><br>")));
        sende("{\"dokumentTyp\":\"ANGEBOT\",\"anrede\":\"FRAU\",\"kundenName\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body", Matchers.startsWith("Sehr geehrte Damen und Herren,<br><br>")));
    }
}
