package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mahnstufe;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ProjektNotizBild;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelInProjektUpdateDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelMengeDto;
import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenErfassenDto;
import org.example.kalkulationsprogramm.dto.Projekt.LieferantPerformanceDto;
import org.example.kalkulationsprogramm.dto.Projekt.LieferantenkostenJahrDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektSearchResponseDto;
import org.example.kalkulationsprogramm.dto.Projekt.UmsatzStatistikDto;
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieErfassenDto;
import org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizBildRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.PdfAiExtractorService;
import org.example.kalkulationsprogramm.service.ProjektManagementService;
import org.example.kalkulationsprogramm.service.StuecklistePdfService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.example.kalkulationsprogramm.service.ZugferdExtractorService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/projekte")
@AllArgsConstructor
public class ProjektController {
    private static final int MAX_PAGE_SIZE = 50;

    private final DateiSpeicherService dateiSpeicherService;
    private final ProjektManagementService projektManagementService;
    private final ZugferdExtractorService zugferdExtractorService;
    private final ZugferdErstellService zugferdErstellService;
    private final ProduktkategorieMapper produktkategorieMapper;
    private final StuecklistePdfService stuecklistePdfService;
    private final PdfAiExtractorService pdfAiExtractorService;
    private final FrontendUserProfileService frontendUserProfileService;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final LieferantenRepository lieferantenRepository;
    private final LieferantDokumentProjektAnteilRepository lieferantDokumentProjektAnteilRepository;
    private final LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    private final ProjektNotizRepository projektNotizRepository;
    private final ProjektNotizBildRepository projektNotizBildRepository;
    private final ProjektRepository projektRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;

