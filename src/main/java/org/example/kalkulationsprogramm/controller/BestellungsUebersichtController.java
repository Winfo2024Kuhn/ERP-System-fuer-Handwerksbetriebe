package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungsUebersichtDto;
import org.example.kalkulationsprogramm.dto.Bestellung.RechnungsVorschlagDto;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.BelegAbgelehntException;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.BestellungsUebersichtService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZuordnungService;
import org.example.kalkulationsprogramm.service.RechnungsVorschlagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST-Endpoints der Bestellungs-Übersicht: Dokumenten-Ketten, Rechnungs-Vorschläge,
 * Ein-/Ausblenden und Kosten-Zuordnung. Wie die Ketten gebildet, eingeordnet und
 * mit Rechnungen abgeglichen werden, steht im {@link BestellungsUebersichtService}.
 */
@RestController
@RequestMapping("/api/bestellungen-uebersicht")
@RequiredArgsConstructor
public class BestellungsUebersichtController {

    private final LieferantDokumentRepository dokumentRepository;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    private final KostenstelleRepository kostenstelleRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;
    private final BelegRepository belegRepository;
    private final BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    private final BelegService belegService;
    private final org.example.kalkulationsprogramm.service.BelegAuditService belegAuditService;
    private final RechnungsVorschlagService rechnungsVorschlagService;
    private final LieferantDokumentService lieferantDokumentService;
    private final BestellungsUebersichtService bestellungsUebersichtService;
    private final LieferantDokumentZuordnungService zuordnungService;

    /** Höchstzahl Dokumente einer Kette für die Vorschlagssuche. */
    private static final int MAX_KETTEN_DOKUMENTE = 50;


    /**
     * Gibt alle Dokumenten-Ketten gruppiert nach Status zurück.
     */
    @GetMapping
    public ResponseEntity<BestellungsUebersichtDto> getUebersicht() {
        return ResponseEntity.ok(bestellungsUebersichtService.ladeUebersicht());
    }

    /**
     * Rechnungen, bewertet gegen die Dokumente einer Bestellung – beste zuerst,
     * bei gleicher Quote die zeitlich nächste. Für das Fenster „Rechnung suchen“.
     *
     * <p>Kandidaten sind alle Rechnungen desselben Lieferanten, auch ausgeblendete,
     * bezahlte und schon anderswo verknüpfte (Teillieferungen, Teilrechnungen).
     * Rechnungen, die schon in dieser Kette hängen, fehlen.
     *
     * @param dokumentIds     die Dokumente der Bestellungs-Kette (AB, Lieferschein, …)
     * @param alleLieferanten auch Rechnungen anderer Lieferanten (KI hat den
     *                        Lieferanten falsch erkannt)
     */
    @GetMapping("/rechnung-vorschlaege")
    public ResponseEntity<List<RechnungsVorschlagDto>> getRechnungsVorschlaege(
            @RequestParam("dokumentIds") List<Long> dokumentIds,
            @RequestParam(value = "alleLieferanten", defaultValue = "false") boolean alleLieferanten) {
        if (dokumentIds == null || dokumentIds.isEmpty() || dokumentIds.size() > MAX_KETTEN_DOKUMENTE
                || dokumentIds.stream().anyMatch(id -> id == null || id <= 0)) {
            return ResponseEntity.badRequest().build();
        }
        return bestellungsUebersichtService.rechnungsVorschlaege(dokumentIds, alleLieferanten)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Hängt eine Rechnung an eine Bestellung. Mehrfach erlaubt: eine Rechnung an
     * mehreren Lieferscheinen (Teillieferungen), mehrere Rechnungen an einer AB
     * (Teilrechnungen). Danach rutscht die Kette nach „Rechnung zuordnen“ bzw.
     * „Erledigt“.
     */
    @PostMapping("/rechnung-verknuepfen")
    public ResponseEntity<?> rechnungVerknuepfen(@Valid @RequestBody RechnungVerknuepfenRequest request,
            Authentication auth) {
        try {
            rechnungsVorschlagService.verknuepfe(request.bestellDokumentId(), request.rechnungDokumentId(),
                    benutzerId(auth));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", nutzerMeldung(e,
                    "Die Rechnung konnte nicht zugeordnet werden.")));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * „Gehört nicht dazu“: löst alle Verknüpfungen eines Dokuments (zu Vorgängern
     * und Nachfolgern). Der automatische Abgleich verknüpft die gelösten Paare
     * nicht wieder.
     */
    @PostMapping("/abhaengen")
    public ResponseEntity<?> abhaengen(@Valid @RequestBody AbhaengenRequest request, Authentication auth) {
        if (request == null || request.dokumentId() == null || request.dokumentId() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Dokument fehlt."));
        }
        try {
            int geloest = rechnungsVorschlagService.haengeAb(request.dokumentId(), benutzerId(auth));
            return ResponseEntity.ok(Map.of("geloest", geloest));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Papierrechnung von der Karte hochladen: legt eine Rechnung beim Lieferanten
     * der Bestellung an, hängt sie sofort an das Bestelldokument und liest sie im
     * Hintergrund aus. Erlaubt sind PDF, JPG und PNG bis 25 MB.
     */
    @PostMapping(value = "/rechnung-hochladen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> rechnungHochladen(
            @RequestParam("datei") MultipartFile datei,
            @RequestParam("bestellDokumentId") Long bestellDokumentId,
            Authentication auth) {
        if (bestellDokumentId == null || bestellDokumentId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bestellung fehlt."));
        }
        try {
            LieferantDokument rechnung = lieferantDokumentService.rechnungZuBestellungHochladen(
                    bestellDokumentId, datei, hochladenderMitarbeiter(auth));
            return ResponseEntity.ok(BestellungsUebersichtService.toDokumentRef(rechnung));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    nutzerMeldung(e, "Die Rechnung konnte nicht angelegt werden.")));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Die Datei konnte nicht gespeichert werden."));
        }
    }

