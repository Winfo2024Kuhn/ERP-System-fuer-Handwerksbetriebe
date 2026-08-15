package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHinweis;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelDokumentService;
import org.example.kalkulationsprogramm.service.ArtikelImportService;
import org.example.kalkulationsprogramm.service.ArtikelMatchingService;
import org.example.kalkulationsprogramm.service.ArtikelPositionsPreisService;
import org.example.kalkulationsprogramm.service.ArtikelServiceContract;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArtikelController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtikelDokumenttexteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtikelServiceContract artikelService;

    @MockBean
    private ArtikelImportService artikelImportService;

    @MockBean
    private LieferantenRepository lieferantenRepository;

    @MockBean
    private WerkstoffRepository werkstoffRepository;

    @MockBean
    private ArtikelMatchingService artikelMatchingService;

    @MockBean
    private KategorieService kategorieService;

    @MockBean
    private ArtikelPositionsPreisService artikelPositionsPreisService;

    @MockBean
    private ArtikelDokumentService artikelDokumentService;

    @BeforeEach
    void setUp() {
        // Neutraler Default, damit diese Tests den Preisvorschlag nicht extra
        // stubben muessen - siehe ArtikelSuchePreisvorschlagTest fuer den
        // tatsaechlich berechneten Vorschlag.
        when(artikelPositionsPreisService.berechne(any(), any()))
                .thenReturn(new ArtikelPositionsPreisService.ArtikelPositionsVorschlag(
                        "Stk", null, ArtikelPreisHinweis.KEIN_PREIS));
    }

    @Test
    void schreibtDieTexteUndAntwortetMitDemArtikel() throws Exception {
        Artikel gespeichert = new Artikel();
        gespeichert.setId(7L);
        gespeichert.setKurzbeschreibung("Rundrohr 42,4 Lager");
        when(artikelService.aktualisiereDokumenttexte(eq(7L), any())).thenReturn(gespeichert);

        mockMvc.perform(patch("/api/artikel/7/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kurzbeschreibung":"Rundrohr 42,4 Lager",
                                 "beschreibung":"<p>Rundrohr</p>",
                                 "verkaufsaufschlagProzent":40.00}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kurzbeschreibung").value("Rundrohr 42,4 Lager"));
    }

    @Test
    void antwortetNachDemSpeichernMitDerVollenPreisSicht() throws Exception {
        // Regression: Frueher antwortete der Endpoint ueber toDto(artikel, null)
        // - fuer JEDEN Artikel stand preisHinweis=KEIN_PREIS, eine leere
        // Lieferantenliste und anzahlLieferanten=0 in der Antwort, egal wie
        // viele Preise gepflegt sind. Die Detailseite kaschierte das nur durch
        // einen Re-Fetch.
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(3L);
        lieferant.setLieferantenname("Muster Stahlhandel");

        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setLieferant(lieferant);
        preis.setPreis(new BigDecimal("5.75"));

        Artikel gespeichert = new Artikel();
        gespeichert.setId(7L);
        gespeichert.getArtikelpreis().add(preis);
        when(artikelService.aktualisiereDokumenttexte(eq(7L), any())).thenReturn(gespeichert);

        // Nur der mappeArtikelZuDto-Weg reicht den guenstigsten
        // Lieferantenpreis in die Vorschlagsrechnung - toDto(artikel, null)
        // bliebe beim KEIN_PREIS-Default aus dem setUp haengen.
        when(artikelPositionsPreisService.berechne(any(), same(preis)))
                .thenReturn(new ArtikelPositionsPreisService.ArtikelPositionsVorschlag(
                        "lfm", new BigDecimal("5.75"), ArtikelPreisHinweis.OK));

        mockMvc.perform(patch("/api/artikel/7/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kurzbeschreibung\":\"Handlauf-Rohr Lager\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preisHinweis").value("OK"))
                .andExpect(jsonPath("$.positionsEinzelpreis").value(5.75))
                .andExpect(jsonPath("$.anzahlLieferanten").value(1))
                .andExpect(jsonPath("$.guenstigsterPreis").value(5.75))
                .andExpect(jsonPath("$.lieferantenpreise[0].lieferantName").value("Muster Stahlhandel"));
    }

    @Test
    void antwortetMit404BeiUnbekannterId() throws Exception {
        when(artikelService.aktualisiereDokumenttexte(eq(999L), any()))
                .thenThrow(new NotFoundException("Artikel 999 nicht gefunden"));

        mockMvc.perform(patch("/api/artikel/999/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kurzbeschreibung\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void antwortetMit400BeiUeberlangerEingabe() throws Exception {
        when(artikelService.aktualisiereDokumenttexte(eq(7L), any()))
                .thenThrow(new IllegalArgumentException("Die Beschreibung darf hoechstens 10000 Zeichen lang sein."));

        mockMvc.perform(patch("/api/artikel/7/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beschreibung\":\"" + "x".repeat(10_001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reichtDenBodyAnDenServiceWeiter() throws Exception {
        Artikel gespeichert = new Artikel();
        gespeichert.setId(7L);
        gespeichert.setBeschreibung("<p>Rohr</p>");
        when(artikelService.aktualisiereDokumenttexte(eq(7L), any())).thenReturn(gespeichert);

        mockMvc.perform(patch("/api/artikel/7/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beschreibung\":\"<p>Rohr</p><script>alert(1)</script>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beschreibung").value(not(containsString("script"))));
    }

    // ==================================================================
    // Ungueltige IDs (TESTING_SECURITY.md: negativ, 0, Long.MAX_VALUE)
    // ==================================================================

    @Test
    void antwortetMit404BeiNegativerId() throws Exception {
        when(artikelService.aktualisiereDokumenttexte(eq(-1L), any()))
                .thenThrow(new NotFoundException("Artikel -1 nicht gefunden"));

        mockMvc.perform(patch("/api/artikel/-1/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kurzbeschreibung\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void antwortetMit404BeiIdGleich0() throws Exception {
        when(artikelService.aktualisiereDokumenttexte(eq(0L), any()))
                .thenThrow(new NotFoundException("Artikel 0 nicht gefunden"));

        mockMvc.perform(patch("/api/artikel/0/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kurzbeschreibung\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void antwortetMit404BeiMaximalGrosserId() throws Exception {
        when(artikelService.aktualisiereDokumenttexte(eq(Long.MAX_VALUE), any()))
                .thenThrow(new NotFoundException("Artikel " + Long.MAX_VALUE + " nicht gefunden"));

        mockMvc.perform(patch("/api/artikel/" + Long.MAX_VALUE + "/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kurzbeschreibung\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void behandeltSqlInjectionAlsGewoehnlichenText() throws Exception {
        Artikel gespeichert = new Artikel();
        gespeichert.setId(7L);
        gespeichert.setKurzbeschreibung("'; DROP TABLE artikel; --");
        when(artikelService.aktualisiereDokumenttexte(eq(7L), any())).thenReturn(gespeichert);

        mockMvc.perform(patch("/api/artikel/7/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kurzbeschreibung\":\"'; DROP TABLE artikel; --\"}"))
                .andExpect(status().isOk());
    }
}
