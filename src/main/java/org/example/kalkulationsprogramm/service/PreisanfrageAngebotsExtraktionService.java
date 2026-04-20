package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageAngebot;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferantStatus;
import org.example.kalkulationsprogramm.domain.PreisanfragePosition;
import org.example.kalkulationsprogramm.repository.PreisanfrageAngebotRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.PreisanfragePositionRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageRepository;
import org.example.kalkulationsprogramm.service.ArtikelMatchingAgentService.MatchingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Extrahiert Angebotspreise aus Angebots-PDFs per Gemini (PDF-Direkt, kein
 * Text-Preprocessing) und speichert sie als {@link PreisanfrageAngebot}-Zeilen.
 * Parallel laeuft pro gematchter Position der {@link ArtikelMatchingAgentService}
 * fuer den Artikelstamm-Update (DRY).
 *
 * <p>Policy (festgelegt 2026-04-20): Das PDF geht IMMER direkt als
 * {@code inline_data} mit {@code mime_type=application/pdf} an Gemini — niemals
 * PDFTextStripper, Tika oder OCR davor. {@code rufGeminiApiMitPrompt} erledigt das.
 *
 * <p><b>Idempotenz:</b> bestehende {@link PreisanfrageAngebot}-Zeilen werden NICHT
 * ueberschrieben (manuelle Korrekturen haben Vorrang). Fuer erneute Extraktion muss
 * der Angebots-Eintrag zuvor geloescht werden.
 */
@Service
public class PreisanfrageAngebotsExtraktionService {

    private static final Logger log = LoggerFactory.getLogger(PreisanfrageAngebotsExtraktionService.class);

    /** Obergrenze fuer die Anzahl Zusatzpositionen, die pro Angebot als Notiz geloggt werden. */
    private static final int MAX_ZUSATZPOSITIONEN_LOG = 10;
    /** Maximale Bemerkungs-Laenge aus dem Angebot (Gemini kann lang antworten). */
    private static final int MAX_BEMERKUNG_LEN = 2000;

    private final PreisanfrageRepository preisanfrageRepository;
    private final PreisanfrageLieferantRepository preisanfrageLieferantRepository;
    private final PreisanfragePositionRepository preisanfragePositionRepository;
    private final PreisanfrageAngebotRepository preisanfrageAngebotRepository;
    private final GeminiDokumentAnalyseService geminiService;
    private final ArtikelMatchingAgentService artikelMatchingAgentService;
    private final ObjectMapper objectMapper;

    @Value("${file.mail-attachment-dir:uploads/email}")
    private String attachmentDir;

    public PreisanfrageAngebotsExtraktionService(
            PreisanfrageRepository preisanfrageRepository,
            PreisanfrageLieferantRepository preisanfrageLieferantRepository,
            PreisanfragePositionRepository preisanfragePositionRepository,
            PreisanfrageAngebotRepository preisanfrageAngebotRepository,
            GeminiDokumentAnalyseService geminiService,
            ArtikelMatchingAgentService artikelMatchingAgentService,
            ObjectMapper objectMapper) {
        this.preisanfrageRepository = preisanfrageRepository;
        this.preisanfrageLieferantRepository = preisanfrageLieferantRepository;
        this.preisanfragePositionRepository = preisanfragePositionRepository;
        this.preisanfrageAngebotRepository = preisanfrageAngebotRepository;
        this.geminiService = geminiService;
        this.artikelMatchingAgentService = artikelMatchingAgentService;
        this.objectMapper = objectMapper;
    }

    /** Ergebnis einer kompletten Extraktions-Runde. */
    public record ExtraktionsErgebnis(
            int verarbeiteteLieferanten,
            int extrahierteAngebote,
            int fehler,
            List<String> hinweise) {}

