package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller für Offene Posten - Eingangsrechnungen von Lieferanten.
 *
 * Berechtigungslogik (konfigurierbar per Abteilungs-Flag in der DB):
 * - darfRechnungenGenehmigen=true: Sieht ALLE Eingangsrechnungen, kann genehmigen
 * - darfRechnungenSehen=true: Sieht NUR genehmigte Rechnungen
 * - Beide Flags false / Kein Token: Kein Zugriff (leere Liste bzw. 403)
 */
@Slf4j
@RestController
@RequestMapping("/api/offene-posten")
@RequiredArgsConstructor
public class OffenePostenController {

    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final FrontendUserProfileService frontendUserProfileService;
    private final GeminiDokumentAnalyseService geminiDokumentAnalyseService;
    private final ProjektRepository projektRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final DateiSpeicherService dateiSpeicherService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * Gibt alle offenen (unbezahlten) Eingangsrechnungen zurück.
     *
     * Berechtigungslogik (konfigurierbar pro Abteilung in der DB):
     * - darfRechnungenGenehmigen=true: Alle offenen Rechnungen, kann genehmigen
     * - darfRechnungenSehen=true: NUR genehmigte Rechnungen
     * - Beides false / Kein Token: Leere Liste
     */

    @GetMapping("/eingang")
    public ResponseEntity<List<EingangsrechnungDto>> getOffeneEingangsrechnungen(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            Authentication authentication) {

        Mitarbeiter mitarbeiter = resolveMitarbeiter(token, authentication);
        boolean darfGenehmigen = hatBerechtigung(mitarbeiter, Abteilung::getDarfRechnungenGenehmigen);
        boolean darfSehen = hatBerechtigung(mitarbeiter, Abteilung::getDarfRechnungenSehen);

        List<LieferantGeschaeftsdokument> rechnungen;

        if (darfGenehmigen) {
            rechnungen = geschaeftsdokumentRepository.findAllOffeneEingangsrechnungen();
        } else if (darfSehen) {
            rechnungen = geschaeftsdokumentRepository.findAllOffeneGenehmigte();
        } else {
            rechnungen = List.of();
        }

        List<EingangsrechnungDto> dtos = rechnungen.stream()
                .map(gd -> toDto(gd, darfGenehmigen))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Gibt alle Eingangsrechnungen zurück (auch bezahlte).
     *
     * Berechtigungslogik (konfigurierbar pro Abteilung in der DB):
     * - darfRechnungenGenehmigen=true: Alle Rechnungen
     * - darfRechnungenSehen=true: NUR genehmigte Rechnungen
     * - Beides false / Kein Token: Leere Liste
     */
    @GetMapping("/eingang/alle")
    public ResponseEntity<List<EingangsrechnungDto>> getAlleEingangsrechnungen(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            Authentication authentication) {

        Mitarbeiter mitarbeiter = resolveMitarbeiter(token, authentication);
        boolean darfGenehmigen = hatBerechtigung(mitarbeiter, Abteilung::getDarfRechnungenGenehmigen);
        boolean darfSehen = hatBerechtigung(mitarbeiter, Abteilung::getDarfRechnungenSehen);

        List<LieferantGeschaeftsdokument> rechnungen;

        if (darfGenehmigen) {
            rechnungen = geschaeftsdokumentRepository.findAllEingangsrechnungen();
        } else if (darfSehen) {
            rechnungen = geschaeftsdokumentRepository.findAllGenehmigte();
        } else {
            rechnungen = List.of();
        }

        List<EingangsrechnungDto> dtos = rechnungen.stream()
                .map(gd -> toDto(gd, darfGenehmigen))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Setzt den bezahlt-Status einer Eingangsrechnung.
     * Bei Zahlung innerhalb der Skonto-Frist wird der Skonto-Abzug berechnet.
     */
    @PutMapping("/eingang/{id}/bezahlt")
    @Transactional
    public ResponseEntity<EingangsrechnungDto> setBezahltStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            Authentication authentication) {

        LieferantGeschaeftsdokument gd = geschaeftsdokumentRepository.findById(id).orElse(null);
        if (gd == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean bezahlt = body.getOrDefault("bezahlt", false);
        gd.setBezahlt(bezahlt);

        if (bezahlt) {
            gd.setBezahltAm(LocalDate.now());

            // Skonto-Berechnung wenn innerhalb Skonto-Frist
            if (gd.getSkontoTage() != null && gd.getSkontoProzent() != null
                    && gd.getDokumentDatum() != null && gd.getBetragBrutto() != null) {

                LocalDate skontoFrist = gd.getDokumentDatum().plusDays(gd.getSkontoTage());
                boolean innerhalbSkonto = !LocalDate.now().isAfter(skontoFrist);

                if (innerhalbSkonto) {
                    // Skonto anwenden
                    java.math.BigDecimal brutto = gd.getBetragBrutto();
                    java.math.BigDecimal skontoBetrag = brutto
                            .multiply(gd.getSkontoProzent())
                            .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    java.math.BigDecimal gezahlt = brutto.subtract(skontoBetrag);

                    gd.setTatsaechlichGezahlt(gezahlt);
                    gd.setMitSkonto(true);
                } else {
                    // Kein Skonto - voller Betrag
                    gd.setTatsaechlichGezahlt(gd.getBetragBrutto());
                    gd.setMitSkonto(false);
                }
            } else {
                // Keine Skonto-Konditionen - voller Betrag
                gd.setTatsaechlichGezahlt(gd.getBetragBrutto());
                gd.setMitSkonto(false);
            }
        } else {
            gd.setBezahltAm(null);
            gd.setTatsaechlichGezahlt(null);
            gd.setMitSkonto(false);
        }

        geschaeftsdokumentRepository.save(gd);
        boolean darfGenehmigen = hatBerechtigung(resolveMitarbeiter(token, authentication), Abteilung::getDarfRechnungenGenehmigen);
        return ResponseEntity.ok(toDto(gd, darfGenehmigen));
    }

    /**
     * Setzt den genehmigt-Status einer Eingangsrechnung.
     * Nur Mitarbeiter mit darfRechnungenGenehmigen dürfen genehmigen.
     */
    @PatchMapping("/eingang/{id}/genehmigen")
    @Transactional
    public ResponseEntity<EingangsrechnungDto> setGenehmigtStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            Authentication authentication) {

        Mitarbeiter mitarbeiter = resolveMitarbeiter(token, authentication);
        boolean darfGenehmigen = hatBerechtigung(mitarbeiter, Abteilung::getDarfRechnungenGenehmigen);

        if (!darfGenehmigen) {
            return ResponseEntity.status(403).build(); // Forbidden
        }

        LieferantGeschaeftsdokument gd = geschaeftsdokumentRepository.findById(id).orElse(null);
        if (gd == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean genehmigt = body.getOrDefault("genehmigt", false);
        gd.setGenehmigt(genehmigt);

        LieferantGeschaeftsdokument saved = geschaeftsdokumentRepository.save(gd);
        return ResponseEntity.ok(toDto(saved, darfGenehmigen));
    }

    // ==================== Ausgangsrechnungen manuell ====================

    /**
     * Analysiert eine hochgeladene Datei per Gemini AI und gibt die extrahierten
     * Rechnungsdaten zurück (Vorschau, ohne Speicherung).
     */
    @PostMapping("/ausgang/analyze")
    public ResponseEntity<?> analyzeAusgangsrechnung(
            @RequestParam("datei") MultipartFile datei) {
        if (datei.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Keine Datei hochgeladen"));
        }
        try {
            // Temporäre Datei erstellen für Analyse
            String originalFilename = StringUtils.cleanPath(
                    datei.getOriginalFilename() != null ? datei.getOriginalFilename() : "upload.pdf");
            if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Ungültiger Dateiname"));
            }
            Path tempDir = Files.createTempDirectory("ausgang-analyze-");
            Path tempFile = tempDir.resolve(originalFilename);
            datei.transferTo(tempFile.toFile());

            try {
                LieferantDokumentDto.AnalyzeResponse result =
                        geminiDokumentAnalyseService.analyzeFile(tempFile, originalFilename);
                if (result == null) {
                    return ResponseEntity.ok(Map.of("error", "Analyse fehlgeschlagen - bitte manuell ausfüllen"));
                }
                return ResponseEntity.ok(result);
            } finally {
                // Temp-Dateien aufräumen
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(tempDir);
            }
        } catch (Exception e) {
            log.error("Fehler bei Ausgangsrechnung-Analyse: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("error", "Analyse fehlgeschlagen: " + e.getMessage()));
        }
    }

    /**
     * Importiert eine manuell hochgeladene Ausgangsrechnung als offenen Posten.
     * Erstellt ein ProjektGeschaeftsdokument mit Zuordnung zum Projekt.
     */
    @PostMapping("/ausgang/import")
    @Transactional
    public ResponseEntity<?> importAusgangsrechnung(
            @RequestParam("datei") MultipartFile datei,
            @RequestParam("projektId") Long projektId,
            @RequestParam("rechnungsnummer") String rechnungsnummer,
            @RequestParam(value = "rechnungsdatum", required = false) String rechnungsdatumStr,
            @RequestParam(value = "faelligkeitsdatum", required = false) String faelligkeitsdatumStr,
            @RequestParam(value = "betragBrutto", required = false) String betragBruttoStr,
            @RequestParam(value = "geschaeftsdokumentart", required = false, defaultValue = "Rechnung") String geschaeftsdokumentart) {

        if (datei.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Keine Datei hochgeladen"));
        }
        if (!StringUtils.hasText(rechnungsnummer)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rechnungsnummer ist erforderlich"));
        }

        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (projekt == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Projekt nicht gefunden"));
        }

        // Duplikat-Prüfung
        if (projektDokumentRepository.existsByDokumentid(rechnungsnummer)) {
            return ResponseEntity.status(409).body(Map.of("error",
                    "Rechnungsnummer '" + rechnungsnummer + "' existiert bereits"));
        }

        try {
            // Datei speichern
            ProjektDokument saved = dateiSpeicherService.speichereDatei(
                    datei, projektId, DokumentGruppe.GESCHAEFTSDOKUMENTE);

            // Falls speichereDatei kein ProjektGeschaeftsdokument erstellt hat (z.B. bei Nicht-ZUGFeRD),
            // müssen wir es zu einem upgraden
            ProjektGeschaeftsdokument geschaeftsdokument;
            if (saved instanceof ProjektGeschaeftsdokument existing) {
                geschaeftsdokument = existing;
            } else {
                // Bestehenden Eintrag löschen und als Geschäftsdokument neu anlegen
                String gespeicherterDateiname = saved.getGespeicherterDateiname();
                String originalDateiname = saved.getOriginalDateiname();
                String dateityp = saved.getDateityp();
                Long dateiGroesse = saved.getDateigroesse();
                projektDokumentRepository.delete(saved);
                projektDokumentRepository.flush();

                geschaeftsdokument = new ProjektGeschaeftsdokument();
                geschaeftsdokument.setProjekt(projekt);
                geschaeftsdokument.setOriginalDateiname(originalDateiname);
                geschaeftsdokument.setGespeicherterDateiname(gespeicherterDateiname);
                geschaeftsdokument.setDateityp(dateityp);
                geschaeftsdokument.setDateigroesse(dateiGroesse);
                geschaeftsdokument.setUploadDatum(LocalDate.now());
                geschaeftsdokument.setDokumentGruppe(DokumentGruppe.GESCHAEFTSDOKUMENTE);
            }

            // Metadaten setzen
            geschaeftsdokument.setDokumentid(rechnungsnummer);
            geschaeftsdokument.setGeschaeftsdokumentart(geschaeftsdokumentart);
            geschaeftsdokument.setBezahlt(false);

            if (StringUtils.hasText(rechnungsdatumStr)) {
                geschaeftsdokument.setRechnungsdatum(LocalDate.parse(rechnungsdatumStr));
            }
            if (StringUtils.hasText(faelligkeitsdatumStr)) {
                geschaeftsdokument.setFaelligkeitsdatum(LocalDate.parse(faelligkeitsdatumStr));
            }
            if (StringUtils.hasText(betragBruttoStr)) {
                geschaeftsdokument.setBruttoBetrag(new BigDecimal(betragBruttoStr));
            }

            ProjektGeschaeftsdokument result = projektDokumentRepository.save(geschaeftsdokument);
            log.info("Manuelle Ausgangsrechnung importiert: {} für Projekt {} (ID: {})",
                    rechnungsnummer, projekt.getBauvorhaben(), result.getId());

            return ResponseEntity.ok(Map.of(
                    "id", result.getId(),
                    "rechnungsnummer", rechnungsnummer,
                    "projektId", projektId
            ));
        } catch (Exception e) {
            log.error("Fehler beim Import der Ausgangsrechnung: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Import fehlgeschlagen: " + e.getMessage()));
        }
    }

    // ==================== Hilfsmethoden ====================

    /**
     * Löst den Mitarbeiter auf: zuerst über X-Auth-Token (Legacy),
     * dann über die Session-Authentifizierung (FrontendUserProfile → Mitarbeiter).
     */
    private Mitarbeiter resolveMitarbeiter(String token, Authentication authentication) {
        // 1. Versuch: Legacy-Token
        if (StringUtils.hasText(token)) {
            Mitarbeiter m = mitarbeiterRepository.findByLoginToken(token).orElse(null);
            if (m != null) return m;
        }

        // 2. Fallback: Session-basierte Authentifizierung
        if (authentication != null && authentication.getPrincipal() instanceof FrontendUserPrincipal principal) {
            return frontendUserProfileService.findById(principal.getId())
                    .map(FrontendUserProfile::getMitarbeiter)
                    .orElse(null);
        }

        return null;
    }

    /**
     * Prüft ob der Mitarbeiter mindestens eine Abteilung hat, bei der das
     * angegebene Flag true ist.
     */
    private boolean hatBerechtigung(Mitarbeiter mitarbeiter,
            java.util.function.Function<Abteilung, Boolean> flagGetter) {
        if (mitarbeiter == null || mitarbeiter.getAbteilungen() == null) {
            return false;
        }
        return mitarbeiter.getAbteilungen().stream()
                .anyMatch(abt -> Boolean.TRUE.equals(flagGetter.apply(abt)));
    }

    private EingangsrechnungDto toDto(LieferantGeschaeftsdokument gd, boolean darfGenehmigen) {
        EingangsrechnungDto dto = new EingangsrechnungDto();
        dto.id = gd.getId();
        dto.dokumentNummer = gd.getDokumentNummer();
        dto.dokumentDatum = gd.getDokumentDatum();
        dto.zahlungsziel = gd.getZahlungsziel();
        dto.betragNetto = gd.getBetragNetto() != null ? gd.getBetragNetto().doubleValue() : null;
        dto.betragBrutto = gd.getBetragBrutto() != null ? gd.getBetragBrutto().doubleValue() : null;
        dto.bezahlt = Boolean.TRUE.equals(gd.getBezahlt());
        dto.bezahltAm = gd.getBezahltAm();
        dto.bereitsGezahlt = Boolean.TRUE.equals(gd.getBereitsGezahlt());
        dto.zahlungsart = gd.getZahlungsart();
        dto.genehmigt = Boolean.TRUE.equals(gd.getGenehmigt());
        dto.darfGenehmigen = darfGenehmigen;

        // Skonto-Felder
        dto.skontoTage = gd.getSkontoTage();
        dto.skontoProzent = gd.getSkontoProzent() != null ? gd.getSkontoProzent().doubleValue() : null;
        dto.nettoTage = gd.getNettoTage();
        dto.tatsaechlichGezahlt = gd.getTatsaechlichGezahlt() != null ? gd.getTatsaechlichGezahlt().doubleValue()
                : null;
        dto.mitSkonto = Boolean.TRUE.equals(gd.getMitSkonto());

        // Skonto-Frist und verbleibende Tage berechnen
        if (gd.getSkontoTage() != null && gd.getDokumentDatum() != null && !dto.bezahlt) {
            dto.skontoFrist = gd.getDokumentDatum().plusDays(gd.getSkontoTage());
            long verbleibend = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dto.skontoFrist);
            dto.skontoVerbleibendeTage = (int) verbleibend;
            dto.skontoAbgelaufen = verbleibend < 0;
        }

        if (gd.getDokument() != null) {
            dto.dokumentId = gd.getDokument().getId();
            dto.dateiname = gd.getDokument().getEffektiverDateiname();

            if (gd.getDokument().getLieferant() != null) {
                dto.lieferantId = gd.getDokument().getLieferant().getId();
                dto.lieferantName = gd.getDokument().getLieferant().getLieferantenname();
            }

            // PDF-URL zusammenbauen
            if (gd.getDokument().getAttachment() != null) {
                // Dokument aus Email-Attachment - verwende Unified Email API
                var att = gd.getDokument().getAttachment();
                if (att.getEmail() != null) {
                    dto.pdfUrl = "/api/emails/" + att.getEmail().getId() +
                            "/attachments/" + att.getId();
                }
            } else if (gd.getDokument().getGespeicherterDateiname() != null) {
                // Manuell hochgeladenes Dokument
                dto.pdfUrl = "/api/lieferanten/" + dto.lieferantId +
                        "/dokumente/" + gd.getDokument().getId() + "/download";
            }
        }

        // Überfällig berechnen
        if (dto.zahlungsziel != null && !dto.bezahlt) {
            dto.ueberfaellig = LocalDate.now().isAfter(dto.zahlungsziel);
        }

        // Referenznummer für Gutschriften
        dto.referenzNummer = gd.getReferenzNummer();
        if (gd.getDokument() != null && gd.getDokument().getTyp() != null) {
            dto.typ = gd.getDokument().getTyp().name();
        }

        return dto;
    }

