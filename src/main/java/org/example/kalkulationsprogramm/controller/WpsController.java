package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.domain.WpsLage;
import org.example.kalkulationsprogramm.domain.WpsProjektZuweisung;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.WpsProjektZuweisungRepository;
import org.example.kalkulationsprogramm.repository.WpsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

/**
 * CRUD-API für Schweißanweisungen (WPS – Welding Procedure Specification)
 * nach EN ISO 15614-1. Bestandteil der EN 1090 EXC 2 Dokumentation.
 */
@RestController
@RequestMapping("/api/wps")
@RequiredArgsConstructor
public class WpsController {

    private final WpsRepository repository;
    private final ProjektRepository projektRepository;
    private final WpsProjektZuweisungRepository zuweisungRepository;
    private final MitarbeiterRepository mitarbeiterRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    // --- Response / Request DTOs ---

    public record WpsResponse(
            Long id,
            String wpsNummer,
            String bezeichnung,
            String norm,
            String schweissProzes,
            String grundwerkstoff,
            String zusatzwerkstoff,
            String nahtart,
            BigDecimal blechdickeMin,
            BigDecimal blechdickeMax,
            LocalDate revisionsdatum,
            LocalDate gueltigBis,
            String originalDateiname,
            String gespeicherterDateiname,
            String quelle,
            LocalDateTime erstelltAm,
            List<LageResponse> lagen) {
    }

    public record WpsRequest(
            String wpsNummer,
            String bezeichnung,
            String norm,
            String schweissProzes,
            String grundwerkstoff,
            String zusatzwerkstoff,
            String nahtart,
            BigDecimal blechdickeMin,
            BigDecimal blechdickeMax,
            LocalDate revisionsdatum,
            LocalDate gueltigBis,
            String originalDateiname,
            String gespeicherterDateiname,
            List<LageRequest> lagen) {
    }

    public record LageResponse(
            Long id,
            Integer nummer,
            String typ,
            BigDecimal currentA,
            BigDecimal voltageV,
            BigDecimal wireSpeed,
            BigDecimal fillerDiaMm,
            BigDecimal gasFlow,
            String bemerkung) {
    }

    public record LageRequest(
            Integer nummer,
            String typ,
            BigDecimal currentA,
            BigDecimal voltageV,
            BigDecimal wireSpeed,
            BigDecimal fillerDiaMm,
            BigDecimal gasFlow,
            String bemerkung) {
    }

    public record ZuweisungResponse(
            Long id,
            Long wpsId,
            String wpsNummer,
            String wpsBezeichnung,
            Long schweisserId,
            String schweisserName,
            String schweisspruefer,
            LocalDate einsatzDatum,
            String bemerkung) {
    }

    public record ZuweisungRequest(
            Long wpsId,
            Long schweisserId,
            String schweisspruefer,
            LocalDate einsatzDatum,
            String bemerkung) {
    }

    private WpsResponse toResponse(Wps w) {
        List<LageResponse> lagen = w.getLagen().stream()
                .map(l -> new LageResponse(
                        l.getId(),
                        l.getNummer(),
                        l.getTyp() != null ? l.getTyp().name() : null,
                        l.getCurrentA(),
                        l.getVoltageV(),
                        l.getWireSpeed(),
                        l.getFillerDiaMm(),
                        l.getGasFlow(),
                        l.getBemerkung()))
                .toList();
        return new WpsResponse(
                w.getId(),
                w.getWpsNummer(),
                w.getBezeichnung(),
                w.getNorm(),
                w.getSchweissProzes(),
                w.getGrundwerkstoff(),
                w.getZusatzwerkstoff(),
                w.getNahtart(),
                w.getBlechdickeMin(),
                w.getBlechdickeMax(),
                w.getRevisionsdatum(),
                w.getGueltigBis(),
                w.getOriginalDateiname(),
                w.getGespeicherterDateiname(),
                w.getQuelle() != null ? w.getQuelle().name() : Wps.Quelle.EDITOR.name(),
                w.getErstelltAm(),
                lagen);
    }

    private ZuweisungResponse toZuweisungResponse(WpsProjektZuweisung z) {
        Mitarbeiter m = z.getSchweisser();
        return new ZuweisungResponse(
                z.getId(),
                z.getWps().getId(),
                z.getWps().getWpsNummer(),
                z.getWps().getBezeichnung(),
                m != null ? m.getId() : null,
                m != null ? m.getVorname() + " " + m.getNachname() : null,
                z.getSchweisspruefer(),
                z.getEinsatzDatum(),
                z.getBemerkung());
    }

    // --- Standard CRUD ---