    /**
     * Synchroner Einstieg fuer den Controller (manueller Button-Trigger).
     * Lese-Validierung passiert hier; die eigentliche Arbeit fuer jeden PAL
     * laeuft in separaten Transaktionen.
     */
    @Transactional(readOnly = true)
    public ExtraktionsErgebnis extrahiereFuerPreisanfrage(Long preisanfrageId) {
        if (preisanfrageId == null || preisanfrageId <= 0) {
            throw new IllegalArgumentException(
                    "preisanfrageId muss positiv sein, war: " + preisanfrageId);
        }
        Preisanfrage pa = preisanfrageRepository.findById(preisanfrageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Preisanfrage nicht gefunden: " + preisanfrageId));

        List<PreisanfrageLieferant> kandidaten = preisanfrageLieferantRepository
                .findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(pa.getId())
                .stream()
                .filter(p -> p.getStatus() == PreisanfrageLieferantStatus.BEANTWORTET)
                .filter(p -> p.getAntwortEmail() != null)
                .toList();

        List<PreisanfragePosition> positionen = preisanfragePositionRepository
                .findByPreisanfrageIdOrderByReihenfolgeAsc(pa.getId());

        int verarbeitet = 0;
        int neueAngebote = 0;
        int fehler = 0;
        java.util.ArrayList<String> hinweise = new java.util.ArrayList<>();

        for (PreisanfrageLieferant pal : kandidaten) {
            boolean hatBereitsAngebote = !preisanfrageAngebotRepository
                    .findByPreisanfrageLieferantId(pal.getId()).isEmpty();
            if (hatBereitsAngebote) {
                hinweise.add(lieferantLabel(pal) + ": Angebote vorhanden, keine erneute Extraktion");
                continue;
            }
            verarbeitet++;
            try {
                int n = extrahiereFuerLieferant(pal.getId(), positionen);
                neueAngebote += n;
                hinweise.add(lieferantLabel(pal) + ": " + n + " Position(en) extrahiert");
            } catch (RuntimeException e) {
                fehler++;
                log.warn("Extraktion fehlgeschlagen fuer pal={}: {}", pal.getId(), e.getMessage(), e);
                hinweise.add(lieferantLabel(pal) + ": FEHLER — " + e.getMessage());
            }
        }
        return new ExtraktionsErgebnis(verarbeitet, neueAngebote, fehler, hinweise);
    }

    /**
     * Async-Variante fuer den Auto-Trigger aus {@link PreisanfrageService}, wenn
     * die Preisanfrage auf {@code VOLLSTAENDIG} wechselt. Schluckt Fehler, damit
     * der auslosende Status-Uebergang nicht scheitert.
     */
    @Async
    public void extrahiereAsync(Long preisanfrageId) {
        try {
            ExtraktionsErgebnis ergebnis = extrahiereFuerPreisanfrage(preisanfrageId);
            log.info("Auto-Trigger Angebotsextraktion fuer preisanfrage={}: {} verarbeitet, {} neue Angebote, {} Fehler",
                    preisanfrageId, ergebnis.verarbeiteteLieferanten(),
                    ergebnis.extrahierteAngebote(), ergebnis.fehler());
        } catch (RuntimeException e) {
            log.warn("Auto-Trigger Angebotsextraktion fuer preisanfrage={} abgebrochen: {}",
                    preisanfrageId, e.getMessage(), e);
        }
    }

    /**
     * Eigene Transaktion pro Lieferant: ein PAL-Fehler stoppt den Gesamt-Lauf nicht.
     */
    @Transactional
    public int extrahiereFuerLieferant(Long palId, List<PreisanfragePosition> positionen) {
        PreisanfrageLieferant pal = preisanfrageLieferantRepository.findById(palId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "PreisanfrageLieferant nicht gefunden: " + palId));

        Email antwort = pal.getAntwortEmail();
        if (antwort == null) {
            throw new IllegalStateException("PAL " + palId + " hat keine Antwort-Email");
        }
        EmailAttachment pdf = findePdfAnhang(antwort);
        if (pdf == null) {
            throw new IllegalStateException("Antwort-Email zu PAL " + palId + " hat keinen PDF-Anhang");
        }
        byte[] bytes = ladePdfBytes(pdf);

        String prompt = baueExtraktionsPrompt(positionen);
        String antwortJson = geminiService.rufGeminiApiMitPrompt(bytes, "application/pdf", prompt, true);
        if (antwortJson == null || antwortJson.isBlank()) {
            throw new IllegalStateException("Gemini lieferte leere Antwort fuer PAL " + palId);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(antwortJson);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Gemini-Antwort ist kein gueltiges JSON (PAL " + palId + "): " + e.getMessage(), e);
        }

        return verarbeiteExtraktionsAntwort(pal, root, positionen);
    }

