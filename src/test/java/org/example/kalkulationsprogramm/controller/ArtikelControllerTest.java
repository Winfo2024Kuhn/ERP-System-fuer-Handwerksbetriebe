package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Werkstoff;
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

    private void mockArtikelSuche(List<Artikel> artikel) {
        when(artikelService.suche(any(), any())).thenReturn(new PageImpl<>(artikel));
    }
}
