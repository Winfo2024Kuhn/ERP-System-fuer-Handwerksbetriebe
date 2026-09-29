package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.dto.Telefon.AbgehoertDto;
import org.example.kalkulationsprogramm.dto.Telefon.AbholErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktRufnummerDto;
import org.example.kalkulationsprogramm.dto.Telefon.SprachnachrichtDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonAnrufDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonBerechtigungDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonStatusDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonZuordnenDto;
import org.example.kalkulationsprogramm.service.telefon.TelefonAbholService;
import org.example.kalkulationsprogramm.service.telefon.TelefonBerechtigungService;
import org.example.kalkulationsprogramm.service.telefon.TelefonEinstellungenService;
import org.example.kalkulationsprogramm.service.telefon.TelefonLiveService;
import org.example.kalkulationsprogramm.service.telefon.TelefonService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Anrufliste und Anrufbeantworter. Alle Endpunkte außer {@code /berechtigung}
 * und dem Lesen gemerkter Rufnummern verlangen das Abteilungs-Recht
 * "Anrufe & Anrufbeantworter".
 */
@RestController
@RequestMapping("/api/telefon")
@RequiredArgsConstructor
public class TelefonController {

    private final TelefonService telefonService;
    private final TelefonBerechtigungService berechtigung;
    private final TelefonEinstellungenService einstellungen;
    private final TelefonAbholService abholService;
    private final TelefonLiveService liveService;

    @GetMapping("/berechtigung")
    public TelefonBerechtigungDto berechtigung(Authentication authentication) {
        return new TelefonBerechtigungDto(berechtigung.darfTelefonSehen(authentication));
    }

    /** Live-Strom für das Anruf-Fenster (Server-Sent Events). */
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
                                        @RequestParam(required = false) String suche,
                                        @RequestParam(required = false) Long kundeId,
                                        @RequestParam(required = false) Long lieferantId,
                                        @RequestParam(defaultValue = "0") int seite,
                                        @RequestParam(defaultValue = "50") int groesse,
                                        Authentication authentication) {
        berechtigung.verlange(authentication);
        return telefonService.anrufe(art, nurUnbekannt, suche, kundeId, lieferantId, seite, groesse);
    }

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
                                                      @RequestParam(required = false) Long kundeId,
                                                      @RequestParam(required = false) Long lieferantId,
                                                      Authentication authentication) {
        berechtigung.verlange(authentication);
        return telefonService.sprachnachrichten(nurNeue, anrufbeantworter, kundeId, lieferantId);
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

    @DeleteMapping("/kontakt-rufnummern/{id}")
    public ResponseEntity<Void> kontaktRufnummerLoeschen(@PathVariable Long id, Authentication authentication) {
        berechtigung.verlange(authentication);
        telefonService.loescheKontaktRufnummer(id);
        return ResponseEntity.noContent().build();
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
