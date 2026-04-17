package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlag;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlagStatus;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlagTyp;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.ArtikelVorschlagRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ArtikelVorschlagController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtikelVorschlagControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ArtikelVorschlagRepository vorschlagRepository;
    @MockBean private ArtikelRepository artikelRepository;
    @MockBean private KategorieRepository kategorieRepository;
    @MockBean private WerkstoffRepository werkstoffRepository;
    @MockBean private LieferantenArtikelPreiseRepository preiseRepository;

    // ─────────────────────────────────────────────────────────────
    // GET /api/artikel-vorschlaege
    // ─────────────────────────────────────────────────────────────

    @Nested
    class ListeVorschlaege {

        @Test
        void liste_default_status_pending() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            given(vorschlagRepository.findByStatusOrderByErstelltAmDesc(ArtikelVorschlagStatus.PENDING))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/api/artikel-vorschlaege"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        void liste_mit_explizitem_status() throws Exception {
            ArtikelVorschlag v = vorschlag(2L, ArtikelVorschlagStatus.APPROVED);
            given(vorschlagRepository.findByStatusOrderByErstelltAmDesc(ArtikelVorschlagStatus.APPROVED))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/api/artikel-vorschlaege").param("status", "APPROVED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("APPROVED"));
        }

        @Test
        void liste_mit_lowercase_status_wird_gematcht() throws Exception {
            given(vorschlagRepository.findByStatusOrderByErstelltAmDesc(ArtikelVorschlagStatus.REJECTED))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/artikel-vorschlaege").param("status", "rejected"))
                    .andExpect(status().isOk());
        }

        @Test
        void liste_mit_unbekanntem_status_fallbackt_auf_pending() throws Exception {
            given(vorschlagRepository.findByStatusOrderByErstelltAmDesc(ArtikelVorschlagStatus.PENDING))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/artikel-vorschlaege").param("status", "UNSINN"))
                    .andExpect(status().isOk());
        }

        @Test
        void response_enthaelt_alle_dto_felder() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            v.setTyp(ArtikelVorschlagTyp.KONFLIKT_EXTERNE_NUMMER);
            v.setLieferant(lieferant(100L, "Stahl AG"));
            v.setQuelleDokument(dokument(5L, "rechnung.pdf"));
            v.setExterneArtikelnummer("X1");
            v.setProduktname("FL 30x5");
            v.setProduktlinie("FL");
            v.setProdukttext("Volltext");
            Kategorie parent = kategorie(1, "Stahl", null);
            Kategorie child = kategorie(2, "Flachstahl", parent);
            v.setVorgeschlageneKategorie(child);
            v.setVorgeschlagenerWerkstoff(werkstoff(1L, "S235JR"));
            v.setMasse(new BigDecimal("1.17"));
            v.setHoehe(5);
            v.setBreite(30);
            v.setEinzelpreis(new BigDecimal("2.50"));
            v.setPreiseinheit("kg");
            v.setKiKonfidenz(new BigDecimal("0.95"));
            v.setKiBegruendung("perfekter Match");
            v.setKonfliktArtikel(artikel(77L, "Bestehender"));
            v.setTrefferArtikel(artikel(88L, "Treffer"));
            given(vorschlagRepository.findByStatusOrderByErstelltAmDesc(any()))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/api/artikel-vorschlaege"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].typ").value("KONFLIKT_EXTERNE_NUMMER"))
                    .andExpect(jsonPath("$[0].lieferantName").value("Stahl AG"))
                    .andExpect(jsonPath("$[0].quelleDokumentBezeichnung").value("rechnung.pdf"))
                    .andExpect(jsonPath("$[0].vorgeschlageneKategoriePfad").value("Stahl > Flachstahl"))
                    .andExpect(jsonPath("$[0].vorgeschlagenerWerkstoffName").value("S235JR"))
                    .andExpect(jsonPath("$[0].konfliktArtikelId").value(77))
                    .andExpect(jsonPath("$[0].trefferArtikelId").value(88));
        }

        @Test
        void response_mit_kategorie_ohne_beschreibung() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            Kategorie kat = kategorie(5, null, null);
            v.setVorgeschlageneKategorie(kat);
            given(vorschlagRepository.findByStatusOrderByErstelltAmDesc(any()))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/api/artikel-vorschlaege"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].vorgeschlageneKategoriePfad").value("?"));
        }

        @Test
        void response_mit_null_status_und_typ_wird_toleriert() throws Exception {
            ArtikelVorschlag v = new ArtikelVorschlag();
            v.setId(1L);
            v.setStatus(null);
            v.setTyp(null);
            given(vorschlagRepository.findByStatusOrderByErstelltAmDesc(any()))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/api/artikel-vorschlaege"))
                    .andExpect(status().isOk());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/artikel-vorschlaege/count
    // ─────────────────────────────────────────────────────────────

    @Test
    void count_gibt_anzahl_pending() throws Exception {
        given(vorschlagRepository.countByStatus(ArtikelVorschlagStatus.PENDING)).willReturn(7L);

        mockMvc.perform(get("/api/artikel-vorschlaege/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(7));
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/artikel-vorschlaege/{id}
    // ─────────────────────────────────────────────────────────────

    @Nested
    class Detail {

        @Test
        void detail_gefunden() throws Exception {
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(vorschlag(1L, ArtikelVorschlagStatus.PENDING)));

            mockMvc.perform(get("/api/artikel-vorschlaege/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        void detail_nicht_gefunden_gibt_404() throws Exception {
            given(vorschlagRepository.findById(99L)).willReturn(Optional.empty());

            mockMvc.perform(get("/api/artikel-vorschlaege/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PATCH /api/artikel-vorschlaege/{id}
    // ─────────────────────────────────────────────────────────────

    @Nested
    class Patch {

        @Test
        void patch_aktualisiert_alle_felder() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(kategorieRepository.findById(5)).willReturn(Optional.of(kategorie(5, "Flachstahl", null)));
            given(werkstoffRepository.findById(1L)).willReturn(Optional.of(werkstoff(1L, "S235JR")));

            String body = """
                    {
                        "produktname":"FL 30x5",
                        "produktlinie":"FL",
                        "produkttext":"Vollständige Beschreibung",
                        "externeArtikelnummer":"FL30X5",
                        "kategorieId":5,
                        "werkstoffId":1,
                        "masse":1.17,
                        "hoehe":5,
                        "breite":30,
                        "einzelpreis":2.50,
                        "preiseinheit":"kg"
                    }
                    """;

            mockMvc.perform(patch("/api/artikel-vorschlaege/1")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.produktname").value("FL 30x5"))
                    .andExpect(jsonPath("$.vorgeschlagenerWerkstoffName").value("S235JR"));
        }

        @Test
        void patch_auf_nicht_pending_gibt_400() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.APPROVED);
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));

            mockMvc.perform(patch("/api/artikel-vorschlaege/1")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void patch_nicht_gefunden_gibt_404() throws Exception {
            given(vorschlagRepository.findById(99L)).willReturn(Optional.empty());

            mockMvc.perform(patch("/api/artikel-vorschlaege/99")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void patch_mit_unbekannter_kategorie_ignoriert_zuweisung() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(kategorieRepository.findById(999)).willReturn(Optional.empty());
            given(werkstoffRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(patch("/api/artikel-vorschlaege/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"kategorieId\":999,\"werkstoffId\":999}"))
                    .andExpect(status().isOk());
        }

        @Test
        void patch_mit_leerem_body_aktualisiert_nur_bearbeitetAm() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            v.setProduktname("Original");
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));

            mockMvc.perform(patch("/api/artikel-vorschlaege/1")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.produktname").value("Original"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/artikel-vorschlaege/{id}/approve
    // ─────────────────────────────────────────────────────────────

    @Nested
    class Approve {

        @Test
        void approve_legt_artikel_und_preis_an() throws Exception {
            ArtikelVorschlag v = vollstaendigerVorschlag();
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(artikelRepository.save(any())).willAnswer(i -> {
                Artikel a = i.getArgument(0);
                a.setId(77L);
                return a;
            });

            mockMvc.perform(post("/api/artikel-vorschlaege/1/approve")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.trefferArtikelId").value(77));

            verify(artikelRepository).save(any(ArtikelWerkstoffe.class));
            verify(preiseRepository).save(any());
        }

        @Test
        void approve_mit_update_vor_freigabe() throws Exception {
            ArtikelVorschlag v = vollstaendigerVorschlag();
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(artikelRepository.save(any())).willAnswer(i -> {
                Artikel a = i.getArgument(0);
                a.setId(77L);
                return a;
            });

            mockMvc.perform(post("/api/artikel-vorschlaege/1/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"produktname\":\"Korrigiert\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.produktname").value("Korrigiert"));
        }

        @Test
        void approve_ohne_preis_und_ohne_externe_nummer_legt_nur_artikel_an() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            v.setLieferant(lieferant(100L, "X"));
            v.setProduktname("Neu");
            // kein einzelpreis, kein externeArtikelnummer
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(artikelRepository.save(any())).willAnswer(i -> {
                Artikel a = i.getArgument(0);
                a.setId(77L);
                return a;
            });

            mockMvc.perform(post("/api/artikel-vorschlaege/1/approve")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(preiseRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void approve_ohne_lieferant_legt_nur_artikel_an() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            v.setProduktname("Neu");
            v.setEinzelpreis(new BigDecimal("1.0"));
            // kein lieferant
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(artikelRepository.save(any())).willAnswer(i -> {
                Artikel a = i.getArgument(0);
                a.setId(77L);
                return a;
            });

            mockMvc.perform(post("/api/artikel-vorschlaege/1/approve")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(preiseRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void approve_mit_nur_externer_nummer_aber_ohne_preis() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            v.setLieferant(lieferant(100L, "X"));
            v.setProduktname("Neu");
            v.setExterneArtikelnummer("EXT-1");
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(artikelRepository.save(any())).willAnswer(i -> {
                Artikel a = i.getArgument(0);
                a.setId(77L);
                return a;
            });

            mockMvc.perform(post("/api/artikel-vorschlaege/1/approve")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(preiseRepository).save(any());
        }

        @Test
        void approve_nicht_pending_gibt_400() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.APPROVED);
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));

            mockMvc.perform(post("/api/artikel-vorschlaege/1/approve")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void approve_nicht_gefunden_gibt_404() throws Exception {
            given(vorschlagRepository.findById(99L)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/artikel-vorschlaege/99/approve")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        void approve_ohne_hoehe_und_breite() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            v.setProduktname("Neu");
            // Kein hoehe/breite gesetzt
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(artikelRepository.save(any())).willAnswer(i -> {
                Artikel a = i.getArgument(0);
                a.setId(77L);
                return a;
            });

            mockMvc.perform(post("/api/artikel-vorschlaege/1/approve")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        void approve_mit_leerer_externer_nummer_speichert_keinen_preis() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            v.setLieferant(lieferant(100L, "X"));
            v.setProduktname("Neu");
            v.setExterneArtikelnummer("   ");
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(artikelRepository.save(any())).willAnswer(i -> {
                Artikel a = i.getArgument(0);
                a.setId(77L);
                return a;
            });

            mockMvc.perform(post("/api/artikel-vorschlaege/1/approve")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(preiseRepository, org.mockito.Mockito.never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/artikel-vorschlaege/{id}/reject
    // ─────────────────────────────────────────────────────────────

    @Nested
    class Reject {

        @Test
        void reject_pending_setzt_rejected() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));
            given(vorschlagRepository.save(any())).willAnswer(i -> i.getArgument(0));

            mockMvc.perform(post("/api/artikel-vorschlaege/1/reject"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        }

        @Test
        void reject_nicht_pending_gibt_400() throws Exception {
            ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.APPROVED);
            given(vorschlagRepository.findById(1L)).willReturn(Optional.of(v));

            mockMvc.perform(post("/api/artikel-vorschlaege/1/reject"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void reject_nicht_gefunden_gibt_404() throws Exception {
            given(vorschlagRepository.findById(99L)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/artikel-vorschlaege/99/reject"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE /api/artikel-vorschlaege/{id}
    // ─────────────────────────────────────────────────────────────

    @Nested
    class Delete {

        @Test
        void delete_vorhandenen_vorschlag() throws Exception {
            given(vorschlagRepository.existsById(1L)).willReturn(true);

            mockMvc.perform(delete("/api/artikel-vorschlaege/1"))
                    .andExpect(status().isNoContent());

            verify(vorschlagRepository).deleteById(1L);
        }

        @Test
        void delete_nicht_vorhandenen_vorschlag_gibt_404() throws Exception {
            given(vorschlagRepository.existsById(99L)).willReturn(false);

            mockMvc.perform(delete("/api/artikel-vorschlaege/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private ArtikelVorschlag vorschlag(Long id, ArtikelVorschlagStatus status) {
        ArtikelVorschlag v = new ArtikelVorschlag();
        v.setId(id);
        v.setStatus(status);
        v.setTyp(ArtikelVorschlagTyp.NEU_ANLAGE);
        v.setErstelltAm(LocalDateTime.now());
        return v;
    }

    private ArtikelVorschlag vollstaendigerVorschlag() {
        ArtikelVorschlag v = vorschlag(1L, ArtikelVorschlagStatus.PENDING);
        v.setLieferant(lieferant(100L, "Stahl AG"));
        v.setProduktname("FL 30x5");
        v.setProduktlinie("FL");
        v.setProdukttext("Flachstahl 30x5");
        v.setExterneArtikelnummer("FL30X5");
        v.setVorgeschlageneKategorie(kategorie(5, "Flachstahl", null));
        v.setVorgeschlagenerWerkstoff(werkstoff(1L, "S235JR"));
        v.setMasse(new BigDecimal("1.17"));
        v.setHoehe(5);
        v.setBreite(30);
        v.setEinzelpreis(new BigDecimal("2.50"));
        v.setPreiseinheit("kg");
        return v;
    }

    private Lieferanten lieferant(Long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        return l;
    }

    private LieferantDokument dokument(Long id, String name) {
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setOriginalDateiname(name);
        return d;
    }

    private Kategorie kategorie(Integer id, String beschreibung, Kategorie parent) {
        Kategorie k = new Kategorie();
        k.setId(id);
        k.setBeschreibung(beschreibung);
        k.setParentKategorie(parent);
        return k;
    }

    private Werkstoff werkstoff(Long id, String name) {
        Werkstoff w = new Werkstoff();
        w.setId(id);
        w.setName(name);
        return w;
    }

    private Artikel artikel(Long id, String name) {
        ArtikelWerkstoffe a = new ArtikelWerkstoffe();
        a.setId(id);
        a.setProduktname(name);
        return a;
    }
}
