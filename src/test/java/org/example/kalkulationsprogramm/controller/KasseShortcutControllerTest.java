package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.KasseSaldoService;
import org.example.kalkulationsprogramm.service.KasseShortcutService;
import org.example.kalkulationsprogramm.service.KasseUnterdeckungException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests fuer den KasseShortcutController. Pruft Happy-Path,
 * Berechtigung, 409-Unterdeckung und Pflichtfeld-Validierung.
 *
 * DSGVO-Dummy: Empfaenger ist "Diana Mustermann".
 */
@WebMvcTest(KasseShortcutController.class)
@AutoConfigureMockMvc(addFilters = false)
class KasseShortcutControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private BelegService belegService;
    @MockBean private KasseShortcutService kasseShortcutService;
    @MockBean private KasseSaldoService kasseSaldoService;
    @MockBean private KasseEinstellungRepository kasseEinstellungRepository;
    @MockBean private SachkontoRepository sachkontoRepository;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private Mitarbeiter caller() {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(42L);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        return m;
    }

    private void mockAuth(boolean darfScannen, boolean darfSehen) {
        Mitarbeiter c = caller();
        given(belegService.findCaller(any(), any())).willReturn(c);
        given(belegService.darfScannen(c)).willReturn(darfScannen);
        given(belegService.darfSehen(c)).willReturn(darfSehen);
    }

    private Beleg dummyBeleg(BelegKategorie kategorie) {
        Beleg b = new Beleg();
        b.setId(1L);
        b.setBelegKategorie(kategorie);
        b.setBetragBrutto(new BigDecimal("100.00"));
        b.setStatus(BelegStatus.VALIDIERT);
        return b;
    }

    @Test
    @DisplayName("POST /bank-abhebung Happy-Path -> 200")
    void bankAbhebung_happyPath() throws Exception {
        mockAuth(true, true);
        given(kasseShortcutService.bankAbhebung(any(), any(), any(), any(), any()))
                .willReturn(dummyBeleg(BelegKategorie.KASSE_EINNAHME));
        given(belegService.toDto(any(Beleg.class)))
                .willReturn(BelegDto.Response.builder().id(1L).build());

        String body = "{\"betrag\":500.00,\"datum\":\"2026-05-13\",\"belegNr\":\"EC-1\",\"beschreibung\":\"x\"}";
        mockMvc.perform(post("/api/buchhaltung/kasse/bank-abhebung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /bank-abhebung mit Betrag=0 -> 400")
    void bankAbhebung_betragNull_400() throws Exception {
        mockAuth(true, true);
        given(kasseShortcutService.bankAbhebung(any(), any(), any(), any(), any()))
                .willThrow(new IllegalArgumentException("Betrag fehlt oder ist nicht positiv"));

        String body = "{\"betrag\":0,\"datum\":\"2026-05-13\",\"belegNr\":\"EC-1\",\"beschreibung\":\"x\"}";
        mockMvc.perform(post("/api/buchhaltung/kasse/bank-abhebung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /privatentnahme mit Unterdeckung -> 409 + Body mit projizierterSaldo + mindestbestand")
    void privatentnahme_unterdeckung_409() throws Exception {
        mockAuth(true, true);
        given(kasseShortcutService.privatEntnahme(any(), any(), any(), any()))
                .willThrow(new KasseUnterdeckungException(
                        new BigDecimal("-50.00"), new BigDecimal("0.00")));

        String body = "{\"betrag\":500.00,\"datum\":\"2026-05-13\",\"beschreibung\":\"x\"}";
        mockMvc.perform(post("/api/buchhaltung/kasse/privatentnahme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.projizierterSaldo").value(-50.00))
                .andExpect(jsonPath("$.mindestbestand").value(0.00));
    }

    @Test
    @DisplayName("GET /saldo ohne Permission -> 403")
    void getSaldo_ohneSehen_403() throws Exception {
        mockAuth(false, false);

        mockMvc.perform(get("/api/buchhaltung/kasse/saldo"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /saldo mit Permission -> 200 + Werte")
    void getSaldo_ok() throws Exception {
        mockAuth(false, true);
        given(kasseSaldoService.berechneAktuellenSaldo()).willReturn(new BigDecimal("250.00"));
        given(kasseSaldoService.getMindestbestand()).willReturn(new BigDecimal("50.00"));

        mockMvc.perform(get("/api/buchhaltung/kasse/saldo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(250.00))
                .andExpect(jsonPath("$.mindestbestand").value(50.00));
    }

    @Test
    @DisplayName("PUT /einstellung mit aktiver Automatik aber ohne Pflichtfelder -> 400")
    void putEinstellung_aktivOhnePflichtfelder_400() throws Exception {
        mockAuth(true, true);
        KasseEinstellung k = new KasseEinstellung();
        k.setId(1L);
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));
        given(kasseEinstellungRepository.save(any(KasseEinstellung.class)))
                .willAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "mindestbestand", 0,
                "ehegattengehaltAktiv", true);
        mockMvc.perform(put("/api/buchhaltung/kasse/einstellung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // ===================== Security-Vektoren (TESTING_SECURITY.md) =====================
    //
    // Wir pruefen hier, dass die neuen Endpoints typischen Angriffstexten
    // nicht naiv vertrauen: SQL-Injection-Strings, XSS-Strings und ungueltige
    // numerische IDs duerfen NICHT zu 5xx fuehren, sondern entweder als
    // gewoehnlicher Text gespeichert werden (Service-Mock haelt das ab) oder
    // mit 400/4xx abgewiesen werden. Wichtiger Punkt: kein Stack-Trace-Leak.

    @Test
    @DisplayName("POST /bank-abhebung mit SQL-Injection-String in beschreibung -> 200 (wird nur als Text gespeichert)")
    void bankAbhebung_sqlInjectionString_200() throws Exception {
        mockAuth(true, true);
        given(kasseShortcutService.bankAbhebung(any(), any(), any(), any(), any()))
                .willReturn(dummyBeleg(BelegKategorie.KASSE_EINNAHME));
        given(belegService.toDto(any(Beleg.class)))
                .willReturn(BelegDto.Response.builder().id(1L).build());

        String body = "{\"betrag\":500.00,\"datum\":\"2026-05-13\",\"belegNr\":\"EC-1\","
                + "\"beschreibung\":\"'; DROP TABLE beleg; --\"}";
        mockMvc.perform(post("/api/buchhaltung/kasse/bank-abhebung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /privatentnahme mit XSS-String in beschreibung -> nicht ausgefuehrt, kein 5xx")
    void privatentnahme_xssString_kein5xx() throws Exception {
        mockAuth(true, true);
        given(kasseShortcutService.privatEntnahme(any(), any(), any(), any()))
                .willReturn(dummyBeleg(BelegKategorie.PRIVATENTNAHME));
        given(belegService.toDto(any(Beleg.class)))
                .willReturn(BelegDto.Response.builder().id(1L).build());

        String body = "{\"betrag\":50.00,\"datum\":\"2026-05-13\","
                + "\"beschreibung\":\"<script>alert('xss')</script>\"}";
        mockMvc.perform(post("/api/buchhaltung/kasse/privatentnahme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("POST /lohn-zahlung wenn Sachkonto 4120 fehlt -> 404, kein 5xx")
    void lohnZahlung_kontoFehlt_404() throws Exception {
        mockAuth(true, true);
        given(sachkontoRepository.findByNummer("4120")).willReturn(Optional.empty());

        String body = "{\"betrag\":500.00,\"datum\":\"2026-05-13\","
                + "\"empfaengerName\":\"Diana Mustermann\"}";
        mockMvc.perform(post("/api/buchhaltung/kasse/lohn-zahlung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /bank-abhebung mit negativem Betrag -> 400")
    void bankAbhebung_negativ_400() throws Exception {
        mockAuth(true, true);
        given(kasseShortcutService.bankAbhebung(any(), any(), any(), any(), any()))
                .willThrow(new IllegalArgumentException("Betrag fehlt oder ist nicht positiv"));

        String body = "{\"betrag\":-1.00,\"datum\":\"2026-05-13\",\"belegNr\":\"EC-1\",\"beschreibung\":\"x\"}";
        mockMvc.perform(post("/api/buchhaltung/kasse/bank-abhebung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /privateinlage ohne Auth -> 403")
    void privateinlage_ohneAuth_403() throws Exception {
        mockAuth(false, false);

        String body = "{\"betrag\":50.00,\"datum\":\"2026-05-13\",\"beschreibung\":\"x\"}";
        mockMvc.perform(post("/api/buchhaltung/kasse/privateinlage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /einstellung mit aktiver Automatik + allen Pflichtfeldern -> 200")
    void putEinstellung_aktivVollstaendig_200() throws Exception {
        mockAuth(true, true);
        KasseEinstellung k = new KasseEinstellung();
        k.setId(1L);
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));
        given(kasseEinstellungRepository.save(any(KasseEinstellung.class)))
                .willAnswer(inv -> inv.getArgument(0));
        Sachkonto lohn = new Sachkonto();
        lohn.setId(42L);
        lohn.setNummer("4120");
        lohn.setBezeichnung("Loehne & Gehaelter");
        Sachkonto einlage = new Sachkonto();
        einlage.setId(11L);
        einlage.setBezeichnung("Privateinlage-Konto");
        given(sachkontoRepository.findByNummer("4120")).willReturn(Optional.of(lohn));
        given(sachkontoRepository.findById(11L)).willReturn(Optional.of(einlage));

        // Body OHNE sachkontoId/kostenstelleId — wird hart auf 4120 gebucht,
        // keine Kostenstelle.
        String body = "{\"mindestbestand\":50,\"ehegattengehaltAktiv\":true,"
                + "\"ehegattengehaltBetrag\":500.00,\"ehegattengehaltTag\":1,"
                + "\"privateinlageSachkontoId\":11,"
                + "\"ehegattengehaltEmpfaengerName\":\"Diana Mustermann\"}";
        mockMvc.perform(put("/api/buchhaltung/kasse/einstellung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ehegattengehaltAktiv").value(true))
                .andExpect(jsonPath("$.ehegattengehaltEmpfaengerName").value("Diana Mustermann"));
    }
}