    // ─────────────────────────────────────────────────────────────
    // interne Helfer
    // ─────────────────────────────────────────────────────────────

    /**
     * Baut den Prompt fuer Gemini. WICHTIG: jede Position bekommt ihre id mit — damit
     * kann Gemini ohne Fuzzy-Match zuordnen.
     */
    String baueExtraktionsPrompt(List<PreisanfragePosition> positionen) {
        StringBuilder sb = new StringBuilder();
        sb.append("Du analysierst ein Angebots-PDF eines Lieferanten zu einer Preisanfrage.\n");
        sb.append("Deine Aufgabe: pro Positionszeile (id, produktname, werkstoff, menge, einheit, externe Artikelnummer) ")
          .append("den vom Lieferanten angebotenen Einzelpreis finden und im JSON zurueckgeben.\n\n");
        sb.append("Positionen (exakt diese IDs zurueckgeben):\n");
        for (PreisanfragePosition p : positionen) {
            sb.append("  - positionId=").append(p.getId())
              .append(" | produktname='").append(nullSafe(p.getProduktname())).append("'")
              .append(" | externeArtikelnummer='").append(nullSafe(p.getExterneArtikelnummer())).append("'")
              .append(" | werkstoff='").append(nullSafe(p.getWerkstoffName())).append("'")
              .append(" | menge=").append(p.getMenge())
              .append(" | einheit='").append(nullSafe(p.getEinheit())).append("'")
              .append("\n");
        }
        sb.append("\nAntworte AUSSCHLIESSLICH mit gueltigem JSON in folgendem Schema:\n");
        sb.append("""
                {
                  "angebote": [
                    { "positionId": <LONG>, "einzelpreis": <NUMBER>, "preiseinheit": "<kg|t|stk|m|...>",
                      "mwstProzent": <NUMBER optional>, "lieferzeitTage": <INT optional>,
                      "gueltigBis": "<yyyy-MM-dd optional>", "bemerkung": "<STRING optional>" }
                  ],
                  "zusatzpositionen": [
                    { "bezeichnung": "<STRING>", "menge": <NUMBER optional>, "einheit": "<STRING optional>",
                      "einzelpreis": <NUMBER optional>, "positionsTyp": "DIENSTLEISTUNG|NEBENKOSTEN" }
                  ],
                  "gueltigBis": "<yyyy-MM-dd optional>",
                  "bemerkung": "<STRING optional>"
                }
                """);
        sb.append("\nREGELN:\n");
        sb.append("- Nenne NUR Positionen, fuer die du einen Preis im PDF findest. Keine Null-Preise erfinden.\n");
        sb.append("- Zusatzpositionen (Zuschnitt, Fracht, Verpackung, Mindermenge, Skonto) NIEMALS in 'angebote' — ");
        sb.append("immer in 'zusatzpositionen' mit passendem positionsTyp.\n");
        sb.append("- Preise netto ohne MwSt in 'einzelpreis'.\n");
        sb.append("- Kein Markdown, keine Erklaerung, nur das JSON-Objekt.\n");
        return sb.toString();
    }

