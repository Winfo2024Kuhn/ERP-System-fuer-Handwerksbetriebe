package org.example.kalkulationsprogramm.controller;

import tools.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.controller.advice.RestExceptionHandler;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAkzeptierenRequest;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.FreigabeErklaerung;
import org.example.kalkulationsprogramm.service.FreigabeCodeAnforderungException;
import org.example.kalkulationsprogramm.service.FreigabeCodeException;
import org.example.kalkulationsprogramm.service.FreigabeCodeService;
import org.example.kalkulationsprogramm.util.ConstraintMessageResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-Tests für POST /api/internal/freigabe/{uuid}/akzeptieren.
 *
 * <p>Beweissicherungs-Erweiterung: Vor- und Nachname sind seit V317 Pflicht.
 * Validierungsfehler werden vom {@link RestExceptionHandler} als HTTP 400 mit
 * Feldnamen ausgeliefert — getestet werden hier Happy-Path und die typischen
 * Verstöße (fehlend, zu kurz).</p>
 */
@WebMvcTest(FreigabeInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RestExceptionHandler.class)
class FreigabeInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DokumentFreigabeService freigabeService;

    @MockitoBean
    private DateiSpeicherService dateiSpeicherService;

    @MockitoBean
    private FreigabeCodeService freigabeCodeService;

    /**
     * Wird vom {@link RestExceptionHandler}-Konstruktor verlangt (nullable). Wir
     * geben einen leeren Mock rein, damit Spring den Bean deterministisch auflöst —
     * sonst hängt das Verhalten an der konkreten Spring-Version, ob {@code @Nullable}
     * auf Konstruktorparametern für eine Null-Injection ausreicht.
     */
    @MockitoBean
    private ConstraintMessageResolver constraintMessageResolver;

    @Test
    void akzeptieren_gueltigerRequest_gibt200MitUnterzeichnerNameZurueck() throws Exception {
        DokumentFreigabe saved = new DokumentFreigabe();
        saved.setUuid("uuid-ok");
        saved.setDokumentNummer("ANG-2026-0001");
        saved.setDokumentArt("Angebot");
        saved.setStatus(FreigabeStatus.ACCEPTED);
        saved.setAkzeptiertAm(LocalDateTime.of(2026, 5, 12, 10, 30));
        saved.setHashAcceptance("hash-xyz");
        saved.setUnterzeichnerVorname("Max");
        saved.setUnterzeichnerNachname("Mustermann");
        saved.setUnterzeichnerName("Max Mustermann");
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-ok"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any(), eq("123456"), any()))
                .willReturn(saved);

        String json = objectMapper.writeValueAsString(validRequest());

        mockMvc.perform(post("/api/internal/freigabe/uuid-ok/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value("uuid-ok"))
                .andExpect(jsonPath("$.unterzeichnerName").value("Max Mustermann"));
    }

    @Test
    void akzeptieren_reichtAusgewaehlteAlternativenAnService() throws Exception {
        DokumentFreigabe saved = new DokumentFreigabe();
        saved.setUuid("uuid-alt");
        saved.setDokumentNummer("ANG-2026-0002");
        saved.setDokumentArt("Angebot");
        saved.setStatus(FreigabeStatus.ACCEPTED);
        saved.setAkzeptiertAm(LocalDateTime.of(2026, 5, 12, 10, 30));
        saved.setUnterzeichnerName("Max Mustermann");
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-alt"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any(), eq("123456"), any()))
                .willReturn(saved);

        FreigabeAkzeptierenRequest req = validRequest();
        req.setAusgewaehlteAlternativen(List.of("alt-1", "alt-2"));
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-alt/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(freigabeService).akzeptiereMitCode(
                eq("uuid-alt"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), captor.capture(), eq("123456"), any());
        assertThat(captor.getValue()).containsExactly("alt-1", "alt-2");
    }

    @Test
    void akzeptieren_reichtRechtsnachweisAnServiceUndMeldetVorzeitigenBeginn() throws Exception {
        DokumentFreigabe saved = new DokumentFreigabe();
        saved.setUuid("uuid-recht");
        saved.setDokumentNummer("ANG-2026-0003");
        saved.setDokumentArt("Angebot");
        saved.setStatus(FreigabeStatus.ACCEPTED);
        saved.setAkzeptiertAm(LocalDateTime.of(2026, 10, 13, 9, 0));
        saved.setUnterzeichnerName("Max Mustermann");
        saved.setVorzeitigerBeginn(true);
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-recht"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any(), eq("123456"), any()))
                .willReturn(saved);

        FreigabeAkzeptierenRequest req = validRequest();
        req.setVorzeitigerBeginn(true);
        req.setWiderrufBelehrungStand("2026-10-13");
        req.setDatenschutzStand("2026-10-13");
        req.setBestaetigungText("Ich beauftrage Angebot ANG-2026-0003 verbindlich und zahlungspflichtig.");
        req.setKnopfText("Zahlungspflichtig beauftragen");

        mockMvc.perform(post("/api/internal/freigabe/uuid-recht/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vorzeitigerBeginn").value(true));

        ArgumentCaptor<FreigabeErklaerung> captor = ArgumentCaptor.forClass(FreigabeErklaerung.class);
        verify(freigabeService).akzeptiereMitCode(
                eq("uuid-recht"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any(), eq("123456"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new FreigabeErklaerung(true, "2026-10-13", "2026-10-13",
                "Ich beauftrage Angebot ANG-2026-0003 verbindlich und zahlungspflichtig.",
                "Zahlungspflichtig beauftragen"));
    }

    @Test
    void akzeptieren_ueberlangerKnopftext_gibt400() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setKnopfText("x".repeat(10_001));

        mockMvc.perform(post("/api/internal/freigabe/uuid-lang/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        verify(freigabeService, never()).akzeptiereMitCode(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void akzeptieren_ohneVorname_gibt400UndZeigtFeldnamen() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setVorname(null);
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='vorname')]").exists());
    }

    @Test
    void akzeptieren_zuKurzerNachname_gibt400() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setNachname("X");
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='nachname')]").exists());
    }

    @Test
    void akzeptieren_leererUnterzeichnerName_gibt400() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setUnterzeichnerName("");
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='unterzeichnerName')]").exists());
    }

    /**
     * Fachlicher Abbruch-Pfad: Der Service wirft {@link IllegalStateException},
     * wenn der Vorgang bereits angenommen, abgelaufen oder zurueckgezogen ist.
     * Der Kunde muss dafuer eine verstaendliche Meldung sehen und nicht einen
     * generischen Serverfehler — deshalb 410 Gone plus Klartext im Body.
     */
    @Test
    void akzeptieren_bereitsAngenommenerVorgang_gibt410MitKlartextMeldung() throws Exception {
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-zweitlink"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any(), eq("123456"), any()))
                .willThrow(new IllegalStateException("Angebot wurde bereits angenommen."));

        String json = objectMapper.writeValueAsString(validRequest());

        mockMvc.perform(post("/api/internal/freigabe/uuid-zweitlink/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Angebot wurde bereits angenommen."));
    }

    @Test
    void akzeptieren_unbekannteUuid_gibt404() throws Exception {
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-gibts-nicht"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any(), eq("123456"), any()))
                .willThrow(new IllegalArgumentException(DokumentFreigabeService.UNBEKANNTE_UUID_MESSAGE));

        String json = objectMapper.writeValueAsString(validRequest());

        mockMvc.perform(post("/api/internal/freigabe/uuid-gibts-nicht/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void akzeptieren_ohneBestaetigung_gibt400() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setBestaetigung(false);
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    private static FreigabeAkzeptierenRequest validRequest() {
        FreigabeAkzeptierenRequest req = new FreigabeAkzeptierenRequest();
        req.setEmail("max@mustermann.de");
        req.setVorname("Max");
        req.setNachname("Mustermann");
        req.setUnterzeichnerName("Max Mustermann");
        req.setBestaetigung(true);
        req.setClientIp("1.2.3.4");
        req.setUserAgent("Mozilla/5.0 Test");
        req.setCode("123456");
        return req;
    }

    // ============== Bestätigungscode anfordern ==============

    @Test
    void code_versendet_gibt200MitMaskierterAdresseUndWartezeit() throws Exception {
        given(freigabeCodeService.fordereCodeAn("uuid-code"))
                .willReturn(new FreigabeCodeService.CodeVersendet(
                        "m•••@example.de", LocalDateTime.of(2026, 10, 11, 14, 15), 30));

        mockMvc.perform(post("/api/internal/freigabe/uuid-code/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientIp\":\"1.2.3.4\",\"userAgent\":\"Test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.emailMaskiert").value("m•••@example.de"))
                .andExpect(jsonPath("$.gueltigBis").value("2026-10-11T14:15:00"))
                .andExpect(jsonPath("$.erneutAnfordernInSekunden").value(30));
    }

    @Test
    void code_ohneBody_funktioniertEbenfalls() throws Exception {
        given(freigabeCodeService.fordereCodeAn("uuid-ohne-body"))
                .willReturn(new FreigabeCodeService.CodeVersendet("m•••@example.de", null, 30));

        mockMvc.perform(post("/api/internal/freigabe/uuid-ohne-body/code"))
                .andExpect(status().isOk());
    }

    @Test
    void code_unbekannteUuid_gibt404() throws Exception {
        given(freigabeCodeService.fordereCodeAn("uuid-weg"))
                .willThrow(new IllegalArgumentException(DokumentFreigabeService.UNBEKANNTE_UUID_MESSAGE));

        mockMvc.perform(post("/api/internal/freigabe/uuid-weg/code"))
                .andExpect(status().isNotFound());
    }

    private void erwarteAnforderungsfehler(FreigabeCodeAnforderungException.Grund grund, Long sekunden,
                                           int status) throws Exception {
        given(freigabeCodeService.fordereCodeAn("uuid-fehler"))
                .willThrow(new FreigabeCodeAnforderungException(grund, "Meldung " + grund, sekunden));

        var ergebnis = mockMvc.perform(post("/api/internal/freigabe/uuid-fehler/code"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Meldung " + grund));
        if (sekunden == null) {
            ergebnis.andExpect(jsonPath("$.erneutAnfordernInSekunden").doesNotExist());
        } else {
            ergebnis.andExpect(jsonPath("$.erneutAnfordernInSekunden").value(sekunden));
        }
    }

    @Test
    void code_keineEmail_gibt409() throws Exception {
        erwarteAnforderungsfehler(FreigabeCodeAnforderungException.Grund.KEINE_EMAIL, null, 409);
    }

    @Test
    void code_nichtMehrOffen_gibt410() throws Exception {
        erwarteAnforderungsfehler(FreigabeCodeAnforderungException.Grund.NICHT_OFFEN, null, 410);
    }

    @Test
    void code_onlineGesperrt_gibt423() throws Exception {
        erwarteAnforderungsfehler(FreigabeCodeAnforderungException.Grund.ONLINE_GESPERRT, null, 423);
    }

    @Test
    void code_laeuftSchonFuerDiesenLink_gibt429MitWartezeit() throws Exception {
        given(freigabeCodeService.fordereCodeAn("uuid-doppelklick"))
                .willThrow(new org.springframework.dao.PessimisticLockingFailureException("NOWAIT"));

        mockMvc.perform(post("/api/internal/freigabe/uuid-doppelklick/code"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Ihr Code wird gerade verschickt. Bitte sehen Sie gleich in Ihr Postfach."))
                .andExpect(jsonPath("$.erneutAnfordernInSekunden").value(30));
    }

    @Test
    void code_zuFrueh_gibt429MitSekunden() throws Exception {
        erwarteAnforderungsfehler(FreigabeCodeAnforderungException.Grund.ZU_FRUEH, 17L, 429);
    }

    @Test
    void code_versandFehlgeschlagen_gibt502() throws Exception {
        erwarteAnforderungsfehler(FreigabeCodeAnforderungException.Grund.VERSAND_FEHLGESCHLAGEN, null, 502);
    }

    // ============== Annahme mit Code ==============

    @Test
    void akzeptieren_falscherCode_gibt422MitVerbleibendenVersuchen() throws Exception {
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-falsch"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any(), eq("123456"), any()))
                .willThrow(new FreigabeCodeException(FreigabeCodeException.Grund.FALSCH, "Der Code stimmt nicht.", 3));

        mockMvc.perform(post("/api/internal/freigabe/uuid-falsch/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Der Code stimmt nicht."))
                .andExpect(jsonPath("$.codeFehler").value("FALSCH"))
                .andExpect(jsonPath("$.verbleibendeVersuche").value(3));
    }

    @Test
    void akzeptieren_fuenfterFehler_meldetNeuenCodeMitAdresseUndWartezeit() throws Exception {
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-neu"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any(), eq("123456"), any()))
                .willThrow(new FreigabeCodeException(FreigabeCodeException.Grund.NEUER_CODE_VERSCHICKT,
                        "Neuer Code unterwegs.", null, 30L, "m•••@example.de"));

        mockMvc.perform(post("/api/internal/freigabe/uuid-neu/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeFehler").value("NEUER_CODE_VERSCHICKT"))
                .andExpect(jsonPath("$.erneutAnfordernInSekunden").value(30))
                .andExpect(jsonPath("$.emailMaskiert").value("m•••@example.de"));
    }

    @Test
    void akzeptieren_gesperrterCode_gibt422OhneVersuche() throws Exception {
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-gesperrt"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any(), eq("123456"), any()))
                .willThrow(new FreigabeCodeException(FreigabeCodeException.Grund.GESPERRT, "Gesperrt.", null));

        mockMvc.perform(post("/api/internal/freigabe/uuid-gesperrt/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeFehler").value("GESPERRT"))
                .andExpect(jsonPath("$.verbleibendeVersuche").isEmpty())
                .andExpect(jsonPath("$.erneutAnfordernInSekunden").doesNotExist())
                .andExpect(jsonPath("$.emailMaskiert").doesNotExist());
    }

    @Test
    void akzeptieren_ohneCode_gibt400OhneServiceaufruf() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setCode(null);

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(freigabeService);
    }

    @Test
    void akzeptieren_ueberlangerCode_gibt400OhneServiceaufruf() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setCode("1".repeat(10_001));

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(freigabeService);
    }

    @Test
    void akzeptieren_nameMitScriptUndSql_wirdUnveraendertAlsTextDurchgereicht() throws Exception {
        // XSS/SQL-Strings sind hier nur Text: Escaping macht die Mail, Persistenz läuft parametrisiert.
        FreigabeAkzeptierenRequest req = validRequest();
        req.setVorname("<script>alert(1)</script>");
        req.setNachname("'; DROP TABLE x; --");
        req.setUnterzeichnerName("<script>alert(1)</script> '; DROP TABLE x; --");
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-xss"), anyString(), anyString(), anyString(),
                eq("<script>alert(1)</script>"), eq("'; DROP TABLE x; --"), anyString(), any(), eq("123456"), any()))
                .willThrow(new FreigabeCodeException(FreigabeCodeException.Grund.FALSCH, "Der Code stimmt nicht.", 4));

        mockMvc.perform(post("/api/internal/freigabe/uuid-xss/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ============== Bestandsendpunkte: Ansicht, PDF, Bereinigung ==============

    private static DokumentFreigabe ansichtFreigabe(String uuid, FreigabeStatus status) {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setUuid(uuid);
        f.setStatus(status);
        f.setDokumentNummer("AN-2026-0001");
        f.setDokumentArt("Nachtrags Angebot");
        f.setAblaufDatum(LocalDateTime.now().plusDays(3));
        f.setDokumentDatei("angebot.pdf");
        return f;
    }

    @Test
    void ansicht_unbekannt_gibt404() throws Exception {
        given(freigabeService.findByUuidUndAktualisiereStatus("uuid-weg")).willReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/internal/freigabe/uuid-weg")).andExpect(status().isNotFound());
    }

    @Test
    void ansicht_mitPositionen_liefertBetraegeUndPdfPfad() throws Exception {
        DokumentFreigabe f = ansichtFreigabe("uuid-ansicht", FreigabeStatus.PENDING);
        given(freigabeService.findByUuidUndAktualisiereStatus("uuid-ansicht")).willReturn(java.util.Optional.of(f));
        given(freigabeService.ladePositionsAnsicht(f)).willReturn(new DokumentFreigabeService.FreigabePositionsAnsicht(
                List.of(), new java.math.BigDecimal("100.00"), new java.math.BigDecimal("119.00"),
                new java.math.BigDecimal("19.0"), true, null));

        mockMvc.perform(get("/api/internal/freigabe/uuid-ansicht"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.pdfPfad").value("/api/internal/freigabe/uuid-ansicht/pdf"))
                .andExpect(jsonPath("$.basisBrutto").value(119.00))
                .andExpect(jsonPath("$.hatAlternativen").value(true));
    }

    @Test
    void ansicht_ohnePositionen_bleibtSchlank() throws Exception {
        DokumentFreigabe f = ansichtFreigabe("uuid-alt", FreigabeStatus.ACCEPTED);
        given(freigabeService.findByUuidUndAktualisiereStatus("uuid-alt")).willReturn(java.util.Optional.of(f));
        given(freigabeService.ladePositionsAnsicht(f)).willReturn(null);

        mockMvc.perform(get("/api/internal/freigabe/uuid-alt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.positionen").doesNotExist());
    }

    @Test
    void pdf_unbekannt_gibt404() throws Exception {
        given(freigabeService.findByUuidUndAktualisiereStatus("uuid-weg")).willReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/internal/freigabe/uuid-weg/pdf")).andExpect(status().isNotFound());
    }

    @Test
    void pdf_abgelaufenOderZurueckgezogen_gibt410() throws Exception {
        for (FreigabeStatus s : List.of(FreigabeStatus.EXPIRED, FreigabeStatus.REVOKED)) {
            given(freigabeService.findByUuidUndAktualisiereStatus("uuid-" + s))
                    .willReturn(java.util.Optional.of(ansichtFreigabe("uuid-" + s, s)));

            mockMvc.perform(get("/api/internal/freigabe/uuid-" + s + "/pdf")).andExpect(status().isGone());
        }
    }

    @Test
    void pdf_ohneDatei_gibt404() throws Exception {
        for (String datei : java.util.Arrays.asList(null, "  ")) {
            DokumentFreigabe f = ansichtFreigabe("uuid-ohne-datei", FreigabeStatus.PENDING);
            f.setDokumentDatei(datei);
            given(freigabeService.findByUuidUndAktualisiereStatus("uuid-ohne-datei")).willReturn(java.util.Optional.of(f));

            mockMvc.perform(get("/api/internal/freigabe/uuid-ohne-datei/pdf")).andExpect(status().isNotFound());
        }
    }

    @Test
    void pdf_vorhanden_liefertPdfMitSprechendemNamen() throws Exception {
        DokumentFreigabe f = ansichtFreigabe("uuid-pdf", FreigabeStatus.ACCEPTED);
        given(freigabeService.findByUuidUndAktualisiereStatus("uuid-pdf")).willReturn(java.util.Optional.of(f));
        given(dateiSpeicherService.ladeDokumentAlsResource("angebot.pdf"))
                .willReturn(new org.springframework.core.io.ByteArrayResource("%PDF-1.4".getBytes()));

        mockMvc.perform(get("/api/internal/freigabe/uuid-pdf/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"NachtragsAngebot_AN-2026-0001.pdf\""));
    }

    @Test
    void pdf_ohneArtUndNummer_nutztPlatzhalterImNamen() throws Exception {
        DokumentFreigabe f = ansichtFreigabe("uuid-pdf-leer", FreigabeStatus.PENDING);
        f.setDokumentArt(null);
        f.setDokumentNummer(null);
        given(freigabeService.findByUuidUndAktualisiereStatus("uuid-pdf-leer")).willReturn(java.util.Optional.of(f));
        given(dateiSpeicherService.ladeDokumentAlsResource("angebot.pdf"))
                .willReturn(new org.springframework.core.io.ByteArrayResource("%PDF-1.4".getBytes()));

        mockMvc.perform(get("/api/internal/freigabe/uuid-pdf-leer/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"Dokument_uuid-pdf-leer.pdf\""));
    }

    @Test
    void pdf_dateiNichtLadbar_gibt404() throws Exception {
        DokumentFreigabe f = ansichtFreigabe("uuid-pdf-kaputt", FreigabeStatus.PENDING);
        given(freigabeService.findByUuidUndAktualisiereStatus("uuid-pdf-kaputt")).willReturn(java.util.Optional.of(f));
        given(dateiSpeicherService.ladeDokumentAlsResource("angebot.pdf")).willThrow(new IllegalStateException("weg"));

        mockMvc.perform(get("/api/internal/freigabe/uuid-pdf-kaputt/pdf")).andExpect(status().isNotFound());
    }

    @Test
    void pdfLoeschen_abgelaufeneFreigabe_gibt204() throws Exception {
        given(freigabeService.entfernePdfAusFreigabe("uuid-x"))
                .willReturn(DokumentFreigabeService.PdfEntfernenErgebnis.ENTFERNT);

        mockMvc.perform(delete("/api/internal/freigabe/uuid-x/pdf")).andExpect(status().isNoContent());
        verify(freigabeService).entfernePdfAusFreigabe("uuid-x");
    }

    @Test
    void pdfLoeschen_offeneOderAngenommeneFreigabe_gibt409() throws Exception {
        given(freigabeService.entfernePdfAusFreigabe("uuid-offen"))
                .willReturn(DokumentFreigabeService.PdfEntfernenErgebnis.NOCH_GUELTIG);

        mockMvc.perform(delete("/api/internal/freigabe/uuid-offen/pdf")).andExpect(status().isConflict());
    }

    @Test
    void pdfLoeschen_unbekannteUuid_gibt404() throws Exception {
        given(freigabeService.entfernePdfAusFreigabe("unbekannt"))
                .willReturn(DokumentFreigabeService.PdfEntfernenErgebnis.UNBEKANNT);

        mockMvc.perform(delete("/api/internal/freigabe/unbekannt/pdf")).andExpect(status().isNotFound());
    }

    @Test
    void akzeptieren_serviceValidierung_gibt400MitMeldung() throws Exception {
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-name"), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), eq("123456"), any()))
                .willThrow(new IllegalArgumentException("Vor- und Nachname sind für die digitale Annahme erforderlich."));

        mockMvc.perform(post("/api/internal/freigabe/uuid-name/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vor- und Nachname sind für die digitale Annahme erforderlich."));
    }

    @Test
    void akzeptieren_serviceValidierungOhneMeldung_gibt400MitStandardtext() throws Exception {
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-leer"), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), eq("123456"), any()))
                .willThrow(new IllegalArgumentException());

        mockMvc.perform(post("/api/internal/freigabe/uuid-leer/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ungültige Eingabe."));
    }

    @Test
    void akzeptieren_kuerztUserAgentUndAkzeptiertFehlenden() throws Exception {
        DokumentFreigabe saved = ansichtFreigabe("uuid-ua", FreigabeStatus.ACCEPTED);
        given(freigabeService.akzeptiereMitCode(
                eq("uuid-ua"), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(saved);

        FreigabeAkzeptierenRequest lang = validRequest();
        lang.setUserAgent("x".repeat(600));
        mockMvc.perform(post("/api/internal/freigabe/uuid-ua/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lang)))
                .andExpect(status().isOk());
        verify(freigabeService).akzeptiereMitCode(eq("uuid-ua"), any(), eq("x".repeat(500)),
                any(), any(), any(), any(), any(), any(), any());

        FreigabeAkzeptierenRequest ohne = validRequest();
        ohne.setUserAgent(null);
        mockMvc.perform(post("/api/internal/freigabe/uuid-ua/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ohne)))
                .andExpect(status().isOk());
        verify(freigabeService).akzeptiereMitCode(eq("uuid-ua"), any(), org.mockito.ArgumentMatchers.isNull(),
                any(), any(), any(), any(), any(), any(), any());
    }
}
