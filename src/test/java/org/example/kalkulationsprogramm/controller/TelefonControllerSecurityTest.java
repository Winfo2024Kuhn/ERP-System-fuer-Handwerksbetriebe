package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.dto.Telefon.AbholErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufKontaktUeberblickDto;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufbeantworterDto;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.dto.Telefon.SprachnachrichtDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonAnrufDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonZuordnenDto;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.telefon.AnlagenInfo;
import org.example.kalkulationsprogramm.service.telefon.AnrufKontaktUeberblickService;
import org.example.kalkulationsprogramm.service.telefon.TelefonAbholService;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlage;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnrufmonitorService;
import org.example.kalkulationsprogramm.service.telefon.TelefonBerechtigungService;
import org.example.kalkulationsprogramm.service.telefon.TelefonEinstellungenService;
import org.example.kalkulationsprogramm.service.telefon.TelefonLiveService;
import org.example.kalkulationsprogramm.service.telefon.TelefonService;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TelefonController.class, TelefonEinstellungenController.class, AbteilungBerechtigungController.class})
@Import({SecurityConfig.class, TelefonBerechtigungService.class, TelefonControllerSecurityTest.Filters.class})
class TelefonControllerSecurityTest {

    @TestConfiguration
    static class Filters {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired MockMvc mvc;
    @MockBean FrontendUserDetailsService frontendUserDetailsService;
    @MockBean FrontendUserProfileRepository profiles;
    @MockBean TelefonService telefonService;
    @MockBean TelefonEinstellungenService einstellungen;
    @MockBean TelefonAbholService abholService;
    @MockBean TelefonLiveService liveService;
    @MockBean AnrufKontaktUeberblickService kontaktUeberblick;
    @MockBean TelefonAnlage anlage;
    @MockBean TelefonAnrufmonitorService anrufmonitor;
    @MockBean AbteilungRepository abteilungen;
    @MockBean AbteilungDokumentBerechtigungRepository dokumentRechte;

    @TempDir Path tmp;

    private static final long MIT_RECHT = 70L;
    private static final long OHNE_RECHT = 71L;

    private RequestPostProcessor mitRecht() {
        profil(MIT_RECHT, true);
        return user(new FrontendUserPrincipal(MIT_RECHT, "max@example.com", "Max Mustermann", "", true, Set.of()));
    }

    private RequestPostProcessor ohneRecht() {
        profil(OHNE_RECHT, false);
        return user(new FrontendUserPrincipal(OHNE_RECHT, "erika@example.com", "Erika Mustermann", "", true, Set.of()));
    }

    private void profil(long id, boolean telefon) {
        Abteilung a = new Abteilung();
        a.setDarfTelefonSehen(telefon);
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id + 1000);
        m.setAbteilungen(Set.of(a));
        FrontendUserProfile p = new FrontendUserProfile();
        p.setId(id);
        p.setDisplayName("Max Mustermann");
        p.setMitarbeiter(m);
        when(profiles.findById(id)).thenReturn(Optional.of(p));
    }

    private static TelefonAnrufDto anruf() {
        return new TelefonAnrufDto(1L, LocalDateTime.of(2026, 9, 29, 11, 55), "VERPASST", null, "09311234567", "2323", 0,
                null, "AUTOMATISCH", new KontaktKurzDto("KUNDE", 5L, "Mustermann GmbH", "K-1", "Würzburg"), List.of(), null);
    }

    private static SprachnachrichtDto nachricht() {
        return new SprachnachrichtDto(3L, 1, LocalDateTime.of(2026, 9, 29, 8, 14), "09311234567", 42, false,
                LocalDateTime.of(2026, 9, 29, 12, 4), "Max Mustermann", "KEINE", null, List.of(), null);
    }

    // ------------------------------------------------------------ ohne Anmeldung / ohne Recht

