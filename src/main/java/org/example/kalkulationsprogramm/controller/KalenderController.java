package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.KalenderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kalender")
@RequiredArgsConstructor
public class KalenderController {

    private final KalenderService kalenderService;
    private final MitarbeiterRepository mitarbeiterRepository;

    /**
     * DTO für Teilnehmer.
     */
    public record TeilnehmerDto(Long id, String name) {
        static TeilnehmerDto fromEntity(Mitarbeiter m) {
            return new TeilnehmerDto(m.getId(), m.getVorname() + " " + m.getNachname());
        }
    }

    /**
     * DTO für Kalendereinträge (zur Vermeidung von Lazy-Loading-Problemen).
     */
    public record KalenderEintragDto(
            Long id,
            String titel,
            String beschreibung,
            LocalDate datum,
            LocalTime startZeit,
            LocalTime endeZeit,
            boolean ganztaegig,
            String farbe,
            Long projektId,
            String projektName,
            Long kundeId,
            String kundeName,
            Long lieferantId,
            String lieferantName,
            Long anfrageId,
            String anfrageBetreff,
            Long erstellerId,
            String erstellerName,
            List<TeilnehmerDto> teilnehmer,
            boolean darfBearbeiten) {

        static KalenderEintragDto fromEntity(KalenderEintrag e, boolean darfBearbeiten) {
            List<TeilnehmerDto> teilnehmerDtos = new ArrayList<>();
            if (e.getTeilnehmer() != null) {
                teilnehmerDtos = e.getTeilnehmer().stream()
                        .map(TeilnehmerDto::fromEntity)
                        .collect(Collectors.toList());
            }

            return new KalenderEintragDto(
                    e.getId(),
                    e.getTitel(),
                    e.getBeschreibung(),
                    e.getDatum(),
                    e.getStartZeit(),
                    e.getEndeZeit(),
                    e.isGanztaegig(),
                    e.getFarbe(),
                    e.getProjekt() != null ? e.getProjekt().getId() : null,
                    e.getProjekt() != null ? e.getProjekt().getBauvorhaben() : null,
                    e.getKunde() != null ? e.getKunde().getId() : null,
                    e.getKunde() != null ? e.getKunde().getName() : null,
                    e.getLieferant() != null ? e.getLieferant().getId() : null,
                    e.getLieferant() != null ? e.getLieferant().getLieferantenname() : null,
                    e.getAnfrage() != null ? e.getAnfrage().getId() : null,
                    e.getAnfrage() != null ? e.getAnfrage().getBauvorhaben() : null,
                    e.getErsteller() != null ? e.getErsteller().getId() : null,
                    e.getErsteller() != null ? e.getErsteller().getVorname() + " " + e.getErsteller().getNachname()
                            : null,
                    teilnehmerDtos,
                    darfBearbeiten);
        }
    }

    /**
     * Lädt die Termine eines Zeitraums ({@code von}/{@code bis}, z. B. die sichtbare Woche)
     * oder eines Monats ({@code jahr}/{@code monat}). Welche Termine man sieht, entscheidet
     * die Anmeldung; {@code mitarbeiterId} wirkt nur für Admins.
     */
    @GetMapping
    public ResponseEntity<List<KalenderEintragDto>> getEintraege(
            @RequestParam(required = false) Integer jahr,
            @RequestParam(required = false) Integer monat,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis,
            @RequestParam(required = false) Long mitarbeiterId,
            Authentication authentication) {
        if (von == null && bis == null && jahr != null && monat != null) {
            LocalDate[] zeitraum = KalenderService.monatsZeitraum(jahr, monat);
            von = zeitraum[0];
            bis = zeitraum[1];
        }
        KalenderService.KalenderBetrachter betrachter = kalenderService.betrachter(authentication);
        List<KalenderEintrag> eintraege = kalenderService.sichtbareEintraege(betrachter, von, bis, mitarbeiterId);
        return ResponseEntity.ok(alsDtos(eintraege, betrachter));
    }

    // ==================== MOBILE ENDPOINTS (Token-basiert) ====================

