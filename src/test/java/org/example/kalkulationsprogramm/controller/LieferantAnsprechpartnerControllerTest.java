package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.LieferantAnsprechpartner;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.LieferantAnsprechpartnerRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.lieferantansprechpartner.LieferantAnsprechpartnerService;
import org.example.kalkulationsprogramm.service.telefon.RufnummernZuordnungService;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ansprechpartner eines Lieferanten über die echte {@link SecurityConfig} und den echten
 * {@link LieferantAnsprechpartnerService}: Anmeldung, CSRF, Handy-Token, ungültige IDs,
 * fremde Lieferanten, Pflichtfelder, Überlänge, SQL- und HTML-Texte.
 */
@WebMvcTest(LieferantAnsprechpartnerController.class)
@Import({ SecurityConfig.class, LieferantAnsprechpartnerService.class,
        LieferantAnsprechpartnerControllerTest.EchteFilterBeans.class })
class LieferantAnsprechpartnerControllerTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    private static final String BASIS = "/api/lieferanten/7/ansprechpartner";
    private static final String TOKEN = "12345678-1234-4234-8234-123456789abc";
    private static final AtomicInteger NAECHSTE_IP = new AtomicInteger();

    @Autowired MockMvc mvc;

    @MockitoBean LieferantAnsprechpartnerRepository repository;
    @MockitoBean LieferantenRepository lieferantenRepository;
    @MockitoBean RufnummernZuordnungService zuordnung;
    @MockitoBean MitarbeiterRepository mitarbeiterRepository;
    @MockitoBean FrontendUserDetailsService frontendUserDetailsService;

    private Lieferanten lieferant;

    @BeforeEach
    void setUp() {
        SitzungsRechteImTest.rechteUnveraendert(frontendUserDetailsService);
        lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Muster Stahl KG");
        given(lieferantenRepository.existsById(7L)).willReturn(true);
        given(lieferantenRepository.findById(7L)).willReturn(Optional.of(lieferant));
        given(repository.save(any())).willAnswer(a -> {
            LieferantAnsprechpartner p = a.getArgument(0);
            if (p.getId() == null) {
                p.setId(11L);
            }
            return p;
        });
    }

    private static RequestPostProcessor buero() {
        return user(new FrontendUserPrincipal(1L, "max.mustermann", "Max Mustermann", "hash", true,
                Set.of(FrontendUserRole.USER)));
    }

    private static RequestPostProcessor handyToken() {
        int n = NAECHSTE_IP.incrementAndGet();
        return request -> {
            request.setRemoteAddr("127.1." + (n / 250) + "." + (n % 250 + 1));
            request.setParameter("token", TOKEN);
            return request;
        };
    }

    private LieferantAnsprechpartner erika() {
        LieferantAnsprechpartner a = new LieferantAnsprechpartner();
        a.setId(3L);
        a.setLieferant(lieferant);
        a.setVorname("Erika");
        a.setNachname("Musterfrau");
        a.setAufgabe("Innendienst");
        return a;
    }

    @Test
    @DisplayName("Ohne Anmeldung → 401, ohne CSRF-Token → 403")
    void anmeldungUndCsrf() throws Exception {
        mvc.perform(get(BASIS)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"nachname\":\"Mustermann\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASIS).with(buero()).contentType(MediaType.APPLICATION_JSON).content("{\"nachname\":\"Mustermann\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(BASIS + "/3").with(buero()).contentType(MediaType.APPLICATION_JSON).content("{\"nachname\":\"Mustermann\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASIS + "/3").with(buero())).andExpect(status().isForbidden());
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("Handy-App-Token darf die Ansprechpartner-Pflege nicht aufrufen")
    void handyGesperrt() throws Exception {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(5L);
        m.setAktiv(true);
        given(mitarbeiterRepository.findByLoginTokenAndAktivTrue(TOKEN)).willReturn(Optional.of(m));

        mvc.perform(get(BASIS).with(handyToken())).andExpect(status().isForbidden());
        mvc.perform(post(BASIS).with(handyToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nachname\":\"Mustermann\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Liste liefert die DTO-Felder")
    void liste() throws Exception {
        given(repository.findeFuerLieferant(eq(7L), any())).willReturn(List.of(erika()));

        mvc.perform(get(BASIS).with(buero()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].vorname").value("Erika"))
                .andExpect(jsonPath("$[0].nachname").value("Musterfrau"))
                .andExpect(jsonPath("$[0].aufgabe").value("Innendienst"))
                .andExpect(jsonPath("$[0].anrede").doesNotExist());
    }

    @Test
    @DisplayName("Anlegen → 201 mit DTO; unbekannte Felder (altes „vertreter“) stören nicht")
    void anlegen() throws Exception {
        mvc.perform(post(BASIS).with(buero()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anrede\":\"FRAU\",\"vorname\":\" Erika \",\"nachname\":\"Musterfrau\","
                                + "\"email\":\"\",\"vertreter\":\"alt\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.anrede").value("FRAU"))
                .andExpect(jsonPath("$.vorname").value("Erika"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    @DisplayName("Ändern → 200, Löschen → 204")
    void aendernUndLoeschen() throws Exception {
        LieferantAnsprechpartner erika = erika();
        given(repository.findByIdAndLieferantId(3L, 7L)).willReturn(Optional.of(erika));

        mvc.perform(put(BASIS + "/3").with(buero()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nachname\":\"Musterfrau\",\"aufgabe\":\"Buchhaltung\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aufgabe").value("Buchhaltung"))
                .andExpect(jsonPath("$.vorname").doesNotExist());
        mvc.perform(delete(BASIS + "/3").with(buero()).with(csrf())).andExpect(status().isNoContent());
        verify(repository).delete(erika);
    }

    @Test
    @DisplayName("Fremder oder unbekannter Lieferant / Ansprechpartner → 404 mit Meldung")
    void nichtGefunden() throws Exception {
        given(lieferantenRepository.existsById(8L)).willReturn(true);
        given(repository.findByIdAndLieferantId(3L, 8L)).willReturn(Optional.empty());

        mvc.perform(get("/api/lieferanten/" + Long.MAX_VALUE + "/ansprechpartner").with(buero()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Lieferant nicht gefunden."));
        mvc.perform(put("/api/lieferanten/8/ansprechpartner/3").with(buero()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nachname\":\"Mustermann\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ansprechpartner nicht gefunden."));
        mvc.perform(delete("/api/lieferanten/8/ansprechpartner/3").with(buero()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1" })
    @DisplayName("Ungültige IDs → 400, ohne die Datenbank zu fragen")
    void ungueltigeIds(String id) throws Exception {
        mvc.perform(get("/api/lieferanten/" + id + "/ansprechpartner").with(buero())).andExpect(status().isBadRequest());
        mvc.perform(delete(BASIS + "/" + id).with(buero()).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/lieferanten/abc/ansprechpartner").with(buero())).andExpect(status().isBadRequest());
        verify(repository, never()).delete(any());
        verify(repository, never()).findByIdAndLieferantId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Leerer Nachname, kaputte E-Mail und Überlänge → 400 mit Handwerker-Satz")
    void eingabefehler() throws Exception {
        mvc.perform(post(BASIS).with(buero()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nachname\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte einen Nachnamen eintragen."));
        mvc.perform(post(BASIS).with(buero()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nachname\":\"Mustermann\",\"email\":\"kein-at\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Die E-Mail-Adresse ist ungültig."));
        mvc.perform(post(BASIS).with(buero()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nachname\":\"Mustermann\",\"notiz\":\"" + "x".repeat(10_001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Notiz ist zu lang."));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("SQL- und HTML-Texte werden unverändert als Text gespeichert und geliefert")
    void sqlUndHtmlAlsText() throws Exception {
        mvc.perform(post(BASIS).with(buero()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nachname\":\"'; DROP TABLE lieferanten; --\","
                                + "\"notiz\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nachname").value("'; DROP TABLE lieferanten; --"))
                .andExpect(jsonPath("$.notiz").value("<script>alert(1)</script>"));
    }
}
