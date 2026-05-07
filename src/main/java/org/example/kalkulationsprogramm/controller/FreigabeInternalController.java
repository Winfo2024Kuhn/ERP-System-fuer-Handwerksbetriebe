package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAkzeptierenRequest;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAkzeptiertResponse;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAnsichtDto;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Server-zu-Server-Schnittstelle, die das Astro-Frontend (auf bauschlosserei-kuhn.de)
 * über den Cloudflare-Tunnel anspricht. Liegt unter /api/internal/** und wird vom
 * {@link org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter} abgesichert –
 * Spring-Auth ist hier bewusst aus.
 */
@RestController
@RequestMapping("/api/internal/freigabe")
@RequiredArgsConstructor
@Slf4j
public class FreigabeInternalController
{
    private final DokumentFreigabeService freigabeService;
    private final DateiSpeicherService dateiSpeicherService;

    /**
     * Wird beim Aufruf der Astro-Seite /freigabe/[uuid] aufgerufen, um die anzuzeigenden
     * Daten zu holen. Liefert immer ein DTO – der Status (PENDING / ACCEPTED / EXPIRED /
     * REVOKED) entscheidet, was Astro dem Kunden zeigt.
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<FreigabeAnsichtDto> hole(@PathVariable String uuid)
    {
        Optional<DokumentFreigabe> opt = freigabeService.findByUuidUndAktualisiereStatus(uuid);
        if (opt.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(opt.get()));
    }

    /**
     * Streamt die zur Freigabe gehörige PDF, damit Astro sie unter eigener Domain
     * an den Kunden weiterreichen kann. Funktioniert nur für noch nicht abgelaufene
     * Freigaben.
     */
    @GetMapping("/{uuid}/pdf")
    public ResponseEntity<Resource> ladeDokumentPdf(@PathVariable String uuid)
    {
        Optional<DokumentFreigabe> opt = freigabeService.findByUuidUndAktualisiereStatus(uuid);
        if (opt.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }
        DokumentFreigabe freigabe = opt.get();
        if (freigabe.getStatus() == FreigabeStatus.EXPIRED || freigabe.getStatus() == FreigabeStatus.REVOKED)
        {
            return ResponseEntity.status(410).build(); // 410 Gone
        }
        if (freigabe.getDokumentDatei() == null || freigabe.getDokumentDatei().isBlank())
        {
            return ResponseEntity.notFound().build();
        }
        try
        {
            Resource resource = dateiSpeicherService.ladeDokumentAlsResource(freigabe.getDokumentDatei());
            String anzeigeName = "%s_%s.pdf".formatted(
                    freigabe.getDokumentArt() == null ? "Dokument" : freigabe.getDokumentArt().replaceAll("\\s+", ""),
                    freigabe.getDokumentNummer() == null ? freigabe.getUuid() : freigabe.getDokumentNummer());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + anzeigeName + "\"")
                    .body(resource);
        }
        catch (Exception e)
        {
            log.warn("PDF zur Freigabe {} nicht ladbar: {}", uuid, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Löscht die temporär gespeicherte PDF einer abgelaufenen Freigabe.
     * Wird von der Internetseite aufgerufen, wenn der Freigabe-Link abgelaufen ist.
     */
    @DeleteMapping("/{uuid}/pdf")
    public ResponseEntity<Void> loeschePdf(@PathVariable String uuid)
    {
        freigabeService.loeschePdfFuerFreigabe(uuid);
        return ResponseEntity.noContent().build();
    }

    /**
     * Markiert die Freigabe als digital akzeptiert. IP und User-Agent werden vom Astro-
     * Layer aus den echten Request-Headern (cf-connecting-ip, x-forwarded-for) extrahiert
     * und mitgeschickt – wir können sie hier serverseitig nicht direkt sehen, weil der
     * Cloudflare-Tunnel davorsitzt.
     */
    @PostMapping("/{uuid}/akzeptieren")
    public ResponseEntity<?> akzeptiere(@PathVariable String uuid,
                                        @RequestBody FreigabeAkzeptierenRequest request)
    {
        if (!request.isBestaetigung())
        {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Bitte bestätigen Sie, dass Sie das Dokument geprüft haben."));
        }
        try
        {
            DokumentFreigabe freigabe = freigabeService.akzeptiere(
                    uuid,
                    request.getClientIp(),
                    truncate(request.getUserAgent(), 500),
                    request.getEmail());
            return ResponseEntity.ok(FreigabeAkzeptiertResponse.builder()
                    .uuid(freigabe.getUuid())
                    .dokumentNummer(freigabe.getDokumentNummer())
                    .dokumentArt(freigabe.getDokumentArt())
                    .akzeptiertAm(freigabe.getAkzeptiertAm())
                    .hashAcceptance(freigabe.getHashAcceptance())
                    .build());
        }
        catch (IllegalArgumentException e)
        {
            return ResponseEntity.notFound().build();
        }
        catch (IllegalStateException e)
        {
            return ResponseEntity.status(410).body(java.util.Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    private FreigabeAnsichtDto toDto(DokumentFreigabe f)
    {
        return FreigabeAnsichtDto.builder()
                .uuid(f.getUuid())
                .status(f.getStatus().name())
                .dokumentNummer(f.getDokumentNummer())
                .dokumentArt(f.getDokumentArt())
                .dokumentBetrag(f.getDokumentBetrag())
                .bauvorhaben(f.getBauvorhaben())
                .kundeName(f.getKundeName())
                .kundeEmail(f.getKundeEmail())
                .erstelltAm(f.getErstelltAm())
                .ablaufDatum(f.getAblaufDatum())
                .akzeptiertAm(f.getAkzeptiertAm())
                .abgelaufen(f.istAbgelaufen())
                .pdfPfad("/api/internal/freigabe/" + f.getUuid() + "/pdf")
                .build();
    }

    private static String truncate(String s, int max)
    {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
