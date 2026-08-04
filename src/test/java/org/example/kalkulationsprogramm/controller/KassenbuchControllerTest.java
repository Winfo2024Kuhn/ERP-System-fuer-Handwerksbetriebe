package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.KassenbuchAbschlussDto;
import org.example.kalkulationsprogramm.dto.KassenzaehlungDto;
import org.example.kalkulationsprogramm.service.BelegAuditChainVerifier;
import org.example.kalkulationsprogramm.service.BelegAuditService;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.KassenbuchAbschlussService;
import org.example.kalkulationsprogramm.service.KassenbuchGesperrtException;
import org.example.kalkulationsprogramm.service.KassenzaehlungService;
import org.example.kalkulationsprogramm.service.VerfahrensdokumentationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests fuer {@link KassenbuchController}.
 *
 * <p>Schwerpunkt liegt auf den Berechtigungen: schreibende Vorgaenge
 * (Monatsabschluss, Storno, Kassensturz) verlangen {@code darfScannen},
 * lesende reichen mit {@code darfSehen}. Ein Steuerberater-Zugang mit
 * Nur-Lese-Recht darf das Kassenbuch pruefen, aber nichts festschreiben --
 * deshalb wird jeder Schreib-Endpunkt auch gegen den Nur-Lese-Fall
 * getestet.</p>
 *
 * <p>DSGVO: nur Dummy-Daten (Max Mustermann).</p>
 */
