package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.BelegAbgelehntException;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.LieferantDokumentAbgleich;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.RechnungsVorschlagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
 * - Laufende Bestellungen (AB oder Lieferschein vorhanden, keine Rechnung)
 * - Abgeschlossen (Rechnung vorhanden, noch nicht zugeordnet)
 * - Zugeordnet (Rechnung vorhanden und Projekten zugeordnet)
 * - Ausgeblendet (erledigt: bezahlte/ausgeblendete Rechnung oder alles ausgeblendet)
 *
 * <p>Ketten entstehen aus ALLEN Dokumenten, auch ausgeblendeten: Bezahlte
 * Rechnungen sind fast immer ausgeblendet – ohne sie stünde ihr Lieferschein
 * dauerhaft unter „Rechnung fehlt“.
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
    private final RechnungsVorschlagService rechnungsVorschlagService;
    private final LieferantDokumentService lieferantDokumentService;

    /** Höchstzahl Dokumente einer Kette für die Vorschlagssuche. */
    private static final int MAX_KETTEN_DOKUMENTE = 50;
    /** Kartenvorschlag: nur Rechnungen von so vielen Tagen vor … */
    static final int KARTE_TAGE_VORHER = 30;
    /** … bis so vielen Tagen nach einem Bestelldokument. */
    static final int KARTE_TAGE_NACHHER = 180;
    /** Das Fenster „Rechnung suchen“ zeigt höchstens so viele Rechnungen (beste zuerst). */
    private static final int MAX_VORSCHLAEGE = 200;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.mail-attachment-dir}")
    private String attachmentDir;

    /**
     * Gibt alle Dokumenten-Ketten gruppiert nach Status zurück.
     */
    @GetMapping
    public ResponseEntity<BestellungsUebersichtDto> getUebersicht() {
        Uebersicht uebersicht = ladeUebersicht();
        BestellungsUebersichtDto dto = uebersicht.dto();
        var speicher = rechnungsVorschlagService.neuerSpeicher();
        // Jede laufende Bestellung bekommt die wahrscheinlichste Rechnung als Vorschlag
        List<DokumentenKette> laufendMitVorschlag = dto.laufendeBestellungen().stream()
                .map(kette -> kette.mitVorschlag(besterVorschlag(kette, uebersicht.bestand(), speicher)))
                .toList();
        return ResponseEntity.ok(new BestellungsUebersichtDto(
                dto.offeneAnfragen(), laufendMitVorschlag, dto.abgeschlossen(), dto.zugeordnet(), dto.ausgeblendet()));
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
        Dokumentbestand bestand = ladeDokumente();
        List<LieferantDokument> bestellDokumente = dokumentIds.stream()
                .map(bestand.nachId()::get)
                .filter(Objects::nonNull)
                .filter(RechnungsVorschlagService::istBestellDokument)
                .toList();
        if (bestellDokumente.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Set<Long> kettenIds = new HashSet<>();
        bestellDokumente.forEach(d -> collectKettenIds(d, kettenIds));
        Long lieferantId = bestellDokumente.stream()
                .map(d -> d.getLieferant() != null ? d.getLieferant().getId() : null)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        List<LieferantDokument> kandidaten = (alleLieferanten ? bestand.rechnungen() : bestand.rechnungenVon(lieferantId))
                .stream()
                .filter(r -> !kettenIds.contains(r.getId()))
                .toList();
        var vorschlaege = rechnungsVorschlagService.bewerte(bestellDokumente, kandidaten,
                lieferantId != null ? bestand.dokumenteVon(lieferantId) : null, rechnungsVorschlagService.neuerSpeicher());
        int anzahl = Math.min(vorschlaege.size(), MAX_VORSCHLAEGE);
        List<RechnungsVorschlagDto> liste = new ArrayList<>(anzahl);
        for (int i = 0; i < anzahl; i++) {
            int quote = vorschlaege.get(i).trefferquote();
            // Gleichstand mit dem Nachbarn: dann ist der Vorschlag nicht eindeutig
            boolean gleichauf = (i > 0 && vorschlaege.get(i - 1).trefferquote() == quote)
                    || (i + 1 < vorschlaege.size() && vorschlaege.get(i + 1).trefferquote() == quote);
            liste.add(toVorschlagDto(vorschlaege.get(i), !gleichauf));
        }
        return ResponseEntity.ok(liste);
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
            return ResponseEntity.ok(toDokumentRef(rechnung));
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

    private RechnungsVorschlagDto besterVorschlag(DokumentenKette kette, Dokumentbestand bestand,
            LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        List<LieferantDokument> bestellDokumente = kette.dokumente().stream()
                .map(ref -> bestand.nachId().get(ref.id))
                .filter(Objects::nonNull)
                .toList();
        // Auf der Karte nur Rechnungen desselben Lieferanten (auch ausgeblendete): hält
        // die Übersicht schnell. Fremde Lieferanten zeigt das Fenster „Rechnung suchen“.
        if (kette.lieferantId() == null) {
            return null;
        }
        Set<Long> kettenIds = kette.dokumente().stream().map(d -> d.id).collect(Collectors.toSet());
        List<LocalDate> bestellDaten = bestellDokumente.stream()
                .filter(RechnungsVorschlagService::istBestellDokument)
                .map(d -> d.getGeschaeftsdaten() != null ? d.getGeschaeftsdaten().getDokumentDatum() : null)
                .filter(Objects::nonNull)
                .toList();
        // Nur Rechnungen im Zeitfenster bewerten – sonst wächst die Übersicht mit jedem Jahr.
        List<LieferantDokument> kandidaten = bestand.rechnungenVon(kette.lieferantId()).stream()
                .filter(r -> !kettenIds.contains(r.getId()))
                .filter(r -> imKartenFenster(r, bestellDaten))
                .toList();
        return rechnungsVorschlagService.besterVorschlag(bestellDokumente, kandidaten,
                        bestand.dokumenteVon(kette.lieferantId()), speicher)
                .map(b -> toVorschlagDto(b.vorschlag(), b.eindeutig()))
                .orElse(null);
    }

    /**
     * Rechnung liegt höchstens {@link #KARTE_TAGE_VORHER} Tage vor bzw.
     * {@link #KARTE_TAGE_NACHHER} Tage nach einem Bestelldokument der Kette. Ohne
     * Datum (Rechnung oder Bestellung) bleibt sie im Rennen.
     */
    static boolean imKartenFenster(LieferantDokument rechnung, List<LocalDate> bestellDaten) {
        LocalDate datum = rechnung.getGeschaeftsdaten() != null ? rechnung.getGeschaeftsdaten().getDokumentDatum() : null;
        if (datum == null || bestellDaten.isEmpty()) {
            return true;
        }
        return bestellDaten.stream().anyMatch(b -> !datum.isBefore(b.minusDays(KARTE_TAGE_VORHER))
                && !datum.isAfter(b.plusDays(KARTE_TAGE_NACHHER)));
    }

    private RechnungsVorschlagDto toVorschlagDto(RechnungsVorschlagService.Vorschlag v, boolean eindeutig) {
        LieferantDokument bestellung = v.bestellDokument();
        var bestellDaten = bestellung.getGeschaeftsdaten();
        return new RechnungsVorschlagDto(
                toDokumentRef(v.rechnung()),
                v.rechnung().getLieferant() != null ? v.rechnung().getLieferant().getLieferantenname() : null,
                bestellung.getId(),
                bestellung.getTyp(),
                bestellDaten != null ? bestellDaten.getDokumentNummer() : null,
                v.trefferquote(),
                v.einschaetzung().sicher(),
                eindeutig,
                v.einschaetzung().gruende(),
                RechnungsVorschlagService.gehoertSchonZu(v.rechnung()));
    }

    /**
     * Alle Lieferanten-Dokumente, einmal geladen und für die Vorschläge sortiert.
     *
     * @param nachId              alle Dokumente nach ID (auch ausgeblendete)
     * @param dokumenteJeLieferant alle Dokumente je Lieferant – das Umfeld, an dem
     *                             sich Kundennummern von Auftragsnummern unterscheiden
     * @param rechnungen           alle Rechnungen
     */
    private record Dokumentbestand(List<LieferantDokument> alle, Map<Long, LieferantDokument> nachId,
            Map<Long, List<LieferantDokument>> dokumenteJeLieferant, List<LieferantDokument> rechnungen) {

        List<LieferantDokument> dokumenteVon(Long lieferantId) {
            return lieferantId == null ? List.of() : dokumenteJeLieferant.getOrDefault(lieferantId, List.of());
        }

        List<LieferantDokument> rechnungenVon(Long lieferantId) {
            return dokumenteVon(lieferantId).stream()
                    .filter(d -> d.getTyp() == LieferantDokumentTyp.RECHNUNG)
                    .toList();
        }
    }

    private Dokumentbestand ladeDokumente() {
        List<LieferantDokument> alle = dokumentRepository.findAll();
        Map<Long, LieferantDokument> nachId = new HashMap<>();
        Map<Long, List<LieferantDokument>> jeLieferant = new HashMap<>();
        List<LieferantDokument> rechnungen = new ArrayList<>();
        for (LieferantDokument d : alle) {
            nachId.put(d.getId(), d);
            if (d.getLieferant() != null && d.getLieferant().getId() != null) {
                jeLieferant.computeIfAbsent(d.getLieferant().getId(), k -> new ArrayList<>()).add(d);
            }
            if (d.getTyp() == LieferantDokumentTyp.RECHNUNG) {
                rechnungen.add(d);
            }
        }
        return new Dokumentbestand(alle, nachId, jeLieferant, rechnungen);
    }

    /**
     * Die gruppierten Ketten plus der Dokumentbestand für die Rechnungs-Vorschläge.
     */
    private record Uebersicht(BestellungsUebersichtDto dto, Dokumentbestand bestand) {
    }

    private Uebersicht ladeUebersicht() {
        Dokumentbestand bestand = ladeDokumente();

        // IDs aller bereits zugeordneten Dokumente
        Set<Long> zugeordneteDokumentIds = projektAnteilRepository.findAll().stream()
                .map(a -> a.getDokument().getId())
                .collect(Collectors.toSet());

        // IDs aller Lagerbestellungen (Bestellungen ohne Projekt-Zuordnung)
        Set<Long> lagerbestellungIds = geschaeftsdokumentRepository.findAll().stream()
                .filter(gd -> Boolean.TRUE.equals(gd.getLagerbestellung()))
                .map(gd -> gd.getDokument() != null ? gd.getDokument().getId() : -1L)
                .collect(Collectors.toSet());

        // Ketten aus ALLEN Dokumenten: Die bezahlte (ausgeblendete) Rechnung gehört
        // zu ihrem Lieferschein, sonst stünde der ewig unter „Rechnung fehlt“.
        List<DokumentenKette> alleKetten = buildKetten(bestand.alle());

        List<DokumentenKette> offeneAnfragen = new ArrayList<>();
        List<DokumentenKette> laufendeBestellungen = new ArrayList<>();
        List<DokumentenKette> abgeschlossen = new ArrayList<>();
        List<DokumentenKette> zugeordnet = new ArrayList<>();
        List<DokumentenKette> ausgeblendet = new ArrayList<>();

        for (DokumentenKette kette : alleKetten) {
            switch (einordnen(kette, zugeordneteDokumentIds, lagerbestellungIds)) {
                case RECHNUNG_ZUORDNEN -> abgeschlossen.add(kette);
                case ERLEDIGT -> ausgeblendet.add(kette);
                case ZUGEORDNET -> zugeordnet.add(kette);
                case LAUFEND -> laufendeBestellungen.add(kette);
                case ANFRAGE -> offeneAnfragen.add(kette);
                case KEINE -> { /* z. B. nur sonstige Dokumente – gehört nicht in die Übersicht */ }
            }
        }

        // Nach Datum sortieren (neueste zuerst); ohne Belegdatum zählt der Eingang
        Comparator<DokumentenKette> byDate = (a, b) -> neuestesDatum(b).compareTo(neuestesDatum(a));

        offeneAnfragen.sort(byDate);
        laufendeBestellungen.sort(byDate);
        abgeschlossen.sort(byDate);
        zugeordnet.sort(byDate);
        ausgeblendet.sort(byDate);

        return new Uebersicht(new BestellungsUebersichtDto(
                offeneAnfragen, laufendeBestellungen, abgeschlossen, zugeordnet, ausgeblendet), bestand);
    }

    private enum Bereich {
        ANFRAGE, LAUFEND, RECHNUNG_ZUORDNEN, ZUGEORDNET, ERLEDIGT, KEINE
    }

    /**
     * Status-Regel je Kette, in dieser Reihenfolge:
     * <ol>
     *   <li>Eine eingeblendete Rechnung, die weder Projekten zugeordnet noch
     *       Lagerbestellung ist → „Rechnung zuordnen“.</li>
     *   <li>Eine ausgeblendete Rechnung oder alles ausgeblendet → erledigt.</li>
     *   <li>Sonst wie bisher über alle Dokumente: zugeordnet, laufend, Anfrage.</li>
     * </ol>
     */
    private static Bereich einordnen(DokumentenKette kette, Set<Long> zugeordneteDokumentIds,
            Set<Long> lagerbestellungIds) {
        List<DokumentRef> dokumente = kette.dokumente();
        boolean offeneRechnung = dokumente.stream()
                .anyMatch(d -> d.typ == LieferantDokumentTyp.RECHNUNG && !d.ausgeblendet
                        && !zugeordneteDokumentIds.contains(d.id) && !lagerbestellungIds.contains(d.id));
        if (offeneRechnung) {
            return Bereich.RECHNUNG_ZUORDNEN;
        }
        boolean ausgeblendeteRechnung = dokumente.stream()
                .anyMatch(d -> d.typ == LieferantDokumentTyp.RECHNUNG && d.ausgeblendet);
        if (ausgeblendeteRechnung || dokumente.stream().allMatch(d -> d.ausgeblendet)) {
            return Bereich.ERLEDIGT;
        }
        if (dokumente.stream().anyMatch(d -> d.typ == LieferantDokumentTyp.RECHNUNG)) {
            // Alle Rechnungen eingeblendet und zugeordnet (sonst griffe Regel 1)
            return Bereich.ZUGEORDNET;
        }
        if (dokumente.stream().anyMatch(d -> d.typ == LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG
                || d.typ == LieferantDokumentTyp.LIEFERSCHEIN)) {
            // Ware ist bestellt oder schon geliefert, die Rechnung fehlt noch.
            return Bereich.LAUFEND;
        }
        if (dokumente.stream().allMatch(d -> d.typ == LieferantDokumentTyp.ANGEBOT)) {
            return Bereich.ANFRAGE;
        }
        return Bereich.KEINE;
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
     * Bildet Dokumenten-Ketten basierend auf Verknüpfungen. Eine Kette umfasst alle
     * Dokumente, die über Verknüpfungen (egal welcher Richtung) zusammenhängen –
     * auch n:m (Teillieferungen, Teilrechnungen).
     */
    List<DokumentenKette> buildKetten(List<LieferantDokument> dokumente) {
        // Map für schnellen Zugriff
        Map<Long, LieferantDokument> dokMap = dokumente.stream()
                .collect(Collectors.toMap(LieferantDokument::getId, d -> d, (a, b) -> a));

        // Set für bereits verarbeitete Dokumente
        Set<Long> verarbeitet = new HashSet<>();
        List<DokumentenKette> ketten = new ArrayList<>();

        for (LieferantDokument dok : dokumente) {
            if (verarbeitet.contains(dok.getId()))
                continue;

            // Sammle alle verknüpften Dokumente
            Set<Long> kettenIds = new HashSet<>();
            collectKettenIds(dok, kettenIds);

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
                    refs,
                    verbindungen(refs, dokMap)));
        }

        return ketten;
    }

    /**
     * Alle Verknüpfungen innerhalb einer Kette, jede Kante einmal: von =
     * Nachfolger (z. B. Rechnung), zu = Vorgänger (z. B. Lieferschein).
     */
    private static List<Verbindung> verbindungen(List<DokumentRef> refs, Map<Long, LieferantDokument> dokMap) {
        Set<Long> ids = refs.stream().map(r -> r.id).collect(Collectors.toSet());
        Set<String> gesehen = new HashSet<>();
        List<Verbindung> verbindungen = new ArrayList<>();
        for (DokumentRef ref : refs) {
            LieferantDokument d = dokMap.get(ref.id);
            if (d == null || d.getVerknuepfteDokumente() == null) {
                continue;
            }
            for (LieferantDokument vorgaenger : d.getVerknuepfteDokumente()) {
                Long zu = vorgaenger.getId();
                if (zu == null || zu.equals(d.getId()) || !ids.contains(zu)) {
                    continue;
                }
                String kante = Math.min(d.getId(), zu) + ":" + Math.max(d.getId(), zu);
                if (gesehen.add(kante)) {
                    verbindungen.add(new Verbindung(d.getId(), zu));
                }
            }
        }
        verbindungen.sort(Comparator.comparing(Verbindung::vonId).thenComparing(Verbindung::zuId));
        return verbindungen;
    }

    private static LocalDate neuestesDatum(DokumentenKette kette) {
        return kette.dokumente().stream()
                .map(d -> d.dokumentDatum != null ? d.dokumentDatum : d.eingangsDatum)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.MIN);
    }

    private static void collectKettenIds(LieferantDokument start, Set<Long> collected) {
        // Iterativ statt rekursiv: lange Ketten (viele Teillieferungen) sprengen sonst den Stack.
        // Verknüpfungen in beide Richtungen durchlaufen: Gespeichert wird nur
        // Nachfolger -> Vorgänger (Rechnung -> AB). Ohne die Rückrichtung bildete eine
        // AB, die vor ihrer Rechnung an der Reihe war, eine eigene Kette ohne Rechnung
        // und blieb dauerhaft bei den laufenden Bestellungen stehen.
        ArrayDeque<LieferantDokument> offen = new ArrayDeque<>();
        if (start != null) {
            offen.push(start);
        }
        while (!offen.isEmpty()) {
            LieferantDokument dok = offen.pop();
            if (!collected.add(dok.getId())) {
                continue;
            }
            if (dok.getVerknuepfteDokumente() != null) {
                dok.getVerknuepfteDokumente().stream().filter(Objects::nonNull).forEach(offen::push);
            }
            if (dok.getVerknuepftVon() != null) {
                dok.getVerknuepftVon().stream().filter(Objects::nonNull).forEach(offen::push);
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
        ref.eingangsDatum = d.getUploadDatum() != null ? d.getUploadDatum().toLocalDate() : null;
        ref.ausgeblendet = d.isAusgeblendet();

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

    /**
     * @param verbindungen alle Verknüpfungen innerhalb der Kette, jede Kante einmal
     */
    public record DokumentenKette(
            String id,
            Long lieferantId,
            String lieferantName,
            List<DokumentRef> dokumente,
            List<Verbindung> verbindungen,
            /** Nur bei laufenden Bestellungen: die wahrscheinlichste Rechnung. */
            RechnungsVorschlagDto rechnungsVorschlag) {

        public DokumentenKette(String id, Long lieferantId, String lieferantName, List<DokumentRef> dokumente) {
            this(id, lieferantId, lieferantName, dokumente, List.of(), null);
        }

        public DokumentenKette(String id, Long lieferantId, String lieferantName, List<DokumentRef> dokumente,
                List<Verbindung> verbindungen) {
            this(id, lieferantId, lieferantName, dokumente, verbindungen, null);
        }

        DokumentenKette mitVorschlag(RechnungsVorschlagDto vorschlag) {
            return new DokumentenKette(id, lieferantId, lieferantName, dokumente, verbindungen, vorschlag);
        }
    }

    /**
     * Eine Verknüpfung in der Kette.
     *
     * @param vonId Nachfolger, z. B. die Rechnung
     * @param zuId  Vorgänger, z. B. der Lieferschein
     */
    public record Verbindung(Long vonId, Long zuId) {
    }

    /**
     * @param rechnung          die vorgeschlagene Rechnung
     * @param bestellDokumentId an dieses Dokument der Bestellung wird sie gehängt
     * @param trefferquote      0–100 %
     * @param eindeutig         {@code false}, wenn eine zweite Rechnung genauso gut passt
     * @param gehoertSchonZu    {@code null} oder das Bestelldokument, an dem die Rechnung
     *                          schon hängt, z. B. „Lieferschein LS-4711“
     */
    public record RechnungsVorschlagDto(
            DokumentRef rechnung,
            String lieferantName,
            Long bestellDokumentId,
            LieferantDokumentTyp bestellDokumentTyp,
            String bestellDokumentNummer,
            int trefferquote,
            boolean sicher,
            boolean eindeutig,
            List<String> gruende,
            String gehoertSchonZu) {
    }

    public record RechnungVerknuepfenRequest(
            @NotNull @Positive Long bestellDokumentId,
            @NotNull @Positive Long rechnungDokumentId) {
    }

    public record AbhaengenRequest(@NotNull @Positive Long dokumentId) {
    }

    public static class DokumentRef {
        public Long id;
        public LieferantDokumentTyp typ;
        public String dokumentNummer;
        public LocalDate dokumentDatum;
        public Double betragBrutto;
        public Double betragNetto;
        public LocalDate liefertermin;
        /** Wann das Dokument ins System kam – Ersatz, wenn kein Belegdatum erkannt wurde. */
        public LocalDate eingangsDatum;
        /** Ausgeblendet (z. B. bezahlte Rechnung) – gehört trotzdem zur Kette. */
        public boolean ausgeblendet;
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
