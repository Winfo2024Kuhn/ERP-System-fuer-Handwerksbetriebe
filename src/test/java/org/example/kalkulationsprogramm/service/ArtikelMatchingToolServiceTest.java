package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlag;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlagTyp;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.ArtikelVorschlagRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelMatchingToolService.MatchingToolContext;
import org.example.kalkulationsprogramm.service.ArtikelMatchingToolService.ToolSideEffect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Unit-Tests für {@link ArtikelMatchingToolService}.
 * Ziel: 100% Branch-Coverage der Tool-Logik ohne echte Datenbank.
 */
@ExtendWith(MockitoExtension.class)
class ArtikelMatchingToolServiceTest {

    @Mock private WerkstoffRepository werkstoffRepository;
    @Mock private KategorieRepository kategorieRepository;
    @Mock private ArtikelRepository artikelRepository;
    @Mock private LieferantenArtikelPreiseRepository artikelPreiseRepository;
    @Mock private ArtikelVorschlagRepository artikelVorschlagRepository;
    @Mock private EntityManager entityManager;
    @Mock private ArtikelPreisHookService preisHookService;

    @Mock private CriteriaBuilder cb;
    @SuppressWarnings("rawtypes")
    @Mock private CriteriaQuery cq;
    @SuppressWarnings("rawtypes")
    @Mock private Root root;
    @SuppressWarnings("rawtypes")
    @Mock private TypedQuery typedQuery;
    @SuppressWarnings("rawtypes")
    @Mock private Path pathMock;
    @SuppressWarnings("rawtypes")
    @Mock private Expression coalesceExpr;
    @SuppressWarnings("rawtypes")
    @Mock private Expression lowerExpr;
    @Mock private Predicate predicate;
    @Mock private Order order;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ArtikelMatchingToolService service;

    @BeforeEach
    void setUp() {
        service = new ArtikelMatchingToolService(
                objectMapper,
                werkstoffRepository,
                kategorieRepository,
                artikelRepository,
                artikelPreiseRepository,
                artikelVorschlagRepository,
                entityManager,
                preisHookService);
    }

    // ─────────────────────────────────────────────────────────────
    // buildFunctionDeclarations
    // ─────────────────────────────────────────────────────────────

    @Test
    void buildFunctionDeclarations_enthaelt_alle_sieben_tools() {
        ArrayNode declarations = service.buildFunctionDeclarations();

        assertThat(declarations).hasSize(7);
        List<String> names = new ArrayList<>();
        declarations.forEach(n -> names.add(n.path("name").asText()));
        assertThat(names).containsExactlyInAnyOrder(
                "list_werkstoffe", "list_kategorien", "search_artikel",
                "get_artikel_details", "update_artikel_preis", "propose_new_artikel",
                "create_kategorie");

        // search_artikel hat keine required-Felder (alles optional)
        JsonNode searchDecl = declarations.get(2);
        assertThat(searchDecl.path("parameters").path("required").isMissingNode()).isTrue();

        // update_artikel_preis hat 5 Pflichtfelder
        JsonNode updateDecl = findByName(declarations, "update_artikel_preis");
        ArrayNode req = (ArrayNode) updateDecl.path("parameters").path("required");
        assertThat(req).hasSize(5);

        // propose_new_artikel hat 3 Pflichtfelder
        JsonNode proposeDecl = findByName(declarations, "propose_new_artikel");
        ArrayNode propReq = (ArrayNode) proposeDecl.path("parameters").path("required");
        assertThat(propReq).hasSize(3);
    }

    private JsonNode findByName(ArrayNode arr, String name) {
        for (JsonNode n : arr) {
            if (name.equals(n.path("name").asText())) return n;
        }
        throw new AssertionError("Tool nicht gefunden: " + name);
    }

    // ─────────────────────────────────────────────────────────────
    // executeTool Dispatcher
    // ─────────────────────────────────────────────────────────────

    @Nested
    class ExecuteToolDispatcher {

        @Test
        void unbekanntes_tool_gibt_fehlermeldung() {
            String result = service.executeTool("unknown_tool", objectMapper.createObjectNode(), ctx());
            assertThat(result).isEqualTo("Unbekanntes Tool: unknown_tool");
        }

        @Test
        void exception_in_tool_wird_abgefangen() {
            when(werkstoffRepository.findAll()).thenThrow(new RuntimeException("DB weg"));

            String result = service.executeTool("list_werkstoffe", objectMapper.createObjectNode(), ctx());

            assertThat(result).contains("Fehler bei list_werkstoffe").contains("DB weg");
        }