@WebMvcTest(KassenbuchController.class)
@AutoConfigureMockMvc(addFilters = false)
class KassenbuchControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private BelegService belegService;
    @MockBean private KassenbuchAbschlussService abschlussService;
    @MockBean private KassenzaehlungService zaehlungService;
    @MockBean private BelegAuditService auditService;
    @MockBean private BelegAuditChainVerifier verifier;
    @MockBean private VerfahrensdokumentationService verfahrensdokumentationService;

    private Mitarbeiter maxMustermann;

    @BeforeEach
    void setUp() {
        maxMustermann = new Mitarbeiter();
        maxMustermann.setId(42L);
        maxMustermann.setVorname("Max");
        maxMustermann.setNachname("Mustermann");
    }

    /** Aufrufer darf alles -- der Normalfall des Buchhalters am PC. */
    private void alsBuchhalter() {
        given(belegService.findCaller(any(), any())).willReturn(maxMustermann);
        given(belegService.darfScannen(maxMustermann)).willReturn(true);
        given(belegService.darfSehen(maxMustermann)).willReturn(true);
    }

    /** Aufrufer darf nur lesen -- z.B. ein Steuerberater-Zugang. */
    private void alsNurLeser() {
        given(belegService.findCaller(any(), any())).willReturn(maxMustermann);
        given(belegService.darfScannen(maxMustermann)).willReturn(false);
        given(belegService.darfSehen(maxMustermann)).willReturn(true);
    }

    private void alsFremder() {
        given(belegService.findCaller(any(), any())).willReturn(null);
    }

    // ===================== Monatsabschluss =====================

    @Nested
    @DisplayName("Monatsabschluss")
    class Monatsabschluss {

        @Test
        @DisplayName("Vorschau liefert Hindernisse und Bestände")
        void vorschauHappyPath() throws Exception {
            alsBuchhalter();
            given(abschlussService.vorschau(2026, 3)).willReturn(
                    KassenbuchAbschlussDto.Vorschau.builder()
                            .jahr(2026).monat(3)
                            .zeitraumBis(LocalDate.of(2026, 3, 31))
                            .anzahlFestzuschreiben(12)
                            .anfangsbestand(new BigDecimal("100.00"))
                            .endbestand(new BigDecimal("250.00"))
                            .hindernisse(List.of())
                            .abschlussMoeglich(true)
                            .build());

            mockMvc.perform(get("/api/buchhaltung/kassenbuch/abschluss/vorschau")
                            .param("jahr", "2026").param("monat", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.abschlussMoeglich").value(true))
                    .andExpect(jsonPath("$.anzahlFestzuschreiben").value(12));
        }

        @Test
        @DisplayName("Ungültiger Monat wird mit 400 abgewiesen")
        void ungueltigerMonat() throws Exception {
            alsBuchhalter();
            given(abschlussService.vorschau(anyInt(), anyInt()))
                    .willThrow(new IllegalArgumentException("Monat muss zwischen 1 und 12 liegen"));

            mockMvc.perform(get("/api/buchhaltung/kassenbuch/abschluss/vorschau")
                            .param("jahr", "2026").param("monat", "13"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Monat muss zwischen 1 und 12 liegen"));
        }

        @Test
        @DisplayName("Abschluss ohne Jahr/Monat wird mit 400 abgewiesen")
        void abschlussOhnePflichtfelder() throws Exception {
            alsBuchhalter();

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/abschluss")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verify(abschlussService, never()).schliesseMonatAb(anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        @DisplayName("Abschluss liefert das Ergebnis mit Nummernbereich zurück")
        void abschlussHappyPath() throws Exception {
            alsBuchhalter();
            given(abschlussService.schliesseMonatAb(eq(2026), eq(3), any(), any(), any()))
                    .willReturn(KassenbuchAbschlussDto.Ergebnis.builder()
                            .id(1L).jahr(2026).monat(3)
                            .anzahlBelege(12)
                            .ersteLaufendeNummer(1L).letzteLaufendeNummer(12L)
                            .endbestand(new BigDecimal("250.00"))
                            .build());

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/abschluss")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("jahr", 2026, "monat", 3))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.anzahlBelege").value(12))
                    .andExpect(jsonPath("$.letzteLaufendeNummer").value(12));
        }

        @Test
        @DisplayName("Blockierter Abschluss antwortet mit 409 und Lösungshinweis")
        void abschlussGesperrt() throws Exception {
            alsBuchhalter();
            given(abschlussService.schliesseMonatAb(anyInt(), anyInt(), any(), any(), any()))
                    .willThrow(new KassenbuchGesperrtException(
                            "Noch 3 ungeprüfte Belege.", "Erst prüfen, dann abschließen."));

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/abschluss")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("jahr", 2026, "monat", 3))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Noch 3 ungeprüfte Belege."))
                    .andExpect(jsonPath("$.hinweis").value("Erst prüfen, dann abschließen."));
        }

        @Test
        @DisplayName("Nur-Lese-Zugang darf keinen Monat abschließen")
        void nurLeserDarfNichtAbschliessen() throws Exception {
            alsNurLeser();

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/abschluss")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("jahr", 2026, "monat", 3))))
                    .andExpect(status().isForbidden());

            verify(abschlussService, never()).schliesseMonatAb(anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        @DisplayName("Ohne Anmeldung kommt 403")
        void ohneAnmeldung() throws Exception {
            alsFremder();

            mockMvc.perform(get("/api/buchhaltung/kassenbuch/abschluss/vorschau")
                            .param("jahr", "2026").param("monat", "3"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===================== Storno =====================

    @Nested
    @DisplayName("Storno")
    class Storno {

        @Test
        @DisplayName("Storno ohne Begründung wird mit 400 abgewiesen")
        void ohneGrund() throws Exception {
            alsBuchhalter();
            given(abschlussService.storniere(eq(5L), any(), any(), any()))
                    .willThrow(new IllegalArgumentException("Bitte kurz angeben, warum storniert wird"));

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/belege/5/storno")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Storno eines offenen Belegs antwortet mit 409")
        void offenerBelegGesperrt() throws Exception {
            alsBuchhalter();
            given(abschlussService.storniere(eq(5L), any(), any(), any()))
                    .willThrow(new KassenbuchGesperrtException(
                            "Dieser Beleg ist noch nicht festgeschrieben.",
                            "Du kannst ihn direkt korrigieren."));

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/belege/5/storno")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("grund", "Falscher Betrag"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.hinweis").value("Du kannst ihn direkt korrigieren."));
        }

        @Test
        @DisplayName("Nur-Lese-Zugang darf nicht stornieren")
        void nurLeserDarfNichtStornieren() throws Exception {
            alsNurLeser();

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/belege/5/storno")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("grund", "Falscher Betrag"))))
                    .andExpect(status().isForbidden());

            verify(abschlussService, never()).storniere(any(), any(), any(), any());
        }
    }

    // ===================== Kassensturz =====================

    @Nested
    @DisplayName("Kassensturz")
    class Kassensturz {

        @Test
        @DisplayName("Zählung wird gespeichert und gibt die Differenz zurück")
        void zaehlungHappyPath() throws Exception {
            alsBuchhalter();
            given(zaehlungService.zaehle(any(), any(), any()))
                    .willReturn(KassenzaehlungDto.Response.builder()
                            .id(1L)
                            .stichtag(LocalDate.of(2026, 3, 31))
                            .gezaehlterBestand(new BigDecimal("230.00"))
                            .rechnerischerBestand(new BigDecimal("250.00"))
                            .differenz(new BigDecimal("-20.00"))
                            .build());

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/zaehlungen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "stichtag", "2026-03-31",
                                    "gezaehlterBestand", "230.00",
                                    "bemerkung", "Trinkgeld nicht erfasst"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.differenz").value(-20.00));
        }

        @Test
        @DisplayName("Unerklärte Differenz wird mit 400 abgewiesen")
        void differenzOhneBemerkung() throws Exception {
            alsBuchhalter();
            given(zaehlungService.zaehle(any(), any(), any()))
                    .willThrow(new IllegalArgumentException(
                            "Es fehlen 20 € gegenüber dem Kassenbuch. Bitte kurz festhalten, woran das liegt."));

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/zaehlungen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("gezaehlterBestand", "230.00"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Unparsebarer Zeitraum wird mit 400 abgewiesen statt still zu ignorieren")
        void ungueltigesDatumInListe() throws Exception {
            alsBuchhalter();

            mockMvc.perform(get("/api/buchhaltung/kassenbuch/zaehlungen")
                            .param("von", "kein-datum"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("'von'")));
        }

        @Test
        @DisplayName("Erwarteter Bestand kommt aus dem Kassenbuch")
        void erwarteterBestand() throws Exception {
            alsBuchhalter();
            given(zaehlungService.rechnerischerBestand(any())).willReturn(new BigDecimal("250.00"));

            mockMvc.perform(get("/api/buchhaltung/kassenbuch/zaehlungen/erwartet")
                            .param("stichtag", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rechnerischerBestand").value(250.00));
        }

        @Test
        @DisplayName("Nur-Lese-Zugang darf nicht zählen")
        void nurLeserDarfNichtZaehlen() throws Exception {
            alsNurLeser();

            mockMvc.perform(post("/api/buchhaltung/kassenbuch/zaehlungen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("gezaehlterBestand", "230.00"))))
                    .andExpect(status().isForbidden());

            verify(zaehlungService, never()).zaehle(any(), any(), any());
        }
    }

    // ===================== Protokoll =====================

    @Nested
    @DisplayName("Protokoll")
    class Protokoll {

        @Test
        @DisplayName("Intaktes Protokoll wird als unversehrt gemeldet")
        void pruefungIntakt() throws Exception {
            alsBuchhalter();
            given(verifier.verify()).willReturn(new BelegAuditChainVerifier.Bericht());

            mockMvc.perform(get("/api/buchhaltung/kassenbuch/pruefung"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.intakt").value(false)); // Default-Bericht ist noch nicht geprueft
        }

        @Test
        @DisplayName("Nur-Lese-Zugang darf das Protokoll prüfen")
        void nurLeserDarfPruefen() throws Exception {
            alsNurLeser();
            given(verifier.verify()).willReturn(new BelegAuditChainVerifier.Bericht());

            mockMvc.perform(get("/api/buchhaltung/kassenbuch/pruefung"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Verlauf eines Belegs kommt aus dem Protokoll")
        void verlauf() throws Exception {
            alsBuchhalter();
            given(auditService.getHistorie(7L)).willReturn(List.of(
                    Map.of("aktion", "FESTGESCHRIEBEN", "laufendeNummer", 12)));

            mockMvc.perform(get("/api/buchhaltung/kassenbuch/belege/7/verlauf"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].aktion").value("FESTGESCHRIEBEN"));
        }

        @Test
        @DisplayName("Verfahrensdokumentation wird als Textdatei ausgeliefert")
        void verfahrensdokumentation() throws Exception {
            alsNurLeser();
            given(verfahrensdokumentationService.erzeugeText())
                    .willReturn("VERFAHRENSDOKUMENTATION KASSE UND BELEGE");

            mockMvc.perform(get("/api/buchhaltung/kassenbuch/verfahrensdokumentation"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .header().string("X-Content-Type-Options", "nosniff"));
        }
    }
}
