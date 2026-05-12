package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.MwstRechnerService;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST-Endpoints für das Beleg-Modul (Buchhaltung).
 *
 * Auth-Strategie:
 * - Mobile (react-zeiterfassung) authentifiziert per ?token=… (loginToken auf
 *   Mitarbeiter), wie bei LieferantenDokumente / Zeiterfassung.
 * - PC (react-pc-frontend) authentifiziert per Session-Cookie über Spring
 *   Security; der eingeloggte FrontendUserPrincipal wird auf einen
 *   Mitarbeiter via E-Mail/Username gemappt — wenn das fehlschlägt,
 *   verwenden wir den Token-Fallback.
 *
 * Berechtigungen kommen aus dem bestehenden System
 * (AbteilungDokumentBerechtigung mit dokument_typ='BELEG'), verwaltet unter
 * Administration → Lieferanten-Dokumentenrechte.
 */
@Slf4j
@RestController
@RequestMapping("/api/buchhaltung")
@RequiredArgsConstructor
public class BelegController {

    private final BelegService belegService;
    private final MwstRechnerService mwstRechnerService;

    // ===================== Helpers =====================

    /**
     * Delegiert das Mapping {@code (token, auth)} → {@link Mitarbeiter} an den
     * {@link BelegService}, damit Token-Login (Mobile) und Session-Login (PC)
     * an einer einzigen Stelle aufgelöst werden. Wichtig: am PC läuft die
     * Auflösung primär über die direkte FK {@code FrontendUserProfile.mitarbeiter}
     * und nicht über E-Mail-Match.
     */
    private Mitarbeiter resolveCaller(String token, Authentication auth) {
        return belegService.findCaller(token, auth);
    }

