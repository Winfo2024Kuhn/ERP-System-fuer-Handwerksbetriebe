package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.ClientIpResolver;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandDetailDto;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandFesthaltenDto;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandSeiteDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;
import org.example.kalkulationsprogramm.service.dokumentstand.DokumentStandAbfrageService;
import org.example.kalkulationsprogramm.service.dokumentstand.DokumentStandFesthaltenService;
import org.example.kalkulationsprogramm.service.dokumentstand.DokumentStandWiederherstellenService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Verlauf eines Ausgangsdokuments („Stände wie bei git“): Liste, einzelner Stand, PDF eines
 * Exports und Wiederherstellen. Rechte wie {@code GET /api/ausgangs-dokumente/{id}} (angemeldet).
 * Der Bearbeiter kommt aus der Anmeldung, die IP fürs GoBD-Audit vom {@link ClientIpResolver}.
 * Fehler als {@link ResponseStatusException}, der {@code RestExceptionHandler} macht {@code {message}} daraus.
 */
@RestController
@RequestMapping("/api/ausgangs-dokumente/{id}/staende")
@RequiredArgsConstructor
public class AusgangsDokumentStandController {

    private final DokumentStandAbfrageService abfrage;
    private final DokumentStandWiederherstellenService wiederherstellen;
    private final DokumentStandFesthaltenService festhalten;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public StandSeiteDto liste(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return abfrage.seite(id, page, size);
    }

    @GetMapping("/{standId}")
    public StandDetailDto detail(@PathVariable Long id, @PathVariable Long standId) {
        return abfrage.detail(id, standId);
    }

    @GetMapping("/{standId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @PathVariable Long standId) {
        byte[] pdf = abfrage.pdf(id, standId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("stand-" + standId + ".pdf").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(pdf);
    }

    /**
     * Stand ohne PDF festhalten – für Druck und PDF-Knopf bei Angebot, Nachtragsangebot und AB, die
     * kein PDF ans Backend schicken. Keine Sperre nötig; kein neuer Stand bei gleichem Inhalt.
     */
    @PostMapping("/festhalten")
    public StandFesthaltenDto festhalten(@PathVariable Long id, Authentication authentication) {
        return festhalten.festhalten(id, angemeldet(authentication).getId());
    }

    @PostMapping("/{standId}/wiederherstellen")
    public AusgangsGeschaeftsDokumentResponseDto wiederherstellen(@PathVariable Long id, @PathVariable Long standId,
            Authentication authentication, HttpServletRequest request) {
        return wiederherstellen.wiederherstellen(id, standId, angemeldet(authentication).getId(),
                clientIpResolver.resolve(request));
    }

    private static FrontendUserPrincipal angemeldet(Authentication authentication) {
        FrontendUserPrincipal principal = FrontendUserPrincipal.aus(authentication);
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte melden Sie sich an.");
        }
        return principal;
    }
}
