package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.dto.Telefon.AbgehoertDto;
import org.example.kalkulationsprogramm.dto.Telefon.AbholErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufKontaktUeberblickDto;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufenDto;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktRufnummerDto;
import org.example.kalkulationsprogramm.dto.Telefon.SprachnachrichtDto;
import org.example.kalkulationsprogramm.dto.Telefon.SteuerberaterAuswahlDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonAnrufDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonBerechtigungDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonStatusDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonZuordnenDto;
import org.example.kalkulationsprogramm.dto.Telefon.WaehlTelefonDto;
import org.example.kalkulationsprogramm.service.telefon.AnrufKontaktUeberblickService;
import org.example.kalkulationsprogramm.service.telefon.TelefonAbholService;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonBerechtigungService;
import org.example.kalkulationsprogramm.service.telefon.TelefonEinstellungenService;
import org.example.kalkulationsprogramm.service.telefon.TelefonLiveService;
import org.example.kalkulationsprogramm.service.telefon.TelefonService;
import org.example.kalkulationsprogramm.service.telefon.TelefonWaehlService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Anrufliste und Anrufbeantworter. Alle Endpunkte außer {@code /berechtigung}
 * und dem Lesen gemerkter Rufnummern verlangen das Abteilungs-Recht
 * "Anrufe & Anrufbeantworter".
 */
@Slf4j
@RestController
@RequestMapping("/api/telefon")
@RequiredArgsConstructor
public class TelefonController {

    private final TelefonService telefonService;
    private final TelefonBerechtigungService berechtigung;
    private final TelefonEinstellungenService einstellungen;
    private final TelefonAbholService abholService;
    private final TelefonLiveService liveService;
    private final AnrufKontaktUeberblickService kontaktUeberblick;
    private final TelefonWaehlService waehlService;

    @GetMapping("/berechtigung")
    public TelefonBerechtigungDto berechtigung(Authentication authentication) {
        return new TelefonBerechtigungDto(berechtigung.darfTelefonSehen(authentication));
    }

    /**
     * Live-Strom für das Anruf-Fenster (Server-Sent Events). Läuft ohne
     * Open-in-View (siehe OpenEntityManagerInViewConfig), sonst hielte jede
     * offene Verbindung eine DB-Verbindung fest.
     */
    @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live(Authentication authentication) {
        FrontendUserProfile profil = berechtigung.verlange(authentication);
        return liveService.verbinde(profil.getId());
    }

    @GetMapping("/status")
    public TelefonStatusDto status(Authentication authentication) {
        berechtigung.verlange(authentication);
        return new TelefonStatusDto(
                einstellungen.istAktiv() && einstellungen.zugang().isPresent(),
                einstellungen.anrufbeantworter(),
                einstellungen.letzteAbholung(),
                einstellungen.letzterFehler(),
                telefonService.anzahlNeueSprachnachrichten());
    }

    @GetMapping("/anrufe")
    public Page<TelefonAnrufDto> anrufe(@RequestParam(required = false) TelefonAnrufArt art,
                                        @RequestParam(defaultValue = "false") boolean nurUnbekannt,
                                        @RequestParam(defaultValue = "false") boolean nurOffen,
                                        @RequestParam(required = false) String suche,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tag,
                                        @RequestParam(required = false) String kontaktart,
                                        @RequestParam(required = false) Long kundeId,
                                        @RequestParam(required = false) Long lieferantId,
                                        @RequestParam(defaultValue = "0") int seite,
                                        @RequestParam(defaultValue = "50") int groesse,
                                        Authentication authentication) {
        berechtigung.verlange(authentication);
        if (nurOffen) {
            return telefonService.offeneVerpassteAnrufe(seite, groesse);
        }
        return telefonService.anrufe(art, nurUnbekannt, suche, tag, kontaktart, kundeId, lieferantId, seite, groesse);
    }

    /**
     * Anruf von Hand zuordnen. Mit Steuerberater + Ansprechpartner + „Nummer merken"
     * füllt das ein leeres Telefonfeld des Ansprechpartners (bewusste, eng begrenzte
     * Ausnahme für Nutzer mit Telefon-Recht, siehe RufnummernZuordnungService).
     */
    @PostMapping("/anrufe/{id}/zuordnung")
    public TelefonAnrufDto anrufZuordnen(@PathVariable Long id, @RequestBody TelefonZuordnenDto dto,
                                         Authentication authentication) {
        berechtigung.verlange(authentication);
        return telefonService.ordneAnrufZu(id, dto);
    }

    @DeleteMapping("/anrufe/{id}/zuordnung")
    public TelefonAnrufDto anrufZuordnungAufheben(@PathVariable Long id, Authentication authentication) {
        berechtigung.verlange(authentication);
        return telefonService.hebeAnrufZuordnungAuf(id);
    }

    @GetMapping("/sprachnachrichten")
    public List<SprachnachrichtDto> sprachnachrichten(@RequestParam(defaultValue = "false") boolean nurNeue,
                                                      @RequestParam(required = false) Integer anrufbeantworter,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tag,
                                                      @RequestParam(required = false) Long kundeId,
                                                      @RequestParam(required = false) Long lieferantId,
                                                      Authentication authentication) {
        berechtigung.verlange(authentication);
        return telefonService.sprachnachrichten(nurNeue, anrufbeantworter, tag, kundeId, lieferantId);
    }

