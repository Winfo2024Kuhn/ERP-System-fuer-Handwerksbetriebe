package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.dto.KassenbuchungDto;
import org.example.kalkulationsprogramm.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({KassenbuchungController.class, BelegController.class})
@AutoConfigureMockMvc(addFilters = false)
class KassenbuchungControllerTest {
    @Autowired MockMvc mvc;
    @MockBean BelegService belege;
    @MockBean KassenbuchungService buchungen;
    @MockBean MwstRechnerService mwst;

    private void auth() {
        var caller = new Mitarbeiter(); caller.setId(42L);
        when(belege.findCaller(any(), any())).thenReturn(caller);
        when(belege.darfScannen(caller)).thenReturn(true);
    }
    private MockMultipartFile daten(String json) {
        return new MockMultipartFile("daten", "", "application/json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private String request() {
        return "{\"art\":\"VON_BANK_GEHOLT\",\"betragBrutto\":119,\"belegDatum\":\"2026-09-14\"}";
    }

    @Test void keinRechtVerbietetBuchungUndListe() throws Exception {
        mvc.perform(multipart("/api/buchhaltung/kassenbuch/buchungen").file(daten(request())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/buchhaltung/kassenbuch/offene-ausgangsrechnungen"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(buchungen);
    }

    @Test void fehlenderBetragIst400() throws Exception {
        auth();
        when(buchungen.buche(argThat(r -> r.getBetragBrutto() == null), isNull(), any()))
                .thenThrow(new IllegalArgumentException("Betrag fehlt oder ist nicht positiv"));
        mvc.perform(multipart("/api/buchhaltung/kassenbuch/buchungen").file(daten("{\"art\":\"VON_BANK_GEHOLT\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Betrag fehlt oder ist nicht positiv"));
    }

    @Test void unterdeckungEnthaeltSaldoFelder() throws Exception {
        auth();
        when(buchungen.buche(any(), isNull(), any())).thenThrow(new KasseUnterdeckungException(new BigDecimal("-19"), BigDecimal.TEN));
        mvc.perform(multipart("/api/buchhaltung/kassenbuch/buchungen").file(daten(request())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.projizierterSaldo").value(-19)).andExpect(jsonPath("$.mindestbestand").value(10));
    }

    @Test void abgeschlossenerMonatEnthaeltHinweis() throws Exception {
        auth();
        when(buchungen.buche(any(), isNull(), any())).thenThrow(new KassenbuchGesperrtException("Monat abgeschlossen", "Laufenden Monat wählen"));
        mvc.perform(multipart("/api/buchhaltung/kassenbuch/buchungen").file(daten(request())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Monat abgeschlossen"))
                .andExpect(jsonPath("$.hinweis").value("Laufenden Monat wählen"));
    }

    @Test void fehlendesStandardkontoIst404() throws Exception {
        auth();
        when(buchungen.buche(any(), isNull(), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Konto 1200 bitte in den Stammdaten anlegen"));
        mvc.perform(multipart("/api/buchhaltung/kassenbuch/buchungen").file(daten(request())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Konto 1200 bitte in den Stammdaten anlegen"));
    }

    @Test void buchungLiefertDtoUndUebergibtDatei() throws Exception {
        auth();
        Beleg beleg = new Beleg(); beleg.setId(1L);
        when(buchungen.buche(any(), any(), any())).thenReturn(beleg);
        when(belege.toDto(beleg)).thenReturn(BelegDto.Response.builder().id(1L).build());
        mvc.perform(multipart("/api/buchhaltung/kassenbuch/buchungen")
                        .file(daten(request())).file(new MockMultipartFile("datei", "beleg.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        verify(buchungen).buche(argThat(r -> r.getArt().equals("VON_BANK_GEHOLT")), argThat(f -> f.getOriginalFilename().equals("beleg.pdf")), any());
    }

    @Test void offeneRechnungenLiefertListe() throws Exception {
        auth();
        when(buchungen.offeneAusgangsrechnungen()).thenReturn(List.of(KassenbuchungDto.OffeneRechnung.builder()
                .id(7L).dokumentNummer("R-MUSTER-7").kundeName("Max Mustermann").build()));
        mvc.perform(get("/api/buchhaltung/kassenbuch/offene-ausgangsrechnungen"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].kundeName").value("Max Mustermann"));
    }

    @Test void unerwarteterFehlerGibtKeineInternenDatenPreis() throws Exception {
        auth();
        when(buchungen.buche(any(), isNull(), any())).thenThrow(new IllegalStateException("Interner Datenbankfehler"));
        mvc.perform(multipart("/api/buchhaltung/kassenbuch/buchungen").file(daten(request())))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("Anlegen fehlgeschlagen"));
    }

    @ParameterizedTest @CsvSource({"KASSE_EINNAHME,VON_BANK_GEHOLT", "KASSE_AUSGABE,ZUR_BANK_GEBRACHT", "PRIVATEINLAGE,EIGENES_GELD_EINGELEGT", "PRIVATENTNAHME,GELD_PRIVAT_ENTNOMMEN"})
    void alterEndpointVerwendetGemeinsamenPfad(String kategorie, String art) throws Exception {
        auth();
        Beleg b = new Beleg(); b.setId(1L);
        when(buchungen.bucheUmbuchung(any(), any())).thenReturn(b);
        when(belege.toDto(b)).thenReturn(BelegDto.Response.builder().id(1L).build());
        mvc.perform(post("/api/buchhaltung/umbuchungen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"belegKategorie\":\"" + kategorie + "\",\"belegDatum\":\"2026-09-14\",\"betragBrutto\":119,\"sachkontoId\":1200,\"beschreibung\":\"Muster\",\"notiz\":\"Testnotiz\",\"zahlungsart\":\"Bar\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        verify(buchungen).bucheUmbuchung(argThat(r -> kategorie.equals(r.getBelegKategorie()) && r.getBetragBrutto().compareTo(new BigDecimal("119")) == 0
                && r.getSachkontoId().equals(1200L) && "Muster".equals(r.getBeschreibung()) && "Testnotiz".equals(r.getNotiz()) && "Bar".equals(r.getZahlungsart())), any());
        verify(belege, never()).createUmbuchung(any(), any());
    }

    @Test void bankBuchungBleibtAufAltemPfad() throws Exception {
        auth();
        Beleg b = new Beleg();
        when(belege.createUmbuchung(any(), any())).thenReturn(b);
        when(belege.toDto(b)).thenReturn(BelegDto.Response.builder().id(2L).build());
        mvc.perform(post("/api/buchhaltung/umbuchungen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"belegKategorie\":\"BANK\",\"betragBrutto\":119}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2));
        verifyNoInteractions(buchungen);
    }
}
