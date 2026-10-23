package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.AngemeldeterMitarbeiterService;
import org.example.kalkulationsprogramm.service.KalenderService;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Termin-Kalender mit echter SecurityConfig und echtem {@link KalenderService} (Issue #267):
 * Sichtbarkeit und Änderungsrechte entscheidet das Backend anhand der Anmeldung, nicht
 * anhand von Request-Werten. Dummy-Daten: Max Mustermann, Erika Musterfrau.
 */
@WebMvcTest(controllers = KalenderController.class)
@Import({ SecurityConfig.class, KalenderService.class, AngemeldeterMitarbeiterService.class,
        KalenderControllerSecurityTest.EchteFilterBeans.class })
class KalenderControllerSecurityTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private FrontendUserDetailsService users;
    @MockitoBean private FrontendUserProfileRepository profile;
    @MockitoBean private MitarbeiterRepository mitarbeiterRepository;
    @MockitoBean private KalenderEintragRepository eintraege;
    @MockitoBean private ProjektRepository projektRepository;
    @MockitoBean private KundeRepository kundeRepository;
    @MockitoBean private LieferantenRepository lieferantenRepository;
    @MockitoBean private AnfrageRepository anfrageRepository;

    private static final LocalDate VON = LocalDate.of(2026, 9, 28);
    private static final LocalDate BIS = LocalDate.of(2026, 11, 8);

    private final Mitarbeiter max = mitarbeiter(7L, "Max", "Mustermann");
    private final Mitarbeiter erika = mitarbeiter(8L, "Erika", "Musterfrau");
    /** Erika lädt Max ein – Max darf ändern. */
    private KalenderEintrag eingeladen;
    /** Erikas privater Termin – Max sieht ihn nicht. */
    private KalenderEintrag privat;
    /** Firmentermin ohne Ersteller und Teilnehmer – alle sehen ihn, nur Admins ändern. */
    private KalenderEintrag firma;

    @BeforeEach
    void daten() {
        reset(eintraege);
        SitzungsRechteImTest.rechteUnveraendert(users);
        eingeladen = termin(1L, erika, Set.of(max));
        privat = termin(2L, erika, Set.of());
        firma = termin(3L, null, Set.of());
        when(eintraege.findByIdWithTeilnehmer(1L)).thenReturn(eingeladen);
        when(eintraege.findByIdWithTeilnehmer(2L)).thenReturn(privat);
        when(eintraege.findByIdWithTeilnehmer(3L)).thenReturn(firma);
        when(eintraege.save(any(KalenderEintrag.class))).thenAnswer(a -> a.getArgument(0));
        when(mitarbeiterRepository.findById(7L)).thenReturn(Optional.of(max));
        when(mitarbeiterRepository.findById(8L)).thenReturn(Optional.of(erika));
        when(mitarbeiterRepository.findAllById(any())).thenAnswer(a -> {
            Iterable<Long> ids = a.getArgument(0);
            List<Mitarbeiter> treffer = new java.util.ArrayList<>();
            ids.forEach(id -> mitarbeiterRepository.findById(id).ifPresent(treffer::add));
            return treffer;
        });
        when(profile.findById(70L)).thenReturn(Optional.of(profil(max)));
        when(profile.findById(71L)).thenReturn(Optional.of(profil(null)));
        when(profile.findById(72L)).thenReturn(Optional.of(profil(null)));
    }

    // ---------- Anmeldung & CSRF ----------

    @Test
    @DisplayName("Ohne Anmeldung: 401, auch beim Lesen")
    void ohneAnmeldung() throws Exception {
        mvc.perform(get("/api/kalender").param("von", "2026-10-01").param("bis", "2026-10-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Schreiben ohne CSRF-Token: 403, nichts gespeichert")
    void ohneCsrf() throws Exception {
        mvc.perform(put("/api/kalender/1").with(user(maxAnmeldung()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Aufmaß", "[]")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/kalender/1").with(user(maxAnmeldung())))
                .andExpect(status().isForbidden());
        verify(eintraege, never()).save(any());
        verify(eintraege, never()).delete(any());
    }

    // ---------- Lesen ----------

    @Test
    @DisplayName("Mitarbeiter sieht nur seine Termine – eine fremde mitarbeiterId wird ignoriert")
    void mitarbeiterSiehtNurEigene() throws Exception {
        when(eintraege.findByMitarbeiterAndDatumBetween(7L, VON, BIS)).thenReturn(List.of(eingeladen, firma));

        mvc.perform(get("/api/kalender").param("von", VON.toString()).param("bis", BIS.toString())
                        .param("mitarbeiterId", "8").with(user(maxAnmeldung())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].darfBearbeiten").value(true))
                .andExpect(jsonPath("$[1].darfBearbeiten").value(false));

        verify(eintraege).findByMitarbeiterAndDatumBetween(7L, VON, BIS);
        verify(eintraege, never()).findByMitarbeiterAndDatumBetween(eq(8L), any(), any());
        verify(eintraege, never()).findByDatumBetween(any(), any());
    }

    @Test
    @DisplayName("Admin ohne Filter sieht alle Termine und darf alle ändern; mit Filter den eines Mitarbeiters")
    void adminSiehtAlles() throws Exception {
        when(eintraege.findByDatumBetween(VON, BIS)).thenReturn(List.of(privat, firma));

        mvc.perform(get("/api/kalender").param("von", VON.toString()).param("bis", BIS.toString())
                        .with(user(adminAnmeldung())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].darfBearbeiten").value(true))
                .andExpect(jsonPath("$[1].darfBearbeiten").value(true));

        mvc.perform(get("/api/kalender").param("von", VON.toString()).param("bis", BIS.toString())
                        .param("mitarbeiterId", "8").with(user(adminAnmeldung())))
                .andExpect(status().isOk());
        verify(eintraege).findByMitarbeiterAndDatumBetween(8L, VON, BIS);
    }

    @Test
    @DisplayName("Benutzer ohne zugeordneten Mitarbeiter sieht nur Firmentermine")
    void ohneMitarbeiterNurFirmentermine() throws Exception {
        when(eintraege.findFirmenEintraegeBetween(VON, BIS)).thenReturn(List.of(firma));

        mvc.perform(get("/api/kalender").param("von", VON.toString()).param("bis", BIS.toString())
                        .with(user(anmeldung(72L, FrontendUserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].darfBearbeiten").value(false));
        verify(eintraege, never()).findByDatumBetween(any(), any());
    }

    @Test
    @DisplayName("jahr/monat funktioniert weiter und wird zum Monatszeitraum")
    void monatFunktioniertWeiter() throws Exception {
        mvc.perform(get("/api/kalender").param("jahr", "2026").param("monat", "12").with(user(maxAnmeldung())))
                .andExpect(status().isOk());
        verify(eintraege).findByMitarbeiterAndDatumBetween(7L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("Ungültige Zeiträume: 400 mit verständlicher Meldung, keine Abfrage")
    void ungueltigeZeitraeume() throws Exception {
        mvc.perform(get("/api/kalender").param("von", "2026-10-31").param("bis", "2026-10-01").with(user(maxAnmeldung())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Das Ende des Zeitraums liegt vor dem Anfang."));
        mvc.perform(get("/api/kalender").param("von", "2026-01-01").param("bis", "2026-12-31").with(user(maxAnmeldung())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/kalender").param("jahr", "2026").param("monat", "13").with(user(maxAnmeldung())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte einen gültigen Monat wählen."));
        mvc.perform(get("/api/kalender").with(user(maxAnmeldung())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/kalender").param("von", "'; DROP TABLE kalender_eintrag; --").param("bis", "2026-10-31")
                        .with(user(maxAnmeldung())))
                .andExpect(status().isBadRequest());
        verify(eintraege, never()).findByMitarbeiterAndDatumBetween(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Einzelner Termin: fremder privater Termin und ungültige IDs sind 404")
    void einzelnerTermin() throws Exception {
        mvc.perform(get("/api/kalender/1").with(user(maxAnmeldung())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darfBearbeiten").value(true));
        mvc.perform(get("/api/kalender/3").with(user(maxAnmeldung())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darfBearbeiten").value(false));
        mvc.perform(get("/api/kalender/2").with(user(maxAnmeldung())))
                .andExpect(status().isNotFound());
        for (String id : List.of("0", "-1", String.valueOf(Long.MAX_VALUE))) {
            mvc.perform(get("/api/kalender/" + id).with(user(maxAnmeldung())))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------- Ändern & Löschen ----------

    @Test
    @DisplayName("Teilnehmer darf ändern; leere Teilnehmerliste entfernt alle")
    void teilnehmerAendertUndEntferntAlle() throws Exception {
        mvc.perform(put("/api/kalender/1").with(user(maxAnmeldung())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Aufmaß Musterstraße", "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titel").value("Aufmaß Musterstraße"))
                .andExpect(jsonPath("$.teilnehmer.length()").value(0));

        assertThat(eingeladen.getTeilnehmer()).isEmpty();
        assertThat(eingeladen.getErsteller()).isSameAs(erika);
    }

    @Test
    @DisplayName("Teilnehmerliste null lässt die Teilnehmer unverändert")
    void teilnehmerNullUnveraendert() throws Exception {
        mvc.perform(put("/api/kalender/1").with(user(maxAnmeldung())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Aufmaß", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teilnehmer.length()").value(1));
    }

    @Test
    @DisplayName("Firmentermin: Mitarbeiter bekommt 403, Admin darf ändern")
    void firmenterminNurAdmin() throws Exception {
        mvc.perform(put("/api/kalender/3").with(user(maxAnmeldung())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Betriebsausflug", "[]")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Diesen Termin können nur der Ersteller, die Teilnehmer und Admins ändern."));
        mvc.perform(delete("/api/kalender/3").with(user(maxAnmeldung())).with(csrf()))
                .andExpect(status().isForbidden());
        verify(eintraege, never()).save(any());
        verify(eintraege, never()).delete(any());

        mvc.perform(put("/api/kalender/3").with(user(adminAnmeldung())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Betriebsausflug", "[]")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Löschen: fremder privater Termin 404, eigener Termin gelöscht, ungültige IDs 404")
    void loeschen() throws Exception {
        mvc.perform(delete("/api/kalender/2").with(user(maxAnmeldung())).with(csrf()))
                .andExpect(status().isNotFound());
        for (String id : List.of("0", "-1", String.valueOf(Long.MAX_VALUE))) {
            mvc.perform(delete("/api/kalender/" + id).with(user(maxAnmeldung())).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        verify(eintraege, never()).delete(any());

        mvc.perform(delete("/api/kalender/1").with(user(maxAnmeldung())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
        verify(eintraege).delete(eingeladen);
    }

    @Test
    @DisplayName("Nicht-Admin macht keinen Termin zum Firmentermin, indem er alle Teilnehmer entfernt")
    void keinFirmenterminDurchEntfernen() throws Exception {
        KalenderEintrag ohneErsteller = termin(4L, null, Set.of(max));
        when(eintraege.findByIdWithTeilnehmer(4L)).thenReturn(ohneErsteller);

        mvc.perform(put("/api/kalender/4").with(user(maxAnmeldung())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Baustelle", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("mindestens einen Teilnehmer")));
        verify(eintraege, never()).save(any());
    }

    // ---------- Neu anlegen ----------

    @Test
    @DisplayName("Benutzer ohne Mitarbeiter und ohne Admin-Recht legt keinen Firmentermin an; Admin darf")
    void firmenterminAnlegenNurAdmin() throws Exception {
        String ohneTeilnehmer = "{\"titel\":\"Betriebsausflug\",\"datum\":\"2026-10-20\",\"ganztaegig\":true,\"teilnehmerIds\":[]}";
        mvc.perform(post("/api/kalender").with(user(anmeldung(72L, FrontendUserRole.USER))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ohneTeilnehmer))
                .andExpect(status().isBadRequest());
        verify(eintraege, never()).save(any());

        mvc.perform(post("/api/kalender").with(user(adminAnmeldung())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ohneTeilnehmer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erstellerId").isEmpty());
    }

    @Test
    @DisplayName("Neuer Termin: Ersteller ist der Angemeldete, nicht die mitgeschickte erstellerId; Text bleibt unverändert")
    void erstellerAusAnmeldung() throws Exception {
        String xss = "<script>alert(1)</script>";
        mvc.perform(post("/api/kalender").with(user(maxAnmeldung())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titel\":\"" + xss.replace("\"", "\\\"") + "\",\"datum\":\"2026-10-20\","
                                + "\"startZeit\":\"08:00:00\",\"endeZeit\":\"09:00:00\",\"ganztaegig\":false,"
                                + "\"teilnehmerIds\":[],\"erstellerId\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erstellerId").value(7))
                .andExpect(jsonPath("$.darfBearbeiten").value(true));

        ArgumentCaptor<KalenderEintrag> gespeichert = ArgumentCaptor.forClass(KalenderEintrag.class);
        verify(eintraege).save(gespeichert.capture());
        assertThat(gespeichert.getValue().getTitel()).isEqualTo(xss);
        assertThat(gespeichert.getValue().getErsteller()).isSameAs(max);
    }

    @Test
    @DisplayName("Neuer Termin: leerer Titel, fehlendes Datum, Ende vor Beginn und Überlänge geben 400")
    void ungueltigeEingaben() throws Exception {
        String[] koerper = {
                "{\"titel\":\"  \",\"datum\":\"2026-10-20\",\"ganztaegig\":true}",
                "{\"titel\":\"Aufmaß\",\"ganztaegig\":true}",
                "{\"titel\":\"Aufmaß\",\"datum\":\"2026-10-20\",\"startZeit\":\"10:00:00\",\"endeZeit\":\"09:00:00\",\"ganztaegig\":false}",
                "{\"titel\":\"" + "x".repeat(10_001) + "\",\"datum\":\"2026-10-20\",\"ganztaegig\":true}",
                "{\"titel\":\"Aufmaß\",\"beschreibung\":\"" + "x".repeat(10_001) + "\",\"datum\":\"2026-10-20\",\"ganztaegig\":true}",
                "{\"titel\":\"Aufmaß\",\"datum\":\"2026-10-20\",\"ganztaegig\":true,\"farbe\":\"'; DROP TABLE x; --\"}",
                "{\"titel\":\"Aufmaß\",\"datum\":\"2026-10-20\",\"ganztaegig\":true,\"farbe\":\"#12345\"}",
        };
        for (String k : koerper) {
            mvc.perform(post("/api/kalender").with(user(maxAnmeldung())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(k))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        verify(eintraege, never()).save(any());
    }

    @Test
    @DisplayName("Ganztägig: Uhrzeiten werden verworfen, auch wenn Ende vor Beginn mitkommt")
    void ganztaegigVerwirftZeiten() throws Exception {
        mvc.perform(post("/api/kalender").with(user(maxAnmeldung())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titel\":\"Urlaubsplanung\",\"datum\":\"2026-10-20\",\"startZeit\":\"10:00:00\","
                                + "\"endeZeit\":\"09:00:00\",\"ganztaegig\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startZeit").isEmpty())
                .andExpect(jsonPath("$.endeZeit").isEmpty());
    }

    // ---------- Hilfen ----------

    private static String body(String titel, String teilnehmer) {
        return "{\"titel\":\"" + titel + "\",\"datum\":\"2026-10-20\",\"startZeit\":\"08:00:00\","
                + "\"endeZeit\":\"09:00:00\",\"ganztaegig\":false,\"farbe\":\"#dc2626\",\"teilnehmerIds\":" + teilnehmer + "}";
    }

    private static FrontendUserPrincipal maxAnmeldung() {
        return anmeldung(70L, FrontendUserRole.USER);
    }

    private static FrontendUserPrincipal adminAnmeldung() {
        return anmeldung(71L, FrontendUserRole.ADMIN);
    }

    private static FrontendUserPrincipal anmeldung(Long id, FrontendUserRole rolle) {
        return new FrontendUserPrincipal(id, "benutzer" + id, "Max Mustermann", "", true, Set.of(rolle));
    }

    private static FrontendUserProfile profil(Mitarbeiter mitarbeiter) {
        FrontendUserProfile p = new FrontendUserProfile();
        p.setMitarbeiter(mitarbeiter);
        return p;
    }

    private static Mitarbeiter mitarbeiter(Long id, String vorname, String nachname) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname(vorname);
        m.setNachname(nachname);
        m.setAktiv(true);
        return m;
    }

    private static KalenderEintrag termin(Long id, Mitarbeiter ersteller, Set<Mitarbeiter> teilnehmer) {
        KalenderEintrag e = new KalenderEintrag();
        e.setId(id);
        e.setTitel("Termin " + id);
        e.setDatum(LocalDate.of(2026, 10, 20));
        e.setErsteller(ersteller);
        e.setTeilnehmer(new HashSet<>(teilnehmer));
        return e;
    }
}