    @Test
    @DisplayName("Anonym: alles 401, nichts wird aufgerufen")
    void anonym() throws Exception {
        mvc.perform(get("/api/telefon/berechtigung")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/telefon/anrufe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/telefon/sprachnachrichten/3/audio")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/telefon/live")).andExpect(status().isUnauthorized());
        verifyNoInteractions(telefonService, liveService, abholService);
    }

    @Test
    @DisplayName("Angemeldet ohne Abteilungs-Recht: Berechtigung false, alle Telefondaten 403")
    void ohneAbteilungsRecht() throws Exception {
        mvc.perform(get("/api/telefon/berechtigung").with(ohneRecht()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.darfTelefonSehen").value(false));
        mvc.perform(get("/api/telefon/status").with(ohneRecht())).andExpect(status().isForbidden());
        mvc.perform(get("/api/telefon/anrufe").with(ohneRecht())).andExpect(status().isForbidden());
        mvc.perform(get("/api/telefon/sprachnachrichten").with(ohneRecht())).andExpect(status().isForbidden());
        mvc.perform(get("/api/telefon/sprachnachrichten/anzahl-neu").with(ohneRecht())).andExpect(status().isForbidden());
        mvc.perform(get("/api/telefon/sprachnachrichten/3/audio").with(ohneRecht())).andExpect(status().isForbidden());
        mvc.perform(get("/api/telefon/live").with(ohneRecht())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/telefon/sprachnachrichten/3").with(ohneRecht()).with(csrf())
                .contentType("application/json").content("{\"abgehoert\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/telefon/anrufe/1/zuordnung").with(ohneRecht()).with(csrf())
                .contentType("application/json").content("{\"kundeId\":5}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/telefon/abholen").with(ohneRecht()).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/telefon/kontakt-rufnummern/1").with(ohneRecht()).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/telefon/kontakt-ueberblick").param("kundeId", "5").with(ohneRecht())).andExpect(status().isForbidden());
        verifyNoInteractions(telefonService, liveService, abholService, kontaktUeberblick);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN ohne Abteilungs-Recht sieht keine Anrufe")
    void adminOhneRecht() throws Exception {
        mvc.perform(get("/api/telefon/berechtigung")).andExpect(jsonPath("$.darfTelefonSehen").value(false));
        mvc.perform(get("/api/telefon/anrufe")).andExpect(status().isForbidden());
        verifyNoInteractions(telefonService);
    }

    // ------------------------------------------------------------ mit Recht

    @Test
    @DisplayName("Mit Recht: Anrufliste mit Filtern")
    void anrufliste() throws Exception {
        when(telefonService.anrufe(eq(TelefonAnrufArt.VERPASST), eq(true), eq("muster"), isNull(), isNull(), eq(0), eq(50)))
                .thenReturn(new PageImpl<>(List.of(anruf())));
        mvc.perform(get("/api/telefon/anrufe").with(mitRecht())
                        .param("art", "VERPASST").param("nurUnbekannt", "true").param("suche", "muster"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].kontakt.name").value("Mustermann GmbH"))
                .andExpect(jsonPath("$.content[0].art").value("VERPASST"));
    }

    @Test
    @DisplayName("Mit Recht: Überblick für das Anruf-Fenster mit Projekten und Anfragen")
    void kontaktUeberblick() throws Exception {
        when(kontaktUeberblick.ueberblick(5L, null)).thenReturn(new AnrufKontaktUeberblickDto("KUNDE", 5L,
                "Max Mustermann", "K-1", "Erika Mustermann", "Hauptstraße 1", "97070", "Würzburg",
                List.of(new AnrufKontaktUeberblickDto.Projekt(9L, "Wintergarten", "2026-001", "Würzburg", false)), 1,
                List.of(new AnrufKontaktUeberblickDto.Anfrage(4L, "Balkongeländer", "AN-2026-044", null, false)), 1));
        mvc.perform(get("/api/telefon/kontakt-ueberblick").param("kundeId", "5").with(mitRecht()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ansprechpartner").value("Erika Mustermann"))
                .andExpect(jsonPath("$.projekte[0].auftragsnummer").value("2026-001"))
                .andExpect(jsonPath("$.anfragen[0].angebotsnummer").value("AN-2026-044"));
    }

    @Test
    @DisplayName("Überblick: fehlende/doppelte IDs 400, unbekannter Kontakt 404, Text statt ID 400")
    void kontaktUeberblickFehler() throws Exception {
        when(kontaktUeberblick.ueberblick(null, null)).thenThrow(new IllegalArgumentException("Bitte genau kundeId oder lieferantId angeben."));
        when(kontaktUeberblick.ueberblick(1L, 1L)).thenThrow(new IllegalArgumentException("Bitte genau kundeId oder lieferantId angeben."));
        when(kontaktUeberblick.ueberblick(Long.MAX_VALUE, null)).thenThrow(new NoSuchElementException("Kunde nicht gefunden"));
        when(kontaktUeberblick.ueberblick(-1L, null)).thenThrow(new NoSuchElementException("Kunde nicht gefunden"));
        when(kontaktUeberblick.ueberblick(null, 0L)).thenThrow(new NoSuchElementException("Lieferant nicht gefunden"));
        mvc.perform(get("/api/telefon/kontakt-ueberblick").with(mitRecht())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/telefon/kontakt-ueberblick").param("kundeId", "1").param("lieferantId", "1").with(mitRecht()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte genau kundeId oder lieferantId angeben."));
        mvc.perform(get("/api/telefon/kontakt-ueberblick").param("kundeId", "-1").with(mitRecht())).andExpect(status().isNotFound());
        mvc.perform(get("/api/telefon/kontakt-ueberblick").param("lieferantId", "0").with(mitRecht())).andExpect(status().isNotFound());
        mvc.perform(get("/api/telefon/kontakt-ueberblick").param("kundeId", String.valueOf(Long.MAX_VALUE)).with(mitRecht()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/telefon/kontakt-ueberblick").param("kundeId", "'; DROP TABLE kunde; --").with(mitRecht()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Mit Recht: nurOffen liefert die offenen verpassten Anrufe aus der Glocke")
    void offeneVerpasste() throws Exception {
        when(telefonService.offeneVerpassteAnrufe(0, 50)).thenReturn(new PageImpl<>(List.of(anruf())));
        mvc.perform(get("/api/telefon/anrufe").with(mitRecht()).param("nurOffen", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].art").value("VERPASST"));
        verify(telefonService, never()).anrufe(any(), anyBoolean(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Ohne Recht: nurOffen → 403")
    void offeneVerpassteOhneRecht() throws Exception {
        mvc.perform(get("/api/telefon/anrufe").param("nurOffen", "true").with(ohneRecht()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(telefonService);
    }

    @Test
    @DisplayName("Ungültige Art → 400, SQL-Injection im Suchfeld wird nur als Text weitergegeben")
    void eingaben() throws Exception {
        mvc.perform(get("/api/telefon/anrufe").with(mitRecht()).param("art", "'; DROP TABLE x; --"))
                .andExpect(status().isBadRequest());
        String boese = "'; DROP TABLE telefon_anruf; --";
        when(telefonService.anrufe(isNull(), anyBoolean(), eq(boese), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/telefon/anrufe").with(mitRecht()).param("suche", boese)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mit Recht: Status mit AB-Namen")
    void status_() throws Exception {
        when(einstellungen.istAktiv()).thenReturn(true);
        when(einstellungen.zugang()).thenReturn(Optional.of(new TelefonZugang("fritz.box", "erp", "x")));
        when(einstellungen.anrufbeantworter()).thenReturn(List.of(new AnrufbeantworterDto(0, "AB Nacht"), new AnrufbeantworterDto(1, "AB Tag")));
        when(telefonService.anzahlNeueSprachnachrichten()).thenReturn(2L);
        mvc.perform(get("/api/telefon/status").with(mitRecht()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eingerichtet").value(true))
                .andExpect(jsonPath("$.anrufbeantworter[1].name").value("AB Tag"))
                .andExpect(jsonPath("$.neueSprachnachrichten").value(2));
    }

    @Test
    @DisplayName("Mit Recht: Zuordnen, Aufheben, Abgehört, Abholen, Kontakt-Rufnummern")
    void schreibendeAktionen() throws Exception {
        when(telefonService.ordneAnrufZu(eq(1L), any(TelefonZuordnenDto.class))).thenReturn(anruf());
        mvc.perform(post("/api/telefon/anrufe/1/zuordnung").with(mitRecht()).with(csrf())
                        .contentType("application/json").content("{\"kundeId\":5,\"nummerMerken\":true}"))
                .andExpect(status().isOk());
        verify(telefonService).ordneAnrufZu(1L, new TelefonZuordnenDto(5L, null, true));

        when(telefonService.hebeAnrufZuordnungAuf(1L)).thenReturn(anruf());
        mvc.perform(delete("/api/telefon/anrufe/1/zuordnung").with(mitRecht()).with(csrf())).andExpect(status().isOk());

        when(telefonService.setzeAbgehoert(eq(3L), eq(true), any())).thenReturn(nachricht());
        mvc.perform(patch("/api/telefon/sprachnachrichten/3").with(mitRecht()).with(csrf())
                        .contentType("application/json").content("{\"abgehoert\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.abgehoertVon").value("Max Mustermann"));

        when(telefonService.ordneNachrichtZu(eq(3L), any())).thenReturn(nachricht());
        mvc.perform(post("/api/telefon/sprachnachrichten/3/zuordnung").with(mitRecht()).with(csrf())
                .contentType("application/json").content("{\"lieferantId\":8}")).andExpect(status().isOk());
        when(telefonService.hebeNachrichtZuordnungAuf(3L)).thenReturn(nachricht());
        mvc.perform(delete("/api/telefon/sprachnachrichten/3/zuordnung").with(mitRecht()).with(csrf())).andExpect(status().isOk());

        when(abholService.abholen()).thenReturn(new AbholErgebnisDto(true, "Abgeholt", 2, 1, 0));
        mvc.perform(post("/api/telefon/abholen").with(mitRecht()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.neueAnrufe").value(2));

        mvc.perform(delete("/api/telefon/kontakt-rufnummern/4").with(mitRecht()).with(csrf())).andExpect(status().isNoContent());
        verify(telefonService).loescheKontaktRufnummer(4L);
    }

    @Test
    @DisplayName("Fehler: ungültige Zuordnung 400, unbekannte/negative/riesige IDs 404")
    void fehlerfaelle() throws Exception {
        when(telefonService.ordneAnrufZu(eq(1L), any())).thenThrow(new IllegalArgumentException("Bitte genau einen Kunden oder einen Lieferanten auswählen."));
        mvc.perform(post("/api/telefon/anrufe/1/zuordnung").with(mitRecht()).with(csrf())
                        .contentType("application/json").content("{\"kundeId\":5,\"lieferantId\":8}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("genau einen")));
        for (long id : new long[]{0L, -1L, Long.MAX_VALUE}) {
            when(telefonService.hebeAnrufZuordnungAuf(id)).thenThrow(new NoSuchElementException("Anruf nicht gefunden"));
            mvc.perform(delete("/api/telefon/anrufe/" + id + "/zuordnung").with(mitRecht()).with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("CSRF bleibt Pflicht für schreibende Telefon-Aktionen")
    void csrfPflicht() throws Exception {
        mvc.perform(post("/api/telefon/abholen").with(mitRecht())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/telefon/sprachnachrichten/3").with(mitRecht())
                .contentType("application/json").content("{\"abgehoert\":true}")).andExpect(status().isForbidden());
        verifyNoInteractions(abholService, telefonService);
    }

    @Test
    @DisplayName("Audio: WAV mit Range-Unterstützung, fehlende Datei 404")
    void audio() throws Exception {
        Path datei = tmp.resolve("aufnahme.wav");
        Files.write(datei, new byte[]{'R', 'I', 'F', 'F', 1, 2, 3, 4, 5, 6});
        when(telefonService.audio(3L)).thenReturn(datei);
        mvc.perform(get("/api/telefon/sprachnachrichten/3/audio").with(mitRecht()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/wav"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/api/telefon/sprachnachrichten/3/audio").with(mitRecht()).header(HttpHeaders.RANGE, "bytes=4-5"))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes(new byte[]{1, 2}));

        when(telefonService.audio(4L)).thenReturn(tmp.resolve("fehlt.wav"));
        mvc.perform(get("/api/telefon/sprachnachrichten/4/audio").with(mitRecht())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Live-Strom öffnet sich mit Profil-ID des Benutzers")
    void live() throws Exception {
        when(liveService.verbinde(MIT_RECHT)).thenReturn(new SseEmitter(1000L));
        mvc.perform(get("/api/telefon/live").with(mitRecht())).andExpect(status().isOk());
        verify(liveService).verbinde(MIT_RECHT);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Kontakt-Rufnummern lesen: für alle angemeldeten; genau ein Kontakt nötig")
    void kontaktRufnummernLesen() throws Exception {
        when(telefonService.kontaktRufnummern(5L, null)).thenReturn(List.of());
        mvc.perform(get("/api/telefon/kontakt-rufnummern").param("kundeId", "5")).andExpect(status().isOk());
        mvc.perform(get("/api/telefon/kontakt-rufnummern")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/telefon/kontakt-rufnummern").param("kundeId", "5").param("lieferantId", "6"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ Einstellungen (ADMIN)

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Einstellungen und Nachholen sind für normale Benutzer gesperrt")
    void einstellungenNurAdmin() throws Exception {
        mvc.perform(get("/api/telefon/einstellungen")).andExpect(status().isForbidden());
        mvc.perform(put("/api/telefon/einstellungen").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/telefon/einstellungen/test").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/telefon/admin/nachholen").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(einstellungen, anlage, abholService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin: Einstellungen lesen/speichern – Passwort kommt nie zurück")
    void einstellungenAdmin() throws Exception {
        TelefonEinstellungenDto dto = new TelefonEinstellungenDto(true, "fritz.box", "erp", true, true, List.of("2323"),
                List.of(new AnrufbeantworterDto(0, "AB Nacht")), 12, 12, "49", "931", null, null, false);
        when(einstellungen.lade(anyBoolean())).thenReturn(dto);
        mvc.perform(get("/api/telefon/einstellungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwortGesetzt").value(true))
                .andExpect(content().string(not(containsString("geheim"))))
                .andExpect(jsonPath("$.passwort").doesNotExist());
        mvc.perform(put("/api/telefon/einstellungen").with(csrf()).contentType("application/json")
                        .content("{\"passwort\":\"geheim\",\"geschaeftsnummern\":[\"2323\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("geheim"))));
        verify(anrufmonitor).einstellungenGeaendert();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin: ungültige Einstellungen → 400 mit Meldung (auch XSS-Text wird nur als Text geliefert)")
    void einstellungenUngueltig() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Die FRITZ!Box-Adresse ist ungültig"))
                .when(einstellungen).speichere(any());
        mvc.perform(put("/api/telefon/einstellungen").with(csrf()).contentType("application/json")
                        .content("{\"host\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ungültig")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin: Verbindungstest übernimmt Vorwahlen; Fehler wird verständlich gemeldet")
    void verbindungstest() throws Exception {
        TelefonZugang z = new TelefonZugang("fritz.box", "erp", "pw");
        when(einstellungen.zugangFuerTest(any(), any(), any())).thenReturn(Optional.of(z));
        when(anlage.pruefeVerbindung(z)).thenReturn(new AnlagenInfo(List.of("2323", "555000"),
                List.of(new AnlagenInfo.Anrufbeantworter(0, "AB Nacht", true)), "49", "931"));
        mvc.perform(post("/api/telefon/einstellungen/test").with(csrf()).contentType("application/json")
                        .content("{\"passwort\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erfolgreich").value(true))
                .andExpect(jsonPath("$.eigeneNummern[0]").value("2323"))
                .andExpect(jsonPath("$.anrufbeantworter[0].name").value("AB Nacht"));
        verify(einstellungen).speichereVorwahlen("49", "931");

        when(anlage.pruefeVerbindung(z)).thenThrow(new TelefonAnlageException(TelefonAnlageException.Grund.ANMELDUNG_FEHLGESCHLAGEN));
        mvc.perform(post("/api/telefon/einstellungen/test").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erfolgreich").value(false))
                .andExpect(jsonPath("$.meldung").value("Benutzername oder Passwort falsch"));

        when(einstellungen.zugangFuerTest(any(), any(), any())).thenReturn(Optional.empty());
        mvc.perform(post("/api/telefon/einstellungen/test").with(csrf()))
                .andExpect(jsonPath("$.erfolgreich").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin: Nachholen mit Tagen, ungültige Tage → 400")
    void nachholen() throws Exception {
        when(abholService.nachholen(90)).thenReturn(new AbholErgebnisDto(true, "Abgeholt", 40, 3, 5));
        mvc.perform(post("/api/telefon/admin/nachholen").with(csrf()).param("tage", "90"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nachtraeglichZugeordnet").value(5));
        when(abholService.nachholen(0)).thenThrow(new IllegalArgumentException("Bitte zwischen 1 und 999 Tagen wählen."));
        mvc.perform(post("/api/telefon/admin/nachholen").with(csrf()).param("tage", "0")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Abteilungs-Berechtigung: darfTelefonSehen lässt sich setzen und wird geliefert")
    void abteilungsFlag() throws Exception {
        Abteilung a = new Abteilung();
        a.setId(1L);
        a.setName("Büro");
        when(abteilungen.findById(1L)).thenReturn(Optional.of(a));
        mvc.perform(put("/api/abteilungen/1/berechtigungen").with(csrf()).contentType("application/json")
                        .content("{\"darfTelefonSehen\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.darfTelefonSehen").value(true));
        verify(abteilungen).save(a);
    }
}
