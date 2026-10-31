package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgangStatus;
import org.example.kalkulationsprogramm.dto.Ids.IdsEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsVerfuegbarDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsVorgangDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.ids.IdsArtikelstammAbgleich;
import org.example.kalkulationsprogramm.service.ids.IdsEinstellungenService;
import org.example.kalkulationsprogramm.service.ids.IdsRueckgabe;
import org.example.kalkulationsprogramm.service.ids.IdsShopBesuchService;
import org.example.kalkulationsprogramm.service.ids.ShopFormular;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shop-Anbindung: Einrichtung nur für Admins, Benutzung für jeden am PC mit CSRF, Rücksprung aus dem Shop
 * ohne Anmeldung und ohne CSRF – aber nur POST, nur mit Schlüssel und größenbegrenzt.
 */
@WebMvcTest(controllers = { IdsEinstellungenController.class, IdsShopController.class, IdsRueckgabeController.class })
@Import({ SecurityConfig.class, IdsControllerSecurityTest.EchteFilterBeans.class })
class IdsControllerSecurityTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IdsEinstellungenService einstellungen;
    @MockitoBean private IdsShopBesuchService shopBesuch;
    @MockitoBean private IdsArtikelstammAbgleich abgleich;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private FrontendUserProfileRepository frontendUserProfileRepository;

    private static final String SCHLUESSEL = "a".repeat(43);
    private static final String ZUGANG = "{\"aktiv\":true,\"protokoll\":\"IDS_CONNECT_2_5\",\"shopUrl\":\"https://shop.example.com\","
            + "\"kundennummer\":\"'; DROP TABLE x; --\",\"benutzername\":\"<script>alert(1)</script>\",\"passwort\":\"geheim\"}";

    private final FrontendUserPrincipal max = new FrontendUserPrincipal(70L, "max@example.com", "Max Mustermann", "", true,
            Set.of(FrontendUserRole.USER));
    private final FrontendUserPrincipal admin = new FrontendUserPrincipal(71L, "admin@example.com", "Erika Musterfrau", "", true,
            Set.of(FrontendUserRole.ADMIN));

    @BeforeEach
    void setUp() {
        given(frontendUserDetailsService.aktuellerStand(any())).willAnswer(i -> Optional.of(i.getArgument(0)));
    }

    @Test
    @DisplayName("Ohne Anmeldung: 401 auf Einstellungen und Benutzung")
    void ohneAnmeldung() throws Exception {
        mockMvc.perform(get("/api/ids/einstellungen")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ids/verfuegbar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ids/vorgaenge/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ids/vorgaenge").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"lieferantId\":7}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(einstellungen, shopBesuch, abgleich);
    }

    @Test
    @DisplayName("Kein Admin: Einstellungen und Zugänge gesperrt (403)")
    void keinAdmin() throws Exception {
        mockMvc.perform(get("/api/ids/einstellungen").with(user(max))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ids/einstellungen").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":true}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ids/einstellungen/zugaenge/7").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(ZUGANG)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ids/einstellungen/zugaenge/7").with(user(max)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(einstellungen);
    }

    @Test
    @DisplayName("Admin: lesen, schalten, Zugang speichern und löschen; ohne CSRF 403")
    void admin() throws Exception {
        given(einstellungen.lade()).willReturn(new IdsEinstellungenDto(true, true, List.of()));
        given(einstellungen.setzeAktiv(true)).willReturn(new IdsEinstellungenDto(true, true, List.of()));

        mockMvc.perform(get("/api/ids/einstellungen").with(user(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.verschluesselungEingerichtet").value(true));
        mockMvc.perform(put("/api/ids/einstellungen").with(user(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":true}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ids/einstellungen").with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":true}")).andExpect(status().isOk());
        mockMvc.perform(put("/api/ids/einstellungen/zugaenge/7").with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(ZUGANG)).andExpect(status().isOk());
        verify(einstellungen).speichereZugang(eq(7L), any());
        mockMvc.perform(delete("/api/ids/einstellungen/zugaenge/7").with(user(admin)).with(csrf())).andExpect(status().isNoContent());

        given(einstellungen.speichereZugang(eq(Long.MAX_VALUE), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Lieferant wurde nicht gefunden."));
        mockMvc.perform(put("/api/ids/einstellungen/zugaenge/" + Long.MAX_VALUE).with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(ZUGANG))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Der Lieferant wurde nicht gefunden."));
        mockMvc.perform(put("/api/ids/einstellungen/zugaenge/abc").with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(ZUGANG)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Benutzung: verfügbar, Shop starten (no-store, Rücksprung über Origin), Warenkorb laden, Artikelstamm")
    void benutzung() throws Exception {
        given(einstellungen.verfuegbar()).willReturn(new IdsVerfuegbarDto(true, List.of(new IdsVerfuegbarDto.Lieferant(7L, "Würth", true))));
        given(shopBesuch.starte(eq(7L), eq(70L), any())).willReturn(new IdsShopBesuchService.Gestartet(42L,
                new ShopFormular("https://shop.example.com", "multipart/form-data", Map.of("action", "WKE"))));
        given(abgleich.ansicht(42L, 70L)).willReturn(new IdsVorgangDto(42L, 7L, "Würth", IdsVorgangStatus.EMPFANGEN,
                null, false, null, null, List.of()));

        mockMvc.perform(get("/api/ids/verfuegbar").with(user(max))).andExpect(status().isOk())
                .andExpect(jsonPath("$.lieferanten[0].lieferantName").value("Würth"));

        mockMvc.perform(post("/api/ids/vorgaenge").with(user(max)).contentType(MediaType.APPLICATION_JSON).content("{\"lieferantId\":7}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ids/vorgaenge").with(user(max)).with(csrf()).header("Origin", "https://erp.example.ts.net")
                .contentType(MediaType.APPLICATION_JSON).content("{\"lieferantId\":7}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.vorgangId").value(42))
                .andExpect(jsonPath("$.formular.felder.action").value("WKE"));
        verify(shopBesuch).starte(7L, 70L, "https://erp.example.ts.net");

        mockMvc.perform(post("/api/ids/vorgaenge").with(user(max)).with(csrf()).header("Origin", "javascript:alert(1)")
                .contentType(MediaType.APPLICATION_JSON).content("{\"lieferantId\":7}")).andExpect(status().isCreated());
        verify(shopBesuch).starte(7L, 70L, "http://localhost");

        mockMvc.perform(post("/api/ids/vorgaenge").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"lieferantId\":-1}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ids/vorgaenge").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/ids/vorgaenge/42").with(user(max))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPFANGEN"));
        mockMvc.perform(post("/api/ids/vorgaenge/42/artikelstamm").with(user(max))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ids/vorgaenge/42/artikelstamm").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"aktualisieren\":[{\"index\":0,\"einkaufspreis\":8.4}]}"))
                .andExpect(status().isOk());

        given(abgleich.ansicht(eq(0L), anyLong()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Dieser Shop-Besuch wurde nicht gefunden."));
        mockMvc.perform(get("/api/ids/vorgaenge/0").with(user(max))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Rücksprung: ohne Anmeldung und CSRF per Multipart, leitet auf die Erklärseite um")
    void rueckgabeMultipart() throws Exception {
        given(shopBesuch.nimmRueckgabeAn(eq(SCHLUESSEL), any()))
                .willReturn(new IdsShopBesuchService.Rueckgabe(42L, IdsShopBesuchService.Ergebnis.OK));
        byte[] xml = "<Warenkorb/>".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/ids/rueckgabe/" + SCHLUESSEL)
                        .part(new MockPart("warenkorb", xml))
                        // Browser schicken immer eine Längenangabe mit; MockMvc setzt sie bei Multipart nicht selbst.
                        .with(r -> {
                            r.addHeader("Content-Length", "512");
                            r.setContent(new byte[512]);
                            return r;
                        }))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/ids/warenkorb-angekommen?ergebnis=ok&vorgang=42"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        ArgumentCaptor<IdsRueckgabe> captor = ArgumentCaptor.forClass(IdsRueckgabe.class);
        verify(shopBesuch).nimmRueckgabeAn(eq(SCHLUESSEL), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().warenkorbBytes()).isEqualTo(xml);
    }

    @Test
    @DisplayName("Rücksprung: url-kodiertes Formular, ungültiger Schlüssel ohne Vorgang")
    void rueckgabeFormular() throws Exception {
        given(shopBesuch.nimmRueckgabeAn(any(), any()))
                .willReturn(new IdsShopBesuchService.Rueckgabe(null, IdsShopBesuchService.Ergebnis.UNGUELTIG));

        // SQL-Zeichen im Pfad weist schon die Firewall von Spring Security ab.
        mockMvc.perform(post("/api/ids/rueckgabe/'; DROP TABLE x; --")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("warenkorb=x"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/ids/rueckgabe/kurz")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("warenkorb=%3CWarenkorb%2F%3E"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/ids/warenkorb-angekommen?ergebnis=ungueltig"));
        ArgumentCaptor<IdsRueckgabe> captor = ArgumentCaptor.forClass(IdsRueckgabe.class);
        verify(shopBesuch).nimmRueckgabeAn(any(), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().warenkorbText()).isEqualTo("<Warenkorb/>");
    }

    @Test
    @DisplayName("Rücksprung: GET gesperrt, zu groß 413, ohne Längenangabe 411")
    void rueckgabeGrenzen() throws Exception {
        mockMvc.perform(get("/api/ids/rueckgabe/" + SCHLUESSEL)).andExpect(status().isForbidden());

        byte[] gross = new byte[2 * 1024 * 1024 + 1];
        mockMvc.perform(post("/api/ids/rueckgabe/" + SCHLUESSEL).contentType(MediaType.APPLICATION_FORM_URLENCODED).content(gross))
                .andExpect(status().isPayloadTooLarge());
        mockMvc.perform(post("/api/ids/rueckgabe/" + SCHLUESSEL).contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isLengthRequired());
        verifyNoInteractions(shopBesuch);
    }
}
