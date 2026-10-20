package org.example.kalkulationsprogramm.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.StornoAntwortDto;
import org.example.kalkulationsprogramm.dto.Freigabe.AbzuloesendeFreigabeDto;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAuditDto;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeStatusKurzDto;
import org.example.kalkulationsprogramm.domain.Mahnstufe;
import org.example.kalkulationsprogramm.config.ClientIpResolver;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentAuditService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.AutoMahnVersandService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.FreigabeAbloesungService;
import org.example.kalkulationsprogramm.service.DatensatzLockService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST Controller für Ausgangs-Geschäftsdokumente.
 * Unterstützt: Erstellen, Aktualisieren, Buchen, Stornieren, Export.
 *
 * <p>Der Bearbeiter im GoBD-Audit (Erstellen, Buchen, Versand, Storno, Löschen)
 * kommt immer aus der Anmeldung ({@link FrontendUserPrincipal#idAus}), nie vom
 * Client — sonst könnte jeder einen fremden Namen ins Protokoll schreiben.
 * Die IP-Adresse liefert aus demselben Grund der {@link ClientIpResolver}.</p>
 */
@RestController
@RequestMapping("/api/ausgangs-dokumente")
@RequiredArgsConstructor
public class AusgangsGeschaeftsDokumentController {

    private static final Logger log = LoggerFactory.getLogger(AusgangsGeschaeftsDokumentController.class);

    private final AusgangsGeschaeftsDokumentService service;
    private final DokumentFreigabeService dokumentFreigabeService;
    private final FreigabeAbloesungService freigabeAbloesungService;
    private final AusgangsGeschaeftsDokumentAuditService auditService;
    private final AutoMahnVersandService autoMahnVersandService;
    private final DatensatzLockService dokumentLockService;
    private final ClientIpResolver clientIpResolver;

    /**
     * Reine Vorschau: rendert die Mahn-PDF einer beliebigen Stufe ohne irgendetwas
     * zu persistieren oder zu versenden. Wird vom DokumentUebersichtEditor benutzt,
     * damit der Sachbearbeiter pro Rechnung kontrollieren kann, wie eine
     * Zahlungserinnerung / 1. / 2. Mahnung konkret aussehen würde — bevor das
     * automatische Mahnverfahren überhaupt scharf geschaltet wird.
     */
    @GetMapping("/{id}/mahnung-vorschau")
    public ResponseEntity<byte[]> mahnungVorschau(@PathVariable Long id, @RequestParam("stufe") Mahnstufe stufe) {
        try {
            byte[] pdf = autoMahnVersandService.generiereVorschauPdfFuerAusgangsRechnung(id, stufe);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=mahn-vorschau-" + id + ".pdf")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            log.warn("Mahn-Vorschau fuer Dokument {} fehlgeschlagen: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Liefert pro AusgangsGeschaeftsDokument-ID die jüngste relevante digitale Freigabe.
     * Wird vom Frontend genutzt, um Status-Badges (z.B. "Angenommen") direkt an Dokument-
     * Karten zu hängen.
     */
    @GetMapping("/freigabe-status")
    public ResponseEntity<java.util.Map<Long, FreigabeStatusKurzDto>> freigabeStatus(@RequestParam("ids") List<Long> ids) {
        var byDokumentId = dokumentFreigabeService.findJuengsteProQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, ids);
        java.util.Map<Long, FreigabeStatusKurzDto> result = new java.util.HashMap<>();
        byDokumentId.forEach((dokumentId, freigabe) -> result.put(dokumentId, FreigabeStatusKurzDto.builder()
                .status(freigabe.getStatus().name())
                .dokumentArt(freigabe.getDokumentArt())
                .dokumentNummer(freigabe.getDokumentNummer())
                .akzeptiertAm(freigabe.getAkzeptiertAm())
                .ablaufDatum(freigabe.getAblaufDatum())
                .erstelltAm(freigabe.getErstelltAm())
                .build()));
        return ResponseEntity.ok(result);
    }

    /**
     * Liefert den vollständigen Audit-Trail einer akzeptierten Freigabe (E-Mail, IP,
     * Zeitstempel, Hash). Wird im Frontend on-demand beim Klick auf den
     * „Angenommen"-Badge geladen — bewusst nicht in der Listen-API, damit personen-
     * bezogene Daten (IP, User-Agent) nicht ungefragt mit jeder Übersicht ausgeliefert werden.
     */
    @GetMapping("/{id}/freigabe-audit")
    public ResponseEntity<FreigabeAuditDto> freigabeAudit(@PathVariable Long id) {
        return dokumentFreigabeService
                .findAuditByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Offene Online-Links, die beim Versand dieses Angebots ungültig werden: ältere Links
     * desselben Angebots und Links anderer Angebote im selben Projekt bzw. derselben Anfrage. Der Editor fragt damit vor dem Versand nach;
     * zurückgezogen werden sie erst beim tatsächlichen Senden ({@code /api/emails/send}).
     */
    @GetMapping("/{id}/abzuloesende-freigaben")
    public ResponseEntity<List<AbzuloesendeFreigabeDto>> abzuloesendeFreigaben(@PathVariable Long id) {
        return ResponseEntity.ok(freigabeAbloesungService.findeAbzuloesendeFreigaben(id));
    }

    /**
     * Einzelnes Dokument abrufen.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AusgangsGeschaeftsDokumentResponseDto> getById(@PathVariable Long id) {
        AusgangsGeschaeftsDokumentResponseDto dto = service.findById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * Alle Dokumente für ein Projekt abrufen.
     */
    @GetMapping("/projekt/{projektId}")
    public ResponseEntity<List<AusgangsGeschaeftsDokumentResponseDto>> getByProjekt(@PathVariable Long projektId) {
        return ResponseEntity.ok(service.findByProjekt(projektId));
    }

    /**
     * Alle Dokumente für ein Anfrage abrufen.
     */
    @GetMapping("/anfrage/{anfrageId}")
    public ResponseEntity<List<AusgangsGeschaeftsDokumentResponseDto>> getByAnfrage(@PathVariable Long anfrageId) {
        return ResponseEntity.ok(service.findByAnfrage(anfrageId));
    }

    /**
     * Zuletzt geänderte, vom User bearbeitete Rechnungsanschrift des Projekts.
     * Der Dokument-Editor zeigt sie beim Erstellen eines neuen Dokuments an,
     * damit vor dem ersten Speichern keine veraltete Kundenadresse zu sehen ist.
     * Wert ist {@code null}, wenn kein Dokument eine bearbeitete Anschrift hat.
     */
    @GetMapping("/projekt/{projektId}/geerbte-rechnungsadresse")
    public ResponseEntity<Map<String, String>> getGeerbteRechnungsadresseByProjekt(@PathVariable Long projektId) {
        String adresse = service.findGeerbteRechnungsadresse(projektId, null);
        return ResponseEntity.ok(Collections.singletonMap("rechnungsadresseOverride", adresse));
    }

    /**
     * Pendant zu {@link #getGeerbteRechnungsadresseByProjekt} für Anfragen;
     * berücksichtigt auch das ggf. mit der Anfrage verknüpfte Projekt.
     */
    @GetMapping("/anfrage/{anfrageId}/geerbte-rechnungsadresse")
    public ResponseEntity<Map<String, String>> getGeerbteRechnungsadresseByAnfrage(@PathVariable Long anfrageId) {
        String adresse = service.findGeerbteRechnungsadresseFuerAnfrage(anfrageId);
        return ResponseEntity.ok(Collections.singletonMap("rechnungsadresseOverride", adresse));
    }

    /**
     * Neues Dokument erstellen.
     *
     * Vergibt zusaetzlich direkt das Soft-Lock fuer den erstellenden User —
     * sonst scheitert das unmittelbar folgende PUT mit 409, weil der
     * Page-Level-Lock-Hook die neu generierte ID erst nach einem Reload sieht.
     */
    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody AusgangsGeschaeftsDokumentErstellenDto dto,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest request) {
        AusgangsGeschaeftsDokument created;
        try {
            // Ersteller kommt aus der Anmeldung, nie aus dem Request (GoBD-Audit).
            created = service.erstellen(dto, FrontendUserPrincipal.idAus(authentication), clientIp(request));
        } catch (ResponseStatusException e) {
            // Fachliche Meldung mit Status (z. B. Gutschrift-Regeln): als JSON {message} über den RestExceptionHandler.
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        // Lock-Vergabe in eigenem try/catch: wenn das schiefgeht, bleibt das
        // bereits persistierte Dokument trotzdem als 200 sichtbar — das naechste
        // PUT scheitert dann sauber mit 409 statt einem irrefuehrenden 400.
        FrontendUserPrincipal principal = FrontendUserPrincipal.aus(authentication);
        if (principal != null) {
            try {
                var lockResult = dokumentLockService.acquire(
                        SperrbarerTyp.AUSGANG,
                        created.getId(),
                        principal.getId(),
                        principal.getDisplayName());
                if (!org.example.kalkulationsprogramm.dto.DatensatzLockDto.ACQUIRED.equals(lockResult.status())) {
                    log.warn("Lock-Vergabe nach Create fuer Dokument {} unerwartet: {}",
                            created.getId(), lockResult.status());
                }
            } catch (RuntimeException lockEx) {
                log.warn("Lock-Vergabe nach Create fuer Dokument {} fehlgeschlagen: {}",
                        created.getId(), lockEx.getMessage());
            }
        }
        return ResponseEntity.ok(service.findById(created.getId()));
    }

    /**
     * Dokument aktualisieren (nur wenn nicht gebucht).
     * Speichern setzt voraus, dass der Caller das Soft-Lock haelt — sonst
     * 409 Conflict, damit ein zweiter offener Tab nicht ueber die Aenderungen
     * eines anderen Users drueberschreibt.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody AusgangsGeschaeftsDokumentUpdateDto dto,
            Authentication authentication) {
        FrontendUserPrincipal principal = FrontendUserPrincipal.aus(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!dokumentLockService.isHeldBy(SperrbarerTyp.AUSGANG, id, principal.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Dokument wird gerade von einem anderen Benutzer bearbeitet.");
        }
        try {
            AusgangsGeschaeftsDokument updated = service.aktualisieren(id, dto);
            return ResponseEntity.ok(service.findById(updated.getId()));
        } catch (ObjectOptimisticLockingFailureException e) {
            // Versionskonflikt: NICHT hier abfangen, sondern an den globalen
            // RestExceptionHandler durchreichen -- der macht daraus sauber 409
            // mit der Handwerker-Meldung statt 400 wie ein gewoehnlicher Fehler.
            throw e;
        } catch (ResponseStatusException e) {
            // Fachliche Meldung mit Status (z. B. Gutschrift-Regeln): als JSON {message} über den RestExceptionHandler.
            throw e;
        } catch (RuntimeException e) {
            log.error("Fehler beim Aktualisieren von Dokument {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Dokument buchen (sperrt für Bearbeitung).
     * Wird normalerweise beim PDF-Export aufgerufen.
     */
    @PostMapping("/{id}/buchen")
    public ResponseEntity<?> buchen(@PathVariable Long id,
                                    Authentication authentication,
                                    jakarta.servlet.http.HttpServletRequest request) {
        try {
            AusgangsGeschaeftsDokument result = service.buchen(
                    id, FrontendUserPrincipal.idAus(authentication), clientIp(request));
            return ResponseEntity.ok(service.findById(result.getId()));
        } catch (ObjectOptimisticLockingFailureException e) {
            // Versionskonflikt: NICHT hier abfangen, sondern an den globalen
            // RestExceptionHandler durchreichen -- der macht daraus sauber 409
            // mit der Handwerker-Meldung statt 400 wie ein gewoehnlicher Fehler.
            throw e;
        } catch (ResponseStatusException e) {
            // Fachliche Meldung mit Status (z. B. Gutschrift-Regeln): als JSON {message} über den RestExceptionHandler.
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Dokument nach E-Mail-Versand buchen (GoBD-konform).
     * Setzt Versanddatum und sperrt das Dokument.
     */
    @PostMapping("/{id}/email-versendet")
    public ResponseEntity<?> emailVersendet(@PathVariable Long id,
                                            Authentication authentication,
                                            jakarta.servlet.http.HttpServletRequest request) {
        try {
            service.buchenNachEmailVersand(id, FrontendUserPrincipal.idAus(authentication), clientIp(request));
            return ResponseEntity.ok(service.findById(id));
        } catch (ObjectOptimisticLockingFailureException e) {
            // Versionskonflikt: NICHT hier abfangen, sondern an den globalen
            // RestExceptionHandler durchreichen -- der macht daraus sauber 409
            // mit der Handwerker-Meldung statt 400 wie ein gewoehnlicher Fehler.
            throw e;
        } catch (ResponseStatusException e) {
            // Fachliche Meldung mit Status (z. B. Gutschrift-Regeln): als JSON {message} über den RestExceptionHandler.
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PDF für ein gebuchtes Dokument speichern.
     * Wird vom Frontend nach PDF-Generierung aufgerufen, damit der Offene-Posten-Eintrag
     * direkt auf die PDF-Datei verweist statt auf den Document-Editor.
     */
    @PostMapping("/{id}/pdf-speichern")
    public ResponseEntity<?> pdfSpeichern(@PathVariable Long id, @RequestBody byte[] pdfBytes) {
        try {
            if (pdfBytes == null || pdfBytes.length == 0) {
                return ResponseEntity.badRequest().body("Keine PDF-Daten erhalten");
            }
            String dateiname = service.speicherePdfFuerDokument(id, pdfBytes);
            return ResponseEntity.ok(java.util.Map.of("dateiname", dateiname));
        } catch (ObjectOptimisticLockingFailureException e) {
            // Versionskonflikt: NICHT hier abfangen, sondern an den globalen
            // RestExceptionHandler durchreichen -- der macht daraus sauber 409
            // mit der Handwerker-Meldung statt 400 wie ein gewoehnlicher Fehler.
            throw e;
        } catch (ResponseStatusException e) {
            // Fachliche Meldung mit Status (z. B. Gutschrift-Regeln): als JSON {message} über den RestExceptionHandler.
            throw e;
        } catch (RuntimeException e) {
            log.error("Fehler beim Speichern der PDF für Dokument {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PDF für ein AusgangsGeschaeftsDokument abrufen (Fallback für alte Einträge).
     * Gibt 404 zurück wenn keine PDF gespeichert ist.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> getPdf(@PathVariable Long id) {
        return ResponseEntity.status(404).body("PDF nicht verfügbar. Bitte exportieren Sie das Dokument erneut.");
    }

    /**
     * Dokument stornieren: nur gebuchte Rechnungen (keine Stornorechnungen, keine Entwürfe).
     * Erstellt ein Storno-Gegendokument. Die Antwort enthält die Stornorechnung und
     * Hinweise zu betroffenen Schlussrechnungen (mitstorniert oder als Entwurf zu prüfen).
     */
    @PostMapping("/{id}/storno")
    public ResponseEntity<?> stornieren(@PathVariable Long id,
                                        @RequestParam(required = false) String grund,
                                        Authentication authentication,
                                        jakarta.servlet.http.HttpServletRequest request) {
        try {
            AusgangsGeschaeftsDokumentService.StornoErgebnis ergebnis = service.stornierenMitHinweisen(
                    id, FrontendUserPrincipal.idAus(authentication), clientIp(request), grund);
            return ResponseEntity.ok(new StornoAntwortDto(
                    service.findById(ergebnis.storno().getId()), ergebnis.hinweise()));
        } catch (ObjectOptimisticLockingFailureException e) {
            // Versionskonflikt: NICHT hier abfangen, sondern an den globalen
            // RestExceptionHandler durchreichen -- der macht daraus sauber 409
            // mit der Handwerker-Meldung statt 400 wie ein gewoehnlicher Fehler.
            throw e;
        } catch (ResponseStatusException e) {
            // Fachliche Meldung mit Status (z. B. Gutschrift-Regeln): als JSON {message} über den RestExceptionHandler.
            throw e;
        } catch (RuntimeException e) {
            log.error("Fehler beim Stornieren von Dokument {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Dokument löschen.
     * Nur Entwürfe (nicht gebucht, versandt, digital angenommen oder storniert) dürfen mit
     * einer Begründung gelöscht werden.
     * Schreibt vor dem Hard-Delete einen GoBD-konformen Audit-Eintrag.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestParam String begruendung,
                                    Authentication authentication,
                                    jakarta.servlet.http.HttpServletRequest request) {
        try {
            AusgangsGeschaeftsDokumentResponseDto dto = service.findById(id);
            if (dto == null) {
                return ResponseEntity.notFound().build();
            }

            String ip = clientIp(request);
            service.loeschen(id, begruendung, FrontendUserPrincipal.idAus(authentication), ip);
            return ResponseEntity.ok().build();
        } catch (ObjectOptimisticLockingFailureException e) {
            // Versionskonflikt: NICHT hier abfangen, sondern an den globalen
            // RestExceptionHandler durchreichen -- der macht daraus sauber 409
            // mit der Handwerker-Meldung statt 400 wie ein gewoehnlicher Fehler.
            throw e;
        } catch (ResponseStatusException e) {
            // Fachliche Meldung mit Status (z. B. Gutschrift-Regeln): als JSON {message} über den RestExceptionHandler.
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Audit-Historie eines Dokuments für die Steuerprüfung.
     * Liefert alle Aktionen (Erstellt, Geändert, Gebucht, Versendet, Storniert, Gelöscht)
     * mit Bearbeiter, Zeitstempel und Begründung.
     */
    @GetMapping("/{id}/historie")
    public ResponseEntity<List<java.util.Map<String, Object>>> historie(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getHistorie(id));
    }

    /**
     * Audit-Historie eines Dokuments anhand der Dokumentnummer.
     * Funktioniert auch für hard-deleted Dokumente — wichtig wenn ein Prüfer
     * eine Lücke im Nummernkreis aufdeckt.
     */
    @GetMapping("/historie/nummer/{dokumentNummer}")
    public ResponseEntity<List<java.util.Map<String, Object>>> historieByNummer(
            @PathVariable String dokumentNummer) {
        return ResponseEntity.ok(auditService.getHistorieByNummer(dokumentNummer));
    }

    /**
     * Absender-IP fürs GoBD-Audit. {@code X-Forwarded-For} zählt nur, wenn ein
     * freigegebener Proxy ihn gesetzt hat — sonst könnte der Client die IP fälschen.
     */
    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        return request == null ? null : clientIpResolver.resolve(request);
    }

    // --- Kunden-Gutschrift ---

    /**
     * Rahmen für „Gutschrift erstellen“: Rechnungsbetrag, schon gutgeschrieben und was
     * höchstens noch geht (Issue #223).
     */
    @GetMapping("/{id}/gutschrift-rahmen")
    public ResponseEntity<AusgangsGeschaeftsDokumentService.GutschriftRahmenDto> getGutschriftRahmen(
            @PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Rechnung wurde nicht gefunden.");
        }
        return ResponseEntity.ok(service.gutschriftRahmen(id));
    }

    // --- Abrechnungsverlauf ---

    /**
     * Abrechnungsverlauf für ein Basisdokument abrufen.
     * Zeigt alle bereits erstellten Rechnungen und den verbleibenden Restbetrag.
     */
    @GetMapping("/{id}/abrechnungsverlauf")
    public ResponseEntity<?> getAbrechnungsverlauf(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.getAbrechnungsverlauf(id));
        } catch (ResponseStatusException e) {
            // Fachliche Meldung mit Status (z. B. Gutschrift-Regeln): als JSON {message} über den RestExceptionHandler.
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // TODO: PDF/ZUGFeRD/XML Export Endpoints werden später implementiert
    // @PostMapping("/{id}/export/pdf")
    // @PostMapping("/{id}/export/zugferd")
    // @PostMapping("/{id}/export/xml")
}
