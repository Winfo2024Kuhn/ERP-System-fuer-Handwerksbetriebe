package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.BelegPosition;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Asynchrone Belegextraktion via GeminiDokumentAnalyseService.
 *
 * Wird vom BelegService nach erfolgreichem Upload aufgerufen. Befüllt
 * Geschäftsdaten + KI-Vorschlag-Felder am Beleg. Der Mitarbeiter am Handy
 * scannt währenddessen schon den nächsten Beleg — diese Methode darf den
 * Upload-Request nicht blockieren.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BelegKiAnalyseService {

    private final BelegRepository belegRepository;
    private final LieferantenRepository lieferantenRepository;
    private final GeminiDokumentAnalyseService geminiService;
    private final BelegZuordnungService zuordnungService;
    private final BelegKiKostenkontoService kostenkontoService;
    private final BelegSplitService belegSplitService;
    private final BelegAuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Prompt fuer die Positions-Extraktion bei aufgeteilten Belegen.
     * Wird nur ausgefuehrt, wenn der Nutzer am Handy "Teilweise" gewaehlt hat.
     * Bewusst kurz gehalten — wir brauchen pro Position nur das Wichtigste,
     * damit der Mitarbeiter im Checkbox-UI eine klare Liste sieht.
     */
    private static final String POSITIONS_PROMPT = """
            Extrahiere ALLE einzelnen Posten/Zeilen dieses Belegs als JSON.
            Antworte AUSSCHLIESSLICH mit gueltigem JSON (keine Erklaerungen, kein Markdown).

            Format:
            {
              "positionen": [
                {
                  "beschreibung": "Artikelname wie auf dem Bon",
                  "menge": 1.0,
                  "einheit": "St" | "kg" | "l" | null,
                  "einzelpreis": 4.99,
                  "betragBrutto": 4.99,
                  "mwstSatz": 19.00
                }
              ]
            }

            Regeln:
            - JEDE Zeile, die einen Preis hat, ist eine Position (auch Pfand, Rabatt).
            - Rabatte als negativen Betrag.
            - mwstSatz aus dem Bon ablesen (oft als A=19%, B=7% gekennzeichnet).
              Wenn nicht erkennbar: Lebensmittel/Grundnahrung -> 7, sonst -> 19.
            - betragBrutto = was tatsaechlich fuer diese Zeile gezahlt wird.
            - Wenn der Bon nur eine Gesamtsumme ohne Einzelposten hat: leeres Array.
            - Beschreibung kurz halten (max 80 Zeichen), Original-Schreibweise behalten.
            """;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    @Async
    @Transactional
    public void analysiereBelegAsync(Long belegId) {
        Beleg beleg = belegRepository.findById(belegId).orElse(null);
        if (beleg == null) {
            log.warn("KI-Analyse: Beleg {} nicht gefunden", belegId);
            return;
        }
        beleg.setKiAnalyseStatus(BelegKiAnalyseStatus.LAEUFT);
        belegRepository.save(beleg);

        try {
            Path datei = Paths.get(uploadPath, "belege", beleg.getGespeicherterDateiname());
            var ergebnis = geminiService.analyzeFile(datei, beleg.getOriginalDateiname());

            if (ergebnis == null) {
                beleg.setKiAnalyseStatus(BelegKiAnalyseStatus.FAILED);
                beleg.setKiFehlerText("KI-Analyse lieferte kein Ergebnis");
                belegRepository.save(beleg);
                return;
            }

            // Geschäftsdaten übernehmen (alle nullable — Buchhalter korrigiert am PC)
            beleg.setBelegNummer(ergebnis.getDokumentNummer());
            beleg.setBelegDatum(ergebnis.getDokumentDatum());
            beleg.setBetragNetto(ergebnis.getBetragNetto());
            beleg.setBetragBrutto(ergebnis.getBetragBrutto());
            beleg.setMwstSatz(ergebnis.getMwstSatz());
            beleg.setZahlungsart(ZahlungsartMapper.zuStammdaten(ergebnis.getZahlungsart()));

            // Unveraenderte KI-Lesung getrennt von den korrigierbaren Geschaeftsdaten sichern.
            beleg.setKiZahlungsart(ergebnis.getZahlungsart());
            beleg.setKiBelegdatum(ergebnis.getDokumentDatum());
            beleg.setKiBetragBrutto(ergebnis.getBetragBrutto());
            // KI-Klassifikation auf den Beleg uebernehmen — bestimmt, ob nachgelagert
            // ein LieferantGeschaeftsdokument auto-erstellt wird (siehe unten).
            beleg.setDokumentTyp(ergebnis.getDokumentTyp());
            if (ergebnis.getAiConfidence() != null) {
                beleg.setKiConfidence(java.math.BigDecimal.valueOf(ergebnis.getAiConfidence())
                        .setScale(2, java.math.RoundingMode.HALF_UP));
            }
            // Einen noch nicht zugeordneten Lieferanten aus den aktiven Stammdaten ergaenzen.
            if (ergebnis.getLieferantName() != null && !ergebnis.getLieferantName().isBlank()
                    && beleg.getLieferant() == null) {
                lieferantenRepository.findByLieferantennameIgnoreCase(ergebnis.getLieferantName().trim())
                        .filter(l -> !Boolean.FALSE.equals(l.getIstAktiv()))
                        .ifPresent(beleg::setLieferant);
            }
            // Immer die reine KI-Lesung sichern, damit Abweichungen vom am Handy
            // gewaehlten Lieferanten bei der Pruefung sichtbar bleiben.
            if (ergebnis.getLieferantName() != null && !ergebnis.getLieferantName().isBlank()) {
                beleg.setKiVorgeschlagenerLieferant(ergebnis.getLieferantName().trim());
            }

            // "Wo gezahlt" (BelegKategorie) aus der erkannten Zahlungsart ableiten,
            // solange der Buchhalter noch nichts vorgegeben hat. Karte/Lastschrift/
            // PayPal/Ueberweisung gehen ueber die Bank- oder Kreditkarten-Kategorie,
            // Bargeld geht in die Kasse. Damit verschwindet die Doppel-Eingabe
            // "Wo gezahlt" + "Zahlungsart" im Validier-Dialog.
            if (beleg.getBelegKategorie() == null
                    || beleg.getBelegKategorie() == BelegKategorie.UNZUGEORDNET) {
                BelegKategorie abgeleitet = ZahlungsartMapper.zuKategorie(beleg.getZahlungsart(), true);
                if (abgeleitet != null) {
                    beleg.setBelegKategorie(abgeleitet);
                }
            }

            // Komplettes Extraktions-JSON für Debug / spätere Re-Analyse
            try {
                beleg.setKiExtraktionJson(objectMapper.writeValueAsString(ergebnis));
            } catch (Exception ignored) {
                // Nicht kritisch — Hauptfelder sind bereits gesetzt
            }

            beleg.setKiAnalyseStatus(BelegKiAnalyseStatus.DONE);
            beleg.setKiFehlerText(null);

            // Wenn der Beleg auf TEILWEISE steht (Mischbon mit privatem + geschaeftlichem
            // Einkauf), extrahieren wir zusaetzlich die Einzelposten und persistieren sie
            // ueber den BelegSplitService — damit hat der Mitarbeiter im Handy die
            // Checkbox-Liste, mit der er die geschaeftlichen Positionen anhakt.
            if (beleg.getAufteilungsModus() == BelegAufteilungsModus.TEILWEISE) {
                try {
                    extrahiereUndSpeicherePositionen(beleg, datei);
                } catch (Exception e) {
                    log.warn("Positions-Extraktion fuer Beleg {} fehlgeschlagen: {}",
                            belegId, e.getMessage());
                }
            }

            // KI-Agent fuer Kostenstelle + Sachkonto: liest die im System
            // angelegten Optionen aus der Datenbank und schlaegt die passende
            // Zuordnung vor. High-Confidence-Treffer (>=0.95) werden direkt
            // gesetzt, damit der Beleg sofort im Verrechnungslohn-Gemeinkosten-
            // Bucket landet; bei niedriger Confidence bleibt es ein Vorschlag.
            // Best-effort: ein KI-Fehler hier darf die Beleg-Extraktion nicht
            // zurueckdrehen, deshalb nur loggen.
            try {
                kostenkontoService.klassifiziereBeleg(beleg);
            } catch (Exception e) {
                log.warn("Kostenkonto-Klassifizierung fuer Beleg {} fehlgeschlagen: {}",
                        belegId, e.getMessage());
            }
            belegRepository.save(beleg);

            // Das automatische Auslesen schreibt Datum, Betrag, Steuersatz und
            // Beschreibung an den Beleg. Auch das ist eine Aenderung an einer
            // Buchhaltungs-Aufzeichnung und gehoert ins Protokoll -- sonst
            // stuende dort nur der leere Beleg von direkt nach dem Upload und
            // niemand koennte spaeter nachvollziehen, welche Werte von der
            // Maschine kamen und welche der Buchhalter selbst gesetzt hat.
            auditService.protokolliereAenderung(beleg, beleg.getUploadedBy(),
                    "Werte automatisch vom Belegbild ausgelesen", null);

            // Auto-Erstellung Lieferanten-Dokument:
            // Wenn KI sagt "RECHNUNG"/"GUTSCHRIFT"/"LIEFERSCHEIN" und ein Lieferant
            // vorhanden ist (per Mobile-Auswahl oder Namens-Match), legen wir parallel
            // ein LieferantDokument + LieferantGeschaeftsdokument an und sortieren es
            // in seine Bestellkette ein. Datei + Vorschau werden weiterhin ueber den
            // Beleg-Endpoint ausgeliefert; das LD haelt nur Geschaeftsdaten + Lieferant.
            try {
                zuordnungService.uebernehmeScannerBeleg(beleg, ergebnis);
            } catch (Exception e) {
                // Best effort — wenn das fehlschlaegt (z.B. Duplikat), aendern wir nichts
                // am Beleg. Buchhalter kann manuell nacharbeiten.
                log.warn("Auto-Erzeugung Eingangsrechnung fuer Beleg {} fehlgeschlagen: {}",
                        belegId, e.getMessage());
            }

        } catch (Exception e) {
            log.error("KI-Analyse fuer Beleg {} fehlgeschlagen: {}", belegId, e.getMessage(), e);
            beleg.setKiAnalyseStatus(BelegKiAnalyseStatus.FAILED);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (msg.length() > 1000) {
                msg = msg.substring(0, 1000);
            }
            beleg.setKiFehlerText(msg);
            belegRepository.save(beleg);
        }
    }

    /**
     * Ruft die KI nochmal mit dem dedizierten Positions-Prompt auf und legt die
     * extrahierten Posten als {@link BelegPosition} am Beleg an. Alle Positionen
     * starten mit {@code istFuerFirma=false} — der Nutzer haakt im Handy-UI an,
     * was zur Firma gehoert.
     *
     * Fehlerfall: Wenn der KI-Call scheitert oder kein gueltiges JSON liefert,
     * legen wir keine Positionen an — der Mitarbeiter kann den Beleg dann
     * trotzdem manuell als VOLLSTAENDIG validieren oder die Positionen am PC
     * eintippen.
     */
    private void extrahiereUndSpeicherePositionen(Beleg beleg, Path datei) throws Exception {
        byte[] bytes = Files.readAllBytes(datei);
        String mimeType = beleg.getMimeType() != null ? beleg.getMimeType() : "application/pdf";
        String json = geminiService.rufGeminiApiMitPrompt(bytes, mimeType, POSITIONS_PROMPT);
        if (json == null || json.isBlank()) {
            log.info("Positions-Extraktion fuer Beleg {} lieferte kein JSON", beleg.getId());
            return;
        }
        JsonNode root = objectMapper.readTree(json);
        JsonNode positionen = root.path("positionen");
        if (!positionen.isArray() || positionen.isEmpty()) {
            log.info("Beleg {} hat keine extrahierten Positionen (Bon ohne Einzelposten)", beleg.getId());
            return;
        }

        List<BelegPosition> ergebnis = new ArrayList<>();
        int sortIdx = 0;
        for (JsonNode n : positionen) {
            BelegPosition p = new BelegPosition();
            p.setSortierung(sortIdx++);
            p.setBeschreibung(textOrFallback(n.path("beschreibung"), "Position " + sortIdx));
            p.setMenge(numericOrNull(n.path("menge")));
            p.setEinheit(textOrNull(n.path("einheit")));
            p.setEinzelpreis(numericOrNull(n.path("einzelpreis")));
            p.setBetragBrutto(numericOrNull(n.path("betragBrutto")));
            p.setMwstSatz(numericOrNull(n.path("mwstSatz")));
            p.setIstFuerFirma(false);
            ergebnis.add(p);
        }
        belegSplitService.speicherePositionen(beleg, ergebnis);
        log.info("Beleg {}: {} Positionen aus KI-Extraktion gespeichert", beleg.getId(), ergebnis.size());
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        String v = n.asText(null);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String textOrFallback(JsonNode n, String fallback) {
        String v = textOrNull(n);
        return v != null ? (v.length() > 500 ? v.substring(0, 500) : v) : fallback;
    }

    private static BigDecimal numericOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        try {
            String raw = n.asText("").trim().replace(',', '.');
            if (raw.isEmpty()) return null;
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