    private int verarbeiteExtraktionsAntwort(PreisanfrageLieferant pal, JsonNode root,
                                             List<PreisanfragePosition> positionen) {
        Map<Long, PreisanfragePosition> positionsIndex = new HashMap<>();
        for (PreisanfragePosition p : positionen) positionsIndex.put(p.getId(), p);

        int angelegt = 0;
        JsonNode angeboteNode = root.path("angebote");
        if (angeboteNode.isArray()) {
            for (JsonNode a : angeboteNode) {
                Long posId = a.has("positionId") && !a.get("positionId").isNull()
                        ? a.get("positionId").asLong() : null;
                if (posId == null || !positionsIndex.containsKey(posId)) {
                    log.debug("Ueberspringe Angebots-Zeile ohne/unbekannte positionId: {}", a);
                    continue;
                }
                // Idempotenz: existierende Zeile NICHT ueberschreiben
                boolean existiert = preisanfrageAngebotRepository
                        .findByPreisanfrageLieferantId(pal.getId()).stream()
                        .anyMatch(x -> Objects.equals(x.getPreisanfragePosition().getId(), posId));
                if (existiert) {
                    log.debug("Angebot fuer pal={} pos={} existiert — nicht ueberschrieben", pal.getId(), posId);
                    continue;
                }
                PreisanfrageAngebot neu = baueAngebot(pal, positionsIndex.get(posId), a, root);
                preisanfrageAngebotRepository.save(neu);
                angelegt++;

                // Artikelstamm-Update (DRY: bestehender Agent-Service)
                try {
                    ObjectNode matchingPosition = baueMatchingPosition(positionsIndex.get(posId), a);
                    MatchingResult mr = artikelMatchingAgentService
                            .matcheOderSchlageAn(matchingPosition, pal.getLieferant(), null);
                    log.info("Artikelstamm-Match pal={} pos={}: {} — {}",
                            pal.getId(), posId, mr.ergebnis(), mr.hinweis());
                } catch (RuntimeException e) {
                    log.warn("Artikelstamm-Match fehlgeschlagen pal={} pos={}: {}",
                            pal.getId(), posId, e.getMessage());
                }
            }
        }

        // Zusatzpositionen: nur loggen, NICHT im Artikelstamm lernen (Policy 12.4)
        JsonNode zusatz = root.path("zusatzpositionen");
        if (zusatz.isArray() && !zusatz.isEmpty()) {
            int max = Math.min(zusatz.size(), MAX_ZUSATZPOSITIONEN_LOG);
            for (int i = 0; i < max; i++) {
                JsonNode z = zusatz.get(i);
                log.info("Zusatzposition (nicht im Stamm) pal={}: bezeichnung='{}' typ={} einzelpreis={}",
                        pal.getId(),
                        nullSafe(z.path("bezeichnung").asText("")),
                        nullSafe(z.path("positionsTyp").asText("")),
                        z.path("einzelpreis").asText(""));
            }
        }

        return angelegt;
    }

    private PreisanfrageAngebot baueAngebot(PreisanfrageLieferant pal, PreisanfragePosition pos,
                                            JsonNode a, JsonNode root) {
        PreisanfrageAngebot neu = new PreisanfrageAngebot();
        neu.setPreisanfrageLieferant(pal);
        neu.setPreisanfragePosition(pos);
        neu.setEinzelpreis(parseBigDecimal(a, "einzelpreis"));
        neu.setMwstProzent(parseBigDecimal(a, "mwstProzent"));
        if (a.has("lieferzeitTage") && !a.get("lieferzeitTage").isNull()) {
            neu.setLieferzeitTage(a.get("lieferzeitTage").asInt());
        }
        LocalDate gueltigBis = parseDate(a, "gueltigBis");
        if (gueltigBis == null) {
            gueltigBis = parseDate(root, "gueltigBis");
        }
        neu.setGueltigBis(gueltigBis);
        String bemerkung = a.path("bemerkung").asText("");
        if (bemerkung.isBlank()) bemerkung = root.path("bemerkung").asText("");
        if (!bemerkung.isBlank()) {
            if (bemerkung.length() > MAX_BEMERKUNG_LEN) {
                bemerkung = bemerkung.substring(0, MAX_BEMERKUNG_LEN);
            }
            neu.setBemerkung(bemerkung);
        }
        neu.setErfasstDurch("ki-extraktion");
        return neu;
    }

