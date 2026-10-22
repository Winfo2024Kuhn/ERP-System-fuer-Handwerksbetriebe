package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHinweis;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArtikelController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtikelStammdatenControllerTest {

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
        when(artikelPositionsPreisService.berechne(any(), any(), any()))
                .thenReturn(new ArtikelPositionsPreisService.ArtikelPositionsVorschlag(
                        "Stk", null, ArtikelPreisHinweis.KEIN_PREIS));
    }

    @Test
    void aendertDieStammdatenUndAntwortetMitEanUndStand() throws Exception {
        Artikel gespeichert = new Artikel();
        gespeichert.setId(7L);
        gespeichert.setProduktname("Rundrohr 42,4 x 2");
        gespeichert.setEan("4006381333931");
        gespeichert.setArtikelnummer("ST-RR-042");
        when(artikelService.aktualisiereStammdaten(eq(7L), any())).thenReturn(gespeichert);

        mockMvc.perform(put("/api/artikel/7/stammdaten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":3,"produktname":"Rundrohr 42,4 x 2","artikelnummer":"ST-RR-042",
                                 "ean":"4006381333931","verrechnungseinheit":"LAUFENDE_METER","profilform":"RUNDROHR"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produktname").value("Rundrohr 42,4 x 2"))
                .andExpect(jsonPath("$.ean").value("4006381333931"))
                .andExpect(jsonPath("$.artikelnummer").value("ST-RR-042"));
    }

    @Test
    void ungueltigeEingabeKommtMit400UndKlartext() throws Exception {
        when(artikelService.aktualisiereStammdaten(eq(7L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Produktnamen eingeben."));

        mockMvc.perform(put("/api/artikel/7/stammdaten").contentType(MediaType.APPLICATION_JSON).content("{\"produktname\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Produktnamen")));
    }

    @Test
    void doppelteArtikelnummerKommtMit409() throws Exception {
        when(artikelService.aktualisiereStammdaten(eq(7L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Die Artikelnummer A-1 gibt es schon bei einem anderen Artikel."));

        mockMvc.perform(put("/api/artikel/7/stammdaten").contentType(MediaType.APPLICATION_JSON).content("{\"produktname\":\"x\",\"artikelnummer\":\"A-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("A-1")));
    }

    @Test
    void unbekannteIdKommtMit404() throws Exception {
        when(artikelService.aktualisiereStammdaten(eq(999L), any())).thenThrow(new NotFoundException("Artikel 999 nicht gefunden"));

        mockMvc.perform(put("/api/artikel/999/stammdaten").contentType(MediaType.APPLICATION_JSON).content("{\"produktname\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}