    // Projekte bearbeiten/ erstellen (Multipart mit Dateien)
    // Projekte bearbeiten/ erstellen (Multipart mit Dateien)
    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ProjektResponseDto> erstelleNeuesProjekt(
            @RequestPart("projektDto") ProjektErstellenDto projektDto,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestPart(value = "dokumente", required = false) List<MultipartFile> dokumente,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId) {
        try {
            Mitarbeiter uploadedBy = resolveMitarbeiterByIds(userProfileId, mitarbeiterId);
            ProjektResponseDto responseDto = projektManagementService.erstelleProjekt(projektDto,
                    projektDto.getStrasse(), projektDto.getPlz(), projektDto.getOrt(), imageFile, dokumente,
                    uploadedBy);
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // Projekte erstellen (JSON ohne Dateien)
    @PostMapping(consumes = { MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<ProjektResponseDto> erstelleNeuesProjektJson(@RequestBody ProjektErstellenDto projektDto,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId) {
        try {
            Mitarbeiter uploadedBy = resolveMitarbeiterByIds(userProfileId, mitarbeiterId);
            ProjektResponseDto responseDto = projektManagementService.erstelleProjekt(projektDto,
                    projektDto.getStrasse(), projektDto.getPlz(), projektDto.getOrt(), null, null, uploadedBy);
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PostMapping("/{projektId}/zeiten")
    public ResponseEntity<ProjektResponseDto> fuegeZeitenHinzu(@PathVariable Long projektId,
            @RequestBody List<ZeitErfassenDto> zeiten) {
        try {
            ProjektResponseDto dto = projektManagementService.fuegeZeitenHinzu(projektId, zeiten);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping(value = "/{projektID}/stueckliste.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> erzeugeStueckliste(@PathVariable Long projektID) {
        try {
            byte[] pdf = stuecklistePdfService.generateForProjekt(projektID);
            String dateiname = "Stueckliste_" + projektID + ".pdf";
            // Sofort unter Planungsdokumente speichern
            dateiSpeicherService.speichereErzeugteDatei(pdf, dateiname, projektID, DokumentGruppe.PLANUNGSDOKUMENTE,
                    MediaType.APPLICATION_PDF_VALUE);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + dateiname + "\"")
                    .body(pdf);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping(value = "/{projektID}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ProjektResponseDto> aktualisiereProjekt(@PathVariable Long projektID,
            @RequestPart("projektDto") ProjektErstellenDto projektDto,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId) {
        try {
            Mitarbeiter uploadedBy = resolveMitarbeiterByIds(userProfileId, mitarbeiterId);
            ProjektResponseDto responseDto = projektManagementService.aktualisiereProjekt(projektID, projektDto,
                    projektDto.getStrasse(), projektDto.getPlz(), projektDto.getOrt(), imageFile, uploadedBy);
            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // Projekt aktualisieren (JSON ohne Dateien)
    @PutMapping(value = "/{projektID}", consumes = { MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<ProjektResponseDto> aktualisiereProjektJson(@PathVariable Long projektID,
            @RequestBody ProjektErstellenDto projektDto,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId) {
        try {
            Mitarbeiter uploadedBy = resolveMitarbeiterByIds(userProfileId, mitarbeiterId);
            ProjektResponseDto responseDto = projektManagementService.aktualisiereProjekt(projektID, projektDto,
                    projektDto.getStrasse(), projektDto.getPlz(), projektDto.getOrt(), null, uploadedBy);
            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PatchMapping(value = "/{projektID}/materialkosten")
    public ResponseEntity<ProjektResponseDto> aktualisiereMaterialkosten(@PathVariable Long projektID,
            @RequestBody List<MaterialkostenErfassenDto> materialkosten) {
        try {
            ProjektResponseDto dto = projektManagementService.aktualisiereMaterialkosten(projektID, materialkosten);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PatchMapping(value = "/{projektID}/kurzbeschreibung", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<ProjektResponseDto> updateKurzbeschreibung(@PathVariable Long projektID,
            @RequestBody String kurzbeschreibung) {
        try {
            ProjektResponseDto response = projektManagementService.updateProjektKurzbeschreibung(projektID,
                    kurzbeschreibung);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PostMapping(value = "/{projektID}/materialkosten/artikel")
    public ResponseEntity<ProjektResponseDto> fuegeArtikelAlsMaterialkosten(@PathVariable Long projektID,
            @RequestBody List<ArtikelMengeDto> artikelAuswahl) {
        try {
            ProjektResponseDto dto = projektManagementService.fuegeArtikelMaterialkosten(projektID, artikelAuswahl);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @DeleteMapping(value = "/{projektID}/materialkosten/artikel/{artikelInProjektID}")
    public ResponseEntity<ProjektResponseDto> entferneArtikelAusMaterialkosten(@PathVariable Long projektID,
            @PathVariable("artikelInProjektID") Long artikelInProjektID) {
        try {
            ProjektResponseDto dto = projektManagementService.entferneArtikelMaterialkosten(projektID,
                    artikelInProjektID);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PatchMapping(value = "/{projektID}/materialkosten/artikel/{artikelInProjektID}")
    public ResponseEntity<ProjektResponseDto> aktualisiereArtikelInProjekt(@PathVariable Long projektID,
            @PathVariable("artikelInProjektID") Long artikelInProjektID,
            @RequestBody ArtikelInProjektUpdateDto dto) {
        try {
            ProjektResponseDto resp = projektManagementService.aktualisiereArtikelInProjekt(projektID,
                    artikelInProjektID, dto);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @DeleteMapping(value = "/{projektID}/materialkosten/{materialID}")
    public ResponseEntity<ProjektResponseDto> entferneMaterialkosten(@PathVariable Long projektID,
            @PathVariable Long materialID) {
        try {
            ProjektResponseDto dto = projektManagementService.entferneMaterialkosten(projektID, materialID);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PostMapping("/{projektID}/produktkategorien")
    public ResponseEntity<?> fuegeProduktkategorienHinzu(@PathVariable Long projektID,
            @RequestBody List<ProjektProduktkategorieErfassenDto> kategorien) {
        try {
            ProjektResponseDto dto = projektManagementService.fuegeProduktkategorienHinzu(projektID, kategorien);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PatchMapping("/{projektID}/produktkategorien/{kategorieID}")
    public ResponseEntity<ProjektResponseDto> aktualisiereProjektProduktkategorie(@PathVariable Long projektID,
            @PathVariable Long kategorieID,
            @RequestBody ProjektProduktkategorieErfassenDto dto) {
        try {
            ProjektResponseDto response = projektManagementService.aktualisiereProjektProduktkategorie(projektID,
                    kategorieID, dto);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @DeleteMapping("/{projektID}/produktkategorien/{kategorieID}")
    public ResponseEntity<ProjektResponseDto> loescheProjektProduktkategorie(@PathVariable Long projektID,
            @PathVariable Long kategorieID) {
        try {
            ProjektResponseDto response = projektManagementService.loescheProjektProduktkategorie(projektID,
                    kategorieID);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/{projektID}/produktkategorien/{ppkId}/referenziert")
    public ResponseEntity<Map<String, Object>> pruefeKategorieReferenziert(@PathVariable Long projektID,
            @PathVariable Long ppkId) {
        boolean referenziert = zeitbuchungRepository.existsByProjektProduktkategorieId(ppkId);
        Map<String, Object> result = new HashMap<>();
        result.put("referenziert", referenziert);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{projektID}")
    public ResponseEntity<Void> loescheProjekt(@PathVariable Long projektID) {
        try {
            this.projektManagementService.loescheProjekt(projektID);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/zugferd/extract", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ZugferdDaten> extractZugferd(@RequestParam("datei") MultipartFile datei) {
        try {
            Path temp = Files.createTempFile("zugferd-", ".pdf.html");
            Files.copy(datei.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);
            ZugferdDaten daten = zugferdExtractorService.extract(temp.toString(), datei.getOriginalFilename());
            Files.deleteIfExists(temp);
            return ResponseEntity.ok(daten);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * KI-gestützte PDF-Analyse. Versucht Dokumentdaten mittels Ollama zu
     * extrahieren.
     * Bei Fehler wird auf Standard-Extraktion zurückgefallen.
     */
    @PostMapping(value = "/zugferd/extract-ai", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ZugferdDaten> extractZugferdWithAi(@RequestParam("datei") MultipartFile datei) {
        try {
            Path temp = Files.createTempFile("zugferd-ai-", ".pdf");
            Files.copy(datei.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);

            // Versuche KI-Analyse
            java.util.Optional<ZugferdDaten> aiResult = pdfAiExtractorService.analyze(temp.toString());

            ZugferdDaten daten;
            if (aiResult.isPresent()) {
                daten = aiResult.get();
                // Falls Dokumenttyp leer, versuche aus Dateiname zu ermitteln
                if (daten.getGeschaeftsdokumentart() == null || daten.getGeschaeftsdokumentart().isBlank()) {
                    daten.setGeschaeftsdokumentart(detectDocTypeFromFilename(datei.getOriginalFilename()));
                }
            } else {
                // Fallback auf Standard-Extraktion
                daten = zugferdExtractorService.extract(temp.toString(), datei.getOriginalFilename());
            }

            Files.deleteIfExists(temp);
            return ResponseEntity.ok(daten);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private String detectDocTypeFromFilename(String filename) {
        if (filename == null)
            return "Rechnung";
        String lower = filename.toLowerCase();
        if (lower.contains("angebot"))
            return "Angebot";
        if (lower.contains("auftragsbestätigung") || lower.contains("auftragsbestaetigung") || lower.contains("ab_"))
            return "Auftragsbestätigung";
        if (lower.contains("mahnung")) {
            if (lower.contains("2.") || lower.contains("zweite"))
                return "2. Mahnung";
            return "1. Mahnung";
        }
        if (lower.contains("zahlungserinnerung"))
            return "Zahlungserinnerung";
        return "Rechnung";
    }

    @PostMapping(value = "/{projektID}/zugferd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjektDokumentResponseDto> erzeugeZugferd(
            @PathVariable Long projektID,
            @RequestPart("datei") MultipartFile pdf,
            @RequestPart("zugferdDaten") ZugferdDaten daten) {
        try {
            Path original = Files.createTempFile("zugferd-original-", ".pdf.html");
            Files.copy(pdf.getInputStream(), original, StandardCopyOption.REPLACE_EXISTING);

            Path zugferdPfad = zugferdErstellService.erzeuge(original.toString(), daten);
            Files.deleteIfExists(original);

            ProjektGeschaeftsdokument dokument = dateiSpeicherService
                    .speichereZugferdDatei(zugferdPfad, pdf.getOriginalFilename(), projektID, daten);
            ProjektDokumentResponseDto dto = mappeDokumentZuDto(dokument);

            Files.deleteIfExists(zugferdPfad);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // Projektdokumente hochladen
    @PostMapping(value = "/{projektID}/dokumente", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<List<ProjektDokumentResponseDto>> uploadDokument(@PathVariable Long projektID,
            @RequestParam("datei") List<MultipartFile> dateien,
            @RequestParam(value = "gruppe", required = false) DokumentGruppe gruppe,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestHeader(value = "X-Mitarbeiter-Id", required = false) Long mitarbeiterId,
            @RequestHeader(value = "X-Lieferant-Id", required = false) Long lieferantId) {
        try {
            DokumentGruppe verwendeteGruppe = gruppe != null ? gruppe : DokumentGruppe.DIVERSE_DOKUMENTE;
            // Ermittle den Mitarbeiter: X-Mitarbeiter-Id hat Vorrang (für mobile App)
            Mitarbeiter uploadedBy = null;
            if (mitarbeiterId != null) {
                // Direct mitarbeiter lookup for mobile app
                uploadedBy = mitarbeiterRepository.findById(mitarbeiterId).orElse(null);
            } else if (userProfileId != null) {
                // PC app: lookup via FrontendUserProfile
                uploadedBy = frontendUserProfileService.findById(userProfileId)
                        .map(profile -> profile.getMitarbeiter())
                        .orElse(null);
            }
            // Ermittle den Lieferanten (optional)
            Lieferanten lieferant = null;
            if (lieferantId != null) {
                lieferant = lieferantenRepository.findById(lieferantId).orElse(null);
            }
            final Mitarbeiter finalUploadedBy = uploadedBy;
            final Lieferanten finalLieferant = lieferant;
            List<ProjektDokumentResponseDto> dtos = dateien.stream().map(datei -> {
                String overrideFilename = null;
                // AI-Namensgebung für Scans (PDFs in DIVERSE_DOKUMENTE)
                if (verwendeteGruppe == DokumentGruppe.DIVERSE_DOKUMENTE
                        && datei.getContentType() != null
                        && datei.getContentType().toLowerCase().contains("pdf")) {
                    overrideFilename = tryGenerateAiFilename(datei);
                }
                ProjektDokument dokument = dateiSpeicherService.speichereDatei(datei, projektID, verwendeteGruppe,
                        finalUploadedBy, finalLieferant, overrideFilename);
                return mappeDokumentZuDto(dokument);
            }).toList();
            return ResponseEntity.ok(dtos);
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private String tryGenerateAiFilename(MultipartFile datei) {
        try {
            Path temp = Files.createTempFile("scan-ai-", ".pdf");
            Files.copy(datei.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);

            // Nutze Standard-Analyse (Rechnungs-Prompt) um Typ, Datum, Kunde zu erkennen
            var dataOpt = pdfAiExtractorService.analyze(temp.toString(), "RECHNUNG");
            Files.deleteIfExists(temp);

            if (dataOpt.isPresent()) {
                var d = dataOpt.get();
                String type = d.getGeschaeftsdokumentart();
                if (type == null || type.isBlank())
                    type = "Scan";
                // Bereinigen
                type = type.replaceAll("[^a-zA-Z0-9äöüÄÖÜß]", "_");

                String date = d.getRechnungsdatum() != null ? d.getRechnungsdatum().toString()
                        : LocalDate.now().toString();

                String customer = d.getKundenName();
                if (customer != null) {
                    customer = customer.replaceAll("[^a-zA-Z0-9äöüÄÖÜß ]", "").trim().replace(" ", "_");
                    if (customer.length() > 20)
                        customer = customer.substring(0, 20);
                } else {
                    customer = "";
                }

                String name = type + "_" + date;
                if (!customer.isEmpty())
                    name += "_" + customer;

                return name + ".pdf";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Kein Override -> Originalname wird verwendet
    }

    @GetMapping("/{projektID}/dokumente")
    public ResponseEntity<List<ProjektDokumentResponseDto>> listeDokumente(@PathVariable Long projektID) {
        List<ProjektDokument> dokumente = dateiSpeicherService.holeDokumenteZuProjekt(projektID);
        List<ProjektDokumentResponseDto> dtos = dokumente.stream()
                .map(this::mappeDokumentZuDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{projektID}/rechnungen")
    public ResponseEntity<List<ProjektDokumentResponseDto>> listeRechnungen(@PathVariable Long projektID) {
        List<ProjektGeschaeftsdokument> rechnungen = dateiSpeicherService.holeRechnungenZuProjekt(projektID);
        List<ProjektDokumentResponseDto> dtos = rechnungen.stream()
                .map(this::mappeDokumentZuDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Gibt die zugeordneten Eingangsrechnungen (Lieferanten-Rechnungen) für ein
     * Projekt zurück.
     * Diese werden für die Nachkalkulation unter "Materialkosten" angezeigt.
     */
    @GetMapping("/{projektID}/eingangsrechnungen")
    @Transactional(readOnly = true)
    public ResponseEntity<List<EingangsrechnungDto>> listeEingangsrechnungen(@PathVariable Long projektID) {
        // 1. Hole Anteile mit eager-loaded Dokument + Geschäftsdaten + Lieferant
        List<LieferantDokumentProjektAnteil> anteile = lieferantDokumentProjektAnteilRepository
                .findByProjektIdEager(projektID);
        
        Map<Long, EingangsrechnungDto> dtoMap = new HashMap<>();

        // Map Anteile first (Priorität)
        for (LieferantDokumentProjektAnteil anteil : anteile) {
            EingangsrechnungDto dto = new EingangsrechnungDto();
            dto.id = anteil.getId();
            dto.prozent = anteil.getProzent();
            dto.berechneterBetrag = anteil.getBerechneterBetrag();
            dto.beschreibung = anteil.getBeschreibung();
            dto.zugeordnetAm = anteil.getZugeordnetAm();
            if (anteil.getZugeordnetVon() != null) {
                dto.zugeordnetVonName = anteil.getZugeordnetVon().getDisplayName();
            }

            if (anteil.getDokument() != null) {
                var dok = anteil.getDokument();
                dto.dokumentId = dok.getId();
                dto.dateiname = dok.getEffektiverDateiname();
                dto.dokumentDatum = dok.getUploadDatum() != null ? dok.getUploadDatum().toLocalDate() : null;

                // Geschäftsdaten aus LieferantGeschaeftsdokument
                if (dok.getGeschaeftsdaten() != null) {
                    var gd = dok.getGeschaeftsdaten();
                    dto.geschaeftsdokumentId = gd.getId();
                    dto.dokumentNummer = gd.getDokumentNummer();
                    dto.gesamtbetrag = gd.getBetragNetto();
                    // berechneterBetrag dynamisch aus Netto neu berechnen – korrigiert brutto-basierte Altdaten
                    if (gd.getBetragNetto() != null && anteil.getProzent() != null) {
                        dto.berechneterBetrag = gd.getBetragNetto()
                                .multiply(BigDecimal.valueOf(anteil.getProzent()))
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    }
                    if (gd.getDokumentDatum() != null) {
                        dto.dokumentDatum = gd.getDokumentDatum();
                    }
                } else {
                    // Fallback Key if no Geschaeftsdaten (unlikely for matched invoices)
                    dto.geschaeftsdokumentId = dok.getId(); 
                }

                // Lieferant
                if (dok.getLieferant() != null) {
                    dto.lieferantId = dok.getLieferant().getId();
                    dto.lieferantName = dok.getLieferant().getLieferantenname();
                }

                // PDF-URL: bevorzugt über Email-Attachment, Fallback über allgemeinen Download-Endpoint
                if (dok.getAttachment() != null && dok.getAttachment().getEmail() != null && dok.getLieferant() != null) {
                    var att = dok.getAttachment();
                    dto.pdfUrl = "/api/lieferanten/" + dok.getLieferant().getId() +
                            "/emails/" + att.getEmail().getId() +
                            "/attachments/" + att.getId();
                } else {
                    dto.pdfUrl = "/api/lieferant-dokumente/" + dok.getId() + "/download";
                }

                // Alle Zuordnungen dieses Dokuments (alle Projekte + Kostenstellen)
                List<LieferantDokumentProjektAnteil> alleDokAnteile = lieferantDokumentProjektAnteilRepository
                        .findByDokumentIdEager(dok.getId());
                dto.alleZuordnungen = alleDokAnteile.stream().map(a -> {
                    AnteilDto ad = new AnteilDto();
                    if (a.getProjekt() != null) {
                        ad.projektId = a.getProjekt().getId();
                        ad.projektName = a.getProjekt().getBauvorhaben();
                        ad.projektNummer = a.getProjekt().getAuftragsnummer();
                    }
                    if (a.getKostenstelle() != null) {
                        ad.kostenstelleId = a.getKostenstelle().getId();
                        ad.kostenstelleName = a.getKostenstelle().getBezeichnung();
                    }
                    ad.prozent = a.getProzent();
                    // berechneterBetrag aus Netto des Dokuments berechnen (korrigiert Altdaten)
                    BigDecimal nettoFuerAnteil = (a.getDokument() != null && a.getDokument().getGeschaeftsdaten() != null)
                            ? a.getDokument().getGeschaeftsdaten().getBetragNetto() : null;
                    if (nettoFuerAnteil != null && a.getProzent() != null) {
                        ad.berechneterBetrag = nettoFuerAnteil
                                .multiply(BigDecimal.valueOf(a.getProzent()))
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    } else {
                        ad.berechneterBetrag = a.getBerechneterBetrag();
                    }
                    ad.beschreibung = a.getBeschreibung();
                    ad.zugeordnetAm = a.getZugeordnetAm();
                    if (a.getZugeordnetVon() != null) {
                        ad.zugeordnetVonName = a.getZugeordnetVon().getDisplayName();
                    }
                    return ad;
                }).collect(java.util.stream.Collectors.toList());

                // Dokumentenkette (verknüpfte Dokumente)
                dto.dokumentenKette = new java.util.ArrayList<>();
                Set<LieferantDokument> verknuepft = dok.getVerknuepfteDokumente();
                Set<LieferantDokument> verknuepftVon = dok.getVerknuepftVon();
                Set<LieferantDokument> alleVerknuepft = new java.util.HashSet<>();
                if (verknuepft != null) alleVerknuepft.addAll(verknuepft);
                if (verknuepftVon != null) alleVerknuepft.addAll(verknuepftVon);
                // Also add the document itself to the chain
                alleVerknuepft.add(dok);
                
                if (alleVerknuepft.size() > 1) {
                    // Sort chain by type order
                    List<LieferantDokument> sortedChain = new java.util.ArrayList<>(alleVerknuepft);
                    sortedChain.sort((a, b) -> {
                        int orderA = getTypOrder(a.getTyp());
                        int orderB = getTypOrder(b.getTyp());
                        return Integer.compare(orderA, orderB);
                    });
                    for (LieferantDokument chainDoc : sortedChain) {
                        DokumentKetteRefDto ref = new DokumentKetteRefDto();
                        ref.id = chainDoc.getId();
                        ref.typ = chainDoc.getTyp().name();
                        if (chainDoc.getGeschaeftsdaten() != null) {
                            ref.dokumentNummer = chainDoc.getGeschaeftsdaten().getDokumentNummer();
                            ref.dokumentDatum = chainDoc.getGeschaeftsdaten().getDokumentDatum();
                            ref.betragNetto = chainDoc.getGeschaeftsdaten().getBetragNetto();
                        }
                        // PDF URL
                        if (chainDoc.getAttachment() != null && chainDoc.getAttachment().getEmail() != null && chainDoc.getLieferant() != null) {
                            var att = chainDoc.getAttachment();
                            ref.pdfUrl = "/api/lieferanten/" + chainDoc.getLieferant().getId() +
                                    "/emails/" + att.getEmail().getId() +
                                    "/attachments/" + att.getId();
                        } else {
                            ref.pdfUrl = "/api/lieferant-dokumente/" + chainDoc.getId() + "/download";
                        }
                        dto.dokumentenKette.add(ref);
                    }
                }
            }
            // Add to map - Key is GeschaeftsdokumentId (usually same as DocumentId)
            if (dto.geschaeftsdokumentId != null) {
                dtoMap.put(dto.geschaeftsdokumentId, dto);
            }
        }

        return ResponseEntity.ok(new java.util.ArrayList<>(dtoMap.values()));
    }

    private int getTypOrder(LieferantDokumentTyp typ) {
        if (typ == null) return 99;
        return switch (typ) {
            case ANGEBOT -> 1;
            case AUFTRAGSBESTAETIGUNG -> 2;
            case LIEFERSCHEIN -> 3;
            case RECHNUNG -> 4;
            case GUTSCHRIFT -> 5;
            case SONSTIG -> 6;
        };
    }

    public static class EingangsrechnungDto {
        public Long id;
        public Long dokumentId;
        public Long geschaeftsdokumentId; // Für Zuordnung aufheben
        public String dokumentNummer;
        public String dateiname;
        public LocalDate dokumentDatum;
        public BigDecimal gesamtbetrag;
        public Integer prozent;
        public BigDecimal berechneterBetrag;
        public String beschreibung;
        public Long lieferantId;
        public String lieferantName;
        public String pdfUrl;
        public String zugeordnetVonName;
        public LocalDateTime zugeordnetAm;
        // Alle Zuordnungen dieses Dokuments (Projektanteile + Kostenstellen)
        public List<AnteilDto> alleZuordnungen;
        // Dokumentenkette
        public List<DokumentKetteRefDto> dokumentenKette;
    }

    public static class AnteilDto {
        public Long projektId;
        public String projektName;
        public String projektNummer;
        public Long kostenstelleId;
        public String kostenstelleName;
        public Integer prozent;
        public BigDecimal berechneterBetrag;
        public String beschreibung;
        public String zugeordnetVonName;
        public LocalDateTime zugeordnetAm;
    }

    public static class DokumentKetteRefDto {
        public Long id;
        public String typ;
        public String dokumentNummer;
        public LocalDate dokumentDatum;
        public BigDecimal betragNetto;
        public String pdfUrl;
    }

    // Sendbare Dokumente (Rechnung/Anfrage/Auftragsbestätigung/Zeichnung) für
    // E-Mail-Versand-Dropdown
    @GetMapping("/{projektID}/email-dokumente")
    public ResponseEntity<List<ProjektDokumentResponseDto>> emailDokumente(@PathVariable Long projektID) {
        List<ProjektDokument> dokumente = dateiSpeicherService.holeDokumenteZuProjekt(projektID);
        java.util.List<ProjektDokumentResponseDto> dtos = dokumente.stream()
                .filter(d -> {
                    boolean isBusiness = d instanceof ProjektGeschaeftsdokument;
                    String name = d.getOriginalDateiname() != null ? d.getOriginalDateiname().toLowerCase() : "";
                    boolean isPdf = (d.getDateityp() != null && d.getDateityp().toLowerCase().contains("pdf"))
                            || name.endsWith(".pdf") || name.endsWith(".pdf.html");
                    boolean isDrawing = name.contains("zeichnung") || name.contains("entwurf");
                    return isBusiness || (isPdf && isDrawing);
                })
                .map(this::mappeDokumentZuDto)
                .toList();

        java.util.Comparator<java.time.LocalDate> dateDesc = java.util.Comparator
                .nullsLast(java.util.Comparator.naturalOrder());
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.GERMANY);
        collator.setStrength(java.text.Collator.PRIMARY);

        dtos = dtos.stream()
                .sorted(java.util.Comparator
                        .comparing(ProjektDokumentResponseDto::getUploadDatum, dateDesc.reversed())
                        .thenComparing(ProjektDokumentResponseDto::getOriginalDateiname,
                                java.util.Comparator.nullsLast(collator)))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/offene-posten")
    public ResponseEntity<List<ProjektDokumentResponseDto>> offenePosten() {
        List<ProjektGeschaeftsdokument> docs = dateiSpeicherService.holeOffeneGeschaeftsdokumente();
        List<ProjektDokumentResponseDto> dtos = docs.stream()
                .map(this::mappeDokumentZuDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/umsatz")
    public ResponseEntity<List<ProjektDokumentResponseDto>> umsatzAuswertung(@RequestParam int jahr,
            @RequestParam(required = false) Integer monat,
            @RequestParam(required = false) String rechnungsnummer,
            @RequestParam(required = false) String auftragsnummer,
            @RequestParam(required = false) String kunde,
            @RequestParam(required = false) Long kategorieId) {
        List<ProjektGeschaeftsdokument> docs = dateiSpeicherService.holeGeschaeftsdokumenteNachJahrUndFilter(jahr,
                monat, rechnungsnummer, auftragsnummer, kunde, kategorieId);
        List<ProjektDokumentResponseDto> dtos = docs.stream().map(this::mappeDokumentZuDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/umsatz/statistiken")
    public ResponseEntity<UmsatzStatistikDto> umsatzStatistiken(@RequestParam int jahr,
            @RequestParam(required = false) Integer monat) {
        UmsatzStatistikDto dto = dateiSpeicherService.holeUmsatzStatistiken(jahr, monat);
        return ResponseEntity.ok(dto);
    }

    /**
     * Lieferantenkosten pro Jahr für Erfolgsanalyse-Charts.
     * Gibt für jedes Jahr die Anzahl Bestellungen und Summe Netto zurück.
     */
    @GetMapping("/umsatz/lieferantenkosten-jahresuebersicht")
    public ResponseEntity<List<LieferantenkostenJahrDto>> getLieferantenkostenProJahr() {
        List<Object[]> results = lieferantGeschaeftsdokumentRepository.getLieferantenkostenProJahr();
        List<LieferantenkostenJahrDto> dtos = results.stream()
                .map(row -> new LieferantenkostenJahrDto(
                        ((Number) row[0]).intValue(), // jahr
                        ((Number) row[1]).intValue(), // bestellungen
                        ((Number) row[2]).doubleValue() // netto
                ))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Lieferanten-Performance Statistik für Erfolgsanalyse.
     * Unterstützt optionale Filterung nach Jahr und Monat.
     */
    @GetMapping("/umsatz/lieferanten-performance")
    public ResponseEntity<List<LieferantPerformanceDto>> getLieferantenPerformance(
            @RequestParam(required = false) Integer jahr,
            @RequestParam(required = false) Integer monat) {
        List<Object[]> results;
        if (jahr != null) {
            results = lieferantGeschaeftsdokumentRepository.getLieferantenPerformanceFiltered(jahr, monat);
        } else {
            results = lieferantGeschaeftsdokumentRepository.getLieferantenPerformance();
        }
        List<LieferantPerformanceDto> dtos = results.stream()
                .map(row -> new LieferantPerformanceDto(
                        (String) row[0], // name
                        ((Number) row[1]).intValue(), // bestellungen
                        ((Number) row[2]).doubleValue() // netto
                ))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PatchMapping("/dokumente/{dokumentID}/bezahlt")
    public ResponseEntity<Void> setzeDokumentBezahlt(@PathVariable Long dokumentID, @RequestParam boolean bezahlt) {
        try {
            dateiSpeicherService.setzeGeschaeftsdokumentBezahlt(dokumentID, bezahlt);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ProjektDokumentResponseDto mappeDokumentZuDto(ProjektDokument dokument) {
        ProjektDokumentResponseDto dto = new ProjektDokumentResponseDto();
        dto.setId(dokument.getId());
        dto.setOriginalDateiname(dokument.getOriginalDateiname());
        dto.setDateityp(dokument.getDateityp());
        // AusgangsGeschaeftsDokument-Einträge: PDF-Datei noch nicht gespeichert → Fallback-URL
        String gespeichert = dokument.getGespeicherterDateiname();
        if (gespeichert != null && gespeichert.startsWith("ausgangs-dok-")) {
            String ausgangsId = gespeichert.replace("ausgangs-dok-", "").replace(".pdf", "");
            dto.setUrl("/api/ausgangs-dokumente/" + ausgangsId + "/pdf");
        } else {
            dto.setUrl("/api/dokumente/" + gespeichert);
        }
        String nameForType = dokument.getOriginalDateiname() != null ? dokument.getOriginalDateiname().toLowerCase()
                : (dokument.getGespeicherterDateiname() != null ? dokument.getGespeicherterDateiname().toLowerCase()
                        : "");
        boolean isHiCAD = nameForType.endsWith(".sza") || nameForType.endsWith(".tcd")
                || nameForType.endsWith(".xlsx") || nameForType.endsWith(".xls") || nameForType.endsWith(".xlsm")
                || nameForType.endsWith(".xlsb");
        if (isHiCAD) {
            try {
                dto.setNetzwerkPfad(dateiSpeicherService.holeNetzwerkPfad(dokument.getGespeicherterDateiname()));
            } catch (Exception ignored) {
            }
        }
        dto.setDokumentGruppe(dokument.getDokumentGruppe().name());
        dto.setUploadDatum(dokument.getUploadDatum());
        dto.setEmailVersandDatum(dokument.getEmailVersandDatum());
        if (dokument instanceof ProjektGeschaeftsdokument geschaeftsdokument) {
            dto.setRechnungsnummer(geschaeftsdokument.getDokumentid());
            dto.setRechnungsdatum(geschaeftsdokument.getRechnungsdatum());
            dto.setFaelligkeitsdatum(geschaeftsdokument.getFaelligkeitsdatum());
            dto.setRechnungsbetrag(geschaeftsdokument.getBruttoBetrag());
            dto.setGeschaeftsdokumentart(geschaeftsdokument.getGeschaeftsdokumentart());
            dto.setBezahlt(geschaeftsdokument.isBezahlt());
            boolean istMahnung = geschaeftsdokument.getGeschaeftsdokumentart() != null
                    && "mahnung".equalsIgnoreCase(geschaeftsdokument.getGeschaeftsdokumentart());
            boolean hatMahnstufe = geschaeftsdokument.getMahnstufe() != null;
            dto.setMahnung(istMahnung || hatMahnstufe);
            if (hatMahnstufe) {
                dto.setMahnstufe(geschaeftsdokument.getMahnstufe().name());
            }
            if (istMahnung && dto.getMahnstufe() == null) {
                dto.setMahnstufe(Mahnstufe.ZAHLUNGSERINNERUNG.name());
            }
            if (geschaeftsdokument.getReferenzDokument() != null) {
                dto.setReferenzDokumentId(geschaeftsdokument.getReferenzDokument().getId());
                dto.setReferenzDokumentNummer(geschaeftsdokument.getReferenzDokument().getDokumentid());
            }
        } else {
            dto.setMahnung(false);
            // Ältere Zeichnungs-PDFs, die noch keine Geschäftsart tragen, als "Zeichnung"
            // kennzeichnen
            String name = dokument.getOriginalDateiname() != null ? dokument.getOriginalDateiname().toLowerCase() : "";
            boolean isPdf = (dokument.getDateityp() != null && dokument.getDateityp().toLowerCase().contains("pdf"))
                    || name.endsWith(".pdf") || name.endsWith(".pdf.html");
            boolean isDrawing = name.contains("zeichnung") || name.contains("entwurf");
            if (isPdf && isDrawing) {
                dto.setGeschaeftsdokumentart("Zeichnung");
            }
        }
        if (dokument.getProjekt() != null) {
            dto.setProjektId(dokument.getProjekt().getId());
            dto.setProjektAuftragsnummer(dokument.getProjekt().getAuftragsnummer());
            dto.setProjektKunde(dokument.getProjekt().getKunde());
            // Anrede aus Kunde ableiten
            Kunde kundeEntity = dokument.getProjekt().getKundenId();
            if (kundeEntity != null) {
                dto.setAnrede(buildAnredeText(kundeEntity.getAnrede(), kundeEntity.getAnsprechspartner()));
            }
            double arbeits = dateiSpeicherService.berechneProjektArbeitskosten(dokument.getProjekt());
            double material = dateiSpeicherService.berechneProjektMaterialkosten(dokument.getProjekt());
            dto.setProjektArbeitskosten(java.math.BigDecimal.valueOf(arbeits));
            dto.setProjektMaterialkosten(java.math.BigDecimal.valueOf(material));
            dto.setProjektKosten(java.math.BigDecimal.valueOf(arbeits + material));
            if (dokument.getProjekt().getProjektProduktkategorien() != null
                    && !dokument.getProjekt().getProjektProduktkategorien().isEmpty()) {
                String katText = dokument.getProjekt().getProjektProduktkategorien().stream()
                        .map(ppk -> produktkategorieMapper
                                .toProduktkategorieResponseDto(ppk.getProduktkategorie())
                                .getPfad())
                        .map(pfad -> {
                            int sepIndex = pfad.indexOf(" > ");
                            return sepIndex > -1 ? pfad.substring(0, sepIndex) : pfad;
                        })
                        .distinct()
                        .collect(java.util.stream.Collectors.joining("\n\n"));
                dto.setProjektKategorie(katText);
            }
        }
        // Lieferant-Zuordnung
        if (dokument.getLieferant() != null) {
            dto.setLieferantId(dokument.getLieferant().getId());
            dto.setLieferantenname(dokument.getLieferant().getLieferantenname());
        }
        // Uploader-Zuordnung
        if (dokument.getUploadedBy() != null) {
            dto.setUploadedByVorname(dokument.getUploadedBy().getVorname());
            dto.setUploadedByNachname(dokument.getUploadedBy().getNachname());
        }
        return dto;
    }

    private String buildAnredeText(org.example.kalkulationsprogramm.domain.Anrede anrede, String ansprechspartner) {
        if (anrede == null) {
            return "Sehr geehrte Damen und Herren";
        }
        String displayText = switch (anrede) {
            case HERR -> "Sehr geehrter Herr";
            case FRAU -> "Sehr geehrte Frau";
            case FAMILIE -> "Sehr geehrte Familie";
            case FIRMA -> "Sehr geehrte Damen und Herren";
            case DAMEN_HERREN -> "Sehr geehrte Damen und Herren";
        };
        if (ansprechspartner != null && !ansprechspartner.isBlank()) {
            return displayText + " " + ansprechspartner.trim();
        }
        return displayText;
    }

    // @GetMapping("/{projektID}/dokumente/{dokumentID}/emailText")
    // public ResponseEntity<String> generiereEmailText(@PathVariable Long
    // projektID,
    // @PathVariable Long dokumentID,
    // @RequestParam String typ) {
    // try {
    // ProjektDokument dokument = dateiSpeicherService.holeDokument(dokumentID);
    // String anrede = dokument.getAnrede() != null ? dokument.getAnrede() : "Damen
    // und Herren";
    // String rechnungsnummer = dokument.getRechnungsnummer() != null ?
    // dokument.getRechnungsnummer() : "";
    // String betrag = dokument.getRechnungsbetrag() != null ?
    // dokument.getRechnungsbetrag() + " €" : "";
    // String faellig = dokument.getFaelligkeitsdatum() != null ?
    // dokument.getFaelligkeitsdatum().toString() : "";
    // String text = "Sehr geehrte " + anrede + ",\n\n" +
    // "anbei erhalten Sie die " + typ + (rechnungsnummer.isEmpty() ? "" : " " +
    // rechnungsnummer) +
    // (betrag.isEmpty() ? "" : " über " + betrag) +
    // (faellig.isEmpty() ? "." : " mit Fälligkeitsdatum " + faellig + ".") +
    // "\n\nMit freundlichen Grüßen\n";
    // return ResponseEntity.ok(text);
    // } catch (Exception e) {
    // return ResponseEntity.notFound().build();
    // }
    // }

    @DeleteMapping({ "/{projektID}/dokumente/{dokumentID}" })
    public ResponseEntity<Void> loescheDokument(@PathVariable Long projektID,
            @PathVariable Long dokumentID) {
        try {
            dateiSpeicherService.loescheDatei(dokumentID);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<ProjektSearchResponseDto> getAlleProjekte(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long kategorieId,
            @RequestParam(required = false) String kunde,
            @RequestParam(required = false) String kundennummer,
            @RequestParam(required = false) String auftragsnummer,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum,
            @RequestParam(required = false) Boolean bezahlt,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {

        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<ProjektResponseDto> projekte = projektManagementService.findeProjekteMitFilter(
                q,
                kategorieId,
                kunde,
                kundennummer,
                auftragsnummer,
                datum,
                bezahlt,
                pageIndex,
                pageSize);
        ProjektSearchResponseDto response = new ProjektSearchResponseDto();
        response.setProjekte(projekte.getContent());
        response.setGesamt(projekte.getTotalElements());
        response.setSeite(projekte.getNumber());
        response.setSeitenGroesse(projekte.getSize());
        return ResponseEntity.ok(response);
    }

    /**
     * Schlanke Projektliste für Dropdowns und Auswahl-Dialoge.
     * Lädt nur id, bauvorhaben, auftragsnummer und kunde - keine E-Mails oder Kilogramm.
     * Deutlich schneller als der Standard-Endpunkt.
     */
    @GetMapping("/simple")
    public ResponseEntity<List<ProjektSucheDto>> getProjekteSimple(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(value = "size", defaultValue = "500") int size,
            @RequestParam(value = "nurOffene", required = false, defaultValue = "false") boolean nurOffene) {
        int pageSize = Math.min(Math.max(size, 1), 500);
        var projekte = projektRepository.findSimpleByQuery(
                q.isBlank() ? null : q.trim(),
                org.springframework.data.domain.PageRequest.of(0, pageSize));
        var stream = projekte.stream();
        if (nurOffene) {
            stream = stream.filter(p -> !p.isAbgeschlossen());
        }
        List<ProjektSucheDto> results = stream
                .map(p -> new ProjektSucheDto(p.getId(), p.getBauvorhaben(), p.getAuftragsnummer(), p.getKunde(), p.isAbgeschlossen()))
                .toList();
        return ResponseEntity.ok(results);
    }

    /**
     * Schnelle Projektsuche für Bestellungszuordnung.
     * Gibt maximal 10 Treffer mit ID, Bauvorhaben, Auftragsnummer und Kunde zurück.
     */
    @GetMapping("/suche")
    public ResponseEntity<List<ProjektSucheDto>> sucheProjekte(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        Page<ProjektResponseDto> projekte = projektManagementService.findeProjekteMitFilter(
                q.trim(), null, null, null, null, null, null, 0, 10);
        List<ProjektSucheDto> results = projekte.getContent().stream()
                .map(p -> new ProjektSucheDto(p.getId(), p.getBauvorhaben(), p.getAuftragsnummer(), p.getKunde(), p.isAbgeschlossen()))
                .toList();
        return ResponseEntity.ok(results);
    }

    public record ProjektSucheDto(Long id, String bauvorhaben, String auftragsnummer, String kunde, boolean abgeschlossen) {
    }


    /**
     * Generiert die nächste verfügbare Auftragsnummer für ein bestimmtes Datum.
     * Format: YYYY/MM/XXXXX
     * WICHTIG: Diese Route muss VOR /{id} definiert werden!
     */
    @GetMapping("/naechste-auftragsnummer")
    public ResponseEntity<NaechsteAuftragsnummerResponse> getNaechsteAuftragsnummer(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum) {
        if (datum == null) {
            datum = LocalDate.now();
        }

        String auftragsnummer = projektManagementService.generiereNaechsteAuftragsnummer(datum);
        long zaehler = projektManagementService.getNaechsterAuftragsnummerZaehler(datum);
        String prefix = "%d/%02d/".formatted(datum.getYear(), datum.getMonthValue());

        return ResponseEntity.ok(new NaechsteAuftragsnummerResponse(auftragsnummer, prefix, zaehler));
    }

    /**
     * Prüft ob eine Auftragsnummer bereits vergeben ist.
     * WICHTIG: Diese Route muss VOR /{id} definiert werden!
     */
    @GetMapping("/auftragsnummer-verfuegbar")
    public ResponseEntity<AuftragsnummerValidierungResponse> pruefeAuftragsnummer(
            @RequestParam String auftragsnummer,
            @RequestParam(required = false) Long projektId) {

        boolean vergeben;
        if (projektId != null) {
            // Beim Bearbeiten: prüfen ob andere Projekte diese Nummer haben
            vergeben = projektManagementService.istAuftragsnummerVergebenFuerAnderesProjekt(auftragsnummer, projektId);
        } else {
            // Beim Erstellen: prüfen ob Nummer überhaupt existiert
            vergeben = projektManagementService.istAuftragsnummerVergeben(auftragsnummer);
        }

        String message = vergeben
                ? "Diese Auftragsnummer ist bereits vergeben. Bitte wählen Sie eine andere."
                : null;

        return ResponseEntity.ok(new AuftragsnummerValidierungResponse(!vergeben, message));
    }

    // Response DTOs für Auftragsnummer
    public record NaechsteAuftragsnummerResponse(String auftragsnummer, String prefix, long zaehler) {
    }

    public record AuftragsnummerValidierungResponse(boolean verfuegbar, String message) {
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjektResponseDto> getProjektById(@PathVariable Long id) {
        try {
            ProjektResponseDto projekt = projektManagementService.findeProjektById(id);
            return ResponseEntity.ok(projekt);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ============== PROJEKT-NOTIZEN ==============

    /**
     * Listet alle Notizen zu einem Projekt, sortiert nach Erstellungsdatum (neueste
     * zuerst).
     */
    @GetMapping("/{projektId}/notizen")
    public ResponseEntity<List<ProjektNotizDto>> getProjektNotizen(
            @PathVariable Long projektId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId) {

        List<ProjektNotiz> notizen = projektNotizRepository.findByProjektIdOrderByErstelltAmDesc(projektId);

        // Ermittle den anfragenden Mitarbeiter (falls Mobile oder PC)
        Mitarbeiter requester = null;
        boolean isMobileRequest = token != null && !token.isBlank();

        if (isMobileRequest) {
            requester = mitarbeiterRepository.findByLoginToken(token).orElse(null);
        } else if (userProfileId != null) {
            requester = frontendUserProfileService.findById(userProfileId)
                    .map(profile -> profile.getMitarbeiter())
                    .orElse(null);
        }

        final Mitarbeiter finalRequester = requester;
        final boolean finalIsMobileRequest = isMobileRequest;

        List<ProjektNotizDto> dtos = notizen.stream()
                .filter(n -> {
                    // 1. Mobile Check
                    if (finalIsMobileRequest && !n.isMobileSichtbar())
                        return false;

                    // 2. Privacy Check (nurFuerErsteller)
                    if (n.isNurFuerErsteller()) {
                        if (finalRequester == null)
                            return false; // Safety: if no user identified, hide private notes
                        // Show only if requester is the creator
                        return n.getMitarbeiter() != null && n.getMitarbeiter().getId().equals(finalRequester.getId());
                    }

                    return true;
                })
                .map(n -> mapNotizToDto(n, finalRequester, finalIsMobileRequest))
                .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Fügt eine einzelne E-Mail-Adresse zu den Projekt-E-Mails hinzu.
     */
    @PostMapping("/{projektId}/emails")
    public ResponseEntity<Map<String, Object>> addProjektEmail(
            @PathVariable Long projektId,
            @RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "E-Mail-Adresse fehlt"));
        }
        email = email.trim().toLowerCase();
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new RuntimeException("Projekt nicht gefunden"));
        if (projekt.getKundenEmails() == null) {
            projekt.setKundenEmails(new java.util.ArrayList<>());
        }
        if (projekt.getKundenEmails().contains(email)) {
            return ResponseEntity.ok(Map.of("message", "E-Mail-Adresse bereits vorhanden", "added", false));
        }
        projekt.getKundenEmails().add(email);
        projektRepository.save(projekt);
        return ResponseEntity.ok(Map.of("message", "E-Mail-Adresse gespeichert", "added", true));
    }

    /**
     * Erstellt eine neue Notiz zu einem Projekt.
     */
    @PostMapping("/{projektId}/notizen")
    public ResponseEntity<ProjektNotizDto> erstelleProjektNotiz(
            @PathVariable Long projektId,
            @RequestBody ProjektNotizCreateDto dto,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestParam(value = "token", required = false) String token) {
        try {
            Projekt projekt = projektRepository.findById(projektId)
                    .orElseThrow(() -> new RuntimeException("Projekt nicht gefunden"));

            Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, token);
            if (mitarbeiter == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
            }

            ProjektNotiz notiz = new ProjektNotiz();
            notiz.setProjekt(projekt);
            notiz.setMitarbeiter(mitarbeiter);
            notiz.setNotiz(dto.notiz);
            // Default true, falls null (wobei boolean primitiv im DTO schwierig wäre, aber
            // hier Feldzugriff)
            notiz.setMobileSichtbar(dto.mobileSichtbar);
            notiz.setNurFuerErsteller(dto.nurFuerErsteller);
            notiz.setErstelltAm(LocalDateTime.now());

            ProjektNotiz gespeichert = projektNotizRepository.save(notiz);
            // Nach Erstellen hat man Rechte darauf
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(mapNotizToDto(gespeichert, mitarbeiter, token != null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    /**
     * Aktualisiert eine bestehende Notiz.
     */
    @PatchMapping("/{projektId}/notizen/{notizId}")
    public ResponseEntity<ProjektNotizDto> updateProjektNotiz(
            @PathVariable Long projektId,
            @PathVariable Long notizId,
            @RequestBody ProjektNotizCreateDto dto, // Wiederverwendung CreateDto für einfaches Update
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestParam(value = "token", required = false) String token) {
        try {
            ProjektNotiz notiz = projektNotizRepository.findById(notizId)
                    .orElseThrow(() -> new RuntimeException("Notiz nicht gefunden"));

            Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, token);
            boolean isMobile = token != null && !token.isBlank();

            // Berechtigungsprüfung
            if (!hasEditPermission(notiz, mitarbeiter, isMobile)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (dto.notiz != null) {
                notiz.setNotiz(dto.notiz);
            }
            // Mobile Sichtbarkeit updatebar
            notiz.setMobileSichtbar(dto.mobileSichtbar);
            notiz.setNurFuerErsteller(dto.nurFuerErsteller);

            ProjektNotiz updated = projektNotizRepository.save(notiz);
            return ResponseEntity.ok(mapNotizToDto(updated, mitarbeiter, isMobile));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Löscht eine Notiz.
     */
    @DeleteMapping("/{projektId}/notizen/{notizId}")
    public ResponseEntity<Void> deleteProjektNotiz(
            @PathVariable Long projektId,
            @PathVariable Long notizId,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestParam(value = "token", required = false) String token) {
        try {
            ProjektNotiz notiz = projektNotizRepository.findById(notizId)
                    .orElseThrow(() -> new RuntimeException("Notiz nicht gefunden"));

            Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, token);
            boolean isMobile = token != null && !token.isBlank();

            // Berechtigungsprüfung
            if (!hasEditPermission(notiz, mitarbeiter, isMobile)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            projektNotizRepository.delete(notiz);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Mitarbeiter resolveMitarbeiter(Long userProfileId, String token) {
        if (token != null && !token.isBlank()) {
            return mitarbeiterRepository.findByLoginToken(token).orElse(null);
        } else if (userProfileId != null) {
            return frontendUserProfileService.findById(userProfileId)
                    .map(profile -> profile.getMitarbeiter())
                    .orElse(null);
        }
        return null;
    }

    private boolean hasEditPermission(ProjektNotiz notiz, Mitarbeiter requester, boolean isMobile) {
        if (requester == null)
            return false;
        // PC Nutzer dürfen alles (vereinfacht, oder man prüft ob Admin)
        // Aber laut Anforderung: "auf mobile app kann jeder user nur seine eigenen
        // notizen löschen oder bearbeiten"
        // Im Umkehrschluss: Am PC darf man wohl alles (oder zumindest mehr), aber
        // sicherheitshalber:
        // PC User -> Darf alles bearbeiten (da Verwaltungssoftware)
        if (!isMobile)
            return true;

        // Mobile User -> Nur eigene
        return notiz.getMitarbeiter() != null && notiz.getMitarbeiter().getId().equals(requester.getId());
    }

    private ProjektNotizDto mapNotizToDto(ProjektNotiz notiz, Mitarbeiter requester, boolean isMobileRequest) {
        ProjektNotizDto dto = new ProjektNotizDto();
        dto.id = notiz.getId();
        dto.notiz = notiz.getNotiz();
        dto.erstelltAm = notiz.getErstelltAm();
        dto.mobileSichtbar = notiz.isMobileSichtbar();
        dto.nurFuerErsteller = notiz.isNurFuerErsteller();

        if (notiz.getMitarbeiter() != null) {
            dto.mitarbeiterId = notiz.getMitarbeiter().getId();
            dto.mitarbeiterVorname = notiz.getMitarbeiter().getVorname();
            dto.mitarbeiterNachname = notiz.getMitarbeiter().getNachname();
        }

        // Permission Flag setzen
        dto.canEdit = hasEditPermission(notiz, requester, isMobileRequest);

        // Bilder mappen
        if (notiz.getBilder() != null) {
            dto.bilder = notiz.getBilder().stream()
                    .map(this::mapBildToDto)
                    .toList();
        } else {
            dto.bilder = List.of();
        }

        return dto;
    }

    private ProjektNotizBildDto mapBildToDto(ProjektNotizBild bild) {
        ProjektNotizBildDto dto = new ProjektNotizBildDto();
        dto.id = bild.getId();
        dto.originalDateiname = bild.getOriginalDateiname();
        dto.url = "/api/dokumente/" + bild.getGespeicherterDateiname();
        dto.erstelltAm = bild.getErstelltAm();
        return dto;
    }

    // ============== PROJEKT-NOTIZ-BILDER ==============

    /**
     * Lädt ein Bild zu einer Notiz hoch.
     */
    @PostMapping(value = "/{projektId}/notizen/{notizId}/bilder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjektNotizBildDto> uploadNotizBild(
            @PathVariable Long projektId,
            @PathVariable Long notizId,
            @RequestParam("datei") MultipartFile datei,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestParam(value = "token", required = false) String token) {
        try {
            ProjektNotiz notiz = projektNotizRepository.findById(notizId)
                    .orElseThrow(() -> new RuntimeException("Notiz nicht gefunden"));

            // Berechtigungsprüfung
            Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, token);
            boolean isMobile = token != null && !token.isBlank();
            if (!hasEditPermission(notiz, mitarbeiter, isMobile)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Datei speichern (nutze DateiSpeicherService-Logik)
            String originalName = datei.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            String gespeicherterName = java.util.UUID.randomUUID().toString() + extension;

            // Speichere in uploads-Verzeichnis
            Path uploadPath = Path.of("uploads", gespeicherterName);
            Files.createDirectories(uploadPath.getParent());
            Files.copy(datei.getInputStream(), uploadPath, StandardCopyOption.REPLACE_EXISTING);

            // Erstelle Bild-Entity
            ProjektNotizBild bild = new ProjektNotizBild();
            bild.setNotiz(notiz);
            bild.setGespeicherterDateiname(gespeicherterName);
            bild.setOriginalDateiname(originalName);
            bild.setDateityp(datei.getContentType());
            bild.setErstelltAm(LocalDateTime.now());

            ProjektNotizBild gespeichert = projektNotizBildRepository.save(bild);

            return ResponseEntity.status(HttpStatus.CREATED).body(mapBildToDto(gespeichert));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Löscht ein Bild von einer Notiz.
     */
    @DeleteMapping("/{projektId}/notizen/{notizId}/bilder/{bildId}")
    public ResponseEntity<Void> deleteNotizBild(
            @PathVariable Long projektId,
            @PathVariable Long notizId,
            @PathVariable Long bildId,
            @RequestHeader(value = "X-User-Profile-Id", required = false) Long userProfileId,
            @RequestParam(value = "token", required = false) String token) {
        try {
            ProjektNotizBild bild = projektNotizBildRepository.findById(bildId)
                    .orElseThrow(() -> new RuntimeException("Bild nicht gefunden"));

            // Prüfe ob Bild zur Notiz gehört
            if (!bild.getNotiz().getId().equals(notizId)) {
                return ResponseEntity.badRequest().build();
            }

            // Berechtigungsprüfung über die Notiz
            Mitarbeiter mitarbeiter = resolveMitarbeiter(userProfileId, token);
            boolean isMobile = token != null && !token.isBlank();
            if (!hasEditPermission(bild.getNotiz(), mitarbeiter, isMobile)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Datei löschen
            try {
                Path filePath = Path.of("uploads", bild.getGespeicherterDateiname());
                Files.deleteIfExists(filePath);
            } catch (Exception ignored) {
            }

            // Entity löschen
            projektNotizBildRepository.delete(bild);

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    public static class ProjektNotizDto {
        public Long id;
        public String notiz;
        public LocalDateTime erstelltAm;
        public Long mitarbeiterId;
        public String mitarbeiterVorname;
        public String mitarbeiterNachname;
        public boolean mobileSichtbar;
        public boolean nurFuerErsteller;
        public boolean canEdit;
        public List<ProjektNotizBildDto> bilder;
    }

    public static class ProjektNotizBildDto {
        public Long id;
        public String originalDateiname;
        public String url;
        public LocalDateTime erstelltAm;
    }

    public static class ProjektNotizCreateDto {
        public String notiz;
        public boolean mobileSichtbar = true; // Default true if omitted
        public boolean nurFuerErsteller = false;
    }

    private Mitarbeiter resolveMitarbeiterByIds(Long userProfileId, Long mitarbeiterId) {
        if (mitarbeiterId != null) {
            return mitarbeiterRepository.findById(mitarbeiterId).orElse(null);
        } else if (userProfileId != null) {
            return frontendUserProfileService.findById(userProfileId)
                    .map(FrontendUserProfile::getMitarbeiter)
                    .orElse(null);
        }
        return null;
    }

}