    /**
     * DTO für Eingangsrechnung mit Skonto-Informationen.
     */
    public static class EingangsrechnungDto {
        public Long id;
        public Long dokumentId;
        public Long lieferantId;
        public String lieferantName;
        public String dokumentNummer;
        public LocalDate dokumentDatum;
        public LocalDate zahlungsziel;
        public Double betragNetto;
        public Double betragBrutto;
        public boolean bezahlt;
        public LocalDate bezahltAm;
        public boolean bereitsGezahlt;
        public String zahlungsart;
        public String dateiname;
        public String pdfUrl;
        public boolean ueberfaellig;
        public boolean genehmigt;
        public boolean darfGenehmigen; // true wenn Abteilung das Flag darfRechnungenGenehmigen hat

        // Neu für Gutschriften-Hierarchie
        public String referenzNummer;
        public String typ; // RECHNUNG oder GUTSCHRIFT

        // Skonto-Felder
        public Integer skontoTage;
        public Double skontoProzent;
        public Integer nettoTage;
        public LocalDate skontoFrist;
        public Integer skontoVerbleibendeTage; // Positive = noch Zeit, Negative = abgelaufen
        public boolean skontoAbgelaufen;
        public Double tatsaechlichGezahlt;
        public boolean mitSkonto;
    }
}