    /**
     * Baut das Matching-JSON fuer {@link ArtikelMatchingAgentService}. Nur die Felder,
     * die der Agent-SYSTEM_PROMPT auswertet.
     */
    ObjectNode baueMatchingPosition(PreisanfragePosition pos, JsonNode angebot) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("externeArtikelnummer", nullSafe(pos.getExterneArtikelnummer()));
        n.put("bezeichnung", nullSafe(pos.getProduktname()));
        if (pos.getMenge() != null) n.put("menge", pos.getMenge());
        n.put("mengeneinheit", nullSafe(pos.getEinheit()));
        BigDecimal einzelpreis = parseBigDecimal(angebot, "einzelpreis");
        if (einzelpreis != null) n.put("einzelpreis", einzelpreis);
        String preiseinheit = angebot.path("preiseinheit").asText("");
        if (preiseinheit.isBlank()) preiseinheit = nullSafe(pos.getEinheit());
        n.put("preiseinheit", preiseinheit);
        return n;
    }

    private EmailAttachment findePdfAnhang(Email mail) {
        if (mail == null || mail.getAttachments() == null) return null;
        return mail.getAttachments().stream()
                .filter(Objects::nonNull)
                .filter(a -> !Boolean.TRUE.equals(a.getInlineAttachment()))
                .filter(EmailAttachment::isPdf)
                .findFirst()
                .orElse(null);
    }

    /**
     * Laedt das PDF — gleiche Pfad-Heuristik wie
     * {@code EmailAttachmentProcessingService.resolveAttachmentPath}
     * (direkt / email-id-subdir / lieferant-id-subdir).
     */
    private byte[] ladePdfBytes(EmailAttachment attachment) {
        Path p = resolveAttachmentPath(attachment);
        if (p == null || !Files.exists(p)) {
            throw new IllegalStateException(
                    "PDF-Datei nicht gefunden: " + (p != null ? p : attachment.getStoredFilename()));
        }
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new IllegalStateException("PDF konnte nicht gelesen werden: " + p, e);
        }
    }

    private Path resolveAttachmentPath(EmailAttachment attachment) {
        if (attachment.getStoredFilename() == null) return null;
        Path basePath = Path.of(attachmentDir).toAbsolutePath().normalize();
        Path direct = basePath.resolve(attachment.getStoredFilename());
        if (Files.exists(direct)) return direct;
        if (attachment.getEmail() != null) {
            Path emailSub = basePath
                    .resolve(String.valueOf(attachment.getEmail().getId()))
                    .resolve(attachment.getStoredFilename());
            if (Files.exists(emailSub)) return emailSub;
        }
        if (attachment.getEmail() != null && attachment.getEmail().getLieferant() != null) {
            Path lieferantSub = basePath
                    .resolve(String.valueOf(attachment.getEmail().getLieferant().getId()))
                    .resolve(attachment.getStoredFilename());
            if (Files.exists(lieferantSub)) return lieferantSub;
        }
        return direct;
    }

    private static BigDecimal parseBigDecimal(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        JsonNode v = n.get(field);
        if (v.isNumber()) return v.decimalValue();
        String s = v.asText("");
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.replace(',', '.').trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        String s = n.get(field).asText("");
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String lieferantLabel(PreisanfrageLieferant pal) {
        return "PAL#" + pal.getId() + " ("
                + (pal.getLieferant() != null && pal.getLieferant().getLieferantenname() != null
                    ? pal.getLieferant().getLieferantenname()
                    : "?")
                + ")";
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    // fuer Tests
    Optional<ExtraktionsErgebnis> extrahiereSicher(Long preisanfrageId) {
        try {
            return Optional.of(extrahiereFuerPreisanfrage(preisanfrageId));
        } catch (RuntimeException e) {
            log.warn("extrahiereSicher({}) fehlgeschlagen: {}",
                    preisanfrageId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // nur fuer Tests — lokales Arbeitsverzeichnis statt File-Lookup
    void setAttachmentDirForTest(String dir) {
        this.attachmentDir = dir;
    }
}
