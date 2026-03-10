package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Angebot;
import org.example.kalkulationsprogramm.domain.AngebotDokument;
import org.example.kalkulationsprogramm.domain.AngebotGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.AngebotNotiz;
import org.example.kalkulationsprogramm.domain.AngebotNotizBild;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;

import org.example.kalkulationsprogramm.dto.Angebot.AngebotDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.Angebot.AngebotErstellenDto;
import org.example.kalkulationsprogramm.dto.Angebot.AngebotResponseDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.repository.AngebotNotizBildRepository;
import org.example.kalkulationsprogramm.repository.AngebotNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.AngebotService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.PdfAiExtractorService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.example.kalkulationsprogramm.service.ZugferdExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/angebote")
@RequiredArgsConstructor
public class AngebotController {

    private static final Logger log = LoggerFactory.getLogger(AngebotController.class);

    private final AngebotService angebotService;
    private final DateiSpeicherService dateiSpeicherService;
    private final ZugferdErstellService zugferdErstellService;
    private final ZugferdExtractorService zugferdExtractorService;
    private final PdfAiExtractorService pdfAiExtractorService;
    private final KundeRepository kundeRepository;
    private final AngebotNotizRepository angebotNotizRepository;
    private final AngebotNotizBildRepository angebotNotizBildRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final FrontendUserProfileService frontendUserProfileService;

