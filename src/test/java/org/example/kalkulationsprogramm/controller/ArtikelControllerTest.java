package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHistorie;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.ArtikelPreisHistorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelImportService;
import org.example.kalkulationsprogramm.service.ArtikelMatchingService;
import org.example.kalkulationsprogramm.service.ArtikelServiceContract;
import org.example.kalkulationsprogramm.service.KategorieService;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArtikelController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtikelControllerTest {

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
    private ArtikelPreisHistorieRepository artikelPreisHistorieRepository;

    @BeforeEach
    void setUp() {
        when(kategorieService.findeKategorieUndUnterkategorieIds(any())).thenReturn(List.of());
    }

    @Test
    void returnsWerkstoffInResponse() throws Exception {
        Werkstoff werkstoff = new Werkstoff();
        werkstoff.setId(5L);
        werkstoff.setName("Aluminium");

        Artikel artikel = new Artikel();
        artikel.setId(1L);
        artikel.setProduktlinie("Linie");
        artikel.setProduktname("Name");
        artikel.setProdukttext("Text");
        artikel.setVerpackungseinheit(1L);
        artikel.setPreiseinheit("Stk");
        artikel.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        artikel.setWerkstoff(werkstoff);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(2L);
        lieferant.setLieferantenname("Lieferant");
        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(artikel);
        lap.setExterneArtikelnummer("A-1");
        lap.setLieferant(lieferant);
        lap.setPreis(java.math.BigDecimal.ONE);
        lap.setPreisAenderungsdatum(new java.util.Date());
        artikel.getArtikelpreis().add(lap);

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel", hasSize(1)))
                .andExpect(jsonPath("$.artikel[0].werkstoffId").value(5L))
                .andExpect(jsonPath("$.artikel[0].werkstoffName").value("Aluminium"))
                .andExpect(jsonPath("$.artikel[0].verrechnungseinheit.name").value("KILOGRAMM"))
                .andExpect(jsonPath("$.gesamt").value(1));
    }

    @Test
    void returnsArticleWithoutPrice() throws Exception {
        Artikel artikel = new Artikel();
        artikel.setId(2L);
        artikel.setExterneArtikelnummer("A-2");
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].id").value(2L))
                .andExpect(jsonPath("$.artikel[0].preis", nullValue()))
                .andExpect(jsonPath("$.artikel[0].lieferantenname", nullValue()))
                .andExpect(jsonPath("$.artikel[0].verrechnungseinheit.name").value("STUECK"));
    }

    @Test
    void doesNotExposeSupplierWhenPriceMissing() throws Exception {
        Artikel artikel = new Artikel();
        artikel.setId(6L);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(9L);
        lieferant.setLieferantenname("Sup");
        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(artikel);
        lap.setLieferant(lieferant);
        lap.setExterneArtikelnummer("A-6");
        artikel.getArtikelpreis().add(lap);

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].lieferantenname", nullValue()))
                .andExpect(jsonPath("$.artikel[0].lieferantenpreise", hasSize(0)));
    }

    @Test
    void returnsArticleWhenPriceDateMissing() throws Exception {
        Artikel artikel = new Artikel();
        artikel.setId(4L);
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Lief");
        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(artikel);
        lap.setLieferant(lieferant);
        lap.setPreis(java.math.BigDecimal.TEN);
        lap.setExterneArtikelnummer("A-4");
        artikel.getArtikelpreis().add(lap);

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].preis").value(10))
                .andExpect(jsonPath("$.artikel[0].preisDatum", nullValue()));
    }

    @Test
    void returnsKgProMeter() throws Exception {
        ArtikelWerkstoffe artikel = new ArtikelWerkstoffe();
        artikel.setId(3L);
        artikel.setMasse(new java.math.BigDecimal("1.23"));
        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].kgProMeter").value(1.23));
    }

    @Test
    void returnsProduktlinienWithoutLieferant1() throws Exception {
        when(artikelService.findeProduktlinienOhneLieferant(1L)).thenReturn(List.of("DIN A", "din B", "DIN A"));

        mockMvc.perform(get("/api/artikel/produktlinien"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value("DIN A"))
                .andExpect(jsonPath("$[1]").value("din B"));
    }

    @Test
    void aggregatesMultipleSuppliersIntoOneRowWithAveragePrice() throws Exception {
        Artikel artikel = new Artikel();
        artikel.setId(10L);
        artikel.setProduktname("Rohr 40x40x2");
        artikel.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        artikel.setDurchschnittspreisNetto(new java.math.BigDecimal("4.8250"));
        artikel.setDurchschnittspreisMenge(new java.math.BigDecimal("1245.000"));
        artikel.setDurchschnittspreisAktualisiertAm(java.time.LocalDateTime.of(2026, 4, 21, 10, 0));

        Lieferanten a = new Lieferanten();
        a.setId(1L);
        a.setLieferantenname("Stahl GmbH");
        Lieferanten b = new Lieferanten();
        b.setId(2L);
        b.setLieferantenname("Metall AG");

        LieferantenArtikelPreise p1 = new LieferantenArtikelPreise();
        p1.setArtikel(artikel);
        p1.setLieferant(a);
        p1.setPreis(new java.math.BigDecimal("5.20"));
        p1.setExterneArtikelnummer("A-1");
        p1.setPreisAenderungsdatum(new java.util.Date());
        LieferantenArtikelPreise p2 = new LieferantenArtikelPreise();
        p2.setArtikel(artikel);
        p2.setLieferant(b);
        p2.setPreis(new java.math.BigDecimal("4.90"));
        p2.setExterneArtikelnummer("B-1");
        p2.setPreisAenderungsdatum(new java.util.Date());
        artikel.getArtikelpreis().add(p1);
        artikel.getArtikelpreis().add(p2);

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel", hasSize(1)))
                .andExpect(jsonPath("$.artikel[0].anzahlLieferanten").value(2))
                .andExpect(jsonPath("$.artikel[0].lieferantenpreise", hasSize(2)))
                .andExpect(jsonPath("$.artikel[0].durchschnittspreisNetto").value(4.8250))
                .andExpect(jsonPath("$.artikel[0].durchschnittspreisMenge").value(1245.000))
                .andExpect(jsonPath("$.artikel[0].lieferantenname").value("Metall AG"));
    }

    @Test
    void returnsPriceHistoryForArticle() throws Exception {
        Artikel artikel = new Artikel();
        artikel.setId(42L);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Metall AG");

        ArtikelPreisHistorie eintrag = new ArtikelPreisHistorie();
        eintrag.setId(100L);
        eintrag.setArtikel(artikel);
        eintrag.setLieferant(lieferant);
        eintrag.setPreis(new java.math.BigDecimal("4.9000"));
        eintrag.setMenge(new java.math.BigDecimal("500.000"));
        eintrag.setEinheit(Verrechnungseinheit.KILOGRAMM);
        eintrag.setQuelle(PreisQuelle.RECHNUNG);
        eintrag.setExterneNummer("B-1");
        eintrag.setBelegReferenz("RE-2026-0042");
        eintrag.setErfasstAm(java.time.LocalDateTime.of(2026, 4, 20, 12, 0));

        when(artikelPreisHistorieRepository.findByArtikel_IdOrderByErfasstAmDesc(42L))
                .thenReturn(List.of(eintrag));

        mockMvc.perform(get("/api/artikel/42/preis-historie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].preis").value(4.9000))
                .andExpect(jsonPath("$[0].menge").value(500.000))
                .andExpect(jsonPath("$[0].einheit.name").value("KILOGRAMM"))
                .andExpect(jsonPath("$[0].quelle").value("RECHNUNG"))
                .andExpect(jsonPath("$[0].lieferantName").value("Metall AG"))
                .andExpect(jsonPath("$[0].belegReferenz").value("RE-2026-0042"));
    }

    @Test
    void returnsEmptyPriceHistoryForUnknownArticle() throws Exception {
        when(artikelPreisHistorieRepository.findByArtikel_IdOrderByErfasstAmDesc(999L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/artikel/999/preis-historie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private void mockArtikelSuche(List<Artikel> artikel) {
        when(artikelService.suche(any(), any())).thenReturn(new PageImpl<>(artikel));
    }
}
