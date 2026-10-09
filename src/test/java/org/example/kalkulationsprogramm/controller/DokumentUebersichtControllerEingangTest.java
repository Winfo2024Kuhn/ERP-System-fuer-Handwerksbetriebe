package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.PositionsTrefferDto;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.service.LieferantDokumentSucheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/** Eingangsdokumente der Dokumentübersicht: Suche nach Positionen, Referenzen, Kommission und Typ. */
@WebMvcTest(DokumentUebersichtController.class)
@AutoConfigureMockMvc(addFilters = false)
class DokumentUebersichtControllerEingangTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AusgangsGeschaeftsDokumentRepository ausgangsRepo;
    @MockBean
    private LieferantGeschaeftsdokumentRepository lieferantGdRepo;
    @MockBean
    private LieferantDokumentSucheService sucheService;

    private static LieferantGeschaeftsdokument gd(long id, LieferantDokumentTyp typ, String nummer) {
        Lieferanten l = new Lieferanten();
        l.setId(3L);
        l.setLieferantenname("Musterstahl GmbH");
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setTyp(typ);
        d.setLieferant(l);
        LieferantGeschaeftsdokument g = new LieferantGeschaeftsdokument();
        g.setId(id);
        g.setDokument(d);
        g.setDokumentNummer(nummer);
        g.setDokumentDatum(LocalDate.of(2026, 9, (int) id));
        g.setBetragBrutto(BigDecimal.TEN);
        return g;
    }

    private void alle(LieferantGeschaeftsdokument... dokumente) {
        when(lieferantGdRepo.findAllSortedByDatum()).thenReturn(new ArrayList<>(List.of(dokumente)));
    }

    @Test
    @DisplayName("Positionstreffer erscheinen mit Trefferzeile, andere Dokumente fallen heraus")
    void positionstreffer() throws Exception {
        alle(gd(1, LieferantDokumentTyp.WERKSTOFFZEUGNIS, "Z-1"), gd(2, LieferantDokumentTyp.RECHNUNG, "RE-2"));
        when(sucheService.suchePositionen(eq("Flachstahl"), isNull(), any(), isNull(), isNull()))
                .thenReturn(Map.of(1L, new PositionsTrefferDto(1L, "Flachstahl 50x5 · Charge 123456", 1)));

        mockMvc.perform(get("/api/dokumentuebersicht/eingang").param("search", "Flachstahl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].typ").value("WERKSTOFFZEUGNIS"))
                .andExpect(jsonPath("$[0].positionsTreffer").value("Flachstahl 50x5 · Charge 123456"))
                .andExpect(jsonPath("$[0].weitereTreffer").value(1));
    }

    @Test
    @DisplayName("Kopfdaten-Treffer ohne Positionstreffer; Jahr, Monat und Typ gehen an die Positionssuche")
    void kopfdatenUndFilter() throws Exception {
        LieferantGeschaeftsdokument zeugnis = gd(1, LieferantDokumentTyp.WERKSTOFFZEUGNIS, "Z-1");
        when(lieferantGdRepo.findAllByDatumBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(new ArrayList<>(List.of(zeugnis, gd(2, LieferantDokumentTyp.LIEFERSCHEIN, "LS-2"))));
        when(sucheService.passtZurEingangssuche(zeugnis, "880011")).thenReturn(true);
        when(sucheService.suchePositionen(eq("880011"), isNull(), eq(EnumSet.of(LieferantDokumentTyp.WERKSTOFFZEUGNIS)),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)))).thenReturn(Map.of());

        mockMvc.perform(get("/api/dokumentuebersicht/eingang").param("search", "880011")
                .param("year", "2026").param("month", "9").param("typ", "WERKSTOFFZEUGNIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dokumentNummer").value("Z-1"))
                .andExpect(jsonPath("$[0].positionsTreffer").doesNotExist());
    }

    @Test
    @DisplayName("Typ-Filter Werkstoffzeugnis")
    void typFilter() throws Exception {
        alle(gd(1, LieferantDokumentTyp.WERKSTOFFZEUGNIS, "Z-1"), gd(2, LieferantDokumentTyp.LIEFERSCHEIN, "LS-2"));

        mockMvc.perform(get("/api/dokumentuebersicht/eingang").param("typ", "WERKSTOFFZEUGNIS"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dokumentNummer").value("Z-1"));
    }

    @Test
    @DisplayName("SQL-Injection und XSS in der Suche sind nur Text")
    void boeseEingaben() throws Exception {
        alle(gd(1, LieferantDokumentTyp.RECHNUNG, "RE-1"));
        when(sucheService.suchePositionen(anyString(), any(), any(), any(), any())).thenReturn(Map.of());

        for (String q : List.of("'; DROP TABLE x; --", "<script>alert(1)</script>", "a".repeat(10_001))) {
            mockMvc.perform(get("/api/dokumentuebersicht/eingang").param("search", q))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
