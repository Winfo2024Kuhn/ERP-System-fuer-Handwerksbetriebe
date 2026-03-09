package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller für Offene Posten - Eingangsrechnungen von Lieferanten.
 * 
 * Berechtigungslogik:
 * - Abteilung 3 (Büro): Sieht ALLE Eingangsrechnungen, kann genehmigen
 * - Abteilung 2 (Buchhaltung): Sieht NUR genehmigte Rechnungen
 * - Andere Abteilungen: Kein Zugriff
 */
@RestController
@RequestMapping("/api/offene-posten")
@RequiredArgsConstructor
public class OffenePostenController {

    private static final Long ABTEILUNG_BUCHHALTUNG = 2L;
    private static final Long ABTEILUNG_BUERO = 3L;

    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final MitarbeiterRepository mitarbeiterRepository;

    /**
     * Gibt alle offenen (unbezahlten) Eingangsrechnungen zurück.
     * 
     * Berechtigungslogik:
     * - Abteilung 3 (Büro): Alle offenen Rechnungen, kann genehmigen
     * - Abteilung 2 (Buchhaltung): NUR genehmigte Rechnungen
     * - Andere/Kein Token: Leere Liste
     */

    @GetMapping("/eingang")
    public ResponseEntity<List<EingangsrechnungDto>> getOffeneEingangsrechnungen(
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {

        Mitarbeiter mitarbeiter = mitarbeiterByToken(token);
        Set<Long> abteilungIds = getAbteilungIds(mitarbeiter);

        List<LieferantGeschaeftsdokument> rechnungen;
        boolean darfGenehmigen = abteilungIds.contains(ABTEILUNG_BUERO);
        boolean istBuchhaltung = abteilungIds.contains(ABTEILUNG_BUCHHALTUNG);

        if (darfGenehmigen) {
            // Abteilung 3 (Büro) sieht ALLE offenen Rechnungen
            rechnungen = geschaeftsdokumentRepository.findAllOffeneEingangsrechnungen();
        } else if (istBuchhaltung) {
            // Abteilung 2 (Buchhaltung) sieht NUR genehmigte Rechnungen
            rechnungen = geschaeftsdokumentRepository.findAllOffeneGenehmigte();
        } else {
            // Andere Abteilungen oder kein Token: Leere Liste
            rechnungen = List.of();
        }

        List<EingangsrechnungDto> dtos = rechnungen.stream()
                .map(gd -> toDto(gd, darfGenehmigen))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Gibt alle Eingangsrechnungen zurück (auch bezahlte).
     * 
     * Berechtigungslogik:
     * - Abteilung 3 (Büro): Alle Rechnungen
     * - Abteilung 2 (Buchhaltung): NUR genehmigte Rechnungen
     * - Andere/Kein Token: Leere Liste
     */
    @GetMapping("/eingang/alle")
    public ResponseEntity<List<EingangsrechnungDto>> getAlleEingangsrechnungen(
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {

        Mitarbeiter mitarbeiter = mitarbeiterByToken(token);
        Set<Long> abteilungIds = getAbteilungIds(mitarbeiter);

        List<LieferantGeschaeftsdokument> rechnungen;
        boolean darfGenehmigen = abteilungIds.contains(ABTEILUNG_BUERO);
        boolean istBuchhaltung = abteilungIds.contains(ABTEILUNG_BUCHHALTUNG);

        if (darfGenehmigen) {
            // Abteilung 3 (Büro) sieht ALLE Rechnungen
            rechnungen = geschaeftsdokumentRepository.findAllEingangsrechnungen();
        } else if (istBuchhaltung) {
            // Abteilung 2 (Buchhaltung) sieht NUR genehmigte Rechnungen
            rechnungen = geschaeftsdokumentRepository.findAllGenehmigte();
        } else {
            // Andere Abteilungen oder kein Token: Leere Liste
            rechnungen = List.of();
        }

        List<EingangsrechnungDto> dtos = rechnungen.stream()
                .map(gd -> toDto(gd, darfGenehmigen))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Setzt den bezahlt-Status einer Eingangsrechnung.
     * Bei Zahlung innerhalb der Skonto-Frist wird der Skonto-Abzug berechnet.
     */
    @PutMapping("/eingang/{id}/bezahlt")
    @Transactional
    public ResponseEntity<EingangsrechnungDto> setBezahltStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {

        LieferantGeschaeftsdokument gd = geschaeftsdokumentRepository.findById(id).orElse(null);
        if (gd == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean bezahlt = body.getOrDefault("bezahlt", false);
        gd.setBezahlt(bezahlt);

        if (bezahlt) {
            gd.setBezahltAm(LocalDate.now());

            // Skonto-Berechnung wenn innerhalb Skonto-Frist
            if (gd.getSkontoTage() != null && gd.getSkontoProzent() != null
                    && gd.getDokumentDatum() != null && gd.getBetragBrutto() != null) {

                LocalDate skontoFrist = gd.getDokumentDatum().plusDays(gd.getSkontoTage());
                boolean innerhalbSkonto = !LocalDate.now().isAfter(skontoFrist);

                if (innerhalbSkonto) {
                    // Skonto anwenden
                    java.math.BigDecimal brutto = gd.getBetragBrutto();
                    java.math.BigDecimal skontoBetrag = brutto
                            .multiply(gd.getSkontoProzent())
                            .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    java.math.BigDecimal gezahlt = brutto.subtract(skontoBetrag);

                    gd.setTatsaechlichGezahlt(gezahlt);
                    gd.setMitSkonto(true);
                } else {
                    // Kein Skonto - voller Betrag
                    gd.setTatsaechlichGezahlt(gd.getBetragBrutto());
                    gd.setMitSkonto(false);
                }
            } else {
                // Keine Skonto-Konditionen - voller Betrag
                gd.setTatsaechlichGezahlt(gd.getBetragBrutto());
                gd.setMitSkonto(false);
            }
        } else {
            gd.setBezahltAm(null);
            gd.setTatsaechlichGezahlt(null);
            gd.setMitSkonto(false);
        }

        geschaeftsdokumentRepository.save(gd);
        boolean darfGenehmigen = getAbteilungIds(mitarbeiterByToken(token)).contains(ABTEILUNG_BUERO);
        return ResponseEntity.ok(toDto(gd, darfGenehmigen));
    }

    /**
     * Setzt den genehmigt-Status einer Eingangsrechnung (für Abteilung 3).
     * Nur Mitarbeiter aus Abteilung 3 (Büro) dürfen genehmigen.
     */
    @PatchMapping("/eingang/{id}/genehmigen")
    @Transactional
    public ResponseEntity<EingangsrechnungDto> setGenehmigtStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {

        // Berechtigungsprüfung: Nur Abteilung 3 darf genehmigen
        Mitarbeiter mitarbeiter = mitarbeiterByToken(token);
        Set<Long> abteilungIds = getAbteilungIds(mitarbeiter);
        boolean darfGenehmigen = abteilungIds.contains(ABTEILUNG_BUERO);

        if (!darfGenehmigen) {
            return ResponseEntity.status(403).build(); // Forbidden
        }

        LieferantGeschaeftsdokument gd = geschaeftsdokumentRepository.findById(id).orElse(null);
        if (gd == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean genehmigt = body.getOrDefault("genehmigt", false);
        gd.setGenehmigt(genehmigt);

        LieferantGeschaeftsdokument saved = geschaeftsdokumentRepository.save(gd);
        return ResponseEntity.ok(toDto(saved, darfGenehmigen));
    }

    // ==================== Hilfsmethoden ====================

    private Mitarbeiter mitarbeiterByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return mitarbeiterRepository.findByLoginToken(token).orElse(null);
    }

    private Set<Long> getAbteilungIds(Mitarbeiter mitarbeiter) {
        if (mitarbeiter == null || mitarbeiter.getAbteilungen() == null) {
            return Set.of();
        }
        return mitarbeiter.getAbteilungen().stream()
                .map(Abteilung::getId)
                .collect(Collectors.toSet());
    }

    private EingangsrechnungDto toDto(LieferantGeschaeftsdokument gd, boolean darfGenehmigen) {
        EingangsrechnungDto dto = new EingangsrechnungDto();
        dto.id = gd.getId();
        dto.dokumentNummer = gd.getDokumentNummer();
        dto.dokumentDatum = gd.getDokumentDatum();
        dto.zahlungsziel = gd.getZahlungsziel();
        dto.betragNetto = gd.getBetragNetto() != null ? gd.getBetragNetto().doubleValue() : null;
        dto.betragBrutto = gd.getBetragBrutto() != null ? gd.getBetragBrutto().doubleValue() : null;
        dto.bezahlt = Boolean.TRUE.equals(gd.getBezahlt());
        dto.bezahltAm = gd.getBezahltAm();
        dto.bereitsGezahlt = Boolean.TRUE.equals(gd.getBereitsGezahlt());
        dto.genehmigt = Boolean.TRUE.equals(gd.getGenehmigt());
        dto.darfGenehmigen = darfGenehmigen;

        // Skonto-Felder
        dto.skontoTage = gd.getSkontoTage();
        dto.skontoProzent = gd.getSkontoProzent() != null ? gd.getSkontoProzent().doubleValue() : null;
        dto.nettoTage = gd.getNettoTage();
        dto.tatsaechlichGezahlt = gd.getTatsaechlichGezahlt() != null ? gd.getTatsaechlichGezahlt().doubleValue()
                : null;
        dto.mitSkonto = Boolean.TRUE.equals(gd.getMitSkonto());

        // Skonto-Frist und verbleibende Tage berechnen
        if (gd.getSkontoTage() != null && gd.getDokumentDatum() != null && !dto.bezahlt) {
            dto.skontoFrist = gd.getDokumentDatum().plusDays(gd.getSkontoTage());
            long verbleibend = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dto.skontoFrist);
            dto.skontoVerbleibendeTage = (int) verbleibend;
            dto.skontoAbgelaufen = verbleibend < 0;
        }

        if (gd.getDokument() != null) {
            dto.dokumentId = gd.getDokument().getId();
            dto.dateiname = gd.getDokument().getEffektiverDateiname();

            if (gd.getDokument().getLieferant() != null) {
                dto.lieferantId = gd.getDokument().getLieferant().getId();
                dto.lieferantName = gd.getDokument().getLieferant().getLieferantenname();
            }

            // PDF-URL zusammenbauen
            if (gd.getDokument().getAttachment() != null) {
                // Dokument aus Email-Attachment - verwende Unified Email API
                var att = gd.getDokument().getAttachment();
                if (att.getEmail() != null) {
                    dto.pdfUrl = "/api/emails/" + att.getEmail().getId() +
                            "/attachments/" + att.getId();
                }
            } else if (gd.getDokument().getGespeicherterDateiname() != null) {
                // Manuell hochgeladenes Dokument
                dto.pdfUrl = "/api/lieferanten/" + dto.lieferantId +
                        "/dokumente/" + gd.getDokument().getId() + "/download";
            }
        }

        // Überfällig berechnen
        if (dto.zahlungsziel != null && !dto.bezahlt) {
            dto.ueberfaellig = LocalDate.now().isAfter(dto.zahlungsziel);
        }

        // Referenznummer für Gutschriften
        dto.referenzNummer = gd.getReferenzNummer();
        if (gd.getDokument() != null && gd.getDokument().getTyp() != null) {
            dto.typ = gd.getDokument().getTyp().name();
        }

        return dto;
    }

    /**
     * DTO für Eingangsrechnung mit Skonto-Informationen.
     */
    public static class EingangsrechnungDto {
        public Long id;
        public Long dokumentId;
        public Long lieferantId;
        public String lieferantName;
        public String dokumentNummer;
        public LocalDate dokumentDatum;
        public LocalDate zahlungsziel;
        public Double betragNetto;
        public Double betragBrutto;
        public boolean bezahlt;
        public LocalDate bezahltAm;
        public boolean bereitsGezahlt;
        public String dateiname;
        public String pdfUrl;
        public boolean ueberfaellig;
        public boolean genehmigt;
        public boolean darfGenehmigen; // true wenn Benutzer aus Abteilung 3 (Büro)

        // Neu für Gutschriften-Hierarchie
        public String referenzNummer;
        public String typ; // RECHNUNG oder GUTSCHRIFT

        // Skonto-Felder
        public Integer skontoTage;
        public Double skontoProzent;
        public Integer nettoTage;
        public LocalDate skontoFrist;
        public Integer skontoVerbleibendeTage; // Positive = noch Zeit, Negative = abgelaufen
        public boolean skontoAbgelaufen;
        public Double tatsaechlichGezahlt;
        public boolean mitSkonto;
    }
}
