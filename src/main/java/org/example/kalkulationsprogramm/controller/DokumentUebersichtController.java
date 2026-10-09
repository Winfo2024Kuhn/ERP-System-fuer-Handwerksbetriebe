package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.PositionsTrefferDto;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.springframework.http.HttpStatus;
import org.example.kalkulationsprogramm.service.LieferantDokumentSucheService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Übersicht aller Geschäftsdokumente (Ausgang & Eingang) mit Filter nach Jahr, Monat und Suche.
 * Im Gegensatz zur RechnungsUebersicht werden hier ALLE Dokumenttypen ausgeliefert
 * (Angebote, Auftragsbestätigungen, Rechnungen usw.) – nicht nur Rechnungen.
 */
@RestController
@RequestMapping("/api/dokumentuebersicht")
@RequiredArgsConstructor
public class DokumentUebersichtController {

    private final AusgangsGeschaeftsDokumentRepository ausgangsRepo;
    private final LieferantGeschaeftsdokumentRepository lieferantGdRepo;
    private final LieferantDokumentZugriffService zugriffService;
    private final LieferantDokumentSucheService dokumentSucheService;

    @GetMapping("/ausgang")
    public ResponseEntity<List<AusgangsDokumentUebersichtDto>> getAusgang(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dokumentNummer,
            @RequestParam(required = false) AusgangsGeschaeftsDokumentTyp typ,
            @RequestParam(required = false) Long kundeId,
            @RequestParam(required = false) Double betragMin,
            @RequestParam(required = false) Double betragMax) {

        List<AusgangsGeschaeftsDokument> dokumente;
        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            dokumente = ausgangsRepo.findByDatumBetweenOrderByDatumDesc(ym.atDay(1), ym.atEndOfMonth());
        } else if (year != null) {
            dokumente = ausgangsRepo.findByDatumBetweenOrderByDatumDesc(
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        } else {
            dokumente = ausgangsRepo.findAllByOrderByDatumDesc();
        }

        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            dokumente = dokumente.stream().filter(d -> matchesAusgangSearch(d, q)).collect(Collectors.toList());
        }
        if (dokumentNummer != null && !dokumentNummer.isBlank()) {
            String q = dokumentNummer.toLowerCase();
            dokumente = dokumente.stream()
                    .filter(d -> d.getDokumentNummer() != null && d.getDokumentNummer().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }
        if (typ != null) {
            dokumente = dokumente.stream().filter(d -> d.getTyp() == typ).collect(Collectors.toList());
        }
        if (kundeId != null) {
            dokumente = dokumente.stream()
                    .filter(d -> d.getKunde() != null && kundeId.equals(d.getKunde().getId()))
                    .collect(Collectors.toList());
        }
        if (betragMin != null) {
            dokumente = dokumente.stream()
                    .filter(d -> d.getBetragBrutto() != null && d.getBetragBrutto().doubleValue() >= betragMin)
                    .collect(Collectors.toList());
        }
        if (betragMax != null) {
            dokumente = dokumente.stream()
                    .filter(d -> d.getBetragBrutto() != null && d.getBetragBrutto().doubleValue() <= betragMax)
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(dokumente.stream().map(this::toAusgangsDto).collect(Collectors.toList()));
    }

    @GetMapping("/eingang")
    public ResponseEntity<List<EingangsDokumentUebersichtDto>> getEingang(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dokumentNummer,
            @RequestParam(required = false) LieferantDokumentTyp typ,
            @RequestParam(required = false) Long lieferantId,
            @RequestParam(required = false) Double betragMin,
            @RequestParam(required = false) Double betragMax,
            Authentication authentication) {

        // Nur Dokumenttypen, die der Angemeldete laut Abteilungsrechten sehen darf (Admin: alle).
        var sichtbareTypen = zugriffService.sichtbareTypen(null, authentication);
        if (sichtbareTypen.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<LieferantGeschaeftsdokument> dokumente;
        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            dokumente = lieferantGdRepo.findAllByDatumOderEingangBetween(ym.atDay(1), ym.atEndOfMonth());
        } else if (year != null) {
            dokumente = lieferantGdRepo.findAllByDatumOderEingangBetween(
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        } else {
            dokumente = lieferantGdRepo.findAllSortedByDatum();
        }

        dokumente = dokumente.stream()
                .filter(d -> d.getDokument() != null && sichtbareTypen.get().contains(d.getDokument().getTyp()))
                .collect(Collectors.toList());

        // Positionen (Material, Werkstoff, Charge …) werden in der Datenbank gesucht –
        // mit denselben Jahr/Monat-, Typ- und Lieferantenfiltern wie die Liste.
        Map<Long, PositionsTrefferDto> positionsTreffer = Map.of();
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase(Locale.ROOT);
            LocalDate[] zeitraum = zeitraum(year, month);
            EnumSet<LieferantDokumentTyp> suchTypen = EnumSet.noneOf(LieferantDokumentTyp.class);
            suchTypen.addAll(sichtbareTypen.get());
            if (typ != null) {
                suchTypen.retainAll(EnumSet.of(typ));
            }
            Map<Long, PositionsTrefferDto> treffer = dokumentSucheService.suchePositionen(search, lieferantId,
                    suchTypen,
                    zeitraum[0], zeitraum[1]);
            positionsTreffer = treffer;
            dokumente = dokumente.stream()
                    .filter(d -> dokumentSucheService.passtZurEingangssuche(d, q) || treffer.containsKey(d.getId()))
                    .collect(Collectors.toList());
        }
        if (dokumentNummer != null && !dokumentNummer.isBlank()) {
            String q = dokumentNummer.toLowerCase();
            dokumente = dokumente.stream()
                    .filter(d -> d.getDokumentNummer() != null && d.getDokumentNummer().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }
        if (typ != null) {
            dokumente = dokumente.stream()
                    .filter(d -> d.getDokument() != null && d.getDokument().getTyp() == typ)
                    .collect(Collectors.toList());
        }
        if (lieferantId != null) {
            dokumente = dokumente.stream()
                    .filter(d -> d.getDokument() != null
                            && d.getDokument().getLieferant() != null
                            && lieferantId.equals(d.getDokument().getLieferant().getId()))
                    .collect(Collectors.toList());
        }
        if (betragMin != null) {
            dokumente = dokumente.stream()
                    .filter(d -> d.getBetragBrutto() != null && d.getBetragBrutto().doubleValue() >= betragMin)
                    .collect(Collectors.toList());
        }
        if (betragMax != null) {
            dokumente = dokumente.stream()
                    .filter(d -> d.getBetragBrutto() != null && d.getBetragBrutto().doubleValue() <= betragMax)
                    .collect(Collectors.toList());
        }

        dokumente.sort(Comparator.comparing(
                DokumentUebersichtController::datumFuerListe,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<Long, PositionsTrefferDto> trefferJeDokument = positionsTreffer;
        return ResponseEntity.ok(dokumente.stream()
                .map(d -> toEingangsDto(d, trefferJeDokument.get(d.getId())))
                .collect(Collectors.toList()));
    }

    // --- Search helpers ---

    private boolean matchesAusgangSearch(AusgangsGeschaeftsDokument d, String q) {
        if (d.getDokumentNummer() != null && d.getDokumentNummer().toLowerCase().contains(q)) return true;
        if (d.getBetreff() != null && d.getBetreff().toLowerCase().contains(q)) return true;
        if (d.getTyp() != null && d.getTyp().name().toLowerCase().contains(q)) return true;
        if (d.getBetragBrutto() != null && d.getBetragBrutto().toPlainString().contains(q)) return true;
        if (d.getKunde() != null && d.getKunde().getName() != null
                && d.getKunde().getName().toLowerCase().contains(q)) return true;
        if (d.getProjekt() != null && d.getProjekt().getAuftragsnummer() != null
                && d.getProjekt().getAuftragsnummer().toLowerCase().contains(q)) return true;
        return false;
    }


    // --- DTO Mapper ---

    private AusgangsDokumentUebersichtDto toAusgangsDto(AusgangsGeschaeftsDokument d) {
        AusgangsDokumentUebersichtDto dto = new AusgangsDokumentUebersichtDto();
        dto.setId(d.getId());
        dto.setDokumentNummer(d.getDokumentNummer());
        dto.setTyp(d.getTyp());
        dto.setDatum(d.getDatum());
        dto.setBetreff(d.getBetreff());
        dto.setBetragBrutto(d.getBetragBrutto() != null ? d.getBetragBrutto().doubleValue() : null);
        dto.setBetragNetto(d.getBetragNetto() != null ? d.getBetragNetto().doubleValue() : null);
        dto.setGebucht(d.isGebucht());
        dto.setStorniert(d.isStorniert());
        dto.setDigitalAngenommen(d.isDigitalAngenommen());
        if (d.getKunde() != null) {
            dto.setKundeId(d.getKunde().getId());
            dto.setKundenName(d.getKunde().getName());
        }
        if (d.getProjekt() != null) {
            dto.setProjektId(d.getProjekt().getId());
            dto.setProjektAuftragsnummer(d.getProjekt().getAuftragsnummer());
        }
        return dto;
    }

    /** Dokumentdatum, ersatzweise das Eingangsdatum (Upload) – so wie der Jahres-/Monatsfilter. */
    private static LocalDate datumFuerListe(LieferantGeschaeftsdokument gd) {
        if (gd.getDokumentDatum() != null) {
            return gd.getDokumentDatum();
        }
        return eingangsDatum(gd);
    }

    private static LocalDate eingangsDatum(LieferantGeschaeftsdokument gd) {
        return gd.getDokument() != null && gd.getDokument().getUploadDatum() != null
                ? gd.getDokument().getUploadDatum().toLocalDate()
                : null;
    }

    /** Zeitraum zu Jahr/Monat wie bei der Liste; {@code [null, null]} = ohne Grenze. */
    private static LocalDate[] zeitraum(Integer year, Integer month) {
        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            return new LocalDate[] { ym.atDay(1), ym.atEndOfMonth() };
        }
        if (year != null) {
            return new LocalDate[] { LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31) };
        }
        return new LocalDate[] { null, null };
    }

    private EingangsDokumentUebersichtDto toEingangsDto(LieferantGeschaeftsdokument gd,
            PositionsTrefferDto positionsTreffer) {
        EingangsDokumentUebersichtDto dto = new EingangsDokumentUebersichtDto();
        if (positionsTreffer != null) {
            dto.setPositionsTreffer(positionsTreffer.trefferText());
            dto.setWeitereTreffer(positionsTreffer.weitereTreffer());
        }
        dto.setId(gd.getId());
        dto.setDokumentNummer(gd.getDokumentNummer());
        dto.setDokumentDatum(gd.getDokumentDatum());
        dto.setEingangsDatum(eingangsDatum(gd));
        dto.setBetragNetto(gd.getBetragNetto() != null ? gd.getBetragNetto().doubleValue() : null);
        dto.setBetragBrutto(gd.getBetragBrutto() != null ? gd.getBetragBrutto().doubleValue() : null);
        dto.setBezahlt(Boolean.TRUE.equals(gd.getBezahlt()));

        if (gd.getDokument() != null) {
            dto.setDokumentId(gd.getDokument().getId());
            dto.setOriginalDateiname(gd.getDokument().getEffektiverDateiname());
            LieferantDokumentTyp t = gd.getDokument().getTyp();
            if (t != null) dto.setTyp(t.name());
            if (gd.getDokument().getLieferant() != null) {
                dto.setLieferantId(gd.getDokument().getLieferant().getId());
                dto.setLieferantName(gd.getDokument().getLieferant().getLieferantenname());
            }
            // PDF-URL: bevorzugt aus E-Mail-Anhang, sonst Lieferant-Download-Endpoint
            if (gd.getDokument().getAttachment() != null
                    && gd.getDokument().getAttachment().getEmail() != null) {
                dto.setPdfUrl("/api/emails/" + gd.getDokument().getAttachment().getEmail().getId()
                        + "/attachments/" + gd.getDokument().getAttachment().getId());
            } else if (gd.getDokument().getGespeicherterDateiname() != null && dto.getLieferantId() != null) {
                dto.setPdfUrl("/api/lieferanten/" + dto.getLieferantId()
                        + "/dokumente/" + gd.getDokument().getId() + "/download");
            }
        }
        return dto;
    }

    // --- DTOs ---

    @Data
    public static class AusgangsDokumentUebersichtDto {
        private Long id;
        private String dokumentNummer;
        private AusgangsGeschaeftsDokumentTyp typ;
        private LocalDate datum;
        private String betreff;
        private Double betragNetto;
        private Double betragBrutto;
        private boolean gebucht;
        private boolean storniert;
        private boolean digitalAngenommen;
        private Long kundeId;
        private String kundenName;
        private Long projektId;
        private String projektAuftragsnummer;
    }

    @Data
    public static class EingangsDokumentUebersichtDto {
        private Long id;
        private Long dokumentId;
        private Long lieferantId;
        private String lieferantName;
        private String dokumentNummer;
        private String typ;
        private LocalDate dokumentDatum;
        /** Eingang (Upload) – die Liste zeigt es, wenn kein Dokumentdatum erkannt wurde. */
        private LocalDate eingangsDatum;
        private Double betragNetto;
        private Double betragBrutto;
        private boolean bezahlt;
        private String originalDateiname;
        private String pdfUrl;
        /** Passende Position bei einer Suche, z. B. „Flachstahl 50x5 · Charge 123456“. */
        private String positionsTreffer;
        /** Weitere passende Positionen desselben Dokuments. */
        private int weitereTreffer;
    }
}
