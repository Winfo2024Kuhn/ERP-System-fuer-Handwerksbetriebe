package org.example.kalkulationsprogramm.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.example.kalkulationsprogramm.domain.Lohnabrechnung;
import org.example.kalkulationsprogramm.domain.LohnabrechnungStatus;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.LohnabrechnungRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.util.KiGeldbetrag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legt Lohnabrechnungen aus Steuerberater-PDFs ab: Sammel-PDF nach den Seitenbereichen
 * der KI aufteilen, jede Teil-PDF dem passenden Mitarbeiter zuweisen und speichern.
 * Korrektur-Läufe für denselben Monat ersetzen den alten Eintrag samt Teil-PDF.
 */
@Slf4j
@Service
public class SteuerberaterLohnabrechnungAblage {

    /** "3" oder "1-2" (1-basiert). */
    private static final Pattern SEITENBEREICH = Pattern.compile("(\\d+)\\s*(?:-\\s*(\\d+))?");

    private final MitarbeiterRepository mitarbeiterRepository;
    private final LohnabrechnungRepository lohnabrechnungRepository;
    private final String lohnabrechnungDir;

    public SteuerberaterLohnabrechnungAblage(MitarbeiterRepository mitarbeiterRepository,
            LohnabrechnungRepository lohnabrechnungRepository,
            @Value("${file.lohnabrechnung-dir:uploads/lohnabrechnungen}") String lohnabrechnungDir) {
        this.mitarbeiterRepository = mitarbeiterRepository;
        this.lohnabrechnungRepository = lohnabrechnungRepository;
        this.lohnabrechnungDir = lohnabrechnungDir;
    }

    /** Was in eine Lohnabrechnung geschrieben wird. */
    private record Abrechnung(Mitarbeiter mitarbeiter, int jahr, int monat, BigDecimal brutto, BigDecimal netto) {
    }

    /**
     * Splittet die Sammel-PDF anhand der KI-Seitenbereiche und legt pro Mitarbeiter eine
     * Lohnabrechnung an. Abrechnungen ohne erkennbaren Mitarbeiter werden übersprungen.
     *
     * @param abrechnungen Array {@code abrechnungen} aus der KI-Antwort
     * @param aiResponse   rohe KI-Antwort, wird an der Lohnabrechnung gespeichert
     */
    public void legeSammelPdfAb(JsonNode abrechnungen, String aiResponse, byte[] fileBytes,
            SteuerberaterAnhang quelle) throws IOException {
        int erstellt = 0;
        int nichtZugeordnet = 0;

        try (PDDocument originalDoc = Loader.loadPDF(fileBytes)) {
            int totalPages = originalDoc.getNumberOfPages();

            for (JsonNode node : abrechnungen) {
                String mitarbeiterName = node.has("mitarbeiterName") ? node.get("mitarbeiterName").asString(null) : null;
                Mitarbeiter mitarbeiter = mitarbeiterName != null ? findMitarbeiterByName(mitarbeiterName) : null;
                if (mitarbeiter == null) {
                    nichtZugeordnet++;
                    log.warn("[Steuerberater] Kein Mitarbeiter gefunden für Abrechnung '{}' in {} – Segment übersprungen",
                            mitarbeiterName, quelle.originalDateiname());
                    continue;
                }

                String seiten = node.has("seiten") ? node.get("seiten").asString("") : "";
                byte[] teilPdf = extrahiereSeiten(originalDoc, seiten, totalPages);
                String gespeicherterName = speicherePdf(teilPdf);

                SteuerberaterPeriode periode = periodeAus(node, quelle.originalDateiname());
                speichere(new Abrechnung(mitarbeiter, periode.jahr(), periode.monat(),
                                KiGeldbetrag.lese(node, "bruttolohn"), KiGeldbetrag.lese(node, "nettolohn")),
                        gespeicherterName, aiResponse, quelle);
                erstellt++;
            }
        }

        log.info("[Steuerberater] Sammel-PDF {} verarbeitet: {} Lohnabrechnung(en) erstellt, {} nicht zuordenbar",
                quelle.originalDateiname(), erstellt, nichtZugeordnet);
    }

    /**
     * Rückfall ohne brauchbare KI-Antwort: ganze PDF als eine Abrechnung,
     * Mitarbeiter und Periode aus dem Dateinamen.
     */
    public void legeNachDateinamenAb(String aiResponse, byte[] fileBytes, SteuerberaterAnhang quelle)
            throws IOException {
        Mitarbeiter mitarbeiter = findMitarbeiterFromFilename(quelle.originalDateiname());
        if (mitarbeiter == null) {
            log.warn("[Steuerberater] Auch im Dateinamen kein Mitarbeiter erkennbar: {} – manuell prüfen!",
                    quelle.originalDateiname());
            return;
        }

        SteuerberaterPeriode periode = SteuerberaterPeriode.ausDateiname(quelle.originalDateiname());
        String gespeicherterName = speicherePdf(fileBytes);
        speichere(new Abrechnung(mitarbeiter, periode.jahr(), periode.monat(), null, null),
                gespeicherterName, aiResponse, quelle);
    }

    /**
     * Jahr und Monat aus der KI-Antwort; fehlende oder ungültige Werte aus dem Dateinamen.
     */
    private static SteuerberaterPeriode periodeAus(JsonNode node, String dateiname) {
        Integer jahr = node.has("jahr") && node.get("jahr").canConvertToInt() ? node.get("jahr").asInt(0) : null;
        Integer monat = node.has("monat") && node.get("monat").canConvertToInt() ? node.get("monat").asInt(0) : null;
        boolean monatGueltig = monat != null && monat >= 1 && monat <= 12;
        if (jahr != null && monatGueltig) {
            return new SteuerberaterPeriode(jahr, monat);
        }
        SteuerberaterPeriode ausDatei = SteuerberaterPeriode.ausDateiname(dateiname);
        return new SteuerberaterPeriode(
                jahr != null ? jahr : ausDatei.jahr(),
                monatGueltig ? monat : ausDatei.monat());
    }

