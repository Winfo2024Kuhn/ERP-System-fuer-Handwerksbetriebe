package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.KassenbuchAbschlussDto;
import org.example.kalkulationsprogramm.dto.KassenzaehlungDto;
import org.example.kalkulationsprogramm.service.BelegAuditChainVerifier;
import org.example.kalkulationsprogramm.service.BelegAuditService;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.KassenbuchAbschlussService;
import org.example.kalkulationsprogramm.service.KassenbuchGesperrtException;
import org.example.kalkulationsprogramm.service.KasseUnterdeckungException;
import org.example.kalkulationsprogramm.service.KassenzaehlungService;
import org.example.kalkulationsprogramm.service.VerfahrensdokumentationService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alles, was das Kassenbuch rechtssicher macht: Monatsabschluss,
 * Kassensturz, Storno und die Pruefung des Protokolls.
 *
 * <p>Bewusst ein eigener Controller neben {@link BelegController}: dort geht
 * es um einzelne Belege, hier um das Kassenbuch als Ganzes. Alle Endpunkte
 * verlangen Session-Auth am PC -- diese Vorgaenge werden nicht am Handy
 * ausgeloest.</p>
 *
 * <p>Berechtigung: schreibende Vorgaenge brauchen {@code darfScannen} -- das
 * ist im bestehenden System die Rolle, die auch Belege validieren darf.
 * Reine Lesezugriffe (Verlauf, Pruefung) reichen mit {@code darfSehen},
 * damit ein Steuerberater-Zugang die Kette pruefen kann, ohne etwas
 * aendern zu duerfen.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/buchhaltung/kassenbuch")
@RequiredArgsConstructor
public class KassenbuchController {

    private final BelegService belegService;
    private final KassenbuchAbschlussService abschlussService;
    private final KassenzaehlungService zaehlungService;
    private final BelegAuditService auditService;
    private final BelegAuditChainVerifier verifier;
    private final VerfahrensdokumentationService verfahrensdokumentationService;

    // ===================== Monatsabschluss =====================

    /** Was ein Abschluss bewirken wuerde und was ihn noch blockiert. */
    @GetMapping("/abschluss/vorschau")
    public ResponseEntity<?> vorschau(
            @RequestParam int jahr,
            @RequestParam int monat,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = leser(token, auth);
        if (caller == null) return verboten();
        try {
            return ResponseEntity.ok(abschlussService.vorschau(jahr, monat));
        } catch (IllegalArgumentException e) {
            return fehler(e);
        }
    }

    @GetMapping("/abschluesse")
    public ResponseEntity<?> listeAbschluesse(
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = leser(token, auth);
        if (caller == null) return verboten();
        return ResponseEntity.ok(abschlussService.listeAbschluesse());
    }

    /**
     * Schliesst den Monat ab: vergibt die laufenden Nummern und schreibt die
     * Belege fest. Nicht umkehrbar -- das Frontend fragt vorher nach.
     */
    @PostMapping("/abschluss")
    public ResponseEntity<?> schliesseAb(
            @RequestBody KassenbuchAbschlussDto.AbschlussRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = schreiber(token, auth);
        if (caller == null) return verboten();
        if (req == null || req.getJahr() == null || req.getMonat() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Jahr und Monat fehlen"));
        }
        try {
            return ResponseEntity.ok(abschlussService.schliesseMonatAb(
                    req.getJahr(), req.getMonat(), req.getBemerkung(), caller, null));
        } catch (KassenbuchGesperrtException e) {
            return gesperrt(e);
        } catch (IllegalArgumentException e) {
            return fehler(e);
        }
    }

    // ===================== Storno =====================

