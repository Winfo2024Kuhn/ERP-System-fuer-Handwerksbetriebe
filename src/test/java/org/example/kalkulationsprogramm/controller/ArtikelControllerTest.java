package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHinweis;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Fertigungszustand;
import org.example.kalkulationsprogramm.domain.Herstellverfahren;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelDokumentService;
import org.example.kalkulationsprogramm.service.ArtikelImportService;
import org.example.kalkulationsprogramm.service.ArtikelMatchingService;
import org.example.kalkulationsprogramm.service.ArtikelPositionsPreisService;
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
    private ArtikelPositionsPreisService artikelPositionsPreisService;

    @MockBean
    private ArtikelDokumentService artikelDokumentService;

    @BeforeEach
    void setUp() {
        when(kategorieService.findeKategorieUndUnterkategorieIds(any())).thenReturn(List.of());
        // Neutraler Default, damit bestehende Tests den Preisvorschlag nicht
        // extra stubben muessen - siehe ArtikelSuchePreisvorschlagTest fuer den
        // tatsaechlich berechneten Vorschlag.
        when(artikelPositionsPreisService.berechne(any(), any()))
                .thenReturn(new ArtikelPositionsPreisService.ArtikelPositionsVorschlag(
                        "Stk", null, ArtikelPreisHinweis.KEIN_PREIS));
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

    // ==================================================================
    // Eine Zeile je Artikel statt einer Zeile je Lieferant
    // ==================================================================

    @Test
    void liefertNurEineZeileJeArtikelMitGuenstigstemPreis() throws Exception {
        Artikel artikel = artikelMitPreisen(10L,
                preis(1L, "Guenstig GmbH", "12.50", true),
                preis(2L, "Mittel GmbH", "15.00", true),
                preis(3L, "Teuer GmbH", "19.90", true));

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                // Frueher entstanden hier drei Zeilen fuer denselben Artikel.
                .andExpect(jsonPath("$.artikel", hasSize(1)))
                .andExpect(jsonPath("$.artikel[0].guenstigsterPreis").value(12.50))
                .andExpect(jsonPath("$.artikel[0].guenstigsterLieferantName").value("Guenstig GmbH"))
                .andExpect(jsonPath("$.artikel[0].anzahlLieferanten").value(3))
                // Alle Lieferanten bleiben verfuegbar, guenstigster zuerst.
                .andExpect(jsonPath("$.artikel[0].lieferantenpreise", hasSize(3)))
                .andExpect(jsonPath("$.artikel[0].lieferantenpreise[0].preis").value(12.50))
                .andExpect(jsonPath("$.artikel[0].lieferantenpreise[2].preis").value(19.90));
    }

    @Test
    void ignoriertVeraltetePreisstaende() throws Exception {
        // Ein alter, guenstigerer Preisstand darf die Kalkulation nicht mehr
        // beeinflussen - er dient nur noch dem Verlauf.
        Artikel artikel = artikelMitPreisen(11L,
                preis(1L, "Lieferant A", "5.00", false),
                preis(1L, "Lieferant A", "8.00", true));

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].guenstigsterPreis").value(8.00))
                .andExpect(jsonPath("$.artikel[0].anzahlLieferanten").value(1));
    }

    @Test
    void beruecksichtigtNurDenGefiltertenLieferanten() throws Exception {
        Artikel artikel = artikelMitPreisen(12L,
                preis(1L, "Guenstig GmbH", "12.50", true),
                preis(2L, "Teuer GmbH", "19.90", true));

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel").param("lieferant", "Teuer GmbH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].guenstigsterPreis").value(19.90))
                .andExpect(jsonPath("$.artikel[0].anzahlLieferanten").value(1));
    }

    // ==================================================================
    // Technische Merkmale und Eignungen
    // ==================================================================

    @Test
    void liefertVerfahrenZustandUndEignungen() throws Exception {
        Werkstoff werkstoff = new Werkstoff();
        werkstoff.setId(1L);
        werkstoff.setName("EN AW-6060");
        werkstoff.setAnzeigename("Aluminium EN AW-6060");
        // Aluminium wird nicht feuerverzinkt, laesst sich aber pulverbeschichten.
        werkstoff.setVerzinkungsgeeignet(false);
        werkstoff.setPulverbeschichtungsgeeignet(true);
        werkstoff.setBeschichtungshinweis("Nicht feuerverzinken.");

        ArtikelWerkstoffe artikel = new ArtikelWerkstoffe();
        artikel.setId(20L);
        artikel.setArtikelnummer("AL-RR-42.4-2");
        artikel.setProduktname("42.4x2");
        artikel.setWerkstoff(werkstoff);
        artikel.setProfilform(Profilform.RUNDROHR);
        artikel.setHerstellverfahren(Herstellverfahren.STRANGGEPRESST);
        artikel.setFertigungszustand(Fertigungszustand.KALTGEFERTIGT);
        artikel.setMassnorm("EN 755-8");
        artikel.setDurchmesser(new java.math.BigDecimal("42.40"));
        artikel.setWandstaerke(new java.math.BigDecimal("2.00"));
        artikel.setMasse(new java.math.BigDecimal("0.6854"));

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].artikelnummer").value("AL-RR-42.4-2"))
                .andExpect(jsonPath("$.artikel[0].massnorm").value("EN 755-8"))
                // Klartext statt nur Normnummer - das ist der Zweck der Enums.
                .andExpect(jsonPath("$.artikel[0].herstellverfahren.anzeigename").value("stranggepresst"))
                .andExpect(jsonPath("$.artikel[0].fertigungszustand.anzeigename").value("kaltgefertigt"))
                .andExpect(jsonPath("$.artikel[0].profilform.anzeigename").value("Rundrohr"))
                .andExpect(jsonPath("$.artikel[0].abmessung").value("42,4 x 2"))
                .andExpect(jsonPath("$.artikel[0].verzinkungsgeeignet").value(false))
                .andExpect(jsonPath("$.artikel[0].pulverbeschichtungsgeeignet").value(true));
    }

    @Test
    void artikelDarfEignungDesWerkstoffsUeberschreiben() throws Exception {
        Werkstoff werkstoff = new Werkstoff();
        werkstoff.setId(1L);
        werkstoff.setName("S235JR");
        werkstoff.setVerzinkungsgeeignet(true);
        werkstoff.setPulverbeschichtungsgeeignet(true);

        Artikel artikel = new Artikel();
        artikel.setId(21L);
        artikel.setWerkstoff(werkstoff);
        // Ausnahme am einzelnen Artikel, z.B. bereits bandverzinkte Ware.
        artikel.setVerzinkungsgeeignet(false);

        mockArtikelSuche(List.of(artikel));

        mockMvc.perform(get("/api/artikel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].verzinkungsgeeignet").value(false))
                .andExpect(jsonPath("$.artikel[0].pulverbeschichtungsgeeignet").value(true));
    }

    // ==================================================================
    // Detailseite
    // ==================================================================

    @Test
    void detailseiteLiefertLieferantenUndPreisverlauf() throws Exception {
        Artikel artikel = artikelMitPreisen(30L,
                preis(1L, "Guenstig GmbH", "10.00", true),
                preis(2L, "Teuer GmbH", "12.50", true),
                preis(2L, "Teuer GmbH", "11.00", false));

        when(artikelService.findeById(30L)).thenReturn(java.util.Optional.of(artikel));

        mockMvc.perform(get("/api/artikel/30/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lieferanten", hasSize(2)))
                .andExpect(jsonPath("$.lieferanten[0].lieferantName").value("Guenstig GmbH"))
                .andExpect(jsonPath("$.lieferanten[0].guenstigster").value(true))
                // 12,50 liegt 25 Prozent ueber dem guenstigsten Preis von 10,00.
                .andExpect(jsonPath("$.lieferanten[1].aufschlagProzent").value(25.0))
                // Der Verlauf enthaelt auch den abgeloesten Preisstand.
                .andExpect(jsonPath("$.preisverlauf", hasSize(3)));
    }

    @Test
    void detailseiteAntwortetMit404BeiUnbekanntemArtikel() throws Exception {
        when(artikelService.findeById(999L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/artikel/999/detail"))
                .andExpect(status().isNotFound());
    }

    @Test
    void liefertFilteroptionenMitErklaerung() throws Exception {
        mockMvc.perform(get("/api/artikel/filteroptionen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.herstellverfahren[0].wert").value("NAHTLOS"))
                .andExpect(jsonPath("$.herstellverfahren[0].name").value("nahtlos"))
                .andExpect(jsonPath("$.fertigungszustand").isArray())
                .andExpect(jsonPath("$.profilform").isArray());
    }

    // ==================================================================
    // Zerlegung der Sucheingabe
    // ==================================================================

    @Test
    void zerlegtSuchbegriffInEinzelneWoerter() {
        // Die Abmessung darf zusammen oder getrennt geschrieben sein.
        assertEquals(List.of("rundrohr", "42.4", "2"),
                ArtikelController.zerlegeSuchbegriff("rundrohr 42.4 x2"));
        assertEquals(List.of("rundrohr", "42.4", "2"),
                ArtikelController.zerlegeSuchbegriff("Rundrohr 42.4x2"));
        assertEquals(List.of("60", "40", "3"),
                ArtikelController.zerlegeSuchbegriff("60x40x3"));
        // Mehrfachnennungen und Leerzeichen duerfen nicht stoeren.
        assertEquals(List.of("rohr", "42.4"),
                ArtikelController.zerlegeSuchbegriff("  rohr   rohr  42.4 "));
        assertEquals(List.of(), ArtikelController.zerlegeSuchbegriff("   "));
        assertEquals(List.of(), ArtikelController.zerlegeSuchbegriff(null));
    }

    @Test
    void sucheVerkraftetSonderzeichenUndUeberlangeEingaben() throws Exception {
        mockArtikelSuche(List.of());

        mockMvc.perform(get("/api/artikel").param("q", "'; DROP TABLE artikel; --"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/artikel").param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/artikel").param("q", "a".repeat(10_001)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/artikel").param("herstellverfahren", "GIBT_ES_NICHT"))
                .andExpect(status().isOk());
    }

    // ==================================================================
    // Hilfsmethoden
    // ==================================================================

    private Artikel artikelMitPreisen(Long artikelId, LieferantenArtikelPreise... preise) {
        Artikel artikel = new Artikel();
        artikel.setId(artikelId);
        for (LieferantenArtikelPreise p : preise) {
            p.setArtikel(artikel);
            artikel.getArtikelpreis().add(p);
        }
        return artikel;
    }

    private LieferantenArtikelPreise preis(Long lieferantId, String name, String betrag, boolean aktuell) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(lieferantId);
        lieferant.setLieferantenname(name);
        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setLieferant(lieferant);
        lap.setPreis(new java.math.BigDecimal(betrag));
        lap.setPreisAenderungsdatum(new java.util.Date());
        lap.setAktuell(aktuell);
        return lap;
    }

    private void mockArtikelSuche(List<Artikel> artikel) {
        when(artikelService.suche(any(), any())).thenReturn(new PageImpl<>(artikel));
    }
}
