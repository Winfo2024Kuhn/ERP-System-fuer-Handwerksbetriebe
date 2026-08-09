package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelImportService;
import org.example.kalkulationsprogramm.service.ArtikelMatchingService;
import org.example.kalkulationsprogramm.service.ArtikelPositionsPreisService;
import org.example.kalkulationsprogramm.service.ArtikelServiceContract;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Belegt, dass jede Trefferzeile der Suche bereits den Preisvorschlag fuer
 * eine Dokumentposition mitbringt - siehe {@link ArtikelPositionsPreisService}.
 *
 * <p>Der Service wird hier bewusst NICHT gemockt, sondern als echter Bean
 * importiert: Er ist zustandslos und ohne Abhaengigkeiten, und diese Tests
 * wollen den tatsaechlich berechneten Preis pruefen, nicht nur, dass
 * irgendein Wert durchgereicht wird.
 */
@WebMvcTest(ArtikelController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ArtikelPositionsPreisService.class)
class ArtikelSuchePreisvorschlagTest {

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

    @Test
    void liefertPreisvorschlagUndTexteJeTreffer() throws Exception {
        ArtikelWerkstoffe traeger = new ArtikelWerkstoffe();
        traeger.setId(7L);
        traeger.setProduktname("T-Stahl");
        traeger.setKurzbeschreibung("T-Stahl 40x40 Lager");
        traeger.setBeschreibung("<p>T-Stahl 40 x 40 x 5 mm</p>");
        traeger.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        traeger.setMasse(new BigDecimal("2.0000"));
        traeger.setVerkaufsaufschlagProzent(new BigDecimal("40.00"));
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(traeger);
        preis.setPreis(new BigDecimal("3.00"));
        preis.setAktuell(true);
        traeger.getArtikelpreis().add(preis);

        when(artikelService.suche(any(), any()))
                .thenReturn(new PageImpl<>(List.of(traeger)));

        mockMvc.perform(get("/api/artikel").param("q", "T-Stahl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].kurzbeschreibung").value("T-Stahl 40x40 Lager"))
                .andExpect(jsonPath("$.artikel[0].positionsEinheit").value("lfm"))
                .andExpect(jsonPath("$.artikel[0].positionsEinzelpreis").value(8.40))
                .andExpect(jsonPath("$.artikel[0].preisHinweis").value("OK"));
    }

    @Test
    void meldetFehlendenAufschlagInDerTrefferzeile() throws Exception {
        Artikel rohr = new Artikel();
        rohr.setId(8L);
        rohr.setProduktname("Rundrohr");
        rohr.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(rohr);
        preis.setPreis(new BigDecimal("6.00"));
        preis.setAktuell(true);
        rohr.getArtikelpreis().add(preis);

        when(artikelService.suche(any(), any())).thenReturn(new PageImpl<>(List.of(rohr)));

        mockMvc.perform(get("/api/artikel").param("q", "Rundrohr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].preisHinweis").value("KEIN_AUFSCHLAG"))
                .andExpect(jsonPath("$.artikel[0].positionsEinzelpreis").value(6.00));
    }

    @Test
    void laesstDenPreisLeerWennKeinerHinterlegtIst() throws Exception {
        Artikel werkstoff = new Artikel();
        werkstoff.setId(9L);
        werkstoff.setProduktname("Vierkantrohr");
        werkstoff.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);

        when(artikelService.suche(any(), any())).thenReturn(new PageImpl<>(List.of(werkstoff)));

        mockMvc.perform(get("/api/artikel").param("q", "Vierkant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikel[0].positionsEinzelpreis").doesNotExist())
                .andExpect(jsonPath("$.artikel[0].preisHinweis").value("KEIN_PREIS"));
    }
}