    @GetMapping
    public List<WpsResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WpsResponse> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(w -> ResponseEntity.ok(toResponse(w)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projekt/{projektId}")
    public List<WpsResponse> getByProjekt(@PathVariable Long projektId) {
        return repository.findByProjektId(projektId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<WpsResponse> create(@RequestBody WpsRequest req) {
        Wps w = new Wps();
        apply(w, req);
        return ResponseEntity.ok(toResponse(repository.save(w)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WpsResponse> update(@PathVariable Long id,
                                               @RequestBody WpsRequest req) {
        return repository.findById(id).map(w -> {
            apply(w, req);
            return ResponseEntity.ok(toResponse(repository.save(w)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Wps wps = repository.findById(id).orElse(null);
        if (wps == null) {
            return ResponseEntity.notFound().build();
        }
        // Bei importierten PDF-WPS die Datei mit löschen.
        if (wps.getGespeicherterDateiname() != null) {
            try {
                Path uploadBase = Path.of(uploadDir).toAbsolutePath().normalize();
                Files.deleteIfExists(uploadBase.resolve(wps.getGespeicherterDateiname()).normalize());
            } catch (IOException ignored) {
            }
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- PDF-Import (extern erstellte Schweißanweisungen) ---

    /**
     * Import einer bereits existierenden Schweißanweisung als PDF.
     * Die WPS wird mit {@code quelle=UPLOAD} angelegt – der Editor ist gesperrt,
     * nur Metadaten und das PDF selbst werden angezeigt.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WpsResponse> uploadPdfWps(
            @RequestParam("datei") MultipartFile datei,
            @RequestParam("wpsNummer") String wpsNummer,
            @RequestParam(value = "bezeichnung", required = false) String bezeichnung,
            @RequestParam(value = "norm", required = false) String norm,
            @RequestParam(value = "schweissProzes", required = false) String schweissProzes,
            @RequestParam(value = "grundwerkstoff", required = false) String grundwerkstoff,
            @RequestParam(value = "nahtart", required = false) String nahtart,
            @RequestParam(value = "gueltigBis", required = false) String gueltigBis) throws IOException {

        if (datei == null || datei.isEmpty() || wpsNummer == null || wpsNummer.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String originalFilename = Path.of(
                StringUtils.cleanPath(Objects.requireNonNull(datei.getOriginalFilename())))
                .getFileName().toString();
        if (!originalFilename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        Path uploadBase = Path.of(uploadDir).toAbsolutePath().normalize();
        Path targetPath = uploadBase.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(uploadBase)) {
            return ResponseEntity.badRequest().build();
        }
        Files.createDirectories(uploadBase);
        datei.transferTo(targetPath);

        Wps w = new Wps();
        w.setQuelle(Wps.Quelle.UPLOAD);
        w.setWpsNummer(wpsNummer.trim());
        w.setBezeichnung(bezeichnung);
        w.setNorm(norm != null && !norm.isBlank() ? norm.trim() : "EN ISO 15614-1");
        w.setSchweissProzes(schweissProzes != null && !schweissProzes.isBlank() ? schweissProzes.trim() : "extern");
        w.setGrundwerkstoff(grundwerkstoff);
        w.setNahtart(nahtart);
        if (gueltigBis != null && !gueltigBis.isBlank()) {
            try {
                w.setGueltigBis(LocalDate.parse(gueltigBis.trim()));
            } catch (Exception ignored) {
                // ungültiges Datum → Feld bleibt null
            }
        }
        w.setOriginalDateiname(originalFilename);
        w.setGespeicherterDateiname(storedFilename);

        return ResponseEntity.ok(toResponse(repository.save(w)));
    }

    /**
     * Streamt die hochgeladene PDF-Datei einer importierten WPS zum Anzeigen im Browser.
     */
    @GetMapping("/{id}/dokument")
    public ResponseEntity<Resource> streamDokument(@PathVariable Long id) {
        Wps w = repository.findById(id).orElse(null);
        if (w == null || w.getGespeicherterDateiname() == null) {
            return ResponseEntity.notFound().build();
        }
        Path uploadBase = Path.of(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadBase.resolve(w.getGespeicherterDateiname()).normalize();
        if (!filePath.startsWith(uploadBase) || !Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath);
        String filename = w.getOriginalDateiname() != null ? w.getOriginalDateiname() : "wps.pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    // --- Projekt-Zuweisung (M:N) ---

    /** WPS einem Projekt zuordnen */
    @PostMapping("/projekt/{projektId}/{wpsId}")
    @Transactional
    public ResponseEntity<Void> assignToProjekt(@PathVariable Long projektId,
                                                 @PathVariable Long wpsId) {
        Wps wps = repository.findById(wpsId).orElse(null);
        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (wps == null || projekt == null) return ResponseEntity.notFound().build();
        wps.getProjekte().add(projekt);
        repository.save(wps);
        return ResponseEntity.ok().build();
    }

    /** WPS aus Projekt entfernen */
    @DeleteMapping("/projekt/{projektId}/{wpsId}")
    @Transactional
    public ResponseEntity<Void> unassignFromProjekt(@PathVariable Long projektId,
                                                     @PathVariable Long wpsId) {
        Wps wps = repository.findById(wpsId).orElse(null);
        if (wps == null) return ResponseEntity.notFound().build();
        wps.getProjekte().removeIf(p -> p.getId().equals(projektId));
        repository.save(wps);
        return ResponseEntity.noContent().build();
    }

    // --- Schweißer-Zuweisungen (individualisiert) ---

    /** Alle individuellen Schweißer-Zuweisungen für ein Projekt */
    @GetMapping("/projekt/{projektId}/zuweisungen")
    public List<ZuweisungResponse> getZuweisungen(@PathVariable Long projektId) {
        return zuweisungRepository.findByProjektId(projektId).stream()
                .map(this::toZuweisungResponse).toList();
    }

    /** Neue Schweißer-Zuweisung für eine WPS in einem Projekt anlegen */
    @PostMapping("/projekt/{projektId}/zuweisungen")
    public ResponseEntity<ZuweisungResponse> createZuweisung(
            @PathVariable Long projektId,
            @RequestBody ZuweisungRequest req) {
        Wps wps = repository.findById(req.wpsId()).orElse(null);
        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (wps == null || projekt == null) return ResponseEntity.notFound().build();

        WpsProjektZuweisung z = new WpsProjektZuweisung();
        z.setWps(wps);
        z.setProjekt(projekt);
        if (req.schweisserId() != null) {
            mitarbeiterRepository.findById(req.schweisserId()).ifPresent(z::setSchweisser);
        }
        z.setSchweisspruefer(req.schweisspruefer());
        z.setEinsatzDatum(req.einsatzDatum());
        z.setBemerkung(req.bemerkung());
        return ResponseEntity.ok(toZuweisungResponse(zuweisungRepository.save(z)));
    }

    /** Schweißer-Zuweisung löschen */
    @DeleteMapping("/zuweisungen/{id}")
    public ResponseEntity<Void> deleteZuweisung(@PathVariable Long id) {
        if (!zuweisungRepository.existsById(id)) return ResponseEntity.notFound().build();
        zuweisungRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- Helper ---

    private void apply(Wps w, WpsRequest req) {
        w.setWpsNummer(req.wpsNummer());
        w.setBezeichnung(req.bezeichnung());
        w.setNorm(req.norm());
        w.setSchweissProzes(req.schweissProzes());
        w.setGrundwerkstoff(req.grundwerkstoff());
        w.setZusatzwerkstoff(req.zusatzwerkstoff());
        w.setNahtart(req.nahtart());
        w.setBlechdickeMin(req.blechdickeMin());
        w.setBlechdickeMax(req.blechdickeMax());
        w.setRevisionsdatum(req.revisionsdatum());
        w.setGueltigBis(req.gueltigBis());
        w.setOriginalDateiname(req.originalDateiname());
        w.setGespeicherterDateiname(req.gespeicherterDateiname());
        applyLagen(w, req.lagen());
    }

    /**
     * Ersetzt die Lagen einer WPS vollständig durch die übergebene Liste.
     * Wir nutzen {@code orphanRemoval=true}, daher genügt {@code clear() + addAll()}.
     */
    private void applyLagen(Wps w, List<LageRequest> lagenReq) {
        w.getLagen().clear();
        if (lagenReq == null) return;
        List<WpsLage> neu = new ArrayList<>();
        int idx = 1;
        for (LageRequest r : lagenReq) {
            WpsLage l = new WpsLage();
            l.setWps(w);
            l.setNummer(r.nummer() != null ? r.nummer() : idx);
            l.setTyp(parseTyp(r.typ()));
            l.setCurrentA(r.currentA());
            l.setVoltageV(r.voltageV());
            l.setWireSpeed(r.wireSpeed());
            l.setFillerDiaMm(r.fillerDiaMm());
            l.setGasFlow(r.gasFlow());
            l.setBemerkung(r.bemerkung());
            neu.add(l);
            idx++;
        }
        w.getLagen().addAll(neu);
    }

    private WpsLage.Typ parseTyp(String t) {
        if (t == null || t.isBlank()) return WpsLage.Typ.FUELL;
        try {
            return WpsLage.Typ.valueOf(t.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WpsLage.Typ.FUELL;
        }
    }
}
