package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.KalenderService;
import org.springframework.http.ResponseEntity;
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
            List<TeilnehmerDto> teilnehmer) {

        static KalenderEintragDto fromEntity(KalenderEintrag e) {
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
                    teilnehmerDtos);
        }
    }

    /**
     * Lädt alle Einträge für einen Monat.
     * Optional: Wenn mitarbeiterId angegeben, werden nur relevante Einträge für
     * diesen Mitarbeiter gezeigt.
     */
    @GetMapping
    public ResponseEntity<List<KalenderEintragDto>> getEintraege(
            @RequestParam int jahr,
            @RequestParam int monat,
            @RequestParam(required = false) Long mitarbeiterId) {
        List<KalenderEintrag> eintraege;
        if (mitarbeiterId != null) {
            // Gefilterte Ansicht: Nur Termine für diesen Mitarbeiter
            eintraege = kalenderService.getEintraegeForMitarbeiter(mitarbeiterId, jahr, monat);
        } else {
            // Keine Filterung: Alle Termine (für Admin-Ansicht etc.)
            eintraege = kalenderService.getEintraegeForMonat(jahr, monat);
        }
        List<KalenderEintragDto> dtos = eintraege.stream()
                .map(KalenderEintragDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
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
        List<KalenderEintragDto> dtos = eintraege.stream()
                .map(KalenderEintragDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
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
        List<KalenderEintragDto> dtos = eintraege.stream()
                .map(KalenderEintragDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Lädt einen einzelnen Eintrag.
     */
    @GetMapping("/{id}")
    public ResponseEntity<KalenderEintragDto> getEintrag(@PathVariable Long id) {
        KalenderEintrag eintrag = kalenderService.getEintragWithTeilnehmer(id);
        if (eintrag == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(eintrag));
    }

    /**
     * Request-Body für Erstellen/Aktualisieren.
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
    }

    /**
     * Erstellt einen neuen Kalendereintrag (PC-Frontend).
     */
    @PostMapping
    public ResponseEntity<KalenderEintragDto> createEintrag(@RequestBody KalenderEintragRequest request) {
        KalenderEintrag eintrag = new KalenderEintrag();
        eintrag.setTitel(request.titel());
        eintrag.setBeschreibung(request.beschreibung());
        eintrag.setDatum(request.datum());
        eintrag.setStartZeit(request.startZeit());
        eintrag.setEndeZeit(request.endeZeit());
        eintrag.setGanztaegig(request.ganztaegig());
        eintrag.setFarbe(request.farbe());

        KalenderEintrag saved = kalenderService.saveEintrag(
                eintrag,
                request.projektId(),
                request.kundeId(),
                request.lieferantId(),
                request.anfrageId(),
                request.erstellerId(),
                request.teilnehmerIds());
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(saved));
    }

    /**
     * Erstellt einen neuen Kalendereintrag (Mobile App).
     */
    @PostMapping("/mobile")
    public ResponseEntity<KalenderEintragDto> createEintragMobile(
            @RequestParam String token,
            @RequestBody KalenderEintragRequest request) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null);
        if (mitarbeiter == null || !Boolean.TRUE.equals(mitarbeiter.getAktiv())) {
            return ResponseEntity.status(401).build();
        }

        KalenderEintrag eintrag = new KalenderEintrag();
        eintrag.setTitel(request.titel());
        eintrag.setBeschreibung(request.beschreibung());
        eintrag.setDatum(request.datum());
        eintrag.setStartZeit(request.startZeit());
        eintrag.setEndeZeit(request.endeZeit());
        eintrag.setGanztaegig(request.ganztaegig());
        eintrag.setFarbe(request.farbe());

        KalenderEintrag saved = kalenderService.saveEintrag(
                eintrag,
                request.projektId(),
                request.kundeId(),
                request.lieferantId(),
                request.anfrageId(),
                mitarbeiter.getId(),
                request.teilnehmerIds());
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(saved));
    }

    /**
     * Aktualisiert einen bestehenden Kalendereintrag.
     */
    @PutMapping("/{id}")
    public ResponseEntity<KalenderEintragDto> updateEintrag(
            @PathVariable Long id,
            @RequestBody KalenderEintragRequest request) {
        KalenderEintrag existing = kalenderService.getEintrag(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        existing.setTitel(request.titel());
        existing.setBeschreibung(request.beschreibung());
        existing.setDatum(request.datum());
        existing.setStartZeit(request.startZeit());
        existing.setEndeZeit(request.endeZeit());
        existing.setGanztaegig(request.ganztaegig());
        existing.setFarbe(request.farbe());

        KalenderEintrag saved = kalenderService.saveEintrag(
                existing,
                request.projektId(),
                request.kundeId(),
                request.lieferantId(),
                request.anfrageId(),
                null,
                request.teilnehmerIds());
        return ResponseEntity.ok(KalenderEintragDto.fromEntity(saved));
    }

    /**
     * Löscht einen Kalendereintrag.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteEintrag(@PathVariable Long id) {
        kalenderService.deleteEintrag(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