    /**
     * Legt eine Lohnabrechnung an oder ersetzt die bestehende desselben
     * Mitarbeiters für denselben Monat (Korrektur-Mail vom Steuerberater).
     */
    private void speichere(Abrechnung abrechnung, String gespeicherterDateiname, String aiResponse,
            SteuerberaterAnhang quelle) {
        Mitarbeiter mitarbeiter = abrechnung.mitarbeiter();
        Lohnabrechnung la = lohnabrechnungRepository
                .findByMitarbeiterIdAndJahrAndMonat(mitarbeiter.getId(), abrechnung.jahr(), abrechnung.monat())
                .orElseGet(Lohnabrechnung::new);

        boolean ersetzt = la.getId() != null;
        if (ersetzt) {
            loescheAlteSplitDatei(la.getGespeicherterDateiname());
        }

        la.setMitarbeiter(mitarbeiter);
        la.setSteuerberater(quelle.steuerberater());
        la.setJahr(abrechnung.jahr());
        la.setMonat(abrechnung.monat());
        la.setOriginalDateiname(quelle.originalDateiname());
        la.setGespeicherterDateiname(gespeicherterDateiname);
        la.setSourceEmail(quelle.email());
        la.setAiRawJson(aiResponse);
        la.setStatus(LohnabrechnungStatus.ANALYSIERT);
        la.setBruttolohn(abrechnung.brutto());
        la.setNettolohn(abrechnung.netto());
        la.setImportDatum(LocalDateTime.now());

        lohnabrechnungRepository.save(la);
        log.info("[Steuerberater] Lohnabrechnung für {} ({}/{}) {}",
                mitarbeiter.getNachname(), abrechnung.monat(), abrechnung.jahr(),
                ersetzt ? "aktualisiert (Korrektur)" : "erstellt");
    }

    /**
     * Löscht die alte gesplittete Teil-PDF einer ersetzten Lohnabrechnung.
     * Original-Mail-Anhänge (mail-attachment-dir) werden nie angefasst.
     */
    private void loescheAlteSplitDatei(String gespeicherterDateiname) {
        if (gespeicherterDateiname == null || gespeicherterDateiname.isBlank()) {
            return;
        }
        try {
            Path basis = Path.of(lohnabrechnungDir).toAbsolutePath().normalize();
            Path datei = basis.resolve(gespeicherterDateiname).normalize();
            if (datei.startsWith(basis)) {
                Files.deleteIfExists(datei);
            }
        } catch (IOException e) {
            log.warn("[Steuerberater] Alte Lohnabrechnungs-PDF konnte nicht gelöscht werden: {}", e.getMessage());
        }
    }

    /**
     * Speichert eine (Teil-)PDF im Lohnabrechnungs-Verzeichnis unter UUID-Namen.
     */
    private String speicherePdf(byte[] pdfBytes) throws IOException {
        Path dir = Path.of(lohnabrechnungDir);
        Files.createDirectories(dir);
        String dateiname = UUID.randomUUID() + ".pdf";
        Files.write(dir.resolve(dateiname), pdfBytes);
        return dateiname;
    }

    /**
     * Extrahiert einen 1-basierten Seitenbereich ("3" oder "1-2") als neue PDF.
     * Ungültige Angaben liefern das gesamte Dokument.
     */
    private static byte[] extrahiereSeiten(PDDocument original, String seitenRange, int totalPages) throws IOException {
        int von = 1;
        int bis = totalPages;

        if (seitenRange != null && !seitenRange.isBlank()) {
            Matcher m = SEITENBEREICH.matcher(seitenRange.trim());
            if (m.matches()) {
                von = Integer.parseInt(m.group(1));
                bis = m.group(2) != null ? Integer.parseInt(m.group(2)) : von;
            }
        }
        von = Math.max(1, Math.min(von, totalPages));
        bis = Math.max(von, Math.min(bis, totalPages));

        try (PDDocument teil = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = von - 1; i < bis; i++) {
                teil.addPage(original.getPage(i));
            }
            teil.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Findet Mitarbeiter anhand des von der KI extrahierten Namens.
     * Erst Vor- und Nachname, dann eindeutiger Nachname-Match.
     */
    private Mitarbeiter findMitarbeiterByName(String name) {
        String cleanName = name.toLowerCase();
        List<Mitarbeiter> aktive = mitarbeiterRepository.findAktiveMenschen();

        for (Mitarbeiter m : aktive) {
            if (cleanName.contains(m.getNachname().toLowerCase())
                    && cleanName.contains(m.getVorname().toLowerCase())) {
                return m;
            }
        }

        // Nachname allein reicht, wenn er eindeutig ist (KI kürzt Vornamen manchmal ab)
        Mitarbeiter einziger = null;
        for (Mitarbeiter m : aktive) {
            if (cleanName.contains(m.getNachname().toLowerCase())) {
                if (einziger != null) {
                    return null; // mehrdeutig
                }
                einziger = m;
            }
        }
        return einziger;
    }

    /**
     * Findet Mitarbeiter anhand des Nachnamens im Dateinamen.
     */
    private Mitarbeiter findMitarbeiterFromFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String cleanFilename = filename.toLowerCase()
                .replaceAll("[_\\-.]", " ")
                .replaceAll("\\s+", " ");

        for (Mitarbeiter ma : mitarbeiterRepository.findAktiveMenschen()) {
            if (cleanFilename.contains(ma.getNachname().toLowerCase())) {
                return ma;
            }
        }
        return null;
    }
}