        @Test
        void dispatcher_route_alle_tools() {
            when(werkstoffRepository.findAll()).thenReturn(List.of());
            when(kategorieRepository.findAll()).thenReturn(List.of());

            assertThat(service.executeTool("list_werkstoffe", objectMapper.createObjectNode(), ctx()))
                    .contains("Keine Werkstoffe");
            assertThat(service.executeTool("list_kategorien", objectMapper.createObjectNode(), ctx()))
                    .contains("Keine Kategorien");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // listWerkstoffe
    // ─────────────────────────────────────────────────────────────

    @Nested
    class ListWerkstoffe {

        @Test
        void leere_liste_gibt_hinweis() {
            when(werkstoffRepository.findAll()).thenReturn(List.of());

            String result = service.listWerkstoffe();

            assertThat(result).isEqualTo("Keine Werkstoffe vorhanden.");
        }

        @Test
        void formatiert_werkstoffe_mit_und_ohne_name() {
            Werkstoff w1 = werkstoff(1L, "S235JR");
            Werkstoff w2 = werkstoff(2L, null);
            when(werkstoffRepository.findAll()).thenReturn(List.of(w1, w2));

            String result = service.listWerkstoffe();

            assertThat(result).startsWith("id | name");
            assertThat(result).contains("1 | S235JR");
            assertThat(result).contains("2 | ");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // listKategorien
    // ─────────────────────────────────────────────────────────────

    @Nested
    class ListKategorien {

        @Test
        void leere_liste_gibt_hinweis() {
            when(kategorieRepository.findAll()).thenReturn(List.of());

            String result = service.listKategorien();

            assertThat(result).isEqualTo("Keine Kategorien vorhanden.");
        }

        @Test
        void formatiert_kategorien_mit_pfad_und_leaf_flag() {
            Kategorie parent = kategorie(1, "Stahl", null);
            Kategorie child = kategorie(2, null, parent);
            when(kategorieRepository.findAll()).thenReturn(List.of(parent, child));

            String result = service.listKategorien();

            assertThat(result).startsWith("id | parentId | isLeaf | pfad");
            // parent hat Kinder -> isLeaf=false
            assertThat(result).contains("1 |  | false | Stahl");
            // child ohne Kinder -> isLeaf=true, null-Beschreibung als "?"
            assertThat(result).contains("2 | 1 | true | Stahl > ?");
        }

        @Test
        void pfad_dreifach_verschachtelt() {
            Kategorie root = kategorie(1, "Metalle", null);
            Kategorie flach = kategorie(2, "Flachstahl", root);
            Kategorie bau = kategorie(3, "Baustahl", flach);
            when(kategorieRepository.findAll()).thenReturn(List.of(root, flach, bau));

            String result = service.listKategorien();

            assertThat(result).contains("3 | 2 | true | Metalle > Flachstahl > Baustahl");
            assertThat(result).contains("2 | 1 | false | Metalle > Flachstahl");
            assertThat(result).contains("1 |  | false | Metalle");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // searchArtikel
    // ─────────────────────────────────────────────────────────────

    @Nested
    class SearchArtikel {

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void setupCriteriaBuilderChain(List<Artikel> ergebnisse) {
            lenient().when(entityManager.getCriteriaBuilder()).thenReturn(cb);
            lenient().when(cb.createQuery(Artikel.class)).thenReturn(cq);
            lenient().when(cq.from(Artikel.class)).thenReturn(root);
            lenient().when(root.get(anyString())).thenReturn(pathMock);
            lenient().when(pathMock.get(anyString())).thenReturn(pathMock);
            lenient().when(cb.equal(any(), any())).thenReturn(predicate);
            lenient().when(cb.lower(any(Expression.class))).thenReturn(lowerExpr);
            lenient().when(cb.coalesce(any(Expression.class), anyString())).thenReturn(coalesceExpr);
            lenient().when(cb.like(any(), anyString())).thenReturn(predicate);
            lenient().when(cb.or(any(Predicate[].class))).thenReturn(predicate);
            lenient().when(cb.and(any(Predicate[].class))).thenReturn(predicate);
            lenient().when(cb.asc(any())).thenReturn(order);
            lenient().when(cq.where(any(Predicate.class))).thenReturn(cq);
            lenient().when(cq.orderBy(any(Order.class))).thenReturn(cq);
            lenient().when(entityManager.createQuery(cq)).thenReturn(typedQuery);
            lenient().when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
            lenient().when(typedQuery.getResultList()).thenReturn(ergebnisse);
        }

        @Test
        void keine_filter_keine_ergebnisse() {
            setupCriteriaBuilderChain(List.of());

            String result = service.searchArtikel(objectMapper.createObjectNode());

            assertThat(result).isEqualTo("Keine Artikel gefunden.");
        }

        @Test
        void alle_filter_und_limits() {
            Artikel a = artikel(10L, "Flachstahl 30x5", "FL", werkstoff(1L, "S235JR"),
                    kategorie(5, "Flachstahl", null));
            setupCriteriaBuilderChain(List.of(a));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("werkstoffId", 1);
            args.put("kategorieId", 5);
            args.put("suchtext", "FL 30x5");
            args.put("limit", 10);

            String result = service.searchArtikel(args);

            assertThat(result).contains("Gefunden: 1 Artikel");
            assertThat(result).contains("Flachstahl 30x5");
            verify(typedQuery).setMaxResults(10);
        }

        @Test
        void negatives_limit_wird_auf_default_korrigiert() {
            setupCriteriaBuilderChain(List.of());

            ObjectNode args = objectMapper.createObjectNode();
            args.put("limit", -1);

            service.searchArtikel(args);

            verify(typedQuery).setMaxResults(20); // MAX_ARTIKEL_SEARCH
        }

        @Test
        void zu_grosses_limit_wird_gedeckelt() {
            setupCriteriaBuilderChain(List.of());

            ObjectNode args = objectMapper.createObjectNode();
            args.put("limit", 999);

            service.searchArtikel(args);

            verify(typedQuery).setMaxResults(50); // MAX_ARTIKEL_SEARCH_LIMIT
        }

        @Test
        void werte_null_werden_ignoriert() {
            setupCriteriaBuilderChain(List.of());

            ObjectNode args = objectMapper.createObjectNode();
            args.putNull("werkstoffId");
            args.putNull("kategorieId");
            args.putNull("limit");

            service.searchArtikel(args);

            verify(typedQuery).setMaxResults(20);
        }

        @Test
        void artikel_mit_nullwerkstoff_und_nullkategorie_werden_formatiert() {
            Artikel a = artikel(11L, "Ohne Werkstoff", null, null, null);
            setupCriteriaBuilderChain(List.of(a));

            String result = service.searchArtikel(objectMapper.createObjectNode());

            assertThat(result).contains("Ohne Werkstoff");
        }

        @Test
        void externe_nummern_werden_gruppiert_formatiert() {
            Artikel a = artikel(12L, "Test", null, null, null);
            Lieferanten lief1 = lieferant(100L, "Stahl AG");
            Lieferanten lief2 = lieferant(101L, "Rohr GmbH");
            LieferantenArtikelPreise lap1 = lap(a, lief1, "ART-001");
            LieferantenArtikelPreise lap2 = lap(a, lief2, "ART-002");
            LieferantenArtikelPreise lap3 = lap(a, null, "ART-003");   // lieferant null Pfad
            LieferantenArtikelPreise lap4 = lap(a, lief1, "");         // blank externe nr
            LieferantenArtikelPreise lap5 = lap(a, lief1, null);       // null externe nr
            a.setArtikelpreis(new ArrayList<>(List.of(lap1, lap2, lap3, lap4, lap5)));
            setupCriteriaBuilderChain(List.of(a));

            String result = service.searchArtikel(objectMapper.createObjectNode());

            assertThat(result).contains("100:ART-001");
            assertThat(result).contains("101:ART-002");
            assertThat(result).contains("?:ART-003");
            assertThat(result).doesNotContain("ART-004");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // getArtikelDetails
    // ─────────────────────────────────────────────────────────────

    @Nested
    class GetArtikelDetails {

        @Test
        void artikelId_fehlt() {
            String result = service.getArtikelDetails(objectMapper.createObjectNode());
            assertThat(result).isEqualTo("artikelId fehlt");
        }

        @Test
        void artikelId_null() {
            ObjectNode args = objectMapper.createObjectNode();
            args.putNull("artikelId");
            String result = service.getArtikelDetails(args);
            assertThat(result).isEqualTo("artikelId fehlt");
        }

        @Test
        void artikel_nicht_gefunden() {
            when(artikelRepository.findById(42L)).thenReturn(Optional.empty());

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 42);

            String result = service.getArtikelDetails(args);

            assertThat(result).isEqualTo("Artikel 42 nicht gefunden.");
        }

        @Test
        void artikel_mit_allen_feldern() {
            Artikel a = artikel(7L, "Rundrohr 25", "RR", werkstoff(1L, "S235JR"),
                    kategorie(3, "Rundrohr", null));
            Lieferanten lief = lieferant(100L, "Stahl AG");
            LieferantenArtikelPreise lap = lap(a, lief, "RR-25");
            lap.setPreis(new BigDecimal("1.23"));
            a.setArtikelpreis(new ArrayList<>(List.of(lap)));
            when(artikelRepository.findById(7L)).thenReturn(Optional.of(a));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 7);

            String result = service.getArtikelDetails(args);

            assertThat(result).contains("produktname=Rundrohr 25");
            assertThat(result).contains("werkstoff=S235JR");
            assertThat(result).contains("kategorie=Rundrohr");
            assertThat(result).contains("lieferant=Stahl AG");
            assertThat(result).contains("preis=1.23");
        }

        @Test
        void artikel_ohne_werkstoff_kategorie_und_lieferant() {
            Artikel a = artikel(8L, "Einzel", null, null, null);
            LieferantenArtikelPreise lap = lap(a, null, null);
            a.setArtikelpreis(new ArrayList<>(List.of(lap)));
            when(artikelRepository.findById(8L)).thenReturn(Optional.of(a));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 8);

            String result = service.getArtikelDetails(args);

            assertThat(result).contains("werkstoff=");
            assertThat(result).contains("lieferant=?");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // updateArtikelPreis
    // ─────────────────────────────────────────────────────────────

    @Nested
    class UpdateArtikelPreis {

        @Test
        void ctx_null() {
            String result = service.updateArtikelPreis(objectMapper.createObjectNode(), null);
            assertThat(result).isEqualTo("Kein Lieferant im Kontext.");
        }

        @Test
        void lieferant_null_im_ctx() {
            MatchingToolContext ctx = new MatchingToolContext(null, null, BigDecimal.valueOf(0.85));
            String result = service.updateArtikelPreis(objectMapper.createObjectNode(), ctx);
            assertThat(result).isEqualTo("Kein Lieferant im Kontext.");
        }

        @Test
        void artikelId_fehlt() {
            String result = service.updateArtikelPreis(objectMapper.createObjectNode(), ctx());
            assertThat(result).isEqualTo("artikelId fehlt");
        }

        @Test
        void externe_nummer_fehlt() {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            String result = service.updateArtikelPreis(args, ctx());
            assertThat(result).isEqualTo("externeArtikelnummer fehlt");
        }

        @Test
        void preis_fehlt() {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            String result = service.updateArtikelPreis(args, ctx());
            assertThat(result).isEqualTo("preisProKg fehlt");
        }

        @Test
        void preis_ungueltig() {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "not-a-number");

            String result = service.updateArtikelPreis(args, ctx());

            assertThat(result).contains("preisProKg ungültig");
        }

        @Test
        void preis_mit_komma_wird_akzeptiert() {
            Artikel a = artikel(1L, "Test", null, null, null);
            a.setArtikelpreis(new ArrayList<>());
            when(artikelRepository.findById(1L)).thenReturn(Optional.of(a));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id(eq("X1"), anyLong()))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "1,23"); // Komma statt Punkt
            args.put("konfidenz", 0.9);
            args.put("begruendung", "Test");

            MatchingToolContext ctx = ctx();
            String result = service.updateArtikelPreis(args, ctx);

            assertThat(result).startsWith("OK.");
            assertThat(ctx.lastEffect()).isInstanceOf(ToolSideEffect.PreisAktualisiert.class);
        }

        @Test
        void artikel_nicht_gefunden() {
            when(artikelRepository.findById(99L)).thenReturn(Optional.empty());

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 99);
            args.put("externeArtikelnummer", "X");
            args.put("preisProKg", "1.0");

            String result = service.updateArtikelPreis(args, ctx());

            assertThat(result).isEqualTo("Artikel 99 nicht gefunden.");
        }

        @Test
        void konflikt_externe_nummer_auf_anderen_artikel() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel zielArtikel = artikel(1L, "Ziel", null, null, null);
            Artikel konfliktArtikel = artikel(2L, "Konflikt", null, null, null);
            LieferantenArtikelPreise vorhanden = lap(konfliktArtikel, lief, "X1");

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(zielArtikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("X1", 100L))
                    .thenReturn(Optional.of(vorhanden));
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> {
                        ArtikelVorschlag v = i.getArgument(0);
                        v.setId(777L);
                        return v;
                    });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "2.5");
            args.put("konfidenz", 0.95);
            args.put("begruendung", "Begründung");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            String result = service.updateArtikelPreis(args, ctx);

            assertThat(result).contains("KONFLIKT").contains("777");
            assertThat(ctx.lastEffect()).isInstanceOf(ToolSideEffect.VorschlagAngelegt.class);
            ToolSideEffect.VorschlagAngelegt v = (ToolSideEffect.VorschlagAngelegt) ctx.lastEffect();
            assertThat(v.typ()).isEqualTo(ArtikelVorschlagTyp.KONFLIKT_EXTERNE_NUMMER);
        }