    /**
     * Hebt einen festgeschriebenen Beleg durch eine Gegenbuchung auf. Das
     * Original bleibt sichtbar im Kassenbuch stehen.
     */
    @PostMapping("/belege/{id}/storno")
    public ResponseEntity<?> storniere(
            @PathVariable Long id,
            @RequestBody KassenbuchAbschlussDto.StornoRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = schreiber(token, auth);
        if (caller == null) return verboten();
        try {
            Beleg storno = abschlussService.storniere(
                    id, req != null ? req.getGrund() : null, caller, null);
            return ResponseEntity.ok(belegService.toDto(storno));
        } catch (KassenbuchGesperrtException e) {
            return gesperrt(e);
        } catch (KasseUnterdeckungException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", e.getMessage(),
                    "projizierterSaldo", e.getProjizierterSaldo(),
                    "mindestbestand", e.getMindestbestand()));
        } catch (IllegalArgumentException e) {
            return fehler(e);
        }
    }

    // ===================== Kassensturz =====================

    @PostMapping("/zaehlungen")
    public ResponseEntity<?> zaehle(
            @RequestBody KassenzaehlungDto.ZaehlRequest req,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = schreiber(token, auth);
        if (caller == null) return verboten();
        try {
            return ResponseEntity.ok(zaehlungService.zaehle(req, caller, null));
        } catch (KassenbuchGesperrtException e) {
            return gesperrt(e);
        } catch (IllegalArgumentException e) {
            return fehler(e);
        }
    }

    @GetMapping("/zaehlungen")
    public ResponseEntity<?> listeZaehlungen(
            @RequestParam(value = "von", required = false) String von,
            @RequestParam(value = "bis", required = false) String bis,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = leser(token, auth);
        if (caller == null) return verboten();
        LocalDate vonDate = parseDate(von);
        LocalDate bisDate = parseDate(bis);
        if (von != null && !von.isBlank() && vonDate == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "'von' ist kein gültiges Datum (JJJJ-MM-TT)"));
        }
        if (bis != null && !bis.isBlank() && bisDate == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "'bis' ist kein gültiges Datum (JJJJ-MM-TT)"));
        }
        return ResponseEntity.ok(zaehlungService.liste(vonDate, bisDate));
    }

    /** Was laut Kassenbuch gerade in der Lade liegen muesste -- Vorgabe fuers Zaehl-Formular. */
    @GetMapping("/zaehlungen/erwartet")
    public ResponseEntity<?> erwarteterBestand(
            @RequestParam(value = "stichtag", required = false) String stichtag,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = leser(token, auth);
        if (caller == null) return verboten();
        LocalDate tag = parseDate(stichtag);
        if (stichtag != null && !stichtag.isBlank() && tag == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Stichtag ist kein gültiges Datum"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stichtag", tag != null ? tag : LocalDate.now());
        body.put("rechnerischerBestand", zaehlungService.rechnerischerBestand(tag));
        return ResponseEntity.ok(body);
    }

    // ===================== Protokoll =====================

    /** Verlauf eines einzelnen Belegs -- wer hat wann was geaendert. */
    @GetMapping("/belege/{id}/verlauf")
    public ResponseEntity<?> verlauf(
            @PathVariable Long id,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = leser(token, auth);
        if (caller == null) return verboten();
        return ResponseEntity.ok(auditService.getHistorie(id));
    }

    /**
     * Rechnet die komplette Protokollkette nach. Ein intakter Bericht ist der
     * Nachweis, dass am Kassenbuch nichts nachtraeglich veraendert wurde.
     */
    @GetMapping("/pruefung")
    public ResponseEntity<?> pruefung(
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = leser(token, auth);
        if (caller == null) return verboten();

        BelegAuditChainVerifier.Bericht b = verifier.verify();
        List<String> fehler = b.getFehler().stream()
                .map(f -> "Kettenposition " + f.chainIndex()
                        + (f.laufendeNummer() != null ? " (Beleg Nr. " + f.laufendeNummer() + ")" : "")
                        + ": " + f.beschreibung())
                .toList();

        String zusammenfassung = b.isIntakt()
                ? ("Das Kassenbuch-Protokoll ist unversehrt. " + b.getGesamtAnzahl()
                   + " Einträge wurden geprüft, die Prüfsummen stimmen lückenlos überein.")
                : ("Das Kassenbuch-Protokoll ist an mindestens einer Stelle verändert worden. "
                   + "Bitte sofort den Steuerberater informieren.");

        return ResponseEntity.ok(KassenbuchAbschlussDto.PruefErgebnis.builder()
                .intakt(b.isIntakt())
                .geprueftEintraege(b.getGesamtAnzahl())
                .letzteKettenposition(b.getLetzterChainIndex())
                .letztePruefsumme(b.getLetzterEntryHash())
                .fehler(fehler)
                .zusammenfassung(zusammenfassung)
                .build());
    }

    // ===================== Verfahrensdokumentation =====================

    /**
     * Die Verfahrensdokumentation zum Herunterladen und Abheften.
     *
     * <p>Die GoBD verlangen eine Beschreibung, wie der Betrieb Belege
     * erfasst und verarbeitet. Fehlt sie, kann ein Pruefer die Buchfuehrung
     * als formell mangelhaft einstufen, selbst wenn inhaltlich alles
     * stimmt. Der Text wird aus dem Code erzeugt und beschreibt damit
     * immer genau das Verfahren, das die Software tatsaechlich anwendet.</p>
     */
    @GetMapping("/verfahrensdokumentation")
    public ResponseEntity<?> verfahrensdokumentation(
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = leser(token, auth);
        if (caller == null) return verboten();
        String text = verfahrensdokumentationService.erzeugeText();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("Verfahrensdokumentation.txt").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(text);
    }

    // ===================== Helfer =====================

    /** Aufrufer, der Belege aendern darf. */
    private Mitarbeiter schreiber(String token, Authentication auth) {
        Mitarbeiter m = belegService.findCaller(token, auth);
        return (m != null && belegService.darfScannen(m)) ? m : null;
    }

    /** Aufrufer, der Belege sehen darf -- reicht fuer Verlauf und Pruefung. */
    private Mitarbeiter leser(String token, Authentication auth) {
        Mitarbeiter m = belegService.findCaller(token, auth);
        return (m != null && belegService.darfSehen(m)) ? m : null;
    }

    private ResponseEntity<Map<String, String>> verboten() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Keine Berechtigung für das Kassenbuch"));
    }

    private ResponseEntity<Map<String, String>> fehler(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", e.getMessage() != null ? e.getMessage() : "Ungültige Eingabe"));
    }

    private ResponseEntity<Map<String, String>> gesperrt(KassenbuchGesperrtException e) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", e.getMessage() != null ? e.getMessage() : "Vorgang gesperrt");
        if (e.getLoesungshinweis() != null) {
            body.put("hinweis", e.getLoesungshinweis());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
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