    @PostMapping(value = "/zugferd/extract", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ZugferdDaten> extractZugferd(@RequestParam("datei") MultipartFile datei) {
        try {
            Path temp = Files.createTempFile("zugferd-", ".pdf.html");
            Files.copy(datei.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);
            ZugferdDaten daten = zugferdExtractorService.extract(temp.toString(), datei.getOriginalFilename());
            Files.deleteIfExists(temp);
            return ResponseEntity.ok(daten);
        } catch (java.io.IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * KI-gestützte PDF-Analyse für Angebote/Auftragsbestätigungen.
     * Verwendet die gleiche KI wie bei Projekten.
     */
    @PostMapping(value = "/zugferd/extract-ai", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ZugferdDaten> extractZugferdWithAi(@RequestParam("datei") MultipartFile datei,
            @RequestParam(value = "dokumentTyp", required = false) String dokumentTyp) {
        try {
            Path temp = Files.createTempFile("zugferd-angebot-ai-", ".pdf");
            Files.copy(datei.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);

            // KI-Analyse mit explizitem Dokumenttyp (Default: Angebot für diesen
            // Controller)
            String typeToUse = (dokumentTyp != null && !dokumentTyp.isBlank()) ? dokumentTyp : "Angebot";
            java.util.Optional<ZugferdDaten> aiResult = pdfAiExtractorService.analyze(temp.toString(), typeToUse);

            ZugferdDaten daten;
            if (aiResult.isPresent()) {
                daten = aiResult.get();
                // Falls Dokumenttyp leer, setze basierend auf Dateiname oder Default
                if (daten.getGeschaeftsdokumentart() == null || daten.getGeschaeftsdokumentart().isBlank()) {
                    daten.setGeschaeftsdokumentart(detectDocTypeFromFilename(datei.getOriginalFilename()));
                }
            } else {
                // Fallback auf Standard-Extraktion
                daten = zugferdExtractorService.extract(temp.toString(), datei.getOriginalFilename());
            }

            Files.deleteIfExists(temp);
            return ResponseEntity.ok(daten);
        } catch (java.io.IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private String detectDocTypeFromFilename(String filename) {
        if (filename == null)
            return "Angebot";
        String lower = filename.toLowerCase();
        if (lower.contains("auftragsbestätigung") || lower.contains("auftragsbestaetigung") || lower.contains("ab_")
                || lower.contains("ab-"))
            return "Auftragsbestätigung";
        return "Angebot";
    }

    @PostMapping
    public ResponseEntity<AngebotResponseDto> erstelle(@RequestBody AngebotErstellenDto dto) {
        AngebotResponseDto created = angebotService.erstelleAngebot(dto);
        return ResponseEntity.ok()
                .header("X-Message", "Angebot gespeichert")
                .body(created);
    }

    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<AngebotResponseDto> erstelleMitBild(
            @RequestPart("angebotDto") AngebotErstellenDto dto,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        AngebotResponseDto created = angebotService.erstelleAngebot(dto, imageFile);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Message", "Angebot gespeichert")
                .body(created);
    }

    @PostMapping(value = "/{angebotID}/dokumente", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<List<AngebotDokumentResponseDto>> uploadDokument(@PathVariable Long angebotID,
            @RequestParam("datei") List<MultipartFile> dateien,
            @RequestParam(value = "gruppe", required = false) DokumentGruppe gruppe) {
        try {
            DokumentGruppe verwendeteGruppe = gruppe != null ? gruppe : DokumentGruppe.DIVERSE_DOKUMENTE;
            List<AngebotDokumentResponseDto> dtos = dateien.stream().map(datei -> {
                AngebotDokument dokument = dateiSpeicherService.speichereAngebotsDatei(datei, angebotID,
                        verwendeteGruppe);
                return mappeDokumentZuDto(dokument);
            }).toList();
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping(value = "/{angebotID}/zugferd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> erzeugeZugferd(@PathVariable Long angebotID,
            @RequestPart("datei") MultipartFile pdf,
            @RequestPart("zugferdDaten") ZugferdDaten daten) {
        try {
            Path original = Files.createTempFile("zugferd-original-", ".pdf.html");
            Files.copy(pdf.getInputStream(), original, StandardCopyOption.REPLACE_EXISTING);

            Path zugferdPfad = zugferdErstellService.erzeuge(original.toString(), daten);
            Files.deleteIfExists(original);

            AngebotGeschaeftsdokument dokument = dateiSpeicherService
                    .speichereAngebotsZugferdDatei(zugferdPfad, pdf.getOriginalFilename(), angebotID, daten);
            AngebotDokumentResponseDto dto = mappeDokumentZuDto(dokument);

            Files.deleteIfExists(zugferdPfad);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (Exception e) {
            log.error("Fehler bei ZUGFeRD-Erzeugung für Angebot {}", angebotID, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Unbekannter Fehler"));
        }
    }

    @GetMapping("/{angebotID}/dokumente")
    public ResponseEntity<List<AngebotDokumentResponseDto>> listeDokumente(@PathVariable Long angebotID,
            @RequestParam(value = "gruppe", required = false) DokumentGruppe gruppe) {
        List<AngebotDokument> dokumente = dateiSpeicherService.holeDokumenteZuAngebot(angebotID);
        // Filter by DokumentGruppe if specified
        if (gruppe != null) {
            dokumente = dokumente.stream()
                    .filter(d -> d.getDokumentGruppe() == gruppe)
                    .toList();
        }
        List<AngebotDokumentResponseDto> dtos = dokumente.stream().map(this::mappeDokumentZuDto).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Sendbare Dokumente (Angebot/Auftragsbestätigung) für E-Mail-Versand-Dropdown.
     * Analog zu ProjektController.emailDokumente().
     */
    @GetMapping("/{angebotID}/email-dokumente")
    public ResponseEntity<List<AngebotDokumentResponseDto>> emailDokumente(@PathVariable Long angebotID) {
        List<AngebotDokument> dokumente = dateiSpeicherService.holeDokumenteZuAngebot(angebotID);
        java.util.List<AngebotDokumentResponseDto> dtos = dokumente.stream()
                .filter(d -> {
                    boolean isBusiness = d instanceof AngebotGeschaeftsdokument;
                    String name = d.getOriginalDateiname() != null ? d.getOriginalDateiname().toLowerCase() : "";
                    boolean isPdf = (d.getDateityp() != null && d.getDateityp().toLowerCase().contains("pdf"))
                            || name.endsWith(".pdf") || name.endsWith(".pdf.html");
                    boolean isDrawing = name.contains("zeichnung") || name.contains("entwurf");
                    return isBusiness || (isPdf && isDrawing);
                })
                .map(this::mappeDokumentZuDto)
                .toList();

        // Sortieren: neueste zuerst, dann alphabetisch
        java.util.Comparator<java.time.LocalDate> dateDesc = java.util.Comparator
                .nullsLast(java.util.Comparator.naturalOrder());
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.GERMANY);
        collator.setStrength(java.text.Collator.PRIMARY);

        dtos = dtos.stream()
                .sorted(java.util.Comparator
                        .comparing(AngebotDokumentResponseDto::getUploadDatum, dateDesc.reversed())
                        .thenComparing(AngebotDokumentResponseDto::getOriginalDateiname,
                                java.util.Comparator.nullsLast(collator)))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @DeleteMapping("/{angebotID}/dokumente/{dokumentID}")
    public ResponseEntity<Void> loescheDokument(@PathVariable Long angebotID, @PathVariable Long dokumentID) {
        try {
            dateiSpeicherService.loescheAngebotDatei(dokumentID);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public List<AngebotResponseDto> liste(@RequestParam(required = false) Integer jahr,
            @RequestParam(required = false) String kundenname,
            @RequestParam(required = false) String kunde,
            @RequestParam(required = false) String bauvorhaben,
            @RequestParam(required = false) String angebotsnummer,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean nurOhneProjekt) {
        // "kunde" als Alias für "kundenname" akzeptieren
        String effektiverKundenname = kundenname != null ? kundenname : kunde;
        return angebotService.suche(jahr, effektiverKundenname, bauvorhaben, angebotsnummer, q, nurOhneProjekt);
    }

    @GetMapping("/jahre")
    public List<Integer> verfuegbareJahre() {
        return angebotService.verfuegbareAnlegeJahre();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loesche(@PathVariable Long id) {
        return angebotService.loesche(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<AngebotResponseDto> aktualisiere(@PathVariable Long id,
            @RequestBody AngebotErstellenDto dto) {
        AngebotResponseDto aktualisiert = angebotService.aktualisiereAngebot(id, dto);
        if (aktualisiert == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("X-Message", "Angebot gespeichert")
                .body(aktualisiert);
    }

    @PatchMapping(value = "/{id}/kurzbeschreibung", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<AngebotResponseDto> updateKurzbeschreibung(@PathVariable Long id,
            @RequestBody String kurzbeschreibung) {
        AngebotResponseDto updated = angebotService.updateAngebotKurzbeschreibung(id, kurzbeschreibung);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @PutMapping(value = "/{id}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<AngebotResponseDto> aktualisiereMitBild(@PathVariable Long id,
            @RequestPart("angebotDto") AngebotErstellenDto dto,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        AngebotResponseDto aktualisiert = angebotService.aktualisiereAngebot(id, dto, imageFile);
        if (aktualisiert == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("X-Message", "Angebot gespeichert")
                .body(aktualisiert);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AngebotResponseDto> hole(@PathVariable Long id) {
        AngebotResponseDto dto = angebotService.findeDto(id);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/projekt-vorlage")
    public ResponseEntity<ProjektErstellenDto> projektVorlage(@PathVariable Long id) {
        Angebot angebot = angebotService.finde(id);
        if (angebot == null) {
            return ResponseEntity.notFound().build();
        }
        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setBauvorhaben(angebot.getBauvorhaben());
        dto.setKunde(angebot.getKunde().getName());
        dto.setBruttoPreis(angebot.getBetrag());
        dto.setKundennummer(angebot.getKunde().getKundennummer());
        dto.setAnlegedatum(angebot.getAnlegedatum());
        List<String> emails = angebot.getKundenEmails() == null ? List.of()
                : angebot.getKundenEmails().stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        dto.setKundenEmails(emails);
        if (angebot.getKunde().getKundennummer() != null) {
            dto.setKundenId(kundeRepository.findByKundennummerIgnoreCase(angebot.getKunde().getKundennummer())
                    .map(Kunde::getId)
                    .orElse(null));
        }
        dto.setAngebotIds(java.util.List.of(angebot.getId()));
        return ResponseEntity.ok(dto);
    }

    private AngebotDokumentResponseDto mappeDokumentZuDto(AngebotDokument dokument) {
        AngebotDokumentResponseDto dto = new AngebotDokumentResponseDto();
        dto.setId(dokument.getId());
        dto.setOriginalDateiname(dokument.getOriginalDateiname());
        dto.setGespeicherterDateiname(dokument.getGespeicherterDateiname());
        dto.setDateityp(dokument.getDateityp());
        dto.setUrl("/api/dokumente/" + dokument.getGespeicherterDateiname());
        dto.setThumbnailUrl("/api/dokumente/" + dokument.getGespeicherterDateiname() + "/thumbnail");
        String nameForType = dokument.getOriginalDateiname() != null ? dokument.getOriginalDateiname().toLowerCase()
                : (dokument.getGespeicherterDateiname() != null ? dokument.getGespeicherterDateiname().toLowerCase()
                        : "");
        boolean isHiCAD = nameForType.endsWith(".sza") || nameForType.endsWith(".tcd");
        if (isHiCAD) {
            try {
                dto.setNetzwerkPfad(dateiSpeicherService.holeNetzwerkPfad(dokument.getGespeicherterDateiname()));
            } catch (Exception ignored) {
            }
        }
        dto.setDokumentGruppe(dokument.getDokumentGruppe().name());
        dto.setUploadDatum(dokument.getUploadDatum());
        dto.setEmailVersandDatum(dokument.getEmailVersandDatum());
        if (dokument instanceof AngebotGeschaeftsdokument geschaeftsdokument) {
            dto.setRechnungsnummer(geschaeftsdokument.getDokumentid());
            dto.setGeschaeftsdokumentart(geschaeftsdokument.getGeschaeftsdokumentart());
            dto.setRechnungsbetrag(geschaeftsdokument.getBruttoBetrag());

        }
        return dto;
    }

    // --- Notizen (Analog zu ProjektController) ---

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AngebotNotizDto {
        private Long id;
        private String notiz;
        private String erstelltAm;
        private Long mitarbeiterId;
        private String mitarbeiterVorname;
        private String mitarbeiterNachname;
        private boolean mobileSichtbar;
        private boolean nurFuerErsteller;
        private boolean canEdit;
        private List<AngebotNotizBildDto> bilder;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AngebotNotizBildDto {
        private Long id;
        private String url;
        private String originalDateiname;
        private String erstelltAm;
    }

    // Hilfsmethode analog zu ProjektController
    private Mitarbeiter resolveMitarbeiter(Long userProfileId, Long mitarbeiterId, String token) {
        if (mitarbeiterId != null) {
            return mitarbeiterRepository.findById(mitarbeiterId).orElse(null);
        }
        if (token != null && !token.isBlank()) {
            return mitarbeiterRepository.findByLoginToken(token).orElse(null);
        }
        if (userProfileId != null) {
            return frontendUserProfileService.findById(userProfileId)
                    .map(profile -> profile.getMitarbeiter())
                    .orElse(null);
        }
        return null;
    }

    private boolean hasEditPermission(AngebotNotiz notiz, Mitarbeiter requester, boolean isMobile) {
        if (requester == null)
            return false;
        if (!isMobile)
            return true; // PC User dürfen alles

        // Private Notizen dürfen nur vom Ersteller bearbeitet werden (auch am PC
        // theoretisch, aber hier prüfen wir erst mal permission allgemein)
        // Aber warten: Die Anforderung war "nur für Ersteller sichtbar". Bearbeiten
        // darf man wahrscheinlich auch nur eigene private Notizen.
        // Im ProjektController Logik war:
        // PC User sehen alles (außer evtl. private? Nein, Anforderung war generell "nur
        // für Ersteller sichtbar").
        // Prüfen wir mal die Projekt-Logik. Im ProjektController wurde beim GET
        // gefiltert.
        // Beim Update/Delete wurde "canEdit" geprüft.

        return notiz.getMitarbeiter() != null && notiz.getMitarbeiter().getId().equals(requester.getId());
    }

    private AngebotNotizDto mapNotizToDto(AngebotNotiz notiz, Mitarbeiter currentMitarbeiter, boolean isMobile) {
        AngebotNotizDto dto = new AngebotNotizDto();
        dto.setId(notiz.getId());
        dto.setNotiz(notiz.getNotiz());
        dto.setErstelltAm(
                notiz.getErstelltAm().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        dto.setMitarbeiterId(notiz.getMitarbeiter().getId());
        dto.setMitarbeiterVorname(notiz.getMitarbeiter().getVorname());
        dto.setMitarbeiterNachname(notiz.getMitarbeiter().getNachname());
        dto.setMobileSichtbar(notiz.isMobileSichtbar());
        dto.setNurFuerErsteller(notiz.isNurFuerErsteller());

        dto.setCanEdit(hasEditPermission(notiz, currentMitarbeiter, isMobile));

        if (notiz.getBilder() != null) {
            dto.setBilder(notiz.getBilder().stream().map(this::mapBildToDto).collect(Collectors.toList()));
        } else {
            dto.setBilder(java.util.Collections.emptyList());
        }

        return dto;
    }

    private AngebotNotizBildDto mapBildToDto(AngebotNotizBild bild) {
        AngebotNotizBildDto dto = new AngebotNotizBildDto();
        dto.setId(bild.getId());
        dto.setOriginalDateiname(bild.getOriginalDateiname());
        dto.setErstelltAm(
                bild.getErstelltAm().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        dto.setUrl("/api/images/" + bild.getGespeicherterDateiname()); // Korrekte URL für DateiController
        return dto;
    }

    @GetMapping("/{angebotId}/notizen")
    public ResponseEntity<List<AngebotNotizDto>> getNotizen(@PathVariable Long angebotId,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId,
            @RequestParam(required = false) String token) {

        List<AngebotNotiz> notizen = angebotNotizRepository.findByAngebotIdOrderByErstelltAmDesc(angebotId);

        Mitarbeiter requestingUser = resolveMitarbeiter(userProfileId, mitarbeiterId, token);
        boolean isMobile = token != null && !token.isBlank();
        final Mitarbeiter finalUser = requestingUser != null ? requestingUser : new Mitarbeiter();

        List<AngebotNotizDto> dtos = notizen.stream()
                .filter(n -> {
                    // Privacy Check (nurFuerErsteller)
                    if (n.isNurFuerErsteller()) {
                        if (finalUser.getId() == null)
                            return false;
                        return n.getMitarbeiter() != null && n.getMitarbeiter().getId().equals(finalUser.getId());
                    }
                    return true;
                })
                .map(n -> mapNotizToDto(n, finalUser, isMobile))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Fügt eine einzelne E-Mail-Adresse zu den Angebot-E-Mails hinzu.
     */
    @PostMapping("/{angebotId}/emails")
    public ResponseEntity<java.util.Map<String, Object>> addAngebotEmail(
            @PathVariable Long angebotId,
            @RequestBody java.util.Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "E-Mail-Adresse fehlt"));
        }
        email = email.trim().toLowerCase();
        Angebot angebot = angebotService.finde(angebotId);
        if (angebot == null) {
            return ResponseEntity.notFound().build();
        }
        if (angebot.getKundenEmails() == null) {
            angebot.setKundenEmails(new java.util.ArrayList<>());
        }
        if (angebot.getKundenEmails().contains(email)) {
            return ResponseEntity.ok(java.util.Map.of("message", "E-Mail-Adresse bereits vorhanden", "added", false));
        }
        angebot.getKundenEmails().add(email);
        angebotService.speichere(angebot);
        return ResponseEntity.ok(java.util.Map.of("message", "E-Mail-Adresse gespeichert", "added", true));
    }

    @PostMapping("/{angebotId}/notizen")
    public ResponseEntity<AngebotNotizDto> addNotiz(@PathVariable Long angebotId, @RequestBody AngebotNotizDto dto,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId,
            @RequestParam(required = false) String token) {

        Angebot angebot = angebotService.finde(angebotId);
        if (angebot == null)
            return ResponseEntity.notFound().build();

        Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token);
        if (mitarbeiter == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        AngebotNotiz notiz = new AngebotNotiz();
        notiz.setAngebot(angebot);
        notiz.setMitarbeiter(mitarbeiter);
        notiz.setNotiz(dto.getNotiz());
        notiz.setMobileSichtbar(dto.isMobileSichtbar());
        notiz.setNurFuerErsteller(dto.isNurFuerErsteller());
        AngebotNotiz saved = angebotNotizRepository.save(notiz);

        boolean isMobile = token != null && !token.isBlank();
        return ResponseEntity.ok(mapNotizToDto(saved, mitarbeiter, isMobile));
    }

    @PatchMapping("/{angebotId}/notizen/{notizId}")
    public ResponseEntity<AngebotNotizDto> updateNotiz(@PathVariable Long angebotId, @PathVariable Long notizId,
            @RequestBody AngebotNotizDto dto,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId,
            @RequestParam(required = false) String token) {

        AngebotNotiz notiz = angebotNotizRepository.findById(notizId)
                .orElseThrow(() -> new RuntimeException("Notiz nicht gefunden"));

        Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token);
        boolean isMobile = token != null && !token.isBlank();

        if (!hasEditPermission(notiz, mitarbeiter, isMobile)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (dto.getNotiz() != null)
            notiz.setNotiz(dto.getNotiz());
        notiz.setMobileSichtbar(dto.isMobileSichtbar());
        notiz.setNurFuerErsteller(dto.isNurFuerErsteller());
        AngebotNotiz saved = angebotNotizRepository.save(notiz);
        return ResponseEntity.ok(mapNotizToDto(saved, mitarbeiter, isMobile));
    }

    @DeleteMapping("/{angebotId}/notizen/{notizId}")
    public ResponseEntity<Void> deleteNotiz(@PathVariable Long angebotId, @PathVariable Long notizId,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId,
            @RequestParam(required = false) String token) {

        AngebotNotiz notiz = angebotNotizRepository.findById(notizId)
                .orElseThrow(() -> new RuntimeException("Notiz nicht gefunden"));

        Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token);
        boolean isMobile = token != null && !token.isBlank();

        if (!hasEditPermission(notiz, mitarbeiter, isMobile)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        for (AngebotNotizBild b : notiz.getBilder()) {
            try {
                // Lösche physische Datei über Service
                dateiSpeicherService.loescheBild("/api/images/" + b.getGespeicherterDateiname());
            } catch (Exception ignored) {
            }
        }

        angebotNotizRepository.delete(notiz);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{angebotId}/notizen/{notizId}/bilder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AngebotNotizBildDto> uploadNotizBild(@PathVariable Long angebotId, @PathVariable Long notizId,
            @RequestParam("datei") MultipartFile file,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId,
            @RequestParam(required = false) String token) {

        AngebotNotiz notiz = angebotNotizRepository.findById(notizId)
                .orElseThrow(() -> new RuntimeException("Notiz nicht gefunden"));

        Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token);
        boolean isMobile = token != null && !token.isBlank();

        if (!hasEditPermission(notiz, mitarbeiter, isMobile)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            // Nutze DateiSpeicherService für Konsistenz mit DateiController
            String url = dateiSpeicherService.speichereBild(file);
            // URL ist z.B. "/api/images/uuid.jpg" -> Dateiname extrahieren
            String gespeicherterName = url.substring(url.lastIndexOf("/") + 1);

            AngebotNotizBild bild = new AngebotNotizBild();
            bild.setNotiz(notiz);
            bild.setGespeicherterDateiname(gespeicherterName);
            bild.setOriginalDateiname(file.getOriginalFilename());
            bild.setDateityp(file.getContentType());
            AngebotNotizBild saved = angebotNotizBildRepository.save(bild);

            return ResponseEntity.ok(mapBildToDto(saved));
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Hochladen des Bildes", e);
        }
    }

    @DeleteMapping("/{angebotId}/notizen/{notizId}/bilder/{bildId}")
    public ResponseEntity<Void> deleteNotizBild(@PathVariable Long angebotId, @PathVariable Long notizId,
            @PathVariable Long bildId,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId,
            @RequestParam(required = false) String token) {

        AngebotNotizBild bild = angebotNotizBildRepository.findById(bildId)
                .orElseThrow(() -> new RuntimeException("Bild nicht gefunden"));

        if (!bild.getNotiz().getId().equals(notizId)) {
            return ResponseEntity.badRequest().build();
        }

        AngebotNotiz notiz = bild.getNotiz();
        Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, mitarbeiterId, token);
        boolean isMobile = token != null && !token.isBlank();

        if (!hasEditPermission(notiz, mitarbeiter, isMobile)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            dateiSpeicherService.loescheBild("/api/images/" + bild.getGespeicherterDateiname());
        } catch (Exception e) {
            log.warn("Fehler beim Löschen des Bildes {}", bild.getGespeicherterDateiname(), e);
        }

        angebotNotizBildRepository.delete(bild);
        return ResponseEntity.ok().build();
    }
}