    /** Nur eigene, für Nutzer formulierte Meldungen durchreichen – technische nie. */
    private static String nutzerMeldung(RuntimeException e, String ersatz) {
        return e instanceof BelegAbgelehntException && e.getMessage() != null ? e.getMessage() : ersatz;
    }

    private static Long benutzerId(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof FrontendUserPrincipal principal
                ? principal.getId() : null;
    }

    private Mitarbeiter hochladenderMitarbeiter(Authentication auth) {
        Long profilId = benutzerId(auth);
        if (profilId == null || frontendUserProfileRepository == null) {
            return null;
        }
        return frontendUserProfileRepository.findById(profilId)
                .map(FrontendUserProfile::getMitarbeiter)
                .orElse(null);
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
        if (!beleg.istKostenrelevant()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Dieser Kassenvorgang kann keiner Kostenstelle zugeordnet werden"));
        }
        // Ein Lieferschein aus dem Scanner trägt keine Kosten – nur Rechnung/Gutschrift.
        if (dokumentRepository.findByBelegId(beleg.getId())
                .filter(d -> d.getTyp() != LieferantDokumentTyp.LIEFERSCHEIN).isPresent()) {
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
            Integer streckungJahre = LieferantDokumentZuordnungService.normalisiereStreckungJahre(anteil.streckungJahre);
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
    public ResponseEntity<?> zuordnenZuProjekten(
            @RequestBody ZuordnungRequest request,
            @RequestParam(value = "token", required = false) String token,
            Authentication auth) {
        if (request == null || request.geschaeftsdokumentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Geschäftsdokument nicht gefunden"));
        }
        FrontendUserProfile zugeordnetVon = zuordnungService.zugeordnetVon(belegService.findCaller(token, auth), auth);
        List<LieferantDokumentZuordnungService.Anteil> anteile = request.projektAnteile == null ? List.of()
                : request.projektAnteile.stream()
                        .filter(Objects::nonNull)
                        .map(a -> new LieferantDokumentZuordnungService.Anteil(a.projektId, a.kostenstelleId,
                                a.betrag, a.prozentanteil, a.beschreibung, a.streckungJahre))
                        .toList();
        try {
            int anzahl = zuordnungService.speichereAnteile(request.geschaeftsdokumentId, anteile, zugeordnetVon);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Erfolgreich " + anzahl + " Zuordnung(en) gespeichert",
                    "zuordnungen", anzahl));
        } catch (NoSuchElementException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
    public ResponseEntity<?> hebeZuordnungAuf(@PathVariable Long dokId) {
        try {
            zuordnungService.hebeZuordnungAuf(dokId);
        } catch (NoSuchElementException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
        return zuordnungService.zugeordnetVon(caller, auth);
    }

    private record VorbereiteteBelegZuordnung(
            Kostenstelle kostenstelle,
            BigDecimal prozentanteil,
            BigDecimal betrag,
            String beschreibung,
            int streckungJahre) {
    }

    // ========== DTOs ==========

    public record AusblendenRequest(
            @NotEmpty
            @Size(max = 500)
            List<Long> dokumentIds) {
    }

    public record RechnungVerknuepfenRequest(
            @NotNull @Positive Long bestellDokumentId,
            @NotNull @Positive Long rechnungDokumentId) {
    }

    public record AbhaengenRequest(@NotNull @Positive Long dokumentId) {
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
