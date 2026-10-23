package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests für ZeitverwaltungController.
 * Schwerpunkt: Der Kalender liefert für Abwesenheiten die echte {@code abwesenheitId},
 * damit das Frontend Krankheit/Urlaub serverseitig löschen kann (Bug: ließ sich vorher
 * nur lokal aus dem UI entfernen).
 */
@WebMvcTest(ZeitverwaltungController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZeitverwaltungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private org.example.kalkulationsprogramm.repository.ZeitbuchungRepository zeitbuchungRepository;
    @MockitoBean
    private org.example.kalkulationsprogramm.repository.AbwesenheitRepository abwesenheitRepository;
    @MockitoBean
    private org.example.kalkulationsprogramm.repository.MitarbeiterRepository mitarbeiterRepository;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.FeiertagService feiertagService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.ZeitkontoService zeitkontoService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.ZeitkontoWechselService zeitkontoWechselService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.ProjektAuswertungPdfService projektAuswertungPdfService;
    @MockitoBean
    private org.example.kalkulationsprogramm.repository.ProjektRepository projektRepository;
    @MockitoBean
    private org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository arbeitsgangStundensatzRepository;
    @MockitoBean
    private org.example.kalkulationsprogramm.repository.ArbeitsgangRepository arbeitsgangRepository;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.ZeitbuchungAuditService auditService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.AngemeldeterMitarbeiterService angemeldeterMitarbeiter;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.MonatsSaldoService monatsSaldoService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.MonatsSaldoWarmupService monatsSaldoWarmupService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.TagesSollService tagesSollService;

    /**
     * Baut den MonatsSaldo, den der Kalender seit dem Korrektur-Bugfix als einzige
     * Quelle fuer die Monatssummen verwendet (vorher summierte der Controller die
     * Tageszeilen selbst und liess dabei Zeitkonto-Korrekturen aus).
     */
    private static org.example.kalkulationsprogramm.domain.MonatsSaldo monatsSaldo(
            int jahr, int monat, String ist, String soll, String abwesenheit, String feiertag, String korrektur) {
        org.example.kalkulationsprogramm.domain.MonatsSaldo saldo =
                new org.example.kalkulationsprogramm.domain.MonatsSaldo();
        saldo.setJahr(jahr);
        saldo.setMonat(monat);
        saldo.setIstStunden(new BigDecimal(ist));
        saldo.setSollStunden(new BigDecimal(soll));
        saldo.setAbwesenheitsStunden(new BigDecimal(abwesenheit));
        saldo.setFeiertagsStunden(new BigDecimal(feiertag));
        saldo.setKorrekturStunden(new BigDecimal(korrektur));
        saldo.setFestgeschrieben(false);
        return saldo;
    }

    /** Baut eine Je-Tag-Map mit einem konstanten Wert fuer jeden Tag im Zeitraum (inklusive). */
    private static Map<LocalDate, BigDecimal> konstanteJeTag(LocalDate von, LocalDate bis, BigDecimal wert) {
        Map<LocalDate, BigDecimal> ergebnis = new LinkedHashMap<>();
        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            ergebnis.put(tag, wert);
        }
        return ergebnis;
    }

    @Test
    void getKalender_LiefertEchteAbwesenheitIdFuerKrankheit() throws Exception {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        Abwesenheit krankheit = new Abwesenheit();
        krankheit.setId(42L);
        krankheit.setMitarbeiter(mitarbeiter);
        krankheit.setTyp(AbwesenheitsTyp.KRANKHEIT);
        krankheit.setDatum(LocalDate.of(2025, 6, 2)); // Montag
        krankheit.setStunden(new BigDecimal("5.00"));
        krankheit.setNotiz("Krankheit (abzgl. 3 h gearbeitet)");

        given(feiertagService.getFeiertageZwischen(any(), any())).willReturn(List.of());
        given(zeitkontoService.berechneSollstundenFuerMonat(anyLong(), any(Integer.class), any(Integer.class)))
                .willReturn(new BigDecimal("160.00"));
        given(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitAfter(anyLong(), any()))
                .willReturn(List.of());
        given(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(anyLong(), any(), any()))
                .willReturn(List.of(krankheit));
        given(tagesSollService.arbeitsSollJeTag(anyLong(), any(), any())).willReturn(
                konstanteJeTag(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30), new BigDecimal("8.00")));
        given(tagesSollService.feiertagsGutschriftJeTag(anyLong(), any(), any())).willReturn(
                konstanteJeTag(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30), BigDecimal.ZERO));
        given(monatsSaldoService.berechneOhneSpeichern(1L, 2025, 6))
                .willReturn(monatsSaldo(2025, 6, "0.00", "160.00", "5.00", "0.00", "0.00"));

        mockMvc.perform(get("/api/zeitverwaltung/kalender")
                        .param("mitarbeiterId", "1")
                        .param("jahr", "2025")
                        .param("monat", "6"))
                .andExpect(status().isOk())
                // Bug-Fix: echte positive ID wird mitgeliefert (für DELETE /api/abwesenheit/{id})
                .andExpect(jsonPath("$..abwesenheitId", hasItem(42)))
                // Anzeige-ID bleibt negativ, um Abwesenheiten von echten Buchungen zu trennen
                .andExpect(jsonPath("$..buchungen[?(@.typ=='KRANKHEIT')].id", hasItem(-42)));
    }

    @Test
    void updateBuchung_RaeumtPruefKennzeichenDesAutoStopsAb() throws Exception {
        // Sobald ein Mensch die vom Auto-Stop geschätzte Endezeit im Kalender
        // korrigiert (oder bestätigt), ist der Prüffall erledigt - die Buchung
        // darf nicht weiter in der Benachrichtigungs-Glocke hängen.
        Mitarbeiter bearbeiter = new Mitarbeiter();
        bearbeiter.setId(1L);
        bearbeiter.setVorname("Max");
        bearbeiter.setNachname("Mustermann");

        Zeitbuchung buchung = new Zeitbuchung();
        buchung.setId(1903L);
        buchung.setMitarbeiter(bearbeiter);
        buchung.setStartZeit(LocalDateTime.of(2026, 7, 29, 16, 48));
        buchung.setEndeZeit(LocalDateTime.of(2026, 7, 29, 20, 0));
        buchung.setAutomatischBeendet(true);
        buchung.setVersion(2);

        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(bearbeiter);
        given(zeitbuchungRepository.findById(1903L)).willReturn(Optional.of(buchung));
        given(zeitbuchungRepository.save(any(Zeitbuchung.class)))
                .willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/zeitverwaltung/buchungen/1903")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aenderungsgrund": "Korrektur im Zeiterfassungskalender",
                                  "endeZeit": "2026-07-29T17:00:00"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(buchung.isAutomatischBeendet()).isFalse();
        assertThat(buchung.getEndeZeit()).isEqualTo(LocalDateTime.of(2026, 7, 29, 17, 0));
    }

    @Test
    void getKalender_WiedereingliederungReduziertSollStundenAnArbeitstag() throws Exception {
        // Waehrend einer laufenden Wiedereingliederung (Stufenplan) ersetzt die
        // reduzierte Stundenzahl das normale Tagessoll - TagesSollService.arbeitsSoll
        // kapselt das (Task 3). Der Controller muss den Wert unveraendert
        // durchreichen statt selbst aus dem Zeitkonto neu zu rechnen.
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        given(feiertagService.getFeiertageZwischen(any(), any())).willReturn(List.of());
        given(zeitkontoService.berechneSollstundenFuerMonat(anyLong(), any(Integer.class), any(Integer.class)))
                .willReturn(new BigDecimal("120.00"));
        given(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitAfter(anyLong(), any()))
                .willReturn(List.of());
        given(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(anyLong(), any(), any()))
                .willReturn(List.of());
        given(tagesSollService.feiertagsGutschriftJeTag(anyLong(), any(), any())).willReturn(
                konstanteJeTag(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30), BigDecimal.ZERO));
        // 2.6.2025 (Montag, tage[1]) liegt in einer Wiedereingliederung mit 2h/Tag -
        // alle anderen Tage bleiben beim vollen Zeitkonto-Soll.
        Map<LocalDate, BigDecimal> sollJeTag = konstanteJeTag(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30),
                new BigDecimal("8.00"));
        sollJeTag.put(LocalDate.of(2025, 6, 2), new BigDecimal("2.00"));
        given(tagesSollService.arbeitsSollJeTag(anyLong(), any(), any())).willReturn(sollJeTag);
        given(monatsSaldoService.berechneOhneSpeichern(1L, 2025, 6))
                .willReturn(monatsSaldo(2025, 6, "0.00", "120.00", "0.00", "0.00", "0.00"));

        mockMvc.perform(get("/api/zeitverwaltung/kalender")
                        .param("mitarbeiterId", "1")
                        .param("jahr", "2025")
                        .param("monat", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tage[1].datum").value("2025-06-02"))
                .andExpect(jsonPath("$.tage[1].sollStunden").value(2.00));
    }

    @Test
    void getKalender_HalberFeiertagLiefertHalbeIstStundenUndStimmtMitMonatsuebersichtUeberein() throws Exception {
        // Bugfix (siehe Plan "Bewusste Verhaltensaenderungen", Punkt 1): bisher
        // zaehlte auch ein halber Feiertag (z.B. Heiligabend) mit den VOLLEN
        // Sollstunden als Ist-Stunden, waehrend sollStundenMonat
        // (ZeitkontoService.berechneSollstundenFuerMonat) den halben Feiertag
        // schon immer korrekt halbierte - Ergebnis: +4h Phantom-Ueberstunden pro
        // halbem Feiertag und ein Kalender, der der Monatsuebersicht widersprach.
        // Dieser Test bildet nach, was in einem Monat passiert, in dem der halbe
        // Feiertag der einzige arbeitsrelevante Tag ist und nicht gearbeitet
        // wird. Seit der Fachentscheidung 12.10.2026 bleibt am halben Feiertag
        // der halbe Tag zu arbeiten: Soll 8, Gutschrift 4 -> -4, keine
        // Phantom-Ueberstunden und kein "ganzer Tag frei".
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        org.example.kalkulationsprogramm.domain.Feiertag heiligabend =
                org.example.kalkulationsprogramm.domain.Feiertag.halberFeiertag(
                        LocalDate.of(2026, 12, 24), "Heiligabend");

        given(feiertagService.getFeiertageZwischen(any(), any())).willReturn(List.of(heiligabend));
        given(zeitkontoService.berechneSollstundenFuerMonat(anyLong(), any(Integer.class), any(Integer.class)))
                .willReturn(new BigDecimal("8.00"));
        given(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitAfter(anyLong(), any()))
                .willReturn(List.of());
        given(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(anyLong(), any(), any()))
                .willReturn(List.of());
        // Steht stellvertretend fuer einen Monat, in dem der halbe Feiertag der
        // einzige arbeitsrelevante Tag ist: TagesSollService liefert dort
        // Soll 8, Gutschrift 4 und damit arbeitsSoll 4.
        Map<LocalDate, BigDecimal> arbeitsSollJeTag = konstanteJeTag(LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31), BigDecimal.ZERO);
        arbeitsSollJeTag.put(LocalDate.of(2026, 12, 24), new BigDecimal("4.00"));
        given(tagesSollService.arbeitsSollJeTag(anyLong(), any(), any())).willReturn(arbeitsSollJeTag);
        Map<LocalDate, BigDecimal> feiertagsGutschriftJeTag = konstanteJeTag(LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31), BigDecimal.ZERO);
        feiertagsGutschriftJeTag.put(LocalDate.of(2026, 12, 24), new BigDecimal("4.00"));
        given(tagesSollService.feiertagsGutschriftJeTag(anyLong(), any(), any()))
                .willReturn(feiertagsGutschriftJeTag);
        // Monatssummen kommen seit dem Korrektur-Bugfix aus dem MonatsSaldo: der
        // halbe Feiertag steht dort als Gutschrift 4.00 gegen ein Soll von 8.00.
        given(monatsSaldoService.berechneOhneSpeichern(1L, 2026, 12))
                .willReturn(monatsSaldo(2026, 12, "0.00", "8.00", "0.00", "4.00", "0.00"));

        mockMvc.perform(get("/api/zeitverwaltung/kalender")
                        .param("mitarbeiterId", "1")
                        .param("jahr", "2026")
                        .param("monat", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tage[23].datum").value("2026-12-24"))
                // Bugfix: bisher 8.00 (voller Tag als Ist gezaehlt), jetzt korrekt 4.00
                .andExpect(jsonPath("$.tage[23].istStunden").value(4.00))
                .andExpect(jsonPath("$.tage[23].sollStunden").value(4.00))
                .andExpect(jsonPath("$.istStundenMonat").value(4.00))
                .andExpect(jsonPath("$.sollStundenMonat").value(8.00))
                // Kalender und Monatsuebersicht widersprechen sich nicht:
                // Soll 8 / Ist 4 (nur Gutschrift) -> der halbe Tag fehlt noch.
                .andExpect(jsonPath("$.differenz").value(-4.00));
    }

    @Test
    void getKalender_ZaehltZeitkontoKorrekturenInDieMonatsdifferenz() throws Exception {
        // Regression: Der Kalender summierte die Monatswerte selbst aus den Tageszeilen
        // (Feiertagsgutschrift + Buchungen + Abwesenheiten) und liess dabei die
        // Zeitkonto-Korrekturen aus, waehrend der Monatsabschluss ueber
        // MonatsSaldo.getGesamtIst() sie mitzaehlt. Das Frontend waehlt je nach
        // Abschlussstatus die eine oder die andere Quelle - derselbe Monat zeigte
        // dadurch offen -7,69 h und festgeschrieben -29,79 h.
        //
        // Der Fall bildet einen realen Januar nach: 127,06 h gebucht, 19,25 h
        // Abwesenheit, 15,40 h Feiertagsgutschrift, 169,40 h Soll und eine
        // Korrektur ueber -22,10 h.
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        given(feiertagService.getFeiertageZwischen(any(), any())).willReturn(List.of());
        given(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitAfter(anyLong(), any()))
                .willReturn(List.of());
        given(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(anyLong(), any(), any()))
                .willReturn(List.of());
        given(tagesSollService.arbeitsSollJeTag(anyLong(), any(), any())).willReturn(
                konstanteJeTag(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), BigDecimal.ZERO));
        given(tagesSollService.feiertagsGutschriftJeTag(anyLong(), any(), any())).willReturn(
                konstanteJeTag(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), BigDecimal.ZERO));
        given(monatsSaldoService.berechneOhneSpeichern(1L, 2026, 1))
                .willReturn(monatsSaldo(2026, 1, "127.06", "169.40", "19.25", "15.40", "-22.10"));

        mockMvc.perform(get("/api/zeitverwaltung/kalender")
                        .param("mitarbeiterId", "1")
                        .param("jahr", "2026")
                        .param("monat", "1"))
                .andExpect(status().isOk())
                // Die Korrektur ist jetzt eigenstaendig ausgewiesen, damit die
                // Monatssumme gegen die Tageszeilen erklaerbar bleibt.
                .andExpect(jsonPath("$.korrekturStundenMonat").value(-22.10))
                // 127,06 + 19,25 + 15,40 - 22,10 = 139,61 (vorher 161,71 ohne Korrektur)
                .andExpect(jsonPath("$.istStundenMonat").value(139.61))
                .andExpect(jsonPath("$.sollStundenMonat").value(169.40))
                // 139,61 - 169,40 = -29,79 (vorher faelschlich -7,69)
                .andExpect(jsonPath("$.differenz").value(-29.79));
    }

    @Test
    void updateBuchung_Gibt409WennMonatFestgeschrieben() throws Exception {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(1L);
        Zeitbuchung b = new Zeitbuchung();
        b.setId(100L);
        b.setMitarbeiter(m);
        b.setStartZeit(LocalDateTime.of(2025, 1, 15, 8, 0));

        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(m);
        given(zeitbuchungRepository.findById(100L)).willReturn(Optional.of(b));
        given(monatsSaldoService.isMonatFestgeschrieben(1L, 2025, 1)).willReturn(true);

        mockMvc.perform(put("/api/zeitverwaltung/buchungen/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aenderungsgrund\":\"Test\",\"notiz\":\"Korrektur\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Dieser Monat ist bereits festgeschrieben. Bitte setzen Sie zuerst den Monatsabschluss zurück."));
    }

    @Test
    void deleteBuchung_Gibt409WennMonatFestgeschrieben() throws Exception {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(1L);
        Zeitbuchung b = new Zeitbuchung();
        b.setId(100L);
        b.setMitarbeiter(m);
        b.setStartZeit(LocalDateTime.of(2025, 1, 15, 8, 0));

        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(m);
        given(zeitbuchungRepository.findById(100L)).willReturn(Optional.of(b));
        given(monatsSaldoService.isMonatFestgeschrieben(1L, 2025, 1)).willReturn(true);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/zeitverwaltung/buchungen/100"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Dieser Monat ist bereits festgeschrieben. Bitte setzen Sie zuerst den Monatsabschluss zurück."));
    }

    // ==================== Bearbeiter im Protokoll kommt aus der Anmeldung ====================

    private static final String KEIN_MITARBEITER =
            org.example.kalkulationsprogramm.service.AngemeldeterMitarbeiterService.KEIN_MITARBEITER;

    private static Mitarbeiter mitarbeiter(long id, String vorname, String nachname) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname(vorname);
        m.setNachname(nachname);
        return m;
    }

    private static Zeitbuchung buchungVon(Mitarbeiter m, long id) {
        Zeitbuchung b = new Zeitbuchung();
        b.setId(id);
        b.setMitarbeiter(m);
        b.setStartZeit(LocalDateTime.of(2026, 9, 1, 7, 0));
        b.setEndeZeit(LocalDateTime.of(2026, 9, 1, 15, 0));
        b.setVersion(1);
        return b;
    }

    @Test
    void updateBuchung_ProtokolliertAngemeldetenMitarbeiterUndIgnoriertBearbeiterIdImBody() throws Exception {
        Mitarbeiter angemeldet = mitarbeiter(7L, "Max", "Mustermann");
        Zeitbuchung buchung = buchungVon(mitarbeiter(3L, "Erika", "Musterfrau"), 500L);
        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(angemeldet);
        given(zeitbuchungRepository.findById(500L)).willReturn(Optional.of(buchung));
        given(zeitbuchungRepository.save(any(Zeitbuchung.class))).willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/zeitverwaltung/buchungen/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aenderungsgrund\":\"Tippfehler\",\"bearbeiterId\":999,\"notiz\":\"x\"}"))
                .andExpect(status().isOk());

        verify(auditService).protokolliereAenderung(eq(buchung), eq(angemeldet),
                eq(org.example.kalkulationsprogramm.domain.ErfassungsQuelle.DESKTOP), eq("Tippfehler"));
        verify(mitarbeiterRepository, never()).findById(999L);
    }

    @Test
    void updateBuchung_OhneZugeordnetenMitarbeiterGibt403UndAendertNichts() throws Exception {
        given(angemeldeterMitarbeiter.bearbeiter(any())).willThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, KEIN_MITARBEITER));

        mockMvc.perform(put("/api/zeitverwaltung/buchungen/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aenderungsgrund\":\"Tippfehler\",\"notiz\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(KEIN_MITARBEITER));

        verify(zeitbuchungRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void updateBuchung_OhneAenderungsgrundGibt400VorDerBearbeiterPruefung() throws Exception {
        mockMvc.perform(put("/api/zeitverwaltung/buchungen/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notiz\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Änderungsgrund ist ein Pflichtfeld"));

        verifyNoInteractions(angemeldeterMitarbeiter);
    }

    @Test
    void deleteBuchung_ProtokolliertStornoImmerMitAngemeldetemMitarbeiter() throws Exception {
        Mitarbeiter angemeldet = mitarbeiter(7L, "Max", "Mustermann");
        Zeitbuchung buchung = buchungVon(mitarbeiter(3L, "Erika", "Musterfrau"), 501L);
        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(angemeldet);
        given(zeitbuchungRepository.findById(501L)).willReturn(Optional.of(buchung));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/zeitverwaltung/buchungen/501")
                        .param("bearbeiterId", "999")
                        .param("grund", "'; DROP TABLE zeitbuchung; --"))
                .andExpect(status().isNoContent());

        verify(auditService).protokolliereStorno(eq(buchung), eq(angemeldet),
                eq(org.example.kalkulationsprogramm.domain.ErfassungsQuelle.DESKTOP),
                eq("'; DROP TABLE zeitbuchung; --"));
        verify(zeitbuchungRepository).deleteById(501L);
        verify(mitarbeiterRepository, never()).findById(999L);
    }

    @Test
    void deleteBuchung_OhneZugeordnetenMitarbeiterGibt403UndLoeschtNichts() throws Exception {
        given(angemeldeterMitarbeiter.bearbeiter(any())).willThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, KEIN_MITARBEITER));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/zeitverwaltung/buchungen/501"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(KEIN_MITARBEITER));

        verify(zeitbuchungRepository, never()).deleteById(anyLong());
        verifyNoInteractions(auditService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
    void updateBuchung_UngueltigeIdGibt404(long id) throws Exception {
        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(mitarbeiter(7L, "Max", "Mustermann"));
        given(zeitbuchungRepository.findById(id)).willReturn(Optional.empty());

        mockMvc.perform(put("/api/zeitverwaltung/buchungen/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aenderungsgrund\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Diese Zeitbuchung gibt es nicht mehr."));

        verifyNoInteractions(auditService);
    }

    @Test
    void createBuchung_ProtokolliertErstellungMitAngemeldetemMitarbeiter() throws Exception {
        Mitarbeiter angemeldet = mitarbeiter(7L, "Max", "Mustermann");
        Mitarbeiter betroffen = mitarbeiter(3L, "Erika", "Musterfrau");
        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(angemeldet);
        given(mitarbeiterRepository.findById(3L)).willReturn(Optional.of(betroffen));
        given(zeitbuchungRepository.save(any(Zeitbuchung.class))).willAnswer(inv -> {
            Zeitbuchung b = inv.getArgument(0);
            b.setId(900L);
            return b;
        });

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/zeitverwaltung/buchungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\":3,\"projektId\":-1,\"typ\":\"PAUSE\","
                                + "\"startZeit\":\"2026-09-01T12:00:00\",\"endeZeit\":\"2026-09-01T12:30:00\","
                                + "\"bearbeiterId\":999}"))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<Zeitbuchung> gespeichert = org.mockito.ArgumentCaptor.forClass(Zeitbuchung.class);
        verify(auditService).protokolliereErstellung(gespeichert.capture(), eq(angemeldet),
                eq(org.example.kalkulationsprogramm.domain.ErfassungsQuelle.DESKTOP));
        assertThat(gespeichert.getValue().getErfasstVon()).isSameAs(angemeldet);
        assertThat(gespeichert.getValue().getErfasstVia())
                .isEqualTo(org.example.kalkulationsprogramm.domain.ErfassungsQuelle.DESKTOP);
        assertThat(gespeichert.getValue().getVersion()).isEqualTo(1);
        verify(mitarbeiterRepository, never()).findById(999L);
    }

    @Test
    void createBuchung_OhneZugeordnetenMitarbeiterGibt403UndLegtNichtsAn() throws Exception {
        given(angemeldeterMitarbeiter.bearbeiter(any())).willThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, KEIN_MITARBEITER));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/zeitverwaltung/buchungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\":3,\"projektId\":-1,\"typ\":\"PAUSE\",\"startZeit\":\"2026-09-01T12:00:00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(KEIN_MITARBEITER));

        verify(zeitbuchungRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createBuchung_Gibt409WennMonatFestgeschrieben() throws Exception {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(1L);
        given(mitarbeiterRepository.findById(1L)).willReturn(Optional.of(m));
        given(monatsSaldoService.isMonatFestgeschrieben(1L, 2025, 1)).willReturn(true);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/zeitverwaltung/buchungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\":1,\"projektId\":-1,\"typ\":\"PAUSE\",\"startZeit\":\"2025-01-15T12:00:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Dieser Monat ist bereits festgeschrieben. Bitte setzen Sie zuerst den Monatsabschluss zurück."));
    }

    // ==================== Issue #269: Ende vor Beginn, Auswertung je Mitarbeiter ====================

    @Test
    void createBuchung_EndeVorBeginnGibt400UndLegtNichtsAn() throws Exception {
        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(mitarbeiter(7L, "Max", "Mustermann"));
        given(mitarbeiterRepository.findById(3L)).willReturn(Optional.of(mitarbeiter(3L, "Erika", "Musterfrau")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/zeitverwaltung/buchungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\":3,\"projektId\":-1,\"typ\":\"PAUSE\","
                                + "\"startZeit\":\"2026-09-01T22:00:00\",\"endeZeit\":\"2026-09-01T06:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ZeitverwaltungController.ENDE_VOR_BEGINN));

        verify(zeitbuchungRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void updateBuchung_EndeGleichBeginnGibt400UndProtokolliertNichts() throws Exception {
        Mitarbeiter m = mitarbeiter(1L, "Max", "Mustermann");
        Zeitbuchung b = new Zeitbuchung();
        b.setId(100L);
        b.setMitarbeiter(m);
        b.setStartZeit(LocalDateTime.of(2026, 9, 1, 8, 0));
        b.setEndeZeit(LocalDateTime.of(2026, 9, 1, 12, 0));
        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(m);
        given(zeitbuchungRepository.findById(100L)).willReturn(Optional.of(b));

        mockMvc.perform(put("/api/zeitverwaltung/buchungen/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aenderungsgrund\":\"Test\",\"endeZeit\":\"2026-09-01T08:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ZeitverwaltungController.ENDE_VOR_BEGINN));

        verify(zeitbuchungRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void projektAuswertung_FiltertNachMitarbeiterUndReichtFilterAnsPdfWeiter() throws Exception {
        org.example.kalkulationsprogramm.domain.Projekt projekt = new org.example.kalkulationsprogramm.domain.Projekt();
        projekt.setId(5L);
        projekt.setBauvorhaben("Geländer Musterstraße");
        Zeitbuchung vonMax = new Zeitbuchung();
        vonMax.setProjekt(projekt);
        vonMax.setMitarbeiter(mitarbeiter(1L, "Max", "Mustermann"));
        vonMax.setStartZeit(LocalDateTime.of(2026, 9, 1, 8, 0));
        vonMax.setAnzahlInStunden(new BigDecimal("2.00"));
        Zeitbuchung vonErika = new Zeitbuchung();
        vonErika.setProjekt(projekt);
        vonErika.setMitarbeiter(mitarbeiter(2L, "Erika", "Musterfrau"));
        vonErika.setStartZeit(LocalDateTime.of(2026, 9, 1, 8, 0));
        vonErika.setAnzahlInStunden(new BigDecimal("3.00"));
        given(zeitbuchungRepository.findByProjektId(5L)).willReturn(List.of(vonMax, vonErika));

        mockMvc.perform(get("/api/zeitverwaltung/auswertung/projekt/5").param("mitarbeiterId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taetigkeiten.length()").value(1))
                .andExpect(jsonPath("$.taetigkeiten[0].anzahlBuchungen").value(1));

        mockMvc.perform(get("/api/zeitverwaltung/auswertung/projekt/5/pdf").param("mitarbeiterId", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(projektAuswertungPdfService);
    }
}
