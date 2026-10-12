package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.EmailSignatureImage;
import org.example.kalkulationsprogramm.dto.Email.EmailSignaturDto;
import org.example.kalkulationsprogramm.dto.Email.EmailSignaturSpeichernRequest;
import org.example.kalkulationsprogramm.service.EmailSignatureService;
import org.example.kalkulationsprogramm.service.EmailSignaturVerwaltungService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * E-Mail-Signaturen. Benutzer sehen und bearbeiten nur ihre eigenen Signaturen, Admins alle
 * (siehe {@link EmailSignaturVerwaltungService}). Fremde Signaturen beantwortet jeder Endpoint
 * mit 404. Die eigene Standard-Signatur sieht ein Benutzer immer; gehört sie ihm nicht, lehnen
 * Ändern und Löschen mit 400 ab. Firmen-Vorlagen sehen alle, festlegen dürfen sie nur Admins.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/email/signatures")
public class EmailSignatureController {

    /** Bildtypen, die als Bild ausgeliefert werden (kein SVG – das kann Skripte enthalten). */
    private static final Set<String> ERLAUBTE_BILDTYPEN = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final EmailSignatureService service;
    private final EmailSignaturVerwaltungService verwaltung;

    @GetMapping
    public List<EmailSignaturDto> list(Authentication authentication) {
        return verwaltung.liste(verwaltung.benutzer(authentication));
    }

    /**
     * Standard-Signatur zum Vorbelegen beim Schreiben einer Mail. Benutzer bekommen immer ihre
     * eigene; nur Admins dürfen per Parameter nach der eines anderen Benutzers fragen.
     */
    @GetMapping("/default")
    public ResponseEntity<EmailSignaturDto> getDefault(@RequestParam(name = "frontendUserId", required = false) Long frontendUserId,
                                                       @RequestParam(name = "frontendUserName", required = false) String frontendUserName,
                                                       Authentication authentication) {
        return verwaltung.standardSignatur(verwaltung.benutzer(authentication), frontendUserId, frontendUserName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** Firmen-Vorlagen mit Formulardaten – Ausgangspunkt für die Signaturen aller Benutzer. */
    @GetMapping("/firmen-vorlagen")
    public List<EmailSignaturDto> firmenVorlagen(Authentication authentication) {
        return verwaltung.firmenVorlagen(verwaltung.benutzer(authentication));
    }

    /** Macht die Signatur zur Firmen-Vorlage. Nur Admin (SecurityConfig). */
    @PutMapping("/{id}/firmen-vorlage")
    public ResponseEntity<?> alsFirmenVorlage(@PathVariable Long id, Authentication authentication) {
        return firmenVorlageSetzen(id, true, authentication);
    }

    /** Die Signatur ist danach keine Firmen-Vorlage mehr. Nur Admin (SecurityConfig). */
    @DeleteMapping("/{id}/firmen-vorlage")
    public ResponseEntity<?> keineFirmenVorlage(@PathVariable Long id, Authentication authentication) {
        return firmenVorlageSetzen(id, false, authentication);
    }

    private ResponseEntity<?> firmenVorlageSetzen(Long id, boolean firmenVorlage, Authentication authentication) {
        try {
            return ResponseEntity.ok(verwaltung.alsFirmenVorlage(id, firmenVorlage, verwaltung.benutzer(authentication)));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * Liefert die aktuelle System-Signatur (fuer automatische Mails). Im UI
     * wird damit angezeigt, welche Signatur an Auto-AB / Mahnung angehaengt
     * wird — und ob der Inhaber noch den Platzhalter editieren muss.
     */
    @GetMapping("/system-default")
    public ResponseEntity<Map<String, Object>> getSystemDefaultRaw(Authentication authentication) {
        return verwaltung.systemSignatur(verwaltung.benutzer(authentication))
                .map(sig -> ResponseEntity.ok(Map.<String, Object>of(
                        "signature", sig,
                        "isPlatzhalter", EmailSignatureService.isPlatzhalterHtml(sig.html())
                )))
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Markiert die angegebene Signatur als System-Default. Andere Signaturen
     * verlieren das Flag automatisch (siehe Service). Nur Admin (SecurityConfig).
     */
    @PutMapping("/{id}/system-default")
    public ResponseEntity<EmailSignaturDto> setSystemDefault(@PathVariable Long id, Authentication authentication) {
        try {
            return ResponseEntity.ok(verwaltung.alsSystemSignatur(id, verwaltung.benutzer(authentication)));
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Macht die Signatur zur Standard-Signatur des angemeldeten Benutzers. */
    @PutMapping("/{id}/standard")
    public ResponseEntity<?> alsStandard(@PathVariable Long id, Authentication authentication) {
        try {
            return ResponseEntity.ok(verwaltung.alsStandard(id, verwaltung.benutzer(authentication)));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /** Der angemeldete Benutzer hat danach keine Standard-Signatur mehr. */
    @DeleteMapping("/standard")
    public ResponseEntity<Void> standardEntfernen(Authentication authentication) {
        verwaltung.standardEntfernen(verwaltung.benutzer(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody EmailSignaturSpeichernRequest req, Authentication authentication) {
        try {
            return ResponseEntity.ok(verwaltung.speichern(req, verwaltung.benutzer(authentication)));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Map<String, String>> preview(@PathVariable Long id,
                                                       @RequestParam(name = "user", required = false) String user,
                                                       Authentication authentication) {
        return verwaltung.sichtbare(id, verwaltung.benutzer(authentication))
                .map(s -> ResponseEntity.ok(Map.of("html", service.renderSignatureHtmlForPreview(s, user))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailSignaturDto> get(@PathVariable Long id, Authentication authentication) {
        return verwaltung.laden(id, verwaltung.benutzer(authentication))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Bild einer alten Signatur (Datei-Upload aus der Zeit vor dem Signatur-Studio). Nur echte
     * Bildtypen werden als Bild ausgeliefert, alles andere als Download – und nie als Seite, die
     * im ERP Skripte ausführen könnte.
     */
    @GetMapping("/{id}/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable Long imageId, Authentication authentication) {
        Optional<EmailSignature> sig = verwaltung.sichtbare(id, verwaltung.benutzer(authentication));
        if (sig.isEmpty()) return ResponseEntity.notFound().build();
        Optional<EmailSignatureImage> img = service.findImage(id, imageId);
        if (img.isEmpty()) return ResponseEntity.notFound().build();
        var pathOpt = service.resolveImagePath(img.get());
        if (pathOpt.isEmpty()) return ResponseEntity.notFound().build();
        try {
            byte[] bytes = Files.readAllBytes(pathOpt.get());
            String typ = Optional.ofNullable(img.get().getContentType()).orElse("").trim().toLowerCase(Locale.ROOT);
            boolean bild = ERLAUBTE_BILDTYPEN.contains(typ);
            ResponseEntity.BodyBuilder antwort = ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, bild ? typ : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "sandbox; default-src 'none'");
            if (!bild) {
                antwort.header(HttpHeaders.CONTENT_DISPOSITION, "attachment");
            }
            return antwort.body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        try {
            verwaltung.loeschen(id, verwaltung.benutzer(authentication));
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<?> deleteImage(@PathVariable Long id, @PathVariable Long imageId, Authentication authentication) {
        try {
            verwaltung.bearbeitbare(id, verwaltung.benutzer(authentication));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
        try {
            service.deleteImage(id, imageId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