    @GetMapping("/sprachnachrichten/anzahl-neu")
    public Map<String, Long> anzahlNeu(Authentication authentication) {
        berechtigung.verlange(authentication);
        return Map.of("anzahl", telefonService.anzahlNeueSprachnachrichten());
    }

    /** Aufnahme als WAV; Spring beantwortet Range-Anfragen (Spulen) automatisch. */
    @GetMapping("/sprachnachrichten/{id}/audio")
    public ResponseEntity<Resource> audio(@PathVariable Long id, Authentication authentication) {
        berechtigung.verlange(authentication);
        Path pfad = telefonService.audio(id);
        if (!Files.isRegularFile(pfad)) {
            throw new NoSuchElementException("Aufnahme nicht gefunden");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(pfad));
    }

    @PatchMapping("/sprachnachrichten/{id}")
    public SprachnachrichtDto abgehoert(@PathVariable Long id, @RequestBody AbgehoertDto dto,
                                        Authentication authentication) {
        FrontendUserProfile profil = berechtigung.verlange(authentication);
        return telefonService.setzeAbgehoert(id, dto.abgehoert(), profil);
    }

    @PostMapping("/sprachnachrichten/{id}/zuordnung")
    public SprachnachrichtDto nachrichtZuordnen(@PathVariable Long id, @RequestBody TelefonZuordnenDto dto,
                                                Authentication authentication) {
        berechtigung.verlange(authentication);
        return telefonService.ordneNachrichtZu(id, dto);
    }

    @DeleteMapping("/sprachnachrichten/{id}/zuordnung")
    public SprachnachrichtDto nachrichtZuordnungAufheben(@PathVariable Long id, Authentication authentication) {
        berechtigung.verlange(authentication);
        return telefonService.hebeNachrichtZuordnungAuf(id);
    }

    @PostMapping("/abholen")
    public AbholErgebnisDto abholen(Authentication authentication) {
        berechtigung.verlange(authentication);
        return abholService.abholen();
    }

    /** "Weitere Rufnummern" eines Kontakts – Kontaktdaten, für alle angemeldeten Benutzer lesbar. */
    @GetMapping("/kontakt-rufnummern")
    public List<KontaktRufnummerDto> kontaktRufnummern(@RequestParam(required = false) Long kundeId,
                                                       @RequestParam(required = false) Long lieferantId) {
        if ((kundeId == null) == (lieferantId == null)) {
            throw new IllegalArgumentException("Bitte genau kundeId oder lieferantId angeben.");
        }
        return telefonService.kontaktRufnummern(kundeId, lieferantId);
    }

    /** Überblick über den Anrufer für das Anruf-Fenster: Adresse, Projekte, Anfragen. */
    @GetMapping("/kontakt-ueberblick")
    public AnrufKontaktUeberblickDto kontaktUeberblick(@RequestParam(required = false) Long kundeId,
                                                       @RequestParam(required = false) Long lieferantId,
                                                       @RequestParam(required = false) Long steuerberaterId,
                                                       Authentication authentication) {
        berechtigung.verlange(authentication);
        return kontaktUeberblick.ueberblick(kundeId, lieferantId, steuerberaterId);
    }

    /** Kanzleien zur Auswahl beim Zuordnen eines Anrufs. */
    @GetMapping("/steuerberater")
    public List<SteuerberaterAuswahlDto> steuerberater(Authentication authentication) {
        berechtigung.verlange(authentication);
        return telefonService.steuerberaterAuswahl();
    }

    @DeleteMapping("/kontakt-rufnummern/{id}")
    public ResponseEntity<Void> kontaktRufnummerLoeschen(@PathVariable Long id, Authentication authentication) {
        berechtigung.verlange(authentication);
        telefonService.loescheKontaktRufnummer(id);
        return ResponseEntity.noContent().build();
    }

    /** Telefone, die beim Anrufen aus dem ERP klingeln können – Auswahl "Telefon an diesem Rechner". */
    @GetMapping("/telefone")
    public List<WaehlTelefonDto> telefone(Authentication authentication) {
        berechtigung.verlange(authentication);
        return waehlService.telefone().stream().map(WaehlTelefonDto::new).toList();
    }

    /** Zurückrufen: erst klingelt {@code telefon}, nach dem Abnehmen wählt die Anlage {@code nummer}. */
    @PostMapping("/anrufen")
    public ResponseEntity<Void> anrufen(@RequestBody AnrufenDto dto, Authentication authentication) {
        FrontendUserProfile profil = berechtigung.verlange(authentication);
        waehlService.anrufen(dto.telefon(), dto.nummer());
        // Nachvollziehbar, wer Gespräche auf dem Firmenanschluss startet – ohne Nummer und Telefonnamen (DSGVO).
        log.info("Telefon: Anruf über Wählhilfe gestartet von Profil {}", profil.getId());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(TelefonAnlageException.class)
    public ResponseEntity<Map<String, String>> anlagenFehler(TelefonAnlageException e) {
        HttpStatus status = switch (e.getGrund()) {
            case NICHT_EINGERICHTET, BESCHAEFTIGT -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> ungueltig(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> nichtGefunden(NoSuchElementException e) {
        return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
    }
}