    private ResponseEntity<Map<String, String>> forbidden(String msg) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", msg));
    }

    // ===================== Permissions =====================

    @GetMapping("/me/permissions")
    public ResponseEntity<BelegDto.PermissionResponse> myPermissions(
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter m = resolveCaller(token, auth);
        if (m == null) {
            return ResponseEntity.ok(BelegDto.PermissionResponse.builder()
                    .darfSehen(false).darfScannen(false).build());
        }
        return ResponseEntity.ok(belegService.getPermissions(m));
    }

    // === Mobile-Spiegel: dieselben Endpoints, aber unter /mobile/ ===
    // Spring Security routet /api/buchhaltung/mobile/** in die token-only
    // zeiterfassungFilterChain — anders als /api/buchhaltung/** das Session-Auth
    // braucht. Die Mobile-PWA hat keine Session, nur einen loginToken.

    @GetMapping("/mobile/me/permissions")
    public ResponseEntity<BelegDto.PermissionResponse> myPermissionsMobile(
            @RequestParam(value = "token", required = false) String token) {
        return myPermissions(token, null);
    }

    @PostMapping(value = "/mobile/belege", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadBelegMobile(
            @RequestPart("datei") MultipartFile datei,
            @RequestParam(value = "lieferantId", required = false) Long lieferantId,
            @RequestParam(value = "aufteilungsModus", required = false) String aufteilungsModus,
            @RequestParam(value = "token", required = false) String token) {
        return uploadBeleg(datei, lieferantId, aufteilungsModus, token, null);
    }

    // ===================== Upload (Mobile + PC) =====================

    @PostMapping(value = "/belege", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadBeleg(
            @RequestPart("datei") MultipartFile datei,
            @RequestParam(value = "lieferantId", required = false) Long lieferantId,
            @RequestParam(value = "aufteilungsModus", required = false) String aufteilungsModus,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Nicht angemeldet"));
        }
        if (!belegService.darfScannen(caller)) {
            return forbidden("Keine Berechtigung zum Scannen von Belegen");
        }
        BelegAufteilungsModus modus = parseEnum(BelegAufteilungsModus.class, aufteilungsModus);
        if (modus == null) modus = BelegAufteilungsModus.VOLLSTAENDIG;
        try {
            Beleg b = belegService.uploadBeleg(datei, lieferantId, modus, caller);
            return ResponseEntity.ok(belegService.toDto(b));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Beleg-Upload fehlgeschlagen", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Upload fehlgeschlagen"));
        }
    }

    // ===================== Positions-Aufteilung =====================

    /**
     * Speichert die Checkbox-Auswahl des Mobile-Clients. Die Request-Liste ist
     * die vollstaendige Ist-Auswahl — was nicht enthalten ist, wird auf
     * istFuerFirma=false gesetzt. Der Service rechnet anschliessend die
     * Firma-Summen am Beleg neu durch.
     */
    @PutMapping("/belege/{id}/positionen")
    public ResponseEntity<?> setzePositionsAuswahl(
            @PathVariable Long id,
            @RequestBody BelegDto.PositionAuswahlRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(belegService.setzePositionsAuswahl(id, req.getFirmaPositionIds()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/mobile/belege/{id}/positionen")
    public ResponseEntity<?> setzePositionsAuswahlMobile(
            @PathVariable Long id,
            @RequestBody BelegDto.PositionAuswahlRequest req,
            @RequestParam(value = "token", required = false) String token) {
        return setzePositionsAuswahl(id, req, token, null);
    }

    /**
     * Mobile-Spiegel fuer den Beleg-Detail-Fetch. Wird von der mobilen
     * Positionen-Auswahl-Page beim Polling auf {@code kiAnalyseStatus DONE}
     * gerufen — die PWA hat nur einen Token, keine Session, und kommt
     * deshalb nicht durch die session-basierte apiFilterChain.
     */
    @GetMapping("/mobile/belege/{id}")
    public ResponseEntity<BelegDto.Response> getBelegMobile(
            @PathVariable Long id,
            @RequestParam(value = "token", required = false) String token) {
        return getBeleg(id, token, null);
    }

    // ===================== MwSt-Rechner =====================

    /**
     * Rechen-Endpoint fuer das MwSt-Rechner-UI (Mobile + PC). Aus zwei der
     * drei Eingaben (netto/brutto/satz) wird der dritte Wert plus der
     * Steuerbetrag berechnet. Keine DB-Aenderung — reiner Rechen-Call.
     *
     * Auth verpflichtend: VPN ersetzt keine User-Auth — sonst waere der
     * Endpoint ein unauthentifizierter Compute-DoS-Vektor.
     */
    @PostMapping("/mwst-rechner")
    public ResponseEntity<?> mwstRechner(
            @RequestBody(required = false) BelegDto.MwstRechnerRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Request-Body fehlt"));
        }
        try {
            MwstRechnerService.MwstErgebnis e = mwstRechnerService.berechne(
                    req.getNetto(), req.getBrutto(), req.getSatzProzent());
            return ResponseEntity.ok(BelegDto.MwstRechnerResponse.builder()
                    .netto(e.getNetto())
                    .brutto(e.getBrutto())
                    .satzProzent(e.getSatzProzent())
                    .mwstBetrag(e.getMwstBetrag())
                    .build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/mobile/mwst-rechner")
    public ResponseEntity<?> mwstRechnerMobile(
            @RequestBody(required = false) BelegDto.MwstRechnerRequest req,
            @RequestParam(value = "token", required = false) String token) {
        return mwstRechner(req, token, null);
    }

    // ===================== Umbuchung (ohne Beleg-Datei, nur PC) =====================

    /**
     * Erfasst eine belegfreie Buchung (Privatentnahme, Privat->Firma, Kasse->Bank).
     * Bewusst KEIN Mobile-Spiegel — Umbuchungen werden am PC erfasst, der
     * Buchhalter hat dort das Formular + alle Sachkonten zur Hand.
     */
    @PostMapping("/umbuchungen")
    public ResponseEntity<?> createUmbuchung(
            @RequestBody BelegDto.UmbuchungCreateRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Beleg b = belegService.createUmbuchung(req, caller);
            return ResponseEntity.ok(belegService.toDto(b));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Umbuchung anlegen fehlgeschlagen", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Anlegen fehlgeschlagen"));
        }
    }

    // ===================== Liste / Detail =====================

    @GetMapping("/belege")
    public ResponseEntity<List<BelegDto.Response>> listBelege(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "kategorie", required = false) String kategorie,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        BelegStatus st = parseEnum(BelegStatus.class, status);
        BelegKategorie kat = parseEnum(BelegKategorie.class, kategorie);
        return ResponseEntity.ok(belegService.listBelege(st, kat));
    }

    @GetMapping("/belege/{id}")
    public ResponseEntity<BelegDto.Response> getBeleg(
            @PathVariable Long id,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        BelegDto.Response r = belegService.getBeleg(id);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/belege/{id}/datei")
    public ResponseEntity<?> downloadDatei(
            @PathVariable Long id,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Beleg beleg = belegService.getRawBeleg(id);
        if (beleg == null) return ResponseEntity.notFound().build();
        try {
            Path p = belegService.getBelegDatei(beleg);
            if (!Files.exists(p)) return ResponseEntity.notFound().build();
            UrlResource res = new UrlResource(p.toUri());

            // Whitelist erlaubter MIME-Types für Inline-Auslieferung.
            // SVG ist bewusst nicht dabei (kann <script> enthalten -> Stored XSS).
            // Bei allem anderen erzwingen wir attachment + octet-stream + nosniff.
            String storedMime = beleg.getMimeType() != null
                    ? beleg.getMimeType().toLowerCase(java.util.Locale.ROOT) : null;
            boolean inlineSicher = storedMime != null && (
                    storedMime.equals("image/jpeg") || storedMime.equals("image/jpg")
                 || storedMime.equals("image/png")  || storedMime.equals("image/webp")
                 || storedMime.equals("image/heic") || storedMime.equals("image/heif")
                 || storedMime.equals("application/pdf"));

            String contentType = inlineSicher ? storedMime : "application/octet-stream";
            String filename = beleg.getOriginalDateiname() != null
                    ? beleg.getOriginalDateiname() : ("beleg-" + id);
            // ContentDisposition.builder kümmert sich um RFC5987-Encoding der filename*=
            // Header — schützt vor CR/LF-Injection via Dateiname.
            ContentDisposition cd = (inlineSicher
                    ? ContentDisposition.inline()
                    : ContentDisposition.attachment())
                    .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(res);
        } catch (Exception e) {
            log.error("Beleg-Download fehlgeschlagen", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===================== Validierung / Löschen =====================

    @PutMapping("/belege/{id}")
    public ResponseEntity<?> updateBeleg(
            @PathVariable Long id,
            @RequestBody BelegDto.UpdateRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        // Validierung setzt Status=VALIDIERT inkl. validiertVon -> GoBD-relevante Mutation.
        // Konsistent zu DELETE: nur darfScannen (Read-only-Konten wie Steuerberater bleiben raus).
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        BelegDto.Response r = belegService.updateBeleg(id, req, caller);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @DeleteMapping("/belege/{id}")
    public ResponseEntity<?> deleteBeleg(
            @PathVariable Long id,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        // Verwerfen ist eine destruktive Aktion — darf nur, wer auch Belege validieren darf
        // (darfScannen). Read-only-Konten wie Steuerberater-Accounts duerfen nicht loeschen.
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean ok = belegService.deleteBeleg(id);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ===================== Steuerberater-Beleg-Export (Issue #58) =====================

    /**
     * Liefert eine flache Liste validierter Belege im Zeitraum fuer den
     * Steuerberater. Eine Zeile pro Beleg, mit Firma-Anteilen bei TEILWEISE
     * statt der Gesamt-Belegsummen. Das Frontend rendert daraus eine HTML-
     * Tabelle im SteuerberaterBelegExportModal und sendet sie per E-Mail.
     *
     * Auth: Session (PC) ueber {@code resolveCaller} + {@code darfSehen}.
     * Kein Mobile-Spiegel — der Export wird vom Buchhalter am PC erstellt.
     */
    @GetMapping("/steuerberater-export")
    public ResponseEntity<?> steuerberaterExport(
            @RequestParam(value = "von", required = false) String von,
            @RequestParam(value = "bis", required = false) String bis,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Strenge Validierung: ein nicht-leerer, aber unparsebarer String darf
        // NICHT silent zu null werden — sonst wuerde ein Tippfehler im Datum dazu
        // fuehren, dass der Export auf den gesamten Datenbestand fallback'ed.
        if (von != null && !von.isBlank() && parseDate(von) == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "'von' ist kein gueltiges Datum (YYYY-MM-DD)"));
        }
        if (bis != null && !bis.isBlank() && parseDate(bis) == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "'bis' ist kein gueltiges Datum (YYYY-MM-DD)"));
        }
        LocalDate vonDate = parseDate(von);
        LocalDate bisDate = parseDate(bis);
        if (vonDate != null && bisDate != null && bisDate.isBefore(vonDate)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "'bis' liegt vor 'von'"));
        }
        return ResponseEntity.ok(belegService.listeFuerSteuerberaterExport(vonDate, bisDate));
    }

    // ===================== Kassenbuch =====================

    @GetMapping("/kassenbuch")
    public ResponseEntity<?> kassenbuch(
            @RequestParam(value = "von", required = false) String von,
            @RequestParam(value = "bis", required = false) String bis,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = resolveCaller(token, auth);
        if (caller == null || !belegService.darfSehen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        LocalDate vonDate = parseDate(von);
        LocalDate bisDate = parseDate(bis);
        return ResponseEntity.ok(belegService.getKassenbuch(vonDate, bisDate));
    }

    // ===================== Helpers =====================

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
