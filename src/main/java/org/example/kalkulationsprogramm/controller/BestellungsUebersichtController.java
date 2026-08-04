package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.BelegService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller für die Bestellungs-Übersicht mit Dokumenten-Ketten.
 * Gruppiert Lieferanten-Dokumente nach Status:
 * - Offene Anfragen (nur Anfrage, keine Folgedokumente)
 * - Laufende Bestellungen (AB vorhanden, keine Rechnung)
 * - Abgeschlossen (Rechnung vorhanden, noch nicht zugeordnet)
 * - Zugeordnet (Rechnung vorhanden und Projekten zugeordnet)
 */
@RestController
@RequestMapping("/api/bestellungen-uebersicht")
@RequiredArgsConstructor
public class BestellungsUebersichtController {

    private final LieferantDokumentRepository dokumentRepository;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final ProjektRepository projektRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    private final KostenstelleRepository kostenstelleRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;
    private final BelegRepository belegRepository;
    private final BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    private final BelegService belegService;
    private final org.example.kalkulationsprogramm.service.BelegAuditService belegAuditService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.mail-attachment-dir}")
    private String attachmentDir;

    /**
     * Gibt alle Dokumenten-Ketten gruppiert nach Status zurück.
     */
    @GetMapping
    public ResponseEntity<BestellungsUebersichtDto> getUebersicht() {
        // Alle Dokumente laden und nach ausgeblendet splitten
        List<LieferantDokument> alleDokumente = dokumentRepository.findAll();
        List<LieferantDokument> aktiveDokumente = new ArrayList<>();
        List<LieferantDokument> ausgeblendeteDokumente = new ArrayList<>();
        for (LieferantDokument d : alleDokumente) {
            if (d.isAusgeblendet()) {
                ausgeblendeteDokumente.add(d);
            } else {
                aktiveDokumente.add(d);
            }
        }

        // IDs aller bereits zugeordneten Dokumente
        Set<Long> zugeordneteDokumentIds = projektAnteilRepository.findAll().stream()
                .map(a -> a.getDokument().getId())
                .collect(Collectors.toSet());

        // IDs aller Lagerbestellungen (Bestellungen ohne Projekt-Zuordnung)
        Set<Long> lagerbestellungIds = geschaeftsdokumentRepository.findAll().stream()
                .filter(gd -> Boolean.TRUE.equals(gd.getLagerbestellung()))
                .map(gd -> gd.getDokument() != null ? gd.getDokument().getId() : -1L)
                .collect(Collectors.toSet());

        // Ketten bilden (nur aus aktiven Dokumenten – ausgeblendete tauchen hier nicht auf)
        List<DokumentenKette> alleKetten = buildKetten(aktiveDokumente);

        // Nach Status gruppieren
        List<DokumentenKette> offeneAnfragen = new ArrayList<>();
        List<DokumentenKette> laufendeBestellungen = new ArrayList<>();
        List<DokumentenKette> abgeschlossen = new ArrayList<>();
        List<DokumentenKette> zugeordnet = new ArrayList<>();

        for (DokumentenKette kette : alleKetten) {
            boolean hatRechnung = kette.dokumente.stream()
                    .anyMatch(d -> d.typ == LieferantDokumentTyp.RECHNUNG);
            boolean hatAB = kette.dokumente.stream()
                    .anyMatch(d -> d.typ == LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
            boolean hatNurAnfrage = kette.dokumente.stream()
                    .allMatch(d -> d.typ == LieferantDokumentTyp.ANGEBOT);

            if (hatRechnung) {
                // Prüfe ob die Rechnung bereits zugeordnet oder als Lagerbestellung markiert ist
                boolean istZugeordnet = kette.dokumente.stream()
                        .filter(d -> d.typ == LieferantDokumentTyp.RECHNUNG)
                        .anyMatch(d -> zugeordneteDokumentIds.contains(d.id) || lagerbestellungIds.contains(d.id));

                if (istZugeordnet) {
                    zugeordnet.add(kette);
                } else {
                    abgeschlossen.add(kette);
                }
            } else if (hatAB) {
                laufendeBestellungen.add(kette);
            } else if (hatNurAnfrage) {
                offeneAnfragen.add(kette);
            }
        }

        // Ausgeblendete Ketten separat aufbauen, ohne weitere Status-Aufteilung
        List<DokumentenKette> ausgeblendet = buildKetten(ausgeblendeteDokumente);

        // Nach Datum sortieren (neueste zuerst)
        Comparator<DokumentenKette> byDate = (a, b) -> {
            LocalDate dateA = a.dokumente.stream()
                    .map(d -> d.dokumentDatum)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.MIN);
            LocalDate dateB = b.dokumente.stream()
                    .map(d -> d.dokumentDatum)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.MIN);
            return dateB.compareTo(dateA);
        };

        offeneAnfragen.sort(byDate);
        laufendeBestellungen.sort(byDate);
        abgeschlossen.sort(byDate);
        zugeordnet.sort(byDate);
        ausgeblendet.sort(byDate);

        return ResponseEntity.ok(new BestellungsUebersichtDto(
                offeneAnfragen, laufendeBestellungen, abgeschlossen, zugeordnet, ausgeblendet));
    }

    /**
     * Blendet eine Dokumenten-Kette aus der Bestellübersicht aus.
     * Erwartet die IDs aller Dokumente der Kette (vom Frontend gebündelt).
     */
    @PostMapping("/ausblenden")
    @Transactional
    public ResponseEntity<?> ausblenden(@Valid @RequestBody AusblendenRequest request) {
        return setAusgeblendet(request, true);
    }

    /**
     * Blendet eine zuvor ausgeblendete Kette wieder ein.
     */
    @PostMapping("/einblenden")
    @Transactional
    public ResponseEntity<?> einblenden(@Valid @RequestBody AusblendenRequest request) {
        return setAusgeblendet(request, false);
    }

    private ResponseEntity<?> setAusgeblendet(AusblendenRequest request, boolean wert) {
        List<LieferantDokument> dokumente = dokumentRepository.findAllById(request.dokumentIds());
        for (LieferantDokument d : dokumente) {
            d.setAusgeblendet(wert);
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "geaendert", dokumente.size()));
    }

    /**
     * Gibt die Geschäftsdaten eines Dokuments für die Bearbeitung zurück.
     */
    @GetMapping("/geschaeftsdaten/{dokId}")
    public ResponseEntity<GeschaeftsdatenDto> getGeschaeftsdaten(@PathVariable Long dokId) {
        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null) {
            return ResponseEntity.notFound().build();
        }

        GeschaeftsdatenDto dto = new GeschaeftsdatenDto();
        dto.id = gd.getId();
        dto.dokumentNummer = gd.getDokumentNummer();
        dto.dokumentDatum = gd.getDokumentDatum();
        dto.betragNetto = gd.getBetragNetto();
        dto.betragBrutto = gd.getBetragBrutto();
        
        // Lagerbestellung Flag
        dto.istLagerbestellung = Boolean.TRUE.equals(gd.getLagerbestellung());
        dto.mwstSatz = gd.getMwstSatz();
        dto.liefertermin = gd.getLiefertermin();
        dto.bestellnummer = gd.getBestellnummer();

        // Lieferant-Info
        if (gd.getDokument() != null && gd.getDokument().getLieferant() != null) {
            dto.lieferantId = gd.getDokument().getLieferant().getId();
            dto.lieferantName = gd.getDokument().getLieferant().getLieferantenname();
        }

        return ResponseEntity.ok(dto);
    }

    /**
     * Aktualisiert die Geschäftsdaten eines Dokuments.
     */
    @PutMapping("/geschaeftsdaten/{dokId}")
    @Transactional
    public ResponseEntity<GeschaeftsdatenDto> updateGeschaeftsdaten(
            @PathVariable Long dokId,
            @RequestBody GeschaeftsdatenDto dto) {

        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null) {
            return ResponseEntity.notFound().build();
        }

        if (dto.dokumentNummer != null)
            gd.setDokumentNummer(dto.dokumentNummer);
        if (dto.dokumentDatum != null)
            gd.setDokumentDatum(dto.dokumentDatum);
        if (dto.betragNetto != null)
            gd.setBetragNetto(dto.betragNetto);
        if (dto.betragBrutto != null)
            gd.setBetragBrutto(dto.betragBrutto);
        if (dto.mwstSatz != null)
            gd.setMwstSatz(dto.mwstSatz);
        if (dto.liefertermin != null)
            gd.setLiefertermin(dto.liefertermin);
        if (dto.bestellnummer != null)
            gd.setBestellnummer(dto.bestellnummer);

        geschaeftsdokumentRepository.save(gd);

        return getGeschaeftsdaten(dokId);
    }

    /**
     * Gibt alle aktiven Kostenstellen zurück.
     */
    @GetMapping("/kostenstellen")
    public ResponseEntity<List<KostenstelleDto>> getKostenstellen() {
        var list = kostenstelleRepository.findByAktivTrueOrderBySortierungAsc().stream()
                .map(k -> new KostenstelleDto(k.getId(), k.getBezeichnung(), k.getTyp().name(), k.getBeschreibung()))
                .toList();
        return ResponseEntity.ok(list);
    }

    /**
     * Belege aus Belege & Kasse, die nicht aus dem E-Mail-Import stammen und noch
     * keine Kostenstellen-Zuordnung haben. Diese Liste liegt fachlich im Einkauf:
     * hier werden Kosten Projekten/Kostenstellen vorsortiert; Buchhaltung bucht
     * anschliessend nur noch Konten/Sachkonten.
     */
    @GetMapping("/belege-offen")
    public ResponseEntity<List<BelegZuordnungDto>> getOffeneBelegeZurKostenstellenZuordnung(
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        if (!darfBelegeSehen(token, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(belegRepository.findNichtEmailImportierteOhneKostenstellenZuordnung().stream()
                .map(this::toBelegZuordnungDto)
                .toList());
    }

    @GetMapping("/belegdaten/{belegId}")
    public ResponseEntity<GeschaeftsdatenDto> getBelegdaten(
            @PathVariable Long belegId,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        if (!darfBelegeSehen(token, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Beleg beleg = belegRepository.findById(belegId).orElse(null);
        if (beleg == null) {
            return ResponseEntity.notFound().build();
        }
        GeschaeftsdatenDto dto = new GeschaeftsdatenDto();
        dto.id = beleg.getId();
        dto.dokumentNummer = beleg.getBelegNummer();
        dto.dokumentDatum = beleg.getBelegDatum();
        dto.betragNetto = effektiverBelegNettoBetrag(beleg);
        dto.betragBrutto = effektiverBelegBruttoBetrag(beleg);
        dto.mwstSatz = beleg.getMwstSatz();
        if (beleg.getLieferant() != null) {
            dto.lieferantId = beleg.getLieferant().getId();
            dto.lieferantName = beleg.getLieferant().getLieferantenname();
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/beleg-zuordnungen/{belegId}")
    public ResponseEntity<List<ZuordnungDto>> getBelegZuordnungen(
            @PathVariable Long belegId,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        if (!darfBelegeSehen(token, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Beleg beleg = belegRepository.findById(belegId).orElse(null);
        if (beleg == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<BelegKostenstellenAnteil> splits = belegKostenstellenAnteilRepository.findByBelegId(belegId);
        if (!splits.isEmpty()) {
            return ResponseEntity.ok(splits.stream().map(this::toZuordnungDto).toList());
        }

        if (beleg.getKostenstelle() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        return ResponseEntity.ok(List.of(toDirekteBelegZuordnungDto(beleg)));
    }

    @PostMapping("/beleg-zuordnen")
    @Transactional
    public ResponseEntity<?> zuordnenBelegKostenstellen(
            @RequestBody(required = false) BelegZuordnungRequest request,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        if (caller == null || !belegService.darfScannen(caller)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request == null || request.belegId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Beleg-ID fehlt"));
        }
        Beleg beleg = belegRepository.findById(request.belegId).orElse(null);
        if (beleg == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Beleg nicht gefunden"));
        }
        if (dokumentRepository.findByBelegId(beleg.getId()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Dieser Beleg ist bereits als Lieferanten-Dokument erfasst"));
        }
        if (request.projektAnteile == null || request.projektAnteile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Keine Kostenstellen-Zuordnung angegeben"));
        }

        FrontendUserProfile zugeordnetVon = resolveZugeordnetVon(caller, auth);

        BigDecimal nettoBetrag = effektiverBelegNettoBetrag(beleg);
        BigDecimal bruttoBetrag = effektiverBelegBruttoBetrag(beleg);
        List<VorbereiteteBelegZuordnung> vorbereiteteZuordnungen = new ArrayList<>();
        BigDecimal prozentSumme = BigDecimal.ZERO;
        BigDecimal betragSumme = BigDecimal.ZERO;
        for (ProjektAnteil anteil : request.projektAnteile) {
            if (anteil.projektId != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Belege ohne E-Mail-Import koennen nur Kostenstellen zugeordnet werden"));
            }
            if (anteil.kostenstelleId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Kostenstelle fehlt"));
            }
            Kostenstelle ks = kostenstelleRepository.findById(anteil.kostenstelleId).orElse(null);
            if (ks == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Kostenstelle nicht gefunden: " + anteil.kostenstelleId));
            }
            if (anteil.prozentanteil != null && anteil.betrag != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Nur Prozent oder Betrag angeben"));
            }
            if (anteil.prozentanteil != null) {
                if (anteil.prozentanteil.compareTo(BigDecimal.ZERO) < 0
                        || anteil.prozentanteil.compareTo(BigDecimal.valueOf(100)) > 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Prozent muss zwischen 0 und 100 liegen"));
                }
                prozentSumme = prozentSumme.add(anteil.prozentanteil);
            } else if (anteil.betrag == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Prozent oder Betrag fehlt"));
            } else {
                if (anteil.betrag.compareTo(BigDecimal.ZERO) <= 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Betrag muss groesser als 0 sein"));
                }
                betragSumme = betragSumme.add(anteil.betrag);
            }
            Integer streckungJahre = normalisiereStreckungJahre(anteil.streckungJahre);
            if (streckungJahre == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Streckung darf höchstens 20 Jahre betragen"));
            }
            vorbereiteteZuordnungen.add(new VorbereiteteBelegZuordnung(
                    ks, anteil.prozentanteil, anteil.betrag, anteil.beschreibung, streckungJahre));
        }
        if (vorbereiteteZuordnungen.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Keine gueltige Kostenstellen-Zuordnung angegeben"));
        }
        if (prozentSumme.compareTo(BigDecimal.valueOf(100)) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Summe der Prozent-Anteile darf 100% nicht ueberschreiten"));
        }
        if (nettoBetrag != null && betragSumme.compareTo(nettoBetrag) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Summe der Betraege darf den Belegbetrag nicht ueberschreiten"));
        }

        belegKostenstellenAnteilRepository.deleteByBelegId(beleg.getId());

        VorbereiteteBelegZuordnung ersteZuordnung = vorbereiteteZuordnungen.get(0);
        // Der Direkt-Shortcut (Beleg.kostenstelle ohne Split-Entity) kann keine
        // Streckung speichern — bei Streckung daher immer einen Split anlegen.
        boolean istVollstaendigeEinzelzuordnung = vorbereiteteZuordnungen.size() == 1
                && ersteZuordnung.streckungJahre() <= 1
                && ((ersteZuordnung.prozentanteil() != null
                        && ersteZuordnung.prozentanteil().compareTo(BigDecimal.valueOf(100)) == 0)
                    || (ersteZuordnung.prozentanteil() == null
                        && nettoBetrag != null
                        && ersteZuordnung.betrag() != null
                        && ersteZuordnung.betrag().compareTo(nettoBetrag) == 0));
        if (istVollstaendigeEinzelzuordnung) {
            beleg.setKostenstelle(ersteZuordnung.kostenstelle());
            belegRepository.save(beleg);
            // Auch die Kostenstelle ist Kontierung und damit protokollpflichtig.
            // Sie bleibt nach der Festschreibung aenderbar -- aber nur, weil
            // jede Aenderung daran sichtbar bleibt.
            belegAuditService.protokolliereAenderung(beleg, null,
                    "Kostenstelle zugeordnet: " + ersteZuordnung.kostenstelle().getBezeichnung(), null);
            return ResponseEntity.ok(Map.of("success", true, "zuordnungen", 1));
        }

        beleg.setKostenstelle(null);
        List<BelegKostenstellenAnteil> neueAnteile = new ArrayList<>();
        int defaultStartJahr = beleg.getBelegDatum() != null ? beleg.getBelegDatum().getYear() : LocalDate.now().getYear();
        for (VorbereiteteBelegZuordnung zuordnung : vorbereiteteZuordnungen) {
            BelegKostenstellenAnteil split = new BelegKostenstellenAnteil();
            split.setBeleg(beleg);
            split.setKostenstelle(zuordnung.kostenstelle());
            split.setBeschreibung(zuordnung.beschreibung());
            split.setZugeordnetVon(zugeordnetVon);
            split.setStreckungStartJahr(defaultStartJahr);
            split.setStreckungJahre(zuordnung.streckungJahre());
            if (zuordnung.prozentanteil() != null) {
                split.setProzent(zuordnung.prozentanteil().intValue());
            } else {
                split.setAbsoluterBetrag(zuordnung.betrag());
            }
            split.berechneAnteil(nettoBetrag, bruttoBetrag);
            neueAnteile.add(split);
        }

        belegRepository.save(beleg);
        belegKostenstellenAnteilRepository.saveAll(neueAnteile);
        belegAuditService.protokolliereAenderung(beleg, null,
                "Kostenstellen-Aufteilung neu gesetzt (" + neueAnteile.size() + " Anteile)", null);
        return ResponseEntity.ok(Map.of("success", true, "zuordnungen", neueAnteile.size()));
    }

    /**
     * Ordnet eine Rechnung anteilig Projekten zu.
     * - Erstellt BestellungProjektZuordnung für die Bestellungsübersicht
     * - Erstellt LieferantDokumentProjektAnteil für Materialkosten/Nachkalkulation
     * - Kopiert das PDF auch als ProjektDokument in die Gruppe EINGANGSRECHNUNGEN
     */
    @PostMapping("/zuordnen")
    @Transactional
    public ResponseEntity<?> zuordnenZuProjekten(
            @RequestBody ZuordnungRequest request,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        // Geschäftsdokument laden
        var gd = geschaeftsdokumentRepository.findById(request.geschaeftsdokumentId).orElse(null);
        if (gd == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Geschäftsdokument nicht gefunden"));
        }

        // LieferantDokument für die Materialkosten-Zuordnung
        LieferantDokument lieferantDokument = gd.getDokument();
        if (lieferantDokument == null) {
             return ResponseEntity.badRequest().body(Map.of("error", "Kein Basis-Dokument vorhanden"));
        }

        // Frontend-User laden (wer ordnet zu?)
        FrontendUserProfile zugeordnetVon = resolveZugeordnetVon(belegService.findCaller(token, auth), auth);

        BigDecimal absolutSumme = BigDecimal.ZERO;
        BigDecimal prozentSumme = BigDecimal.ZERO;
        BigDecimal maximalbetrag = gd.getBetragBrutto() != null ? gd.getBetragBrutto() : gd.getBetragNetto();
        for (ProjektAnteil anteil : request.projektAnteile) {
            if (anteil.prozentanteil != null && anteil.betrag != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Nur Prozent oder Betrag angeben"));
            }
            if (anteil.prozentanteil != null) {
                if (anteil.prozentanteil.compareTo(BigDecimal.ZERO) < 0
                        || anteil.prozentanteil.compareTo(BigDecimal.valueOf(100)) > 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Prozent muss zwischen 0 und 100 liegen"));
                }
                prozentSumme = prozentSumme.add(anteil.prozentanteil);
            } else if (anteil.betrag != null) {
                if (anteil.betrag.compareTo(BigDecimal.ZERO) <= 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Betrag muss groesser als 0 sein"));
                }
                absolutSumme = absolutSumme.add(anteil.betrag);
            }
        }
        if (prozentSumme.compareTo(BigDecimal.valueOf(100)) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Summe der Prozent-Anteile darf 100% nicht ueberschreiten"));
        }
        if (maximalbetrag != null && absolutSumme.compareTo(maximalbetrag) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Summe der Betraege darf den Rechnungsbetrag nicht ueberschreiten"));
        }

        // Lösche alte LieferantDokumentProjektAnteil für dieses Dokument
        List<LieferantDokumentProjektAnteil> alteAnteile = projektAnteilRepository.findByDokumentId(lieferantDokument.getId());
        projektAnteilRepository.deleteAll(alteAnteile);

        // Neue Zuordnungen erstellen
        List<LieferantDokumentProjektAnteil> neueProjektAnteile = new ArrayList<>();
        
        for (ProjektAnteil anteil : request.projektAnteile) {
            // Validierung: Entweder Projekt oder Kostenstelle
            Projekt projekt = null;
            Kostenstelle kostenstelle = null;

            if (anteil.projektId != null) {
                projekt = projektRepository.findById(anteil.projektId).orElse(null);
                if (projekt == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Projekt nicht gefunden: " + anteil.projektId));
                }
            } else if (anteil.kostenstelleId != null) {
                kostenstelle = kostenstelleRepository.findById(anteil.kostenstelleId).orElse(null);
                if (kostenstelle == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Kostenstelle nicht gefunden: " + anteil.kostenstelleId));
                }
            } else {
                // Skip invalid entries
                continue;
            }

            // LieferantDokumentProjektAnteil für Materialkosten/Nachkalkulation und Anzeige
            LieferantDokumentProjektAnteil projektAnteil = new LieferantDokumentProjektAnteil();
            projektAnteil.setDokument(lieferantDokument);
            projektAnteil.setProjekt(projekt);
            projektAnteil.setKostenstelle(kostenstelle);
            // Speichere Prozent als BigDecimal im "prozent"-Integer Feld? 
            // Nein, Anteil-Entity hat Integer für Prozent und BigDecimal für Absolut.
            // Der Request hat BigDecimal für beides.
            if (anteil.prozentanteil != null) {
                projektAnteil.setProzent(anteil.prozentanteil.intValue());
            } else {
                projektAnteil.setAbsoluterBetrag(anteil.betrag);
            }
            projektAnteil.setBeschreibung(anteil.beschreibung);

            // Kostenstreckung nur für Kostenstellen (periodische Gemeinkosten, z.B.
            // Zertifizierung alle 3 Jahre). Projekt-Anteile bleiben einmalig.
            if (kostenstelle != null) {
                Integer streckungJahre = normalisiereStreckungJahre(anteil.streckungJahre);
                if (streckungJahre == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Streckung darf höchstens 20 Jahre betragen"));
                }
                projektAnteil.setStreckungJahre(streckungJahre);
                projektAnteil.setStreckungStartJahr(gd.getDokumentDatum() != null
                        ? gd.getDokumentDatum().getYear()
                        : LocalDate.now().getYear());
            }
            
            // Betrag berechnen: Kostenstellen-Anteile werden netto verrechnet
            // (Vorsteuerabzug landet beim Finanzamt, nicht im Gemeinkostentopf).
            // berechneAnteil(netto, brutto) entscheidet anhand der gesetzten
            // Zuordnung — Projekt-Anteile bleiben brutto.
            if (gd.getBetragNetto() != null || gd.getBetragBrutto() != null) {
                projektAnteil.berechneAnteil(gd.getBetragNetto(), gd.getBetragBrutto());
            }
            neueProjektAnteile.add(projektAnteil);
            projektAnteil.setZugeordnetVon(zugeordnetVon);
            
            // PDF als ProjektDokument in EINGANGSRECHNUNGEN-Gruppe speichern (Nur bei Projektzuordnung)
            if (projekt != null) {
                copyPdfToProject(gd, projekt);
            }
        }

        projektAnteilRepository.saveAll(neueProjektAnteile);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Erfolgreich " + neueProjektAnteile.size() + " Zuordnung(en) gespeichert",
                "zuordnungen", neueProjektAnteile.size()));
    }

    /**
     * Kopiert das PDF des Geschäftsdokuments als ProjektDokument in die EINGANGSRECHNUNGEN-Gruppe.
     */
    private void copyPdfToProject(LieferantGeschaeftsdokument gd, Projekt projekt) {
        if (gd.getDokument() == null || gd.getDokument().getAttachment() == null) {
            return;
        }
        

        var attachment = gd.getDokument().getAttachment();
        var email = attachment.getEmail();
        if (email == null || email.getLieferant() == null) {
            return;
        }
        
        Long lieferantId = email.getLieferant().getId();

        String storedFilename = attachment.getStoredFilename();
        String originalFilename = attachment.getOriginalFilename();
        
        // Quellpfad (Mail-Attachment)
        Path sourcePath = Path.of(attachmentDir).toAbsolutePath().normalize()
                .resolve("email")
                .resolve(String.valueOf(lieferantId))
                .resolve(storedFilename);
        
        if (!Files.exists(sourcePath)) {
            return;
        }
        
        // Erzeuge eindeutigen gespeicherten Dateinamen für Projekt
        String lieferantName = email.getLieferant().getLieferantenname();
        String dokumentNummer = gd.getDokumentNummer() != null ? gd.getDokumentNummer() : "unbekannt";
        String gespeicherterName = "ER_%s_%s_%d.pdf".formatted(
                sanitizeFilename(lieferantName != null ? lieferantName : ""),
                sanitizeFilename(dokumentNummer),
                System.currentTimeMillis());
        
        // Zielpfad (Projekt-Uploads)
        Path projektDir = Path.of(uploadDir).toAbsolutePath().normalize()
                .resolve(String.valueOf(projekt.getId()));
        Path targetPath = projektDir.resolve(gespeicherterName);
        
        try {
            Files.createDirectories(projektDir);
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            // ProjektDokument erstellen
            ProjektDokument dok = new ProjektDokument();
            dok.setProjekt(projekt);
            dok.setOriginalDateiname(originalFilename != null ? originalFilename : storedFilename);
            dok.setGespeicherterDateiname(gespeicherterName);
            dok.setDateityp("application/pdf");
            dok.setDateigroesse(Files.size(targetPath));
            dok.setUploadDatum(LocalDate.now());
            dok.setDokumentGruppe(DokumentGruppe.EINGANGSRECHNUNGEN);
            dok.setLieferant(email.getLieferant());
            
            projektDokumentRepository.save(dok);
        } catch (IOException e) {
            // Fehler beim Kopieren protokollieren, aber nicht abbrechen
            System.err.println("Fehler beim Kopieren des PDFs: " + e.getMessage());
        }
    }
    
    private String sanitizeFilename(String name) {
        if (name == null) return "";
        return name.replaceAll("[^a-zA-Z0-9äöüÄÖÜß_-]", "_").replaceAll("_{2,}", "_");
    }


    /**
     * Markiert eine Rechnung als Lagerbestellung (keine Projektzuordnung nötig).
     */
    @PostMapping("/lagerbestellung/{dokId}")
    @Transactional
    public ResponseEntity<?> markiereAlsLagerbestellung(@PathVariable Long dokId) {
        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Geschäftsdokument nicht gefunden"));
        }

        gd.setLagerbestellung(true);
        geschaeftsdokumentRepository.save(gd);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Als Lagerbestellung markiert"));
    }

    /**
     * Hebt die Zuordnung einer Rechnung zu allen Projekten auf.
     * Die Rechnung wird wieder als "Abgeschlossen" angezeigt.
     */
    @DeleteMapping("/zuordnung/{dokId}")
    @Transactional
    public ResponseEntity<?> hebeZuordnungAuf(@PathVariable Long dokId) {
        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Geschäftsdokument nicht gefunden"));
        }

        // Lösche alle LieferantDokumentProjektAnteil
        if (gd.getDokument() != null) {
            List<LieferantDokumentProjektAnteil> anteile = projektAnteilRepository.findByDokumentId(gd.getDokument().getId());
            projektAnteilRepository.deleteAll(anteile);
        }

        // Optional: Lagerbestellung-Flag zurücksetzen
        gd.setLagerbestellung(false);
        geschaeftsdokumentRepository.save(gd);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Zuordnung aufgehoben - Rechnung wieder in 'Abgeschlossen'"));
    }

    /**
     * Gibt die Zuordnungen für ein Geschäftsdokument zurück.
     */
    @GetMapping("/zuordnungen/{dokId}")
    public ResponseEntity<List<ZuordnungDto>> getZuordnungen(@PathVariable Long dokId) {
        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null || gd.getDokument() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<LieferantDokumentProjektAnteil> anteile = projektAnteilRepository.findByDokumentId(gd.getDokument().getId());

        List<ZuordnungDto> dtos = anteile.stream().map(a -> {
            ZuordnungDto dto = new ZuordnungDto();
            dto.id = a.getId();
            if (a.getProjekt() != null) {
                dto.projektId = a.getProjekt().getId();
                dto.projektName = a.getProjekt().getBauvorhaben();
            } else if (a.getKostenstelle() != null) {
                dto.kostenstelleId = a.getKostenstelle().getId();
                dto.kostenstelleName = a.getKostenstelle().getBezeichnung();
            }
            dto.betrag = a.getBerechneterBetrag();
            dto.prozentanteil = a.getProzent() != null ? BigDecimal.valueOf(a.getProzent()) : null;
            dto.beschreibung = a.getBeschreibung();
            dto.zugeordnetAm = a.getZugeordnetAm();
            if (a.getZugeordnetVon() != null) {
                dto.zugeordnetVonName = a.getZugeordnetVon().getDisplayName();
            }
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Gibt alle Zuordnungen für eine Kostenstelle zurück (aus beiden Quellen).
     */
    @GetMapping("/zuordnungen/kostenstelle/{kostenstelleId}")
    public ResponseEntity<List<ZuordnungDto>> getZuordnungenForKostenstelle(
            @PathVariable Long kostenstelleId,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {

        List<ZuordnungDto> dtos = ladeZuordnungenForKostenstelle(kostenstelleId, darfBelegeSehen(token, auth));

        dtos.sort(Comparator
                .comparing((ZuordnungDto z) -> z.dokumentDatum, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(z -> z.zugeordnetAm, Comparator.nullsLast(Comparator.reverseOrder())));

        return ResponseEntity.ok(dtos);
    }

    /**
     * Lädt alle Zuordnungen einer Kostenstelle aus den drei Quellen
     * (Lieferanten-Dokument-Anteile, Beleg-Splits, direkt zugeordnete Belege).
     * Beleg-Quellen nur, wenn der Aufrufer Belege sehen darf.
     */
    private List<ZuordnungDto> ladeZuordnungenForKostenstelle(Long kostenstelleId, boolean darfBelegeSehen) {
        // Quelle: LieferantDokumentProjektAnteil (Dokumentenverwaltung)
        List<ZuordnungDto> dtos = projektAnteilRepository.findByKostenstelleId(kostenstelleId).stream()
                .map(this::toZuordnungDto)
                .collect(Collectors.toList());

        if (darfBelegeSehen) {
            belegKostenstellenAnteilRepository.findByKostenstelleIdEager(kostenstelleId).stream()
                    .map(this::toZuordnungDto)
                    .forEach(dtos::add);

            belegRepository.findDirektZugeordneteByKostenstelleOhneSplits(kostenstelleId).stream()
                    .map(this::toDirekteBelegZuordnungDto)
                    .forEach(dtos::add);
        }

        return dtos;
    }

    /**
     * Auswertung aller aktiven Kostenstellen für ein Geschäftsjahr inklusive
     * Vorjahresvergleich. Optional auf einen einzelnen Monat eingrenzbar.
     * Speist sowohl die Kostenstellen-Übersicht als auch das
     * Vorjahresvergleichs-Diagramm der Erfolgsanalyse.
     */
    @GetMapping("/kostenstellen/auswertung")
    @Transactional(readOnly = true)
    public ResponseEntity<List<KostenstelleAuswertungDto>> getKostenstellenAuswertung(
            @RequestParam int jahr,
            @RequestParam(value = "monat", required = false) Integer monat,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {

        boolean darfBelegeSehen = darfBelegeSehen(token, auth);

        List<KostenstelleAuswertungDto> result = new ArrayList<>();
        for (Kostenstelle ks : kostenstelleRepository.findByAktivTrueOrderBySortierungAsc()) {
            BigDecimal summeDiesesJahr = BigDecimal.ZERO;
            BigDecimal summeVorjahr = BigDecimal.ZERO;
            long anzahlDiesesJahr = 0;

            for (ZuordnungDto z : ladeZuordnungenForKostenstelle(ks.getId(), darfBelegeSehen)) {
                if (z.dokumentDatum == null || z.betrag == null) {
                    continue;
                }
                if (monat != null && z.dokumentDatum.getMonthValue() != monat) {
                    continue;
                }
                // Kostenstreckung: Kosten werden über mehrere Jahre verteilt (z.B.
                // Zertifizierung alle 3 Jahre). Pro Jahr zählt nur der Jahresanteil,
                // und das für jedes Jahr im Streckungsfenster. Ohne Streckung
                // (streckungJahre = 1) bleibt das Verhalten identisch zum Rechnungsjahr.
                int streckungJahre = (z.streckungJahre != null && z.streckungJahre >= 1) ? z.streckungJahre : 1;
                int startJahr = z.streckungStartJahr != null ? z.streckungStartJahr : z.dokumentDatum.getYear();
                BigDecimal jahresBetrag = z.jahresanteil != null
                        ? z.jahresanteil
                        : z.betrag.divide(BigDecimal.valueOf(streckungJahre), 2, java.math.RoundingMode.HALF_UP);

                if (jahr >= startJahr && jahr < startJahr + streckungJahre) {
                    summeDiesesJahr = summeDiesesJahr.add(jahresBetrag);
                    anzahlDiesesJahr++;
                }
                int vorjahr = jahr - 1;
                if (vorjahr >= startJahr && vorjahr < startJahr + streckungJahre) {
                    summeVorjahr = summeVorjahr.add(jahresBetrag);
                }
            }

            result.add(new KostenstelleAuswertungDto(
                    ks.getId(),
                    ks.getBezeichnung(),
                    ks.getTyp().name(),
                    ks.getBeschreibung(),
                    ks.isIstFixkosten(),
                    ks.isIstInvestition(),
                    summeDiesesJahr,
                    summeVorjahr,
                    anzahlDiesesJahr));
        }

        return ResponseEntity.ok(result);
    }

    private ZuordnungDto toZuordnungDto(LieferantDokumentProjektAnteil a) {
        ZuordnungDto dto = new ZuordnungDto();
        dto.id = a.getId();
        dto.quelle = "LIEFERANT_DOKUMENT";
        if (a.getProjekt() != null) {
            dto.projektId = a.getProjekt().getId();
            dto.projektName = a.getProjekt().getBauvorhaben();
        }
        if (a.getKostenstelle() != null) {
            dto.kostenstelleId = a.getKostenstelle().getId();
            dto.kostenstelleName = a.getKostenstelle().getBezeichnung();
        }
        dto.betrag = a.getBerechneterBetrag();
        dto.prozentanteil = a.getProzent() != null ? BigDecimal.valueOf(a.getProzent()) : null;
        dto.beschreibung = a.getBeschreibung();
        dto.streckungJahre = a.getStreckungJahre();
        dto.streckungStartJahr = a.getStreckungStartJahr();
        dto.jahresanteil = a.getJahresanteil();
        if (a.getZugeordnetAm() != null) {
            dto.zugeordnetAm = a.getZugeordnetAm();
        } else if (a.getDokument().getUploadDatum() != null) {
            dto.zugeordnetAm = a.getDokument().getUploadDatum();
        }
        if (a.getZugeordnetVon() != null) {
            dto.zugeordnetVonName = a.getZugeordnetVon().getDisplayName();
        }
        if (a.getDokument().getLieferant() != null) {
            dto.lieferantName = a.getDokument().getLieferant().getLieferantenname();
        }
        if (a.getDokument().getGeschaeftsdaten() != null) {
            dto.geschaeftsdokumentId = a.getDokument().getGeschaeftsdaten().getId();
            dto.bestellnummer = a.getDokument().getGeschaeftsdaten().getBestellnummer();
            dto.dokumentDatum = a.getDokument().getGeschaeftsdaten().getDokumentDatum();
        }
        dto.dokumentId = a.getDokument().getId();
        return dto;
    }

    private ZuordnungDto toZuordnungDto(BelegKostenstellenAnteil a) {
        ZuordnungDto dto = new ZuordnungDto();
        dto.id = a.getId();
        dto.quelle = "BELEG_SPLIT";
        dto.belegId = a.getBeleg().getId();
        dto.kostenstelleId = a.getKostenstelle().getId();
        dto.kostenstelleName = a.getKostenstelle().getBezeichnung();
        dto.betrag = a.getBerechneterBetrag();
        dto.prozentanteil = a.getProzent() != null ? BigDecimal.valueOf(a.getProzent()) : null;
        dto.beschreibung = a.getBeschreibung();
        dto.streckungJahre = a.getStreckungJahre();
        dto.streckungStartJahr = a.getStreckungStartJahr();
        dto.jahresanteil = a.getJahresanteil();
        dto.zugeordnetAm = a.getZugeordnetAm();
        if (a.getZugeordnetVon() != null) {
            dto.zugeordnetVonName = a.getZugeordnetVon().getDisplayName();
        }
        Beleg beleg = a.getBeleg();
        dto.lieferantName = beleg.getLieferant() != null ? beleg.getLieferant().getLieferantenname() : null;
        dto.bestellnummer = beleg.getBelegNummer();
        dto.dokumentDatum = beleg.getBelegDatum();
        return dto;
    }

    private ZuordnungDto toDirekteBelegZuordnungDto(Beleg beleg) {
        ZuordnungDto dto = new ZuordnungDto();
        dto.id = beleg.getId();
        dto.quelle = "BELEG_DIREKT";
        dto.belegId = beleg.getId();
        dto.kostenstelleId = beleg.getKostenstelle().getId();
        dto.kostenstelleName = beleg.getKostenstelle().getBezeichnung();
        dto.betrag = effektiverBelegNettoBetrag(beleg);
        dto.prozentanteil = BigDecimal.valueOf(100);
        dto.streckungJahre = 1;
        dto.streckungStartJahr = beleg.getBelegDatum() != null ? beleg.getBelegDatum().getYear() : null;
        dto.jahresanteil = dto.betrag;
        dto.beschreibung = beleg.getBeschreibung();
        dto.zugeordnetAm = beleg.getValidiertAm() != null ? beleg.getValidiertAm() : beleg.getUploadDatum();
        dto.lieferantName = beleg.getLieferant() != null ? beleg.getLieferant().getLieferantenname() : null;
        dto.bestellnummer = beleg.getBelegNummer();
        dto.dokumentDatum = beleg.getBelegDatum();
        return dto;
    }

    private BelegZuordnungDto toBelegZuordnungDto(Beleg beleg) {
        BelegZuordnungDto dto = new BelegZuordnungDto();
        dto.id = beleg.getId();
        dto.belegNummer = beleg.getBelegNummer();
        dto.belegDatum = beleg.getBelegDatum();
        dto.beschreibung = beleg.getBeschreibung();
        dto.betragNetto = effektiverBelegNettoBetrag(beleg);
        dto.betragBrutto = effektiverBelegBruttoBetrag(beleg);
        dto.lieferantName = beleg.getLieferant() != null ? beleg.getLieferant().getLieferantenname() : null;
        dto.originalDateiname = beleg.getOriginalDateiname();
        dto.mimeType = beleg.getMimeType();
        dto.pdfUrl = "/api/buchhaltung/belege/" + beleg.getId() + "/datei";
        return dto;
    }

    // Die Regel "Firmenanteil statt Originalbetrag" liegt jetzt an der
    // Beleg-Entity, damit PC- und Handy-Pfad garantiert dasselbe rechnen.
    private BigDecimal effektiverBelegNettoBetrag(Beleg beleg) {
        return beleg.getBuchungsbetragNetto();
    }

    private BigDecimal effektiverBelegBruttoBetrag(Beleg beleg) {
        return beleg.getBuchungsbetragBrutto();
    }

    private boolean darfBelegeSehen(String token, Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        return caller != null && belegService.darfSehen(caller);
    }

    private boolean darfBelegeBearbeiten(String token, Authentication auth) {
        Mitarbeiter caller = belegService.findCaller(token, auth);
        return caller != null && belegService.darfScannen(caller);
    }

    private FrontendUserProfile resolveZugeordnetVon(Mitarbeiter caller, Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof FrontendUserPrincipal principal && principal.getId() != null) {
            FrontendUserProfile sessionProfile = frontendUserProfileRepository.findById(principal.getId()).orElse(null);
            if (sessionProfile != null && sessionProfile.isActive()) {
                if (caller == null || sessionProfile.getMitarbeiter() == null
                        || Objects.equals(sessionProfile.getMitarbeiter().getId(), caller.getId())) {
                    return sessionProfile;
                }
            }
        }
        if (caller != null && caller.getId() != null) {
            return frontendUserProfileRepository.findByMitarbeiterIdAndActiveTrue(caller.getId()).orElse(null);
        }
        return null;
    }

    /**
     * Normalisiert die Streckungs-Jahre einer Kostenstellen-Zuordnung: null/&lt;1 wird zu 1
     * (keine Streckung). Gibt {@code null} zurück, wenn der Wert die zulässige Obergrenze
     * (20 Jahre) überschreitet — der Aufrufer antwortet dann mit Bad Request.
     */
    private static Integer normalisiereStreckungJahre(Integer streckungJahre) {
        int wert = (streckungJahre != null && streckungJahre >= 1) ? streckungJahre : 1;
        return wert > 20 ? null : wert;
    }

    private record VorbereiteteBelegZuordnung(
            Kostenstelle kostenstelle,
            BigDecimal prozentanteil,
            BigDecimal betrag,
            String beschreibung,
            int streckungJahre) {
    }

    /**
     * Bildet Dokumenten-Ketten basierend auf Verknüpfungen.
     */
    List<DokumentenKette> buildKetten(List<LieferantDokument> dokumente) {
        // Map für schnellen Zugriff
        Map<Long, LieferantDokument> dokMap = dokumente.stream()
                .collect(Collectors.toMap(LieferantDokument::getId, d -> d));

        // Set für bereits verarbeitete Dokumente
        Set<Long> verarbeitet = new HashSet<>();
        List<DokumentenKette> ketten = new ArrayList<>();

        for (LieferantDokument dok : dokumente) {
            if (verarbeitet.contains(dok.getId()))
                continue;

            // Sammle alle verknüpften Dokumente
            Set<Long> kettenIds = new HashSet<>();
            collectKettenIds(dok, dokMap, kettenIds);

            if (!kettenIds.isEmpty()) {
                List<DokumentRef> refs = new ArrayList<>();
                LieferantDokument first = null;
                for (Long id : kettenIds) {
                    LieferantDokument d = dokMap.get(id);
                    if (d != null) {
                        refs.add(toDokumentRef(d));
                        verarbeitet.add(id);
                        if (first == null) {
                            first = d;
                        }
                    }
                }

                // Falls kein einziges verknüpftes Dokument in der gefilterten Liste war, Kette überspringen
                if (refs.isEmpty() || first == null) {
                    continue;
                }

                // Sortiere nach Typ-Reihenfolge
                // Sortieren: Erst nach Typ-Rang, dann nach Datum
                refs.sort(Comparator.comparingInt((DokumentRef r) -> getTypReihenfolge(r.typ))
                        .thenComparing(r -> r.dokumentDatum, Comparator.nullsLast(Comparator.naturalOrder())));

                String lieferantName = first.getLieferant() != null
                        ? first.getLieferant().getLieferantenname()
                        : null;
                Long lieferantId = first.getLieferant() != null
                        ? first.getLieferant().getId()
                        : null;

                ketten.add(new DokumentenKette(
                        UUID.randomUUID().toString(),
                        lieferantId,
                        lieferantName,
                        refs));
            }
        }

        return ketten;
    }

    private void collectKettenIds(LieferantDokument dok, Map<Long, LieferantDokument> dokMap, Set<Long> collected) {
        if (dok == null || collected.contains(dok.getId()))
            return;
        collected.add(dok.getId());

        // Verknüpfte Dokumente durchlaufen
        if (dok.getVerknuepfteDokumente() != null) {
            for (LieferantDokument verknuepft : dok.getVerknuepfteDokumente()) {
                collectKettenIds(verknuepft, dokMap, collected);
            }
        }
    }

    private int getTypReihenfolge(LieferantDokumentTyp typ) {
        return switch (typ) {
            case ANGEBOT -> 0;
            case AUFTRAGSBESTAETIGUNG -> 1;
            case LIEFERSCHEIN -> 2;
            case RECHNUNG -> 3;
            case GUTSCHRIFT -> 4;
            case SONSTIG -> 99;
            case BELEG -> 100; // gehört nicht zur Bestellkette, wird hier nicht erwartet
        };
    }

    private DokumentRef toDokumentRef(LieferantDokument d) {
        DokumentRef ref = new DokumentRef();
        ref.id = d.getId();
        ref.typ = d.getTyp();
        ref.dateiname = d.getEffektiverDateiname();

        if (d.getGeschaeftsdaten() != null) {
            var gd = d.getGeschaeftsdaten();
            ref.dokumentNummer = gd.getDokumentNummer();
            ref.dokumentDatum = gd.getDokumentDatum();
            ref.betragBrutto = gd.getBetragBrutto() != null ? gd.getBetragBrutto().doubleValue() : null;
            ref.betragNetto = gd.getBetragNetto() != null ? gd.getBetragNetto().doubleValue() : null;
            ref.liefertermin = gd.getLiefertermin();
        }

        // PDF-URL: Mehrere Quellen prüfen
        // 1. E-Mail Attachment
        if (d.getAttachment() != null && d.getAttachment().getEmail() != null) {
            var att = d.getAttachment();
            ref.pdfUrl = "/api/emails/" + att.getEmail().getId() +
                    "/attachments/" + att.getId();
        } 
        // 2. Gespeicherte Datei über Lieferant
        else if (d.getLieferant() != null && d.getGespeicherterDateiname() != null) {
            ref.pdfUrl = "/api/lieferanten/" + d.getLieferant().getId() +
                    "/dokumente/" + d.getId() + "/download";
        }
        // 3. Fallback: Generischer Dokument-Endpoint
        else if (d.getId() != null) {
            ref.pdfUrl = "/api/lieferant-dokumente/" + d.getId() + "/download";
        }

        return ref;
    }

    // ========== DTOs ==========

    public record BestellungsUebersichtDto(
            List<DokumentenKette> offeneAnfragen,
            List<DokumentenKette> laufendeBestellungen,
            List<DokumentenKette> abgeschlossen,
            List<DokumentenKette> zugeordnet,
            List<DokumentenKette> ausgeblendet) {
    }

    public record AusblendenRequest(
            @NotEmpty
            @Size(max = 500)
            List<Long> dokumentIds) {
    }

    public record DokumentenKette(
            String id,
            Long lieferantId,
            String lieferantName,
            List<DokumentRef> dokumente) {
    }

    public static class DokumentRef {
        public Long id;
        public LieferantDokumentTyp typ;
        public String dokumentNummer;
        public LocalDate dokumentDatum;
        public Double betragBrutto;
        public Double betragNetto;
        public LocalDate liefertermin;
        public String dateiname;
        public String pdfUrl;
    }

    public static class GeschaeftsdatenDto {
        public Long id;
        public String dokumentNummer;
        public LocalDate dokumentDatum;
        public BigDecimal betragNetto;
        public BigDecimal betragBrutto;
        public BigDecimal mwstSatz;
        public LocalDate liefertermin;
        public String bestellnummer;
        public Long lieferantId;
        public String lieferantName;
        public Boolean istLagerbestellung;
    }

    public static class ZuordnungRequest {
        public Long geschaeftsdokumentId;
        public Long frontendUserProfileId;
        public List<ProjektAnteil> projektAnteile;
    }

    public static class BelegZuordnungRequest {
        public Long belegId;
        public Long frontendUserProfileId;
        public List<ProjektAnteil> projektAnteile;
    }

    public static class ProjektAnteil {
        public Long projektId;
        public Long kostenstelleId; // Optional
        public BigDecimal betrag;
        public BigDecimal prozentanteil;
        public String beschreibung;
        /**
         * Über wie viele Jahre die Kosten verteilt werden sollen (nur Kostenstellen).
         * Null oder 1 = keine Streckung. Beispiel: Zertifizierung alle 3 Jahre = 3.
         */
        public Integer streckungJahre;
    }

    public static class ZuordnungDto {
        public Long id;
        public String quelle;
        public Long projektId;
        public String projektName;
        public Long kostenstelleId;
        public String kostenstelleName;
        public BigDecimal betrag;
        public BigDecimal prozentanteil;
        public String beschreibung;
        public LocalDateTime zugeordnetAm;
        public String zugeordnetVonName;

        // Kostenstreckung (periodische Gemeinkosten über mehrere Jahre)
        public Integer streckungJahre;
        public Integer streckungStartJahr;
        public BigDecimal jahresanteil;

        // Extra Info
        public String lieferantName;
        public String bestellnummer;
        public LocalDate dokumentDatum;
        public Long geschaeftsdokumentId;
        public Long dokumentId;
        public Long belegId;
    }

    public static class BelegZuordnungDto {
        public Long id;
        public String belegNummer;
        public LocalDate belegDatum;
        public String beschreibung;
        public BigDecimal betragNetto;
        public BigDecimal betragBrutto;
        public String lieferantName;
        public String originalDateiname;
        public String mimeType;
        public String pdfUrl;
    }

    public record KostenstelleDto(Long id, String bezeichnung, String typ, String beschreibung) {}

    /**
     * Kostenstelle mit aggregierten Kosten für ein Jahr inkl. Vorjahresvergleich.
     */
    public record KostenstelleAuswertungDto(
            Long id,
            String bezeichnung,
            String typ,
            String beschreibung,
            boolean istFixkosten,
            boolean istInvestition,
            BigDecimal summeDiesesJahr,
            BigDecimal summeVorjahr,
            long anzahlDiesesJahr) {
    }
}