        @Test
        void externe_nummer_zeigt_bereits_auf_gleichen_artikel_kein_konflikt() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);
            artikel.setArtikelpreis(new ArrayList<>());
            LieferantenArtikelPreise vorhanden = lap(artikel, lief, "X1");

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("X1", 100L))
                    .thenReturn(Optional.of(vorhanden));
            when(artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(1L, 100L))
                    .thenReturn(Optional.of(vorhanden));
            when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "2.5");
            args.put("konfidenz", 0.95);
            args.put("begruendung", "Begründung");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            String result = service.updateArtikelPreis(args, ctx);

            assertThat(result).startsWith("OK.");
            assertThat(result).contains("war bereits korrekt");
        }

        @Test
        void konfidenz_unter_schwelle_legt_match_vorschlag_an() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("X1", 100L))
                    .thenReturn(Optional.empty());
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> {
                        ArtikelVorschlag v = i.getArgument(0);
                        v.setId(500L);
                        return v;
                    });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "2.5");
            args.put("konfidenz", 0.5); // unter 0.85
            args.put("begruendung", "unsicher");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            String result = service.updateArtikelPreis(args, ctx);

            assertThat(result).contains("MATCH_VORSCHLAG").contains("500");
            verify(artikelPreiseRepository, never()).save(any(LieferantenArtikelPreise.class));
        }

        @Test
        void schwelle_null_im_context_ueberspringt_schwellenpruefung() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);
            artikel.setArtikelpreis(new ArrayList<>());

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("X1", 100L))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(1L, 100L))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "2.5");
            args.put("konfidenz", 0.1);
            args.put("begruendung", "b");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, null);
            String result = service.updateArtikelPreis(args, ctx);

            assertThat(result).startsWith("OK.");
        }

        @Test
        void bestehender_lap_mit_anderer_externer_nummer_wird_gelernt() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);
            artikel.setArtikelpreis(new ArrayList<>());
            LieferantenArtikelPreise bestehend = lap(artikel, lief, "ALT-1");

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("NEU-1", 100L))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(1L, 100L))
                    .thenReturn(Optional.of(bestehend));
            when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "NEU-1");
            args.put("preisProKg", "3.3");
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            String result = service.updateArtikelPreis(args, ctx);

            assertThat(result).contains("dauerhaft gelernt");
            assertThat(bestehend.getExterneArtikelnummer()).isEqualTo("NEU-1");
            ToolSideEffect.PreisAktualisiert eff = (ToolSideEffect.PreisAktualisiert) ctx.lastEffect();
            assertThat(eff.externeNummerGelernt()).isTrue();
        }

        @Test
        void bestehender_lap_mit_leerer_externer_nummer_wird_gelernt() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);
            artikel.setArtikelpreis(new ArrayList<>());
            LieferantenArtikelPreise bestehend = lap(artikel, lief, "");

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("NEU-1", 100L))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(1L, 100L))
                    .thenReturn(Optional.of(bestehend));
            when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "NEU-1");
            args.put("preisProKg", "3.3");
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            service.updateArtikelPreis(args, ctx);

            ToolSideEffect.PreisAktualisiert eff = (ToolSideEffect.PreisAktualisiert) ctx.lastEffect();
            assertThat(eff.externeNummerGelernt()).isTrue();
        }

        @Test
        void bestehender_lap_mit_null_externer_nummer_wird_gelernt() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);
            artikel.setArtikelpreis(new ArrayList<>());
            LieferantenArtikelPreise bestehend = lap(artikel, lief, null);

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("NEU-1", 100L))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(1L, 100L))
                    .thenReturn(Optional.of(bestehend));
            when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "NEU-1");
            args.put("preisProKg", "3.3");
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            service.updateArtikelPreis(args, ctx);

            ToolSideEffect.PreisAktualisiert eff = (ToolSideEffect.PreisAktualisiert) ctx.lastEffect();
            assertThat(eff.externeNummerGelernt()).isTrue();
        }

        @Test
        void bestehender_lap_mit_gleicher_externer_nummer_wird_nicht_gelernt() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);
            artikel.setArtikelpreis(new ArrayList<>());
            LieferantenArtikelPreise bestehend = lap(artikel, lief, "x1"); // lower case

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("X1", 100L))
                    .thenReturn(Optional.of(bestehend));
            when(artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(1L, 100L))
                    .thenReturn(Optional.of(bestehend));
            when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "3.3");
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            service.updateArtikelPreis(args, ctx);

            ToolSideEffect.PreisAktualisiert eff = (ToolSideEffect.PreisAktualisiert) ctx.lastEffect();
            assertThat(eff.externeNummerGelernt()).isFalse();
        }

        @Test
        void hook_wird_mit_menge_und_beleg_gerufen_bei_erfolgreichem_update() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);
            artikel.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
            artikel.setArtikelpreis(new ArrayList<>());

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("X1", 100L))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(1L, 100L))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "1.50");
            args.put("mengeKg", "250");
            args.put("konfidenz", 0.95);
            args.put("begruendung", "eindeutig");

            LieferantDokument doc = new LieferantDokument();
            doc.setId(42L);
            MatchingToolContext ctx = new MatchingToolContext(lief, doc, BigDecimal.valueOf(0.85));

            service.updateArtikelPreis(args, ctx);

            verify(preisHookService).registriere(
                    eq(artikel), eq(lief),
                    eq(new BigDecimal("1.50")), eq(new BigDecimal("250")),
                    eq(Verrechnungseinheit.KILOGRAMM), eq(PreisQuelle.RECHNUNG),
                    eq("X1"), eq("42"), eq("eindeutig"));
        }

        @Test
        void hook_wird_ohne_menge_gerufen_wenn_mengeKg_fehlt() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);
            artikel.setArtikelpreis(new ArrayList<>());

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("X1", 100L))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(1L, 100L))
                    .thenReturn(Optional.empty());
            when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "2.10");
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            service.updateArtikelPreis(args, ctx);

            verify(preisHookService).registriere(
                    eq(artikel), eq(lief),
                    eq(new BigDecimal("2.10")), eq(null),
                    eq(Verrechnungseinheit.KILOGRAMM), eq(PreisQuelle.RECHNUNG),
                    eq("X1"), eq(null), eq("b"));
        }

        @Test
        void hook_wird_nicht_gerufen_bei_konflikt() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel ziel = artikel(1L, "Ziel", null, null, null);
            Artikel konflikt = artikel(2L, "Konflikt", null, null, null);
            LieferantenArtikelPreise vorhanden = lap(konflikt, lief, "X1");

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(ziel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("X1", 100L))
                    .thenReturn(Optional.of(vorhanden));
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> { ((ArtikelVorschlag) i.getArgument(0)).setId(777L); return i.getArgument(0); });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "2.5");
            args.put("mengeKg", "100");
            args.put("konfidenz", 0.95);
            args.put("begruendung", "b");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            service.updateArtikelPreis(args, ctx);

            verify(preisHookService, never()).registriere(
                    any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void hook_wird_nicht_gerufen_bei_konfidenz_unter_schwelle() {
            Lieferanten lief = lieferant(100L, "Stahl AG");
            Artikel artikel = artikel(1L, "Test", null, null, null);

            when(artikelRepository.findById(1L)).thenReturn(Optional.of(artikel));
            when(artikelPreiseRepository.findByExterneArtikelnummerIgnoreCaseAndLieferant_Id("X1", 100L))
                    .thenReturn(Optional.empty());
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> { ((ArtikelVorschlag) i.getArgument(0)).setId(500L); return i.getArgument(0); });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("artikelId", 1);
            args.put("externeArtikelnummer", "X1");
            args.put("preisProKg", "2.5");
            args.put("mengeKg", "100");
            args.put("konfidenz", 0.5);
            args.put("begruendung", "unsicher");

            MatchingToolContext ctx = new MatchingToolContext(lief, null, BigDecimal.valueOf(0.85));
            service.updateArtikelPreis(args, ctx);

            verify(preisHookService, never()).registriere(
                    any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // proposeNewArtikel
    // ─────────────────────────────────────────────────────────────

    @Nested
    class ProposeNewArtikel {

        @Test
        void ctx_null() {
            String result = service.proposeNewArtikel(objectMapper.createObjectNode(), null);
            assertThat(result).isEqualTo("Kein Lieferant im Kontext.");
        }

        @Test
        void lieferant_null() {
            MatchingToolContext ctx = new MatchingToolContext(null, null, BigDecimal.valueOf(0.85));
            String result = service.proposeNewArtikel(objectMapper.createObjectNode(), ctx);
            assertThat(result).isEqualTo("Kein Lieferant im Kontext.");
        }

        @Test
        void produktname_fehlt() {
            String result = service.proposeNewArtikel(objectMapper.createObjectNode(), ctx());
            assertThat(result).isEqualTo("produktname fehlt");
        }

        @Test
        void produktname_nur_whitespace_fehlt() {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", "   ");
            String result = service.proposeNewArtikel(args, ctx());
            assertThat(result).isEqualTo("produktname fehlt");
        }

        @Test
        void minimaler_vorschlag() {
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> {
                        ArtikelVorschlag v = i.getArgument(0);
                        v.setId(42L);
                        return v;
                    });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", "Flachstahl 30x5");
            args.put("konfidenz", 0.7);
            args.put("begruendung", "neu");

            MatchingToolContext ctx = ctx();
            String result = service.proposeNewArtikel(args, ctx);

            assertThat(result).contains("NEU_ANLAGE").contains("42");
            ToolSideEffect.VorschlagAngelegt eff = (ToolSideEffect.VorschlagAngelegt) ctx.lastEffect();
            assertThat(eff.typ()).isEqualTo(ArtikelVorschlagTyp.NEU_ANLAGE);
        }

        @Test
        void vollstaendiger_vorschlag_mit_allen_feldern() {
            Kategorie kat = kategorie(5, "Flachstahl", null);
            Werkstoff werk = werkstoff(1L, "S235JR");
            when(kategorieRepository.findById(5)).thenReturn(Optional.of(kat));
            when(werkstoffRepository.findById(1L)).thenReturn(Optional.of(werk));
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> {
                        ArtikelVorschlag v = i.getArgument(0);
                        v.setId(42L);
                        return v;
                    });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", "FL 30x5");
            args.put("produktlinie", "FL");
            args.put("produkttext", "Flachstahl 30x5 S235JR");
            args.put("externeArtikelnummer", "FL30X5");
            args.put("kategorieId", 5);
            args.put("werkstoffId", 1);
            args.put("masse", "1.178");
            args.put("hoehe", 5);
            args.put("breite", 30);
            args.put("einzelpreis", "95.50");
            args.put("preiseinheit", "100kg");
            args.put("konfidenz", 0.95);
            args.put("begruendung", "neuer Artikel");

            MatchingToolContext ctx = ctx();
            String result = service.proposeNewArtikel(args, ctx);

            assertThat(result).contains("NEU_ANLAGE");
        }

        @Test
        void kategorie_nicht_gefunden_wird_ignoriert() {
            when(kategorieRepository.findById(999)).thenReturn(Optional.empty());
            when(werkstoffRepository.findById(999L)).thenReturn(Optional.empty());
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> {
                        ArtikelVorschlag v = i.getArgument(0);
                        v.setId(1L);
                        return v;
                    });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", "X");
            args.put("kategorieId", 999);
            args.put("werkstoffId", 999);
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            MatchingToolContext ctx = ctx();
            service.proposeNewArtikel(args, ctx);

            ArtikelVorschlag saved = captureArtikelVorschlag();
            assertThat(saved.getVorgeschlageneKategorie()).isNull();
            assertThat(saved.getVorgeschlagenerWerkstoff()).isNull();
        }

        @Test
        void null_werte_werden_ignoriert() {
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> { ArtikelVorschlag v = i.getArgument(0); v.setId(1L); return v; });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", "X");
            args.putNull("kategorieId");
            args.putNull("werkstoffId");
            args.putNull("hoehe");
            args.putNull("breite");
            args.putNull("masse");
            args.putNull("einzelpreis");
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            service.proposeNewArtikel(args, ctx());

            ArtikelVorschlag saved = captureArtikelVorschlag();
            assertThat(saved.getHoehe()).isNull();
            assertThat(saved.getBreite()).isNull();
            assertThat(saved.getMasse()).isNull();
        }

        @Test
        void masse_ungueltig_wird_zu_null() {
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> { ArtikelVorschlag v = i.getArgument(0); v.setId(1L); return v; });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", "X");
            args.put("masse", "abc");
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            service.proposeNewArtikel(args, ctx());

            ArtikelVorschlag saved = captureArtikelVorschlag();
            assertThat(saved.getMasse()).isNull();
        }

        @Test
        void masse_leerer_string_wird_zu_null() {
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> { ArtikelVorschlag v = i.getArgument(0); v.setId(1L); return v; });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", "X");
            args.put("masse", "  ");
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            service.proposeNewArtikel(args, ctx());

            ArtikelVorschlag saved = captureArtikelVorschlag();
            assertThat(saved.getMasse()).isNull();
        }

        @Test
        void ueberlange_strings_werden_getrimmt() {
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> { ArtikelVorschlag v = i.getArgument(0); v.setId(1L); return v; });

            String langerName = "A".repeat(1000);
            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", langerName);
            args.put("produkttext", "B".repeat(3000));
            args.put("begruendung", "C".repeat(2000));
            args.put("konfidenz", 0.9);

            service.proposeNewArtikel(args, ctx());

            ArtikelVorschlag saved = captureArtikelVorschlag();
            assertThat(saved.getProduktname()).hasSize(500);
            assertThat(saved.getProdukttext()).hasSize(2000);
            assertThat(saved.getKiBegruendung()).hasSize(1000);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private MatchingToolContext ctx() {
        return new MatchingToolContext(lieferant(1L, "Test-Lieferant"), null, BigDecimal.valueOf(0.85));
    }

    private Werkstoff werkstoff(Long id, String name) {
        Werkstoff w = new Werkstoff();
        w.setId(id);
        w.setName(name);
        return w;
    }

    private Kategorie kategorie(Integer id, String beschreibung, Kategorie parent) {
        Kategorie k = new Kategorie();
        k.setId(id);
        k.setBeschreibung(beschreibung);
        k.setParentKategorie(parent);
        return k;
    }

    private Artikel artikel(Long id, String produktname, String produktlinie,
                            Werkstoff werkstoff, Kategorie kategorie) {
        ArtikelWerkstoffe a = new ArtikelWerkstoffe();
        a.setId(id);
        a.setProduktname(produktname);
        a.setProduktlinie(produktlinie);
        a.setWerkstoff(werkstoff);
        a.setKategorie(kategorie);
        return a;
    }

    private Lieferanten lieferant(Long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        return l;
    }

    private LieferantenArtikelPreise lap(Artikel a, Lieferanten l, String externeNr) {
        LieferantenArtikelPreise p = new LieferantenArtikelPreise();
        p.setArtikel(a);
        p.setLieferant(l);
        p.setExterneArtikelnummer(externeNr);
        p.setPreisAenderungsdatum(new Date());
        return p;
    }

    private ArtikelVorschlag captureArtikelVorschlag() {
        org.mockito.ArgumentCaptor<ArtikelVorschlag> captor =
                org.mockito.ArgumentCaptor.forClass(ArtikelVorschlag.class);
        verify(artikelVorschlagRepository).save(captor.capture());
        return captor.getValue();
    }

    // ─────────────────────────────────────────────────────────────
    // Leaf-Check in proposeNewArtikel (Etappe 7 / Spec 12.5-B)
    // ─────────────────────────────────────────────────────────────

    @Nested
    class ProposeNewArtikel_LeafCheck {

        @Test
        void non_leaf_kategorie_wird_abgelehnt_und_kein_vorschlag_angelegt() {
            Kategorie nonLeaf = kategorie(5, "Flachstahl", null);
            when(kategorieRepository.findById(5)).thenReturn(Optional.of(nonLeaf));
            when(kategorieRepository.existsByParentKategorie_Id(5)).thenReturn(true);

            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", "Flachstahl 30x5");
            args.put("kategorieId", 5);
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            String result = service.proposeNewArtikel(args, ctx());

            assertThat(result).startsWith("FEHLER:").contains("Flachstahl").contains("Leaf-Kategorie");
            verify(artikelVorschlagRepository, never()).save(any(ArtikelVorschlag.class));
        }

        @Test
        void leaf_kategorie_wird_akzeptiert() {
            when(artikelVorschlagRepository.save(any(ArtikelVorschlag.class)))
                    .thenAnswer(i -> {
                        ArtikelVorschlag v = i.getArgument(0);
                        v.setId(42L);
                        return v;
                    });
            Kategorie leaf = kategorie(7, "Baustahl", null);
            when(kategorieRepository.findById(7)).thenReturn(Optional.of(leaf));
            when(kategorieRepository.existsByParentKategorie_Id(7)).thenReturn(false);

            ObjectNode args = objectMapper.createObjectNode();
            args.put("produktname", "Baustahl 30x5");
            args.put("kategorieId", 7);
            args.put("konfidenz", 0.9);
            args.put("begruendung", "b");

            String result = service.proposeNewArtikel(args, ctx());

            assertThat(result).startsWith("OK.").contains("42");
            ArtikelVorschlag saved = captureArtikelVorschlag();
            assertThat(saved.getVorgeschlageneKategorie()).isEqualTo(leaf);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // create_kategorie (Etappe 7 / Spec 12.5-C)
    // ─────────────────────────────────────────────────────────────

    @Nested
    class CreateKategorie {

        @Test
        void neue_unterkategorie_wird_angelegt_und_seiteneffekt_protokolliert() {
            Kategorie parent = kategorie(1, "Metalle", null);
            when(kategorieRepository.findById(1)).thenReturn(Optional.of(parent));
            when(kategorieRepository.findByParentKategorie_Id(1)).thenReturn(List.of());
            when(kategorieRepository.save(any(Kategorie.class)))
                    .thenAnswer(i -> {
                        Kategorie k = i.getArgument(0);
                        k.setId(99);
                        return k;
                    });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("beschreibung", "Edelstahl-Rundrohr");
            args.put("parentKategorieId", 1);
            args.put("konfidenz", 0.95);
            args.put("begruendung", "Neue Unterkategorie fuer Edelstahl");

            MatchingToolContext ctx = ctx();
            String result = service.createKategorie(args, ctx);

            assertThat(result).startsWith("OK.").contains("99");
            assertThat(ctx.lastEffect())
                    .isInstanceOf(ToolSideEffect.KategorieAngelegt.class);
            ToolSideEffect.KategorieAngelegt eff =
                    (ToolSideEffect.KategorieAngelegt) ctx.lastEffect();
            assertThat(eff.kategorieId()).isEqualTo(99);
            assertThat(eff.bereitsExistiert()).isFalse();
        }

        @Test
        void duplikat_gibt_existierende_id_zurueck_ohne_insert() {
            Kategorie parent = kategorie(1, "Metalle", null);
            Kategorie existierend = kategorie(42, "Flachstahl", parent);
            when(kategorieRepository.findById(1)).thenReturn(Optional.of(parent));
            when(kategorieRepository.findByParentKategorie_Id(1))
                    .thenReturn(List.of(existierend));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("beschreibung", "flachstahl"); // case-insensitive match
            args.put("parentKategorieId", 1);
            args.put("konfidenz", 0.95);
            args.put("begruendung", "b");

            MatchingToolContext ctx = ctx();
            String result = service.createKategorie(args, ctx);

            assertThat(result).startsWith("EXISTIERT_BEREITS").contains("42");
            verify(kategorieRepository, never()).save(any(Kategorie.class));
            ToolSideEffect.KategorieAngelegt eff =
                    (ToolSideEffect.KategorieAngelegt) ctx.lastEffect();
            assertThat(eff.kategorieId()).isEqualTo(42);
            assertThat(eff.bereitsExistiert()).isTrue();
        }

        @Test
        void unbekannter_parent_gibt_fehler() {
            when(kategorieRepository.findById(999)).thenReturn(Optional.empty());

            ObjectNode args = objectMapper.createObjectNode();
            args.put("beschreibung", "X");
            args.put("parentKategorieId", 999);
            args.put("konfidenz", 0.95);
            args.put("begruendung", "b");

            String result = service.createKategorie(args, ctx());

            assertThat(result).startsWith("FEHLER:").contains("999");
            verify(kategorieRepository, never()).save(any(Kategorie.class));
        }

        @Test
        void hauptkategorie_ohne_hohe_konfidenz_abgelehnt() {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("beschreibung", "Neue Hauptkategorie");
            args.putNull("parentKategorieId");
            args.put("konfidenz", 0.5); // unter 0.9
            args.put("begruendung", "b");

            String result = service.createKategorie(args, ctx());

            assertThat(result).startsWith("FEHLER:").contains("Konfidenz");
            verify(kategorieRepository, never()).save(any(Kategorie.class));
        }

        @Test
        void leere_beschreibung_gibt_fehler() {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("beschreibung", "   ");
            args.put("konfidenz", 0.95);
            args.put("begruendung", "b");

            String result = service.createKategorie(args, ctx());

            assertThat(result).isEqualTo("beschreibung fehlt");
            verify(kategorieRepository, never()).save(any(Kategorie.class));
        }

        @Test
        void hauptkategorie_mit_hoher_konfidenz_wird_angelegt() {
            when(kategorieRepository.findByParentKategorieIsNull()).thenReturn(List.of());
            when(kategorieRepository.save(any(Kategorie.class)))
                    .thenAnswer(i -> {
                        Kategorie k = i.getArgument(0);
                        k.setId(77);
                        return k;
                    });

            ObjectNode args = objectMapper.createObjectNode();
            args.put("beschreibung", "Neue Hauptkategorie");
            args.putNull("parentKategorieId");
            args.put("konfidenz", 0.95);
            args.put("begruendung", "b");

            MatchingToolContext ctx = ctx();
            String result = service.createKategorie(args, ctx);

            assertThat(result).startsWith("OK.").contains("77").contains("Hauptkategorie");
            ToolSideEffect.KategorieAngelegt eff =
                    (ToolSideEffect.KategorieAngelegt) ctx.lastEffect();
            assertThat(eff.bereitsExistiert()).isFalse();
        }
    }
}
