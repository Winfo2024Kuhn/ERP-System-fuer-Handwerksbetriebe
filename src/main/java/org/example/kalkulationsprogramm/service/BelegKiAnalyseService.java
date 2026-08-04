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
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
    private final LieferantDokumentRepository lieferantDokumentRepository;
    private final LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    private final GeminiDokumentAnalyseService geminiService;
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
            beleg.setZahlungsart(ergebnis.getZahlungsart());
            // KI-Klassifikation auf den Beleg uebernehmen — bestimmt, ob nachgelagert
            // ein LieferantGeschaeftsdokument auto-erstellt wird (siehe unten).
            beleg.setDokumentTyp(ergebnis.getDokumentTyp());
            if (ergebnis.getAiConfidence() != null) {
                beleg.setKiConfidence(java.math.BigDecimal.valueOf(ergebnis.getAiConfidence())
                        .setScale(2, java.math.RoundingMode.HALF_UP));
            }
            // KI-Vorschlag fuer den Lieferanten nur uebernehmen, wenn der Name auch
            // tatsaechlich einem aktiven Lieferanten in der Stammdaten-Tabelle
            // entspricht. Andernfalls hat der Buchhalter im Validier-Dialog nichts
            // davon — "KI-Vorschlag: Q1 Energie AG" anzuzeigen, obwohl dieser
            // Lieferant gar nicht angelegt ist, fuehrt zu Klick-Aufwand ohne Nutzen
            // (man kann den Vorschlag nicht uebernehmen, ohne ihn vorher manuell
            // anzulegen). Quelle: Lieferantenstammdaten siehe Lieferanten-Entity.
            if (ergebnis.getLieferantName() != null && !ergebnis.getLieferantName().isBlank()
                    && beleg.getLieferant() == null) {
                lieferantenRepository.findByLieferantennameIgnoreCase(ergebnis.getLieferantName().trim())
                        .filter(l -> !Boolean.FALSE.equals(l.getIstAktiv()))
                        .ifPresent(beleg::setLieferant);
            }
            beleg.setKiVorgeschlagenerLieferant(
                    beleg.getLieferant() != null ? beleg.getLieferant().getLieferantenname() : null);

            // "Wo gezahlt" (BelegKategorie) aus der erkannten Zahlungsart ableiten,
            // solange der Buchhalter noch nichts vorgegeben hat. Karte/Lastschrift/
            // PayPal/Ueberweisung gehen ueber die Bank- oder Kreditkarten-Kategorie,
            // Bargeld geht in die Kasse. Damit verschwindet die Doppel-Eingabe
            // "Wo gezahlt" + "Zahlungsart" im Validier-Dialog.
            if (beleg.getBelegKategorie() == null
                    || beleg.getBelegKategorie() == BelegKategorie.UNZUGEORDNET) {
                BelegKategorie abgeleitet = ableitenBelegKategorie(ergebnis.getZahlungsart());
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
            // Zuordnung vor. High-Confidence-Treffer (>=0.80) werden direkt
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

            // Auto-Erstellung Eingangsrechnung:
            // Wenn KI sagt "RECHNUNG"/"GUTSCHRIFT" und ein Lieferant vorhanden ist
            // (per Mobile-Auswahl oder Namens-Match), legen wir parallel ein
            // LieferantDokument + LieferantGeschaeftsdokument an, sodass der Beleg
            // in der "Eingangsrechnungen"-Uebersicht erscheint. Datei + Vorschau
            // werden weiterhin ueber den Beleg-Endpoint ausgeliefert; das LD haelt
            // nur die Geschaeftsdaten + Lieferantenbezug.
            try {
                erstelleEingangsrechnungFallsRechnung(beleg, ergebnis);
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

    /**
     * Mappt eine KI-erkannte Zahlungsart auf die passende {@link BelegKategorie}
     * ("Wo gezahlt"). Liefert null, wenn keine eindeutige Zuordnung moeglich ist —
     * dann bleibt die Kategorie auf {@code UNZUGEORDNET} und der Buchhalter
     * waehlt manuell.
     *
     * Mapping:
     *  - BAR                                       -> KASSE_AUSGABE (Bon = Ausgabe)
     *  - KREDITKARTE                               -> KREDITKARTE
     *  - SEPA_LASTSCHRIFT / UEBERWEISUNG /
     *    VORAUSKASSE / PAYPAL / AMAZON_PAY         -> BANK (alles per Bankkonto)
     *  - SONSTIGE / null                           -> keine Ableitung
     */
    static BelegKategorie ableitenBelegKategorie(String zahlungsart) {
        if (zahlungsart == null || zahlungsart.isBlank()) {
            return null;
        }
        return switch (zahlungsart.trim().toUpperCase()) {
            case "BAR" -> BelegKategorie.KASSE_AUSGABE;
            case "KREDITKARTE" -> BelegKategorie.KREDITKARTE;
            case "SEPA_LASTSCHRIFT", "UEBERWEISUNG", "VORAUSKASSE",
                 "PAYPAL", "AMAZON_PAY" -> BelegKategorie.BANK;
            default -> null;
        };
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

    /**
     * Legt automatisch ein LieferantDokument + LieferantGeschaeftsdokument an,
     * wenn die KI den Beleg als RECHNUNG oder GUTSCHRIFT klassifiziert hat und
     * ein Lieferant zugeordnet werden konnte.
     *
     * Idempotenz:
     *  - Wenn fuer den Beleg schon ein LD existiert (Re-Analyse-Lauf), nichts tun.
     *  - Wenn fuer Lieferant + Rechnungsnummer schon ein LGD existiert (manueller
     *    Import war schneller, oder E-Mail-Anhang), nichts tun und im Log vermerken.
     */
    private void erstelleEingangsrechnungFallsRechnung(Beleg beleg,
                                                       LieferantDokumentDto.AnalyzeResponse ergebnis) {
        LieferantDokumentTyp typ = beleg.getDokumentTyp();
        Lieferanten lieferant = beleg.getLieferant();
        if (typ == null || lieferant == null) {
            return;
        }
        if (typ != LieferantDokumentTyp.RECHNUNG && typ != LieferantDokumentTyp.GUTSCHRIFT) {
            return;
        }

        // Idempotenz 1: Re-Analyse-Lauf -> nicht doppelt anlegen
        if (lieferantDokumentRepository.findByBelegId(beleg.getId()).isPresent()) {
            log.debug("LieferantDokument fuer Beleg {} existiert bereits, kein erneutes Anlegen", beleg.getId());
            return;
        }
        // Idempotenz 2: Rechnungsnummer schon beim Lieferanten erfasst
        String dokNr = beleg.getBelegNummer();
        if (dokNr != null && !dokNr.isBlank()
                && lieferantGeschaeftsdokumentRepository
                        .existsByLieferantIdAndDokumentNummer(lieferant.getId(), dokNr)) {
            log.info("Rechnungs-Nr {} bei Lieferant {} schon vorhanden — Beleg {} bleibt eigenstaendig",
                    dokNr, lieferant.getId(), beleg.getId());
            return;
        }

        // Datei: kein erneutes Kopieren — wir verweisen relativ auf den Beleg-Pfad.
        // resolveLieferantDokumentPath sucht u.a. uploads/{filename}; wenn wir hier
        // "belege/<gespeicherterName>" speichern, klappt der Lookup als
        // uploads/belege/<gespeicherterName>.
        String gespeicherterFuerLD = beleg.getGespeicherterDateiname() != null
                ? "belege/" + beleg.getGespeicherterDateiname()
                : null;

        LieferantDokument ld = new LieferantDokument();
        ld.setLieferant(lieferant);
        ld.setTyp(typ);
        ld.setOriginalDateiname(beleg.getOriginalDateiname());
        ld.setGespeicherterDateiname(gespeicherterFuerLD);
        ld.setUploadDatum(LocalDateTime.now());
        ld.setUploadedBy(beleg.getUploadedBy());
        ld.setBeleg(beleg);
        ld = lieferantDokumentRepository.save(ld);

        LieferantGeschaeftsdokument lgd = new LieferantGeschaeftsdokument();
        lgd.setDokument(ld);
        lgd.setDokumentNummer(beleg.getBelegNummer());
        lgd.setDokumentDatum(beleg.getBelegDatum());
        lgd.setBetragNetto(beleg.getBetragNetto());
        lgd.setBetragBrutto(beleg.getBetragBrutto());
        lgd.setMwstSatz(beleg.getMwstSatz());
        lgd.setZahlungsart(beleg.getZahlungsart());
        lgd.setBereitsGezahlt(Boolean.TRUE.equals(ergebnis.getBereitsGezahlt()));
        lgd.setSkontoTage(ergebnis.getSkontoTage());
        lgd.setSkontoProzent(ergebnis.getSkontoProzent());
        lgd.setNettoTage(ergebnis.getNettoTage());
        lgd.setZahlungsziel(ergebnis.getZahlungsziel());
        lgd.setLiefertermin(ergebnis.getLiefertermin());
        lgd.setReferenzNummer(ergebnis.getReferenzNummer());
        lgd.setBestellnummer(ergebnis.getBestellnummer());
        if (ergebnis.getAiConfidence() != null) {
            lgd.setAiConfidence(ergebnis.getAiConfidence());
        }
        lgd.setAnalysiertAm(LocalDateTime.now());
        try {
            lgd.setAiRawJson(objectMapper.writeValueAsString(ergebnis));
        } catch (Exception ignored) {
            // unkritisch
        }
        lieferantGeschaeftsdokumentRepository.save(lgd);

        log.info("Auto-Eingangsrechnung erzeugt: Beleg {} -> LieferantDokument {}",
                beleg.getId(), ld.getId());
    }
}