    /**
     * Lädt alle Einträge für einen Mitarbeiter in einem Monat (Mobile App).
     */
    @GetMapping("/mobile")
    public ResponseEntity<List<KalenderEintragDto>> getEintraegeMobile(
            @RequestParam String token,
            @RequestParam int jahr,
            @RequestParam int monat) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null);
        if (mitarbeiter == null || !Boolean.TRUE.equals(mitarbeiter.getAktiv())) {
            return ResponseEntity.status(401).build();
        }

        List<KalenderEintrag> eintraege = kalenderService.getEintraegeForMitarbeiter(mitarbeiter.getId(), jahr, monat);
        return ResponseEntity.ok(alsDtos(eintraege, handyBetrachter(mitarbeiter)));
    }

    /**
     * Lädt alle Einträge für einen Mitarbeiter an einem Tag (Tagesansicht).
     */
    @GetMapping("/mobile/tag")
    public ResponseEntity<List<KalenderEintragDto>> getEintraegeForTag(
            @RequestParam String token,
            @RequestParam String datum) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null);
        if (mitarbeiter == null || !Boolean.TRUE.equals(mitarbeiter.getAktiv())) {
            return ResponseEntity.status(401).build();
        }

        LocalDate localDate = LocalDate.parse(datum);
        List<KalenderEintrag> eintraege = kalenderService.getEintraegeForMitarbeiterTag(mitarbeiter.getId(), localDate);
        return ResponseEntity.ok(alsDtos(eintraege, handyBetrachter(mitarbeiter)));
    }

    /**
     * Lädt einen einzelnen Eintrag – 404, wenn er nicht sichtbar ist.
     */
    @GetMapping("/{id}")
    public ResponseEntity<KalenderEintragDto> getEintrag(@PathVariable Long id, Authentication authentication) {
        KalenderService.KalenderBetrachter betrachter = kalenderService.betrachter(authentication);
        KalenderEintrag eintrag = kalenderService.sichtbarerEintrag(id, betrachter);
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(eintrag, kalenderService.darfBearbeiten(eintrag, betrachter)));
    }

    /**
     * Request-Body für Erstellen/Aktualisieren. {@code erstellerId} wird ignoriert –
     * der Ersteller kommt aus der Anmeldung; das Feld bleibt nur für ältere Aufrufer.
     * {@code teilnehmerIds}: {@code null} = unverändert, leere Liste = alle entfernen.
     */
    public record KalenderEintragRequest(
            String titel,
            String beschreibung,
            LocalDate datum,
            LocalTime startZeit,
            LocalTime endeZeit,
            boolean ganztaegig,
            String farbe,
            Long projektId,
            Long kundeId,
            Long lieferantId,
            Long anfrageId,
            List<Long> teilnehmerIds,
            Long erstellerId) {

        KalenderService.TerminDaten alsDaten() {
            return new KalenderService.TerminDaten(titel, beschreibung, datum, startZeit, endeZeit, ganztaegig,
                    farbe, projektId, kundeId, lieferantId, anfrageId, teilnehmerIds);
        }
    }

    /**
     * Erstellt einen neuen Kalendereintrag (PC-Frontend).
     */
    @PostMapping
    public ResponseEntity<KalenderEintragDto> createEintrag(@RequestBody KalenderEintragRequest request,
            Authentication authentication) {
        KalenderService.KalenderBetrachter betrachter = kalenderService.betrachter(authentication);
        KalenderEintrag saved = kalenderService.erstellen(request.alsDaten(), betrachter);
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(saved, kalenderService.darfBearbeiten(saved, betrachter)));
    }

    /**
     * Aktualisiert einen bestehenden Kalendereintrag (Ersteller, Teilnehmer, Admins).
     */
    @PutMapping("/{id}")
    public ResponseEntity<KalenderEintragDto> updateEintrag(
            @PathVariable Long id,
            @RequestBody KalenderEintragRequest request,
            Authentication authentication) {
        KalenderService.KalenderBetrachter betrachter = kalenderService.betrachter(authentication);
        KalenderEintrag saved = kalenderService.aktualisieren(id, request.alsDaten(), betrachter);
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(saved, kalenderService.darfBearbeiten(saved, betrachter)));
    }

    /**
     * Löscht einen Kalendereintrag (Ersteller, Teilnehmer, Admins).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteEintrag(@PathVariable Long id, Authentication authentication) {
        kalenderService.loeschen(id, kalenderService.betrachter(authentication));
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private List<KalenderEintragDto> alsDtos(List<KalenderEintrag> eintraege, KalenderService.KalenderBetrachter betrachter) {
        return eintraege.stream()
                .map(e -> KalenderEintragDto.fromEntity(e, kalenderService.darfBearbeiten(e, betrachter)))
                .collect(Collectors.toList());
    }

    /** In der Handy-App ändert niemand Termine; der Wert sagt nur, ob man beteiligt ist. */
    private static KalenderService.KalenderBetrachter handyBetrachter(Mitarbeiter mitarbeiter) {
        return new KalenderService.KalenderBetrachter(mitarbeiter.getId(), false);
    }
}
