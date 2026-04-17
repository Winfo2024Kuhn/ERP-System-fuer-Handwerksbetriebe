package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlag;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlagStatus;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlagTyp;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.ArtikelVorschlagRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stellt die Tool-Deklarationen und -Implementierungen für den
 * Artikel-Matching-KI-Agenten bereit. Wird ausschließlich vom
 * {@link ArtikelMatchingAgentService} genutzt — nicht im allgemeinen UI-Chat.
 *
 * Sicherheits-Prinzipien:
 * - lieferantId / quelleDokumentId kommen aus dem Context (nicht vom LLM).
 * - Keine freien SQL-Queries, kein Dateizugriff, keine Code-Ausführung.
 * - Alle Suchen hart begrenzt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtikelMatchingToolService {

    private static final int MAX_ARTIKEL_SEARCH = 20;
    private static final int MAX_ARTIKEL_SEARCH_LIMIT = 50;

    private final ObjectMapper objectMapper;
    private final WerkstoffRepository werkstoffRepository;
    private final KategorieRepository kategorieRepository;
    private final ArtikelRepository artikelRepository;
    private final LieferantenArtikelPreiseRepository artikelPreiseRepository;
    private final ArtikelVorschlagRepository artikelVorschlagRepository;
    private final EntityManager entityManager;

    /**
     * Context pro Matching-Call. Der Agent/Dispatcher setzt lieferant und
     * quelleDokument. Das LLM kennt diese IDs nicht und kann sie nicht manipulieren.
     * Enthält zusätzlich den zulässigen Ergebnis-Typ: findet der Agent einen Match,
     * wird das Ergebnis im {@code result}-Feld hinterlegt.
     */
    public static class MatchingToolContext {
        private final Lieferanten lieferant;
        private final LieferantDokument quelleDokument;
        private final BigDecimal autoMatchKonfidenzSchwelle;
        private ToolSideEffect lastEffect;

        public MatchingToolContext(Lieferanten lieferant, LieferantDokument quelleDokument,
                                   BigDecimal autoMatchKonfidenzSchwelle) {
            this.lieferant = lieferant;
            this.quelleDokument = quelleDokument;
            this.autoMatchKonfidenzSchwelle = autoMatchKonfidenzSchwelle;
        }

        public Lieferanten lieferant() { return lieferant; }
        public LieferantDokument quelleDokument() { return quelleDokument; }
        public BigDecimal autoMatchKonfidenzSchwelle() { return autoMatchKonfidenzSchwelle; }
        public ToolSideEffect lastEffect() { return lastEffect; }
        void setLastEffect(ToolSideEffect effect) { this.lastEffect = effect; }
    }

    /**
     * Seiteneffekt eines Tool-Aufrufs — vom Agent ausgewertet.
     */
    public sealed interface ToolSideEffect {
        record PreisAktualisiert(Long artikelId, BigDecimal preis, boolean externeNummerGelernt) implements ToolSideEffect {}
        record VorschlagAngelegt(Long vorschlagId, ArtikelVorschlagTyp typ) implements ToolSideEffect {}
    }

    // ─────────────────────────────────────────────────────────────
    // Function-Declarations für Gemini
    // ─────────────────────────────────────────────────────────────

    public ArrayNode buildFunctionDeclarations() {
        ArrayNode declarations = objectMapper.createArrayNode();

        declarations.add(declareListWerkstoffe());
        declarations.add(declareListKategorien());
        declarations.add(declareSearchArtikel());
        declarations.add(declareGetArtikelDetails());
        declarations.add(declareUpdateArtikelPreis());
        declarations.add(declareProposeNewArtikel());

        return declarations;
    }

    private ObjectNode declareListWerkstoffe() {
        ObjectNode decl = objectMapper.createObjectNode();
        decl.put("name", "list_werkstoffe");
        decl.put("description",
                "Listet alle Werkstoffe (z.B. 'S235JR', 'S355J2', 'Aluminium') mit ID und Name. "
                        + "Nutze dies, um herauszufinden, welcher Werkstoff zur gesuchten Position passt.");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "OBJECT");
        params.set("properties", objectMapper.createObjectNode());
        decl.set("parameters", params);
        return decl;
    }

    private ObjectNode declareListKategorien() {
        ObjectNode decl = objectMapper.createObjectNode();
        decl.put("name", "list_kategorien");
        decl.put("description",
                "Listet den Kategorie-Baum (z.B. 'Flachstahl', 'Rundrohr', 'Winkelstahl') mit ID, "
                        + "Beschreibung und parentId. Nutze dies, um die passende Kategorie für die Position zu finden.");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "OBJECT");
        params.set("properties", objectMapper.createObjectNode());
        decl.set("parameters", params);
        return decl;
    }

    private ObjectNode declareSearchArtikel() {
        ObjectNode decl = objectMapper.createObjectNode();
        decl.put("name", "search_artikel");
        decl.put("description",
                "Sucht Artikel im Materialstamm. Filtert optional nach werkstoffId und/oder kategorieId, "
                        + "und führt LIKE-Suche über produktname, produktlinie, produkttext und hicadName durch. "
                        + "EMPFEHLUNG: zuerst werkstoffId und kategorieId bestimmen, dann mit suchtext auf wenige "
                        + "Kandidaten eingrenzen. Limit max 50.");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "OBJECT");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("werkstoffId", prop("INTEGER", "ID des Werkstoffs aus list_werkstoffe (optional)"));
        props.set("kategorieId", prop("INTEGER", "ID der Kategorie aus list_kategorien (optional)"));
        props.set("suchtext", prop("STRING",
                "LIKE-Suchbegriff, z.B. 'FL 30x5' oder 'Rund 25'. Wird gegen produktname, produktlinie, produkttext und hicadName geprüft."));
        props.set("limit", prop("INTEGER", "Max. Anzahl Treffer (1-50, default 20)"));
        params.set("properties", props);
        decl.set("parameters", params);
        return decl;
    }

    private ObjectNode declareGetArtikelDetails() {
        ObjectNode decl = objectMapper.createObjectNode();
        decl.put("name", "get_artikel_details");
        decl.put("description",
                "Liefert die vollständigen Stammdaten eines Artikels (produktname, produktlinie, werkstoff, "
                        + "kategorie, ArtikelWerkstoffe-Felder wie masse/hoehe/breite) sowie alle bereits "
                        + "hinterlegten externen Artikelnummern und Preise pro Lieferant.");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "OBJECT");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("artikelId", prop("INTEGER", "ID des Artikels"));
        params.set("properties", props);
        ArrayNode req = objectMapper.createArrayNode();
        req.add("artikelId");
        params.set("required", req);
        decl.set("parameters", params);
        return decl;
    }

    private ObjectNode declareUpdateArtikelPreis() {
        ObjectNode decl = objectMapper.createObjectNode();
        decl.put("name", "update_artikel_preis");
        decl.put("description",
                "Aktualisiert den Preis eines bestehenden Artikels für den aktuellen Lieferanten UND lernt dabei "
                        + "die externe Artikelnummer dauerhaft. Ruf dies NUR auf, wenn du dir zu mindestens "
                        + "der Auto-Match-Konfidenzschwelle sicher bist, dass Artikel und Rechnungsposition identisch sind. "
                        + "Falls die externe Nummer bereits auf einen anderen Artikel beim selben Lieferanten zeigt, "
                        + "wird automatisch ein Konflikt-Vorschlag angelegt statt die Zuordnung zu überschreiben.");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "OBJECT");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("artikelId", prop("INTEGER", "ID des Artikels aus search_artikel"));
        props.set("externeArtikelnummer", prop("STRING", "Externe Artikelnummer aus der Rechnung"));
        props.set("preisProKg", prop("NUMBER", "Preis bereits normiert auf €/kg"));
        props.set("konfidenz", prop("NUMBER", "Deine Sicherheit 0.0-1.0 dass Artikel und Position identisch sind"));
        props.set("begruendung", prop("STRING", "Kurze Begründung (max 500 Zeichen)"));
        params.set("properties", props);
        ArrayNode req = objectMapper.createArrayNode();
        req.add("artikelId");
        req.add("externeArtikelnummer");
        req.add("preisProKg");
        req.add("konfidenz");
        req.add("begruendung");
        params.set("required", req);
        decl.set("parameters", params);
        return decl;
    }

    private ObjectNode declareProposeNewArtikel() {
        ObjectNode decl = objectMapper.createObjectNode();
        decl.put("name", "propose_new_artikel");
        decl.put("description",
                "Legt einen Artikel-Vorschlag in der Review-Queue an (Status PENDING). Nutze dies, wenn "
                        + "kein ausreichend ähnlicher Artikel gefunden wurde. Der Vorschlag wird einem Menschen "
                        + "zur Freigabe vorgelegt — lege ihn NUR an, wenn es sich wirklich um einen neuen Artikel handelt "
                        + "(nicht bei Zuschnitt/Fracht/Verpackung).");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "OBJECT");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("externeArtikelnummer", prop("STRING", "Externe Artikelnummer aus der Rechnung"));
        props.set("produktname", prop("STRING", "Kurzer, klarer Name (z.B. 'Flachstahl 30x5')"));
        props.set("produktlinie", prop("STRING", "Produktlinie/Reihe, falls erkennbar"));
        props.set("produkttext", prop("STRING", "Volltext/Beschreibung aus der Rechnung"));
        props.set("kategorieId", prop("INTEGER", "ID der passenden Kategorie (aus list_kategorien, optional)"));
        props.set("werkstoffId", prop("INTEGER", "ID des Werkstoffs (aus list_werkstoffe, optional)"));
        props.set("masse", prop("NUMBER", "Masse pro Meter in kg/m, falls aus Dimensionen berechenbar (optional)"));
        props.set("hoehe", prop("INTEGER", "Höhe in mm (optional)"));
        props.set("breite", prop("INTEGER", "Breite in mm (optional)"));
        props.set("einzelpreis", prop("NUMBER", "Einzelpreis aus der Rechnung"));
        props.set("preiseinheit", prop("STRING", "Einheit des Einzelpreises (z.B. 'kg', 't', '100kg')"));
        props.set("konfidenz", prop("NUMBER", "Deine Sicherheit 0.0-1.0 dass ein Neu-Anlage gerechtfertigt ist"));
        props.set("begruendung", prop("STRING", "Begründung (warum neu, nicht gematcht)"));
        params.set("properties", props);
        ArrayNode req = objectMapper.createArrayNode();
        req.add("produktname");
        req.add("konfidenz");
        req.add("begruendung");
        params.set("required", req);
        decl.set("parameters", params);
        return decl;
    }

    private ObjectNode prop(String type, String description) {
        ObjectNode p = objectMapper.createObjectNode();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    // ─────────────────────────────────────────────────────────────
    // Dispatcher
    // ─────────────────────────────────────────────────────────────

    public String executeTool(String toolName, JsonNode args, MatchingToolContext ctx) {
        long start = System.currentTimeMillis();
        try {
            String result = switch (toolName) {
                case "list_werkstoffe" -> listWerkstoffe();
                case "list_kategorien" -> listKategorien();
                case "search_artikel" -> searchArtikel(args);
                case "get_artikel_details" -> getArtikelDetails(args);
                case "update_artikel_preis" -> updateArtikelPreis(args, ctx);
                case "propose_new_artikel" -> proposeNewArtikel(args, ctx);
                default -> "Unbekanntes Tool: " + toolName;
            };
            log.info("[Tool:{}] OK ({}ms), Ergebnis (gekürzt): {}",
                    toolName, System.currentTimeMillis() - start,
                    result.length() > 300 ? result.substring(0, 300) + "…" : result);
            return result;
        } catch (Exception e) {
            log.warn("[Tool:{}] FEHLER ({}ms): {}", toolName, System.currentTimeMillis() - start, e.getMessage(), e);
            return "Fehler bei " + toolName + ": " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Tool-Implementierungen
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String listWerkstoffe() {
        List<Werkstoff> werkstoffe = werkstoffRepository.findAll();
        if (werkstoffe.isEmpty()) return "Keine Werkstoffe vorhanden.";
        StringBuilder sb = new StringBuilder("id | name\n");
        for (Werkstoff w : werkstoffe) {
            sb.append(w.getId()).append(" | ").append(w.getName() != null ? w.getName() : "").append("\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String listKategorien() {
        List<Kategorie> kategorien = kategorieRepository.findAll();
        if (kategorien.isEmpty()) return "Keine Kategorien vorhanden.";
        StringBuilder sb = new StringBuilder("id | parentId | beschreibung\n");
        for (Kategorie k : kategorien) {
            sb.append(k.getId()).append(" | ")
              .append(k.getParentKategorie() != null ? k.getParentKategorie().getId() : "")
              .append(" | ")
              .append(k.getBeschreibung() != null ? k.getBeschreibung() : "")
              .append("\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String searchArtikel(JsonNode args) {
        Integer werkstoffId = args.has("werkstoffId") && !args.get("werkstoffId").isNull()
                ? args.get("werkstoffId").asInt() : null;
        Integer kategorieId = args.has("kategorieId") && !args.get("kategorieId").isNull()
                ? args.get("kategorieId").asInt() : null;
        String suchtext = args.has("suchtext") ? args.get("suchtext").asText("").trim() : "";
        int limit = args.has("limit") && !args.get("limit").isNull() ? args.get("limit").asInt(MAX_ARTIKEL_SEARCH) : MAX_ARTIKEL_SEARCH;
        if (limit < 1) limit = MAX_ARTIKEL_SEARCH;
        if (limit > MAX_ARTIKEL_SEARCH_LIMIT) limit = MAX_ARTIKEL_SEARCH_LIMIT;

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Artikel> cq = cb.createQuery(Artikel.class);
        Root<Artikel> root = cq.from(Artikel.class);

        List<Predicate> preds = new ArrayList<>();
        if (werkstoffId != null) {
            preds.add(cb.equal(root.get("werkstoff").get("id"), werkstoffId.longValue()));
        }
        if (kategorieId != null) {
            preds.add(cb.equal(root.get("kategorie").get("id"), kategorieId));
        }
        if (!suchtext.isBlank()) {
            String like = "%" + suchtext.toLowerCase() + "%";
            Predicate pName = cb.like(cb.lower(cb.coalesce(root.get("produktname"), "")), like);
            Predicate pLinie = cb.like(cb.lower(cb.coalesce(root.get("produktlinie"), "")), like);
            Predicate pText = cb.like(cb.lower(cb.coalesce(root.get("produkttext"), "")), like);
            Predicate pHicad = cb.like(cb.lower(cb.coalesce(root.get("hicadName"), "")), like);
            preds.add(cb.or(pName, pLinie, pText, pHicad));
        }
        if (!preds.isEmpty()) {
            cq.where(cb.and(preds.toArray(new Predicate[0])));
        }
        cq.orderBy(cb.asc(root.get("id")));

        List<Artikel> ergebnisse = entityManager.createQuery(cq).setMaxResults(limit).getResultList();
        if (ergebnisse.isEmpty()) {
            return "Keine Artikel gefunden.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Gefunden: ").append(ergebnisse.size()).append(" Artikel\n");
        sb.append("id | produktname | produktlinie | werkstoff | kategorie | externe_nummern_bisher\n");
        for (Artikel a : ergebnisse) {
            sb.append(a.getId()).append(" | ")
              .append(nullSafe(a.getProduktname())).append(" | ")
              .append(nullSafe(a.getProduktlinie())).append(" | ")
              .append(a.getWerkstoff() != null ? nullSafe(a.getWerkstoff().getName()) : "").append(" | ")
              .append(a.getKategorie() != null ? nullSafe(a.getKategorie().getBeschreibung()) : "").append(" | ")
              .append(formatExterneNummern(a))
              .append("\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String getArtikelDetails(JsonNode args) {
        if (!args.has("artikelId") || args.get("artikelId").isNull()) {
            return "artikelId fehlt";
        }
        Long artikelId = args.get("artikelId").asLong();
        Optional<Artikel> aOpt = artikelRepository.findById(artikelId);
        if (aOpt.isEmpty()) return "Artikel " + artikelId + " nicht gefunden.";
        Artikel a = aOpt.get();

        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(a.getId()).append("\n");
        sb.append("produktname=").append(nullSafe(a.getProduktname())).append("\n");
        sb.append("produktlinie=").append(nullSafe(a.getProduktlinie())).append("\n");
        sb.append("produkttext=").append(nullSafe(a.getProdukttext())).append("\n");
        sb.append("hicadName=").append(nullSafe(a.getHicadName())).append("\n");
        sb.append("werkstoff=").append(a.getWerkstoff() != null ? a.getWerkstoff().getName() : "").append("\n");
        sb.append("kategorie=").append(a.getKategorie() != null ? a.getKategorie().getBeschreibung() : "").append("\n");
        sb.append("preiseinheit=").append(nullSafe(a.getPreiseinheit())).append("\n");
        sb.append("lieferanten_preise:\n");
        for (LieferantenArtikelPreise lap : a.getArtikelpreis()) {
            sb.append("  - lieferant=")
              .append(lap.getLieferant() != null ? lap.getLieferant().getLieferantenname() : "?")
              .append(" externe_nr=").append(nullSafe(lap.getExterneArtikelnummer()))
              .append(" preis=").append(lap.getPreis() != null ? lap.getPreis().toPlainString() : "")
              .append("\n");
        }
        return sb.toString();
    }

    @Transactional
    public String updateArtikelPreis(JsonNode args, MatchingToolContext ctx) {
        if (ctx == null || ctx.lieferant() == null) return "Kein Lieferant im Kontext.";

        Long artikelId = args.path("artikelId").asLong(0);
        String externeNr = args.path("externeArtikelnummer").asText("").trim();
        String preisStr = args.path("preisProKg").asText(null);
        double konfidenz = args.path("konfidenz").asDouble(0.0);
        String begruendung = args.path("begruendung").asText("");

        if (artikelId == 0) return "artikelId fehlt";
        if (externeNr.isBlank()) return "externeArtikelnummer fehlt";
        if (preisStr == null || preisStr.isBlank()) return "preisProKg fehlt";

        BigDecimal preis;
        try {
            preis = new BigDecimal(preisStr.replace(',', '.'));
        } catch (NumberFormatException e) {
            return "preisProKg ungültig: " + preisStr;
        }

        Optional<Artikel> aOpt = artikelRepository.findById(artikelId);
        if (aOpt.isEmpty()) return "Artikel " + artikelId + " nicht gefunden.";
        Artikel artikel = aOpt.get();
        Lieferanten lieferant = ctx.lieferant();

        // Prüfe Konflikt: externeNr bereits auf einem ANDEREN Artikel für diesen Lieferanten?
        Optional<LieferantenArtikelPreise> konfliktOpt = artikelPreiseRepository
                .findByExterneArtikelnummerIgnoreCaseAndLieferant_Id(externeNr, lieferant.getId());
        if (konfliktOpt.isPresent() && !konfliktOpt.get().getArtikel().getId().equals(artikelId)) {
            Long konfliktArtikelId = konfliktOpt.get().getArtikel().getId();
            ArtikelVorschlag konflikt = new ArtikelVorschlag();
            konflikt.setTyp(ArtikelVorschlagTyp.KONFLIKT_EXTERNE_NUMMER);
            konflikt.setStatus(ArtikelVorschlagStatus.PENDING);
            konflikt.setLieferant(lieferant);
            konflikt.setQuelleDokument(ctx.quelleDokument());
            konflikt.setExterneArtikelnummer(externeNr);
            konflikt.setEinzelpreis(preis);
            konflikt.setPreiseinheit("kg");
            konflikt.setKiKonfidenz(BigDecimal.valueOf(konfidenz));
            konflikt.setKiBegruendung(trimTo(begruendung, 1000));
            konflikt.setKonfliktArtikel(konfliktOpt.get().getArtikel());
            konflikt.setTrefferArtikel(artikel);
            konflikt = artikelVorschlagRepository.save(konflikt);
            ctx.setLastEffect(new ToolSideEffect.VorschlagAngelegt(konflikt.getId(), ArtikelVorschlagTyp.KONFLIKT_EXTERNE_NUMMER));
            log.info("KI-Matching: Konflikt — externe Nr '{}' für Lieferant {} zeigt bereits auf Artikel {}, neuer Treffer Artikel {} — Vorschlag {} erzeugt",
                    externeNr, lieferant.getId(), konfliktArtikelId, artikelId, konflikt.getId());
            return "KONFLIKT: Externe Nummer '" + externeNr + "' zeigt beim Lieferanten bereits auf Artikel "
                    + konfliktArtikelId + ". Vorschlag " + konflikt.getId() + " zur manuellen Klärung angelegt. "
                    + "Preis wurde NICHT aktualisiert.";
        }

        // Prüfe Konfidenzschwelle
        BigDecimal schwelle = ctx.autoMatchKonfidenzSchwelle();
        if (schwelle != null && BigDecimal.valueOf(konfidenz).compareTo(schwelle) < 0) {
            ArtikelVorschlag v = new ArtikelVorschlag();
            v.setTyp(ArtikelVorschlagTyp.MATCH_VORSCHLAG);
            v.setStatus(ArtikelVorschlagStatus.PENDING);
            v.setLieferant(lieferant);
            v.setQuelleDokument(ctx.quelleDokument());
            v.setExterneArtikelnummer(externeNr);
            v.setEinzelpreis(preis);
            v.setPreiseinheit("kg");
            v.setKiKonfidenz(BigDecimal.valueOf(konfidenz));
            v.setKiBegruendung(trimTo(begruendung, 1000));
            v.setTrefferArtikel(artikel);
            v = artikelVorschlagRepository.save(v);
            ctx.setLastEffect(new ToolSideEffect.VorschlagAngelegt(v.getId(), ArtikelVorschlagTyp.MATCH_VORSCHLAG));
            log.info("KI-Matching: Match-Vorschlag (Konfidenz {} < Schwelle {}) — Vorschlag {} für Artikel {}",
                    konfidenz, schwelle, v.getId(), artikelId);
            return "Konfidenz " + konfidenz + " unter Schwelle " + schwelle
                    + " — MATCH_VORSCHLAG " + v.getId() + " angelegt, kein Auto-Update durchgeführt.";
        }

        // Update bestehenden oder neuen Preis-Eintrag
        Optional<LieferantenArtikelPreise> lapOpt = artikelPreiseRepository
                .findByArtikel_IdAndLieferant_Id(artikelId, lieferant.getId());

        LieferantenArtikelPreise lap;
        boolean externeNummerGelernt = false;
        if (lapOpt.isPresent()) {
            lap = lapOpt.get();
            String bisherige = lap.getExterneArtikelnummer();
            if (bisherige == null || bisherige.isBlank() || !bisherige.equalsIgnoreCase(externeNr)) {
                lap.setExterneArtikelnummer(externeNr);
                externeNummerGelernt = true;
            }
        } else {
            lap = new LieferantenArtikelPreise();
            lap.setArtikel(artikel);
            lap.setLieferant(lieferant);
            lap.setExterneArtikelnummer(externeNr);
            externeNummerGelernt = true;
            artikel.getArtikelpreis().add(lap);
        }
        lap.setPreis(preis);
        lap.setPreisAenderungsdatum(new Date());
        artikelPreiseRepository.save(lap);

        ctx.setLastEffect(new ToolSideEffect.PreisAktualisiert(artikelId, preis, externeNummerGelernt));
        log.info("KI-Matching: Artikel {} für Lieferant {} aktualisiert — Preis {} €/kg, externeNummerGelernt={}, Konfidenz {}, Grund: {}",
                artikelId, lieferant.getId(), preis, externeNummerGelernt, konfidenz, begruendung);
        return "OK. Artikel " + artikelId + " Preis=" + preis + " €/kg gesetzt. "
                + (externeNummerGelernt
                    ? "Externe Nummer '" + externeNr + "' dauerhaft gelernt."
                    : "Externe Nummer war bereits korrekt.");
    }

    @Transactional
    public String proposeNewArtikel(JsonNode args, MatchingToolContext ctx) {
        if (ctx == null || ctx.lieferant() == null) return "Kein Lieferant im Kontext.";

        String produktname = args.path("produktname").asText("").trim();
        if (produktname.isBlank()) return "produktname fehlt";
        double konfidenz = args.path("konfidenz").asDouble(0.0);
        String begruendung = args.path("begruendung").asText("");

        ArtikelVorschlag v = new ArtikelVorschlag();
        v.setTyp(ArtikelVorschlagTyp.NEU_ANLAGE);
        v.setStatus(ArtikelVorschlagStatus.PENDING);
        v.setLieferant(ctx.lieferant());
        v.setQuelleDokument(ctx.quelleDokument());
        v.setExterneArtikelnummer(trimTo(args.path("externeArtikelnummer").asText(null), 255));
        v.setProduktname(trimTo(produktname, 500));
        v.setProduktlinie(trimTo(args.path("produktlinie").asText(null), 500));
        v.setProdukttext(trimTo(args.path("produkttext").asText(null), 2000));

        if (args.has("kategorieId") && !args.get("kategorieId").isNull()) {
            kategorieRepository.findById(args.get("kategorieId").asInt()).ifPresent(v::setVorgeschlageneKategorie);
        }
        if (args.has("werkstoffId") && !args.get("werkstoffId").isNull()) {
            werkstoffRepository.findById(args.get("werkstoffId").asLong()).ifPresent(v::setVorgeschlagenerWerkstoff);
        }
        v.setMasse(parseBig(args, "masse"));
        v.setHoehe(args.has("hoehe") && !args.get("hoehe").isNull() ? args.get("hoehe").asInt() : null);
        v.setBreite(args.has("breite") && !args.get("breite").isNull() ? args.get("breite").asInt() : null);
        v.setEinzelpreis(parseBig(args, "einzelpreis"));
        v.setPreiseinheit(trimTo(args.path("preiseinheit").asText(null), 50));
        v.setKiKonfidenz(BigDecimal.valueOf(konfidenz));
        v.setKiBegruendung(trimTo(begruendung, 1000));

        v = artikelVorschlagRepository.save(v);
        ctx.setLastEffect(new ToolSideEffect.VorschlagAngelegt(v.getId(), ArtikelVorschlagTyp.NEU_ANLAGE));
        log.info("KI-Matching: Neu-Anlage-Vorschlag {} für Lieferant {} — '{}' (Konfidenz {})",
                v.getId(), ctx.lieferant().getId(), produktname, konfidenz);
        return "OK. NEU_ANLAGE-Vorschlag " + v.getId() + " in Review-Queue angelegt.";
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String trimTo(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static BigDecimal parseBig(JsonNode args, String field) {
        if (!args.has(field) || args.get(field).isNull()) return null;
        String s = args.get(field).asText(null);
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatExterneNummern(Artikel a) {
        if (a.getArtikelpreis() == null || a.getArtikelpreis().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (LieferantenArtikelPreise lap : a.getArtikelpreis()) {
            if (lap.getExterneArtikelnummer() == null || lap.getExterneArtikelnummer().isBlank()) continue;
            if (!sb.isEmpty()) sb.append(",");
            sb.append(lap.getLieferant() != null ? lap.getLieferant().getId() : "?")
              .append(":").append(lap.getExterneArtikelnummer());
        }
        return sb.toString();
    }
}
