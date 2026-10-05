package org.example.kalkulationsprogramm.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.EmailTextTemplateService;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmailTemplateControllerLueckenTest {

    private final EmailTextTemplateService vorlagen = mock(EmailTextTemplateService.class);
    private final DokumentFreigabeService freigabe = mock(DokumentFreigabeService.class);
    private final FirmeninformationService firma = mock(FirmeninformationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EmailTemplateController(vorlagen, freigabe, firma)).build();
    }

    private org.springframework.test.web.servlet.ResultActions sende(String json) throws Exception {
        return mvc.perform(post("/api/email/template").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"dokumentTyp\":\"\"}", "{\"dokumentTyp\":\"   \"}"})
    @DisplayName("Fehlender oder leerer Dokumenttyp -> 400")
    void ohneTyp(String json) throws Exception {
        sende(json).andExpect(status().isBadRequest());
        verifyNoInteractions(vorlagen);
    }

    @Test
    @DisplayName("DB-Vorlage wird gerendert; Platzhalter-Kontext enthaelt Datum in deutscher Schreibweise und Defaults")
    void dbVorlage() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(vorlagen.render(eq("RECHNUNG"), any())).thenReturn(new EmailService.EmailContent("Betreff", "<p>Text</p>"));

        sende("{\"dokumentTyp\":\"rechnung\",\"kundenName\":\"Max Mustermann\",\"rechnungsdatum\":\"2024-03-05\","
                + "\"faelligkeitsdatum\":\"kein-datum\",\"betrag\":\"10,00 EUR\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Betreff"))
                .andExpect(jsonPath("$.body").value("<p>Text</p>"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> cap = ArgumentCaptor.forClass(Map.class);
        verify(vorlagen).render(eq("RECHNUNG"), cap.capture());
        Map<String, String> ctx = cap.getValue();
        assertEquals("Sehr geehrte Damen und Herren", ctx.get("ANREDE"));
        assertEquals("Max Mustermann", ctx.get("KUNDENNAME"));
        assertEquals("05.03.2024", ctx.get("RECHNUNGSDATUM"));
        assertEquals("kein-datum", ctx.get("FAELLIGKEITSDATUM"));
        assertEquals("", ctx.get("BAUVORHABEN"));
        assertEquals("", ctx.get("REVIEW_LINK"));
        assertEquals("10,00 EUR", ctx.get("BETRAG"));
    }

    @Test
    @DisplayName("Bewertungs-Link: gesetzt -> sicherer Anker, Sonderzeichen werden kodiert; leer/null -> kein Link")
    void bewertungsLink() throws Exception {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setGoogleBewertungsLink("  https://example.com/r?x=\"><script>alert(1)</script>  ");
        when(firma.getFirmeninformation()).thenReturn(dto);
        when(vorlagen.render(anyString(), any())).thenReturn(new EmailService.EmailContent("s", "b"));

        sende("{\"dokumentTyp\":\"RECHNUNG\"}").andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> cap = ArgumentCaptor.forClass(Map.class);
        verify(vorlagen).render(anyString(), cap.capture());
        String link = cap.getValue().get("REVIEW_LINK");
        assertTrue(link.startsWith("<a href=\"https://example.com/r?x=%22%3E%3Cscript%3E"), link);
        assertFalse(link.contains("<script>"));
        assertTrue(link.contains("rel=\"noopener noreferrer\""));
        assertTrue(link.contains("Jetzt Bewertung abgeben"));
        // Das Anfuehrungszeichen kann das href-Attribut nicht mehr beenden.
        assertFalse(link.contains("x=\">"));

        when(firma.getFirmeninformation()).thenReturn(null);
        clearInvocations(vorlagen);
        when(vorlagen.render(anyString(), any())).thenReturn(new EmailService.EmailContent("s", "b"));
        sende("{\"dokumentTyp\":\"RECHNUNG\"}").andExpect(status().isOk());
        verify(vorlagen).render(anyString(), cap.capture());
        assertEquals("", cap.getValue().get("REVIEW_LINK"));
    }

    @Test
    @DisplayName("Bewertungs-Link: javascript:-Adresse ergibt keinen Link")
    void bewertungsLinkNurHttp() throws Exception {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setGoogleBewertungsLink("javascript:alert(1)");
        when(firma.getFirmeninformation()).thenReturn(dto);
        when(vorlagen.render(anyString(), any())).thenReturn(new EmailService.EmailContent("s", "b"));

        sende("{\"dokumentTyp\":\"RECHNUNG\"}").andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> cap = ArgumentCaptor.forClass(Map.class);
        verify(vorlagen).render(anyString(), cap.capture());
        assertEquals("", cap.getValue().get("REVIEW_LINK"));
    }

    @Test
    @DisplayName("Fallback-Rechnung nutzt Bankverbindung aus den Firmendaten")
    void fallbackRechnungMitFirmendaten() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(firma.firmenangabenFuerEmail()).thenReturn(new EmailService.Firmenangaben(
                "Musterbank", "DE00 1234 5678 9012 3456 78", null, null));
        when(vorlagen.render(anyString(), any())).thenReturn(null);

        sende("{\"dokumentTyp\":\"RECHNUNG\",\"kundenName\":\"<b>Max</b>\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("IBAN: DE00 1234 5678 9012 3456 78")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("&lt;b&gt;Max&lt;/b&gt;")));
        verify(firma).firmenangabenFuerEmail();
    }

    @Test
    @DisplayName("Angebot: Freigabe-Block wird angehaengt; Standard-Gueltigkeit und leerer Empfaenger bei fehlenden Werten")
    void angebotMitFreigabe() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(vorlagen.render(eq("ANGEBOT"), any())).thenReturn(new EmailService.EmailContent("Angebot", "<p>A</p>"));
        when(freigabe.erstelleFreigabeBlockFuerDokument(eq(5L), eq(false), eq(""), any(), eq(DokumentFreigabeService.DEFAULT_GUELTIGKEITS_TAGE)))
                .thenReturn(Optional.of("<div>Freigabe</div>"));

        sende("{\"dokumentTyp\":\"ANGEBOT\",\"dokumentId\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("<p>A</p><div>Freigabe</div>"));
    }

    @Test
    @DisplayName("Nachtragsangebot im Anfrage-Kontext: Empfaenger, PDF-Name und Gueltigkeit werden durchgereicht")
    void nachtragMitParametern() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(vorlagen.render(any(), any())).thenReturn(new EmailService.EmailContent("N", "<p>N</p>"));
        when(freigabe.erstelleFreigabeBlockFuerDokument(7L, true, "max@example.com", "abc.pdf", 30))
                .thenReturn(Optional.empty());

        sende("{\"dokumentTyp\":\"NACHTRAGSANGEBOT\",\"dokumentId\":7,\"isAnfrage\":true,\"recipient\":\"max@example.com\","
                + "\"pdfDateiname\":\"abc.pdf\",\"gueltigkeitTage\":30}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("<p>N</p>"));
        verify(freigabe).erstelleFreigabeBlockFuerDokument(7L, true, "max@example.com", "abc.pdf", 30);
    }

    @Test
    @DisplayName("Angebot ohne Dokument-ID und andere Typen: kein Freigabe-Block")
    void keinFreigabeBlock() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(vorlagen.render(any(), any())).thenReturn(new EmailService.EmailContent("s", "b"));

        sende("{\"dokumentTyp\":\"ANGEBOT\"}").andExpect(status().isOk());
        sende("{\"dokumentTyp\":\"AUFTRAGSBESTAETIGUNG\",\"dokumentId\":3}").andExpect(status().isOk());
        verifyNoInteractions(freigabe);
    }

    @Test
    @DisplayName("Fallback ohne DB-Vorlage: Rechnung nutzt feste Texte mit Standardwerten")
    void fallbackRechnung() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(vorlagen.render(any(), any())).thenReturn(null);

        sende("{\"dokumentTyp\":\"SCHLUSSRECHNUNG\",\"bauvorhaben\":\"Gartentor\",\"dokumentnummer\":\"R-1\","
                + "\"rechnungsdatum\":\"2024-03-05\",\"faelligkeitsdatum\":\"2024-04-04\",\"betrag\":\"99,00 EUR\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Schlussrechnung: (BV: Gartentor) Rechnungsnummer: R-1"))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("04.04.2024")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("99,00 EUR")));

        // ohne Datum/Betrag greifen Standardwerte (kein Fehler)
        sende("{\"dokumentTyp\":\"RECHNUNG\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("0,00 €")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAHNUNG", "ZAHLUNGSERINNERUNG", "ERSTE_MAHNUNG", "ZWEITE_MAHNUNG"})
    @DisplayName("Fallback Mahnstufen: Betreff beginnt immer mit 'Mahnung:'")
    void fallbackMahnung(String typ) throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(vorlagen.render(any(), any())).thenReturn(null);
        sende("{\"dokumentTyp\":\"" + typ + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(org.hamcrest.Matchers.startsWith("Mahnung:")));
    }

    @Test
    @DisplayName("Fallback Angebot, Auftragsbestaetigung (Projektnummer als Ersatz fuer Nummer) und Zeichnung")
    void fallbackWeitere() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(vorlagen.render(any(), any())).thenReturn(null);

        sende("{\"dokumentTyp\":\"ANGEBOT\",\"bauvorhaben\":\"Zaun\",\"dokumentnummer\":\"A-5\"}")
                .andExpect(jsonPath("$.subject").value("Anfrage: (BV: Zaun) Anfragesnummer: A-5"));
        sende("{\"dokumentTyp\":\"AUFTRAGSBESTAETIGUNG\",\"bauvorhaben\":\"Zaun\",\"projektnummer\":\"P-9\"}")
                .andExpect(jsonPath("$.subject").value("Auftragsbestätigung: (BV: Zaun) Auftragsnummer: P-9"));
        sende("{\"dokumentTyp\":\"ZEICHNUNG\",\"bauvorhaben\":\"Zaun\"}")
                .andExpect(jsonPath("$.subject").value("Kundenzeichnung BV:(Zaun )"));
    }

    @Test
    @DisplayName("Unbekannter Typ ohne Vorlage: leere Antwort statt Fehler")
    void unbekannterTyp() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(vorlagen.render(any(), any())).thenReturn(null);
        sende("{\"dokumentTyp\":\"'; DROP TABLE x; --\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(""))
                .andExpect(jsonPath("$.body").value(""));
    }

    @Test
    @DisplayName("Interner Fehler im Service -> 500 ohne Details")
    void interneFehler() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
        when(vorlagen.render(any(), any())).thenThrow(new IllegalStateException("DB kaputt"));
        sende("{\"dokumentTyp\":\"RECHNUNG\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }

    @Test
    @DisplayName("Ungueltiges JSON -> 400")
    void ungueltigesJson() throws Exception {
        sende("{kein json").andExpect(status().isBadRequest());
    }
}
