package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelDokumentTyp;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Fertigungszustand;
import org.example.kalkulationsprogramm.domain.Herstellverfahren;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDetailDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumentDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumenttexteRequest;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelResponseDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelSearchResponseDto;
import org.example.kalkulationsprogramm.dto.Artikel.ExterneNummerDto;
import org.example.kalkulationsprogramm.dto.Artikel.LieferantPreisDto;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelDokumentService;
import org.example.kalkulationsprogramm.service.ArtikelImportService;
import org.example.kalkulationsprogramm.service.ArtikelMatchingService;
import org.example.kalkulationsprogramm.service.ArtikelPositionsPreisService;
import org.example.kalkulationsprogramm.service.ArtikelServiceContract;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/artikel")
@AllArgsConstructor
public class ArtikelController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Map<String, SortField> SORT_FIELDS = Map.ofEntries(
            Map.entry("externeArtikelnummer", new SortField("artikelpreis.externeArtikelnummer", true)),
            Map.entry("produktlinie", new SortField("produktlinie", true)),
            Map.entry("produktname", new SortField("produktname", true)),
            Map.entry("produkttext", new SortField("produkttext", true)),
            Map.entry("verpackungseinheit", new SortField("verpackungseinheit", false)),
            Map.entry("werkstoffName", new SortField("werkstoff.name", true)),
            Map.entry("lieferantenname", new SortField("artikelpreis.lieferant.lieferantenname", true)),
            Map.entry("preis", new SortField("artikelpreis.preis", false)),
            Map.entry("preisDatum", new SortField("artikelpreis.preisAenderungsdatum", false)));

    private final ArtikelServiceContract artikelService;
    private final ArtikelImportService artikelImportService;
    private final ArtikelMatchingService artikelMatchingService;
    private final LieferantenRepository lieferantenRepository;
    private final KategorieService kategorieService;
    private final WerkstoffRepository werkstoffRepository;
    private final ArtikelPositionsPreisService artikelPositionsPreisService;
    private final ArtikelDokumentService artikelDokumentService;

    @PostMapping
    @Transactional
    public ResponseEntity<ArtikelResponseDto> erstelle(
            @RequestBody org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto dto) {
        Artikel erstellt = artikelService.erstelleArtikel(dto);
        return ResponseEntity.ok(toDto(erstellt, null));
    }

    @PostMapping("/import/headers")
    public ResponseEntity<List<String>> readHeaders(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(artikelImportService.readHeaders(file));
    }

    @PostMapping("/import/analyze")
    public ResponseEntity<org.example.kalkulationsprogramm.dto.ImportAnalysisResult> analyzeImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("lieferant") String lieferant,
            @RequestParam Map<String, String> spaltenZuordnung) {
        spaltenZuordnung.remove("lieferant");
        return ResponseEntity.ok(artikelImportService.analyzeImport(file, lieferant, spaltenZuordnung));
    }

    @PostMapping("/import")
    public ResponseEntity<Void> importiere(@RequestParam("file") MultipartFile file,
            @RequestParam("lieferant") String lieferant,
            @RequestParam(value = "kategorieId", required = false) Long kategorieId,
            @RequestParam Map<String, String> spaltenZuordnung) {
        spaltenZuordnung.remove("lieferant");
        spaltenZuordnung.remove("kategorieId");
        artikelImportService.importiereCsv(file, lieferant, spaltenZuordnung, kategorieId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/lieferanten")
    public List<String> alleLieferanten() {
        return lieferantenRepository.findAll().stream()
                .filter(l -> l.getIstAktiv() != null && l.getIstAktiv())
                .map(l -> l.getLieferantenname())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    @GetMapping("/produktlinien")
    public List<String> alleProduktlinien() {
        return artikelService.findeProduktlinienOhneLieferant(1L).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)))
                .stream()
                .collect(Collectors.toList());
    }

    @GetMapping("/werkstoffe")
    public List<String> alleWerkstoffe() {
        return werkstoffRepository.findAll().stream()
                .map(Werkstoff::getName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)))
                .stream()
                .collect(Collectors.toList());
    }

    @GetMapping("/werkstoffe/details")
    public List<Map<String, Object>> alleWerkstoffeDetails() {
        return werkstoffRepository.findAll().stream()
                .map(w -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", w.getId());
                    map.put("name", w.getName());
                    return map;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("name"), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    @GetMapping("/match")
    @Transactional(readOnly = true)
    public List<ArtikelResponseDto> match(@RequestParam(required = false) String produktname,
            @RequestParam(required = false) String produktlinie) {
        return artikelMatchingService.findeBesteTreffer(produktname, produktlinie).stream()
                .map(a -> toDto(a, null))
                .collect(Collectors.toList());
    }


    @GetMapping
    @Transactional(readOnly = true)
    public ArtikelSearchResponseDto sucheArtikel(@RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "lieferant", required = false) String lieferant,
            @RequestParam(value = "produktlinie", required = false) String produktlinie,
            @RequestParam(value = "werkstoff", required = false) String werkstoff,
            @RequestParam(value = "kategorieId", required = false) Integer kategorieId,
            @RequestParam(value = "nurMitLieferantenpreis", defaultValue = "false") boolean nurMitLieferantenpreis,
            @RequestParam(value = "herstellverfahren", required = false) String herstellverfahren,
            @RequestParam(value = "fertigungszustand", required = false) String fertigungszustand,
            @RequestParam(value = "profilform", required = false) String profilform,
            @RequestParam(value = "verzinkbar", required = false) Boolean verzinkbar,
            @RequestParam(value = "pulverbeschichtbar", required = false) Boolean pulverbeschichtbar,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "sort", defaultValue = "produktname") String sort,
            @RequestParam(value = "dir", defaultValue = "asc") String direction) {
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<Integer> kategorieIds = kategorieService.findeKategorieUndUnterkategorieIds(kategorieId);
        Specification<Artikel> specification = buildArtikelSpecification(query, lieferant, produktlinie, werkstoff,
            kategorieId, kategorieIds, nurMitLieferantenpreis,
            herstellverfahren, fertigungszustand, profilform, verzinkbar, pulverbeschichtbar);
        Page<Artikel> result = artikelService.suche(specification,
                PageRequest.of(pageIndex, pageSize, buildSort(sort, direction)));

        // Eine Zeile je Artikel. Die Sortierung nach Preis wird hier nachgezogen,
        // weil der guenstigste Preis erst beim Zusammenstellen feststeht.
        List<ArtikelResponseDto> daten = result.stream()
                .map(a -> mappeArtikelZuDto(a, lieferant))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        if ("preis".equals(sort)) {
            Comparator<ArtikelResponseDto> nachPreis = Comparator.comparing(
                    ArtikelResponseDto::getGuenstigsterPreis,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            daten.sort("desc".equalsIgnoreCase(direction) ? nachPreis.reversed() : nachPreis);
        }

        // Vorschaubilder fuer die ganze Trefferseite in einem Rutsch laden,
        // statt je Zeile einzeln nachzuschlagen (N+1).
        Map<Long, String> vorschaubildUrls = artikelDokumentService.ladeVorschaubildUrls(
                daten.stream().map(ArtikelResponseDto::getId).toList());
        daten.forEach(dto -> dto.setVorschaubildUrl(vorschaubildUrls.get(dto.getId())));

        ArtikelSearchResponseDto response = new ArtikelSearchResponseDto();
        response.setArtikel(daten);
        response.setGesamt(result.getTotalElements());
        response.setSeite(result.getNumber());
        response.setSeitenGroesse(result.getSize());
        return response;
    }

    private ArtikelResponseDto toDto(Artikel artikel, LieferantenArtikelPreise preis) {
        ArtikelResponseDto dto = new ArtikelResponseDto();
        dto.setId(artikel.getId());
        Lieferanten lieferant = preis != null ? preis.getLieferant() : null;
        String externeNummer = artikel.getExterneArtikelnummer(lieferant);
        if (externeNummer == null && preis != null) {
            String nummer = preis.getExterneArtikelnummer();
            if (nummer != null && !nummer.isBlank()) {
                externeNummer = nummer;
            }
        }
        if (externeNummer == null && lieferant == null) {
            externeNummer = artikel.getExterneArtikelnummer();
        }
        dto.setExterneArtikelnummer(externeNummer);
        dto.setProduktlinie(artikel.getProduktlinie());
        dto.setProduktname(artikel.getProduktname());
        dto.setProdukttext(artikel.getProdukttext());
        dto.setVerpackungseinheit(artikel.getVerpackungseinheit());
        dto.setPreiseinheit(artikel.getPreiseinheit());
        dto.setVerrechnungseinheit(artikel.getVerrechnungseinheit());
        if (artikel instanceof ArtikelWerkstoffe aw) {
            if (aw.getMasse() != null) {
                dto.setKgProMeter(aw.getMasse());
            }
            dto.setKgProQm(aw.getMassePerQm());
            dto.setMantelflaeche(aw.getMantelflaeche());
            dto.setDurchmesser(aw.getDurchmesser());
            dto.setHoehe(aw.getHoehe());
            dto.setBreite(aw.getBreite());
            dto.setWandstaerke(aw.getWandstaerke());
            dto.setStandardlaenge(aw.getStandardlaenge());
            dto.setAbmessung(aw.getAbmessungText());
        }

        // Technische Merkmale: Verfahren und Zustand sind bewusst eigene Felder,
        // damit die Oberflaeche "geschweisst, kaltgefertigt" anzeigen kann statt
        // nur "EN 10219-2" - danach laesst sich ausserdem filtern.
        dto.setArtikelnummer(artikel.getArtikelnummer());
        dto.setProfilform(artikel.getProfilform());
        dto.setHerstellverfahren(artikel.getHerstellverfahren());
        dto.setFertigungszustand(artikel.getFertigungszustand());
        dto.setMassnorm(artikel.getMassnorm());
        dto.setWerkstoffnorm(artikel.getWirksameWerkstoffnorm());
        dto.setVerzinkungsgeeignet(artikel.istVerzinkungsgeeignet());
        dto.setPulverbeschichtungsgeeignet(artikel.istPulverbeschichtungsgeeignet());
        if (artikel.getWerkstoff() != null) {
            dto.setBeschichtungshinweis(artikel.getWerkstoff().getBeschichtungshinweis());
        }
        if (preis != null) {
            dto.setPreis(preis.getPreis());
            dto.setPreisDatum(preis.getPreisAenderungsdatum());
        }
        LieferantPreisDto lp = new LieferantPreisDto();
        if (preis != null && preis.getLieferant() != null) {
            lp.setLieferantId(preis.getLieferant().getId());
            lp.setLieferantName(preis.getLieferant().getLieferantenname());
            lp.setPreis(preis.getPreis());
            dto.setLieferantenpreise(List.of(lp));
            dto.setLieferantId(lp.getLieferantId());
            dto.setLieferantenname(lp.getLieferantName());
        } else {
            dto.setLieferantenpreise(Collections.emptyList());
        }
        if (artikel.getKategorie() != null) {
            Kategorie kategorie = artikel.getKategorie();
            dto.setKategorieId(Long.valueOf(kategorie.getId()));
            dto.setKategoriePfad(buildPfad(kategorie));
            dto.setMeterware(istKategorieEinsOderUnterkategorie(kategorie));
            Kategorie parent = kategorie.getParentKategorie();
            if (parent != null) {
                dto.setParentKategorieId(Long.valueOf(parent.getId()));
            }
            Kategorie root = kategorie;
            while (root.getParentKategorie() != null) {
                root = root.getParentKategorie();
            }
            if (root != null) {
                dto.setRootKategorieId(Long.valueOf(root.getId()));
                dto.setRootKategorieName(root.getBeschreibung());
            }
        }
        if (artikel.getWerkstoff() != null) {
            dto.setWerkstoffId(artikel.getWerkstoff().getId());
            dto.setWerkstoffName(artikel.getWerkstoff().getName());
        }
        dto.setKurzbeschreibung(artikel.getKurzbeschreibung());
        dto.setBeschreibung(artikel.getBeschreibung());
        dto.setVerkaufsaufschlagProzent(artikel.getVerkaufsaufschlagProzent());

        // Derselbe Preis, der oben in der Preisspalte steht - nicht irgendeiner.
        // mappeArtikelZuDto hat ihn unter Beruecksichtigung des
        // Lieferantenfilters ermittelt und reicht ihn hier durch. Wuerde der
        // Service stattdessen selbst ueber Artikel.getGuenstigsterPreis() gehen,
        // rechnete der Vorschlag mit dem Einkaufspreis eines gerade abgewaehlten
        // Lieferanten - immer dem guenstigsten, also mit zu kleiner Marge auf
        // einem verbindlichen Angebot.
        ArtikelPositionsPreisService.ArtikelPositionsVorschlag vorschlag =
                artikelPositionsPreisService.berechne(artikel, preis);
        dto.setPositionsEinheit(vorschlag.einheit());
        dto.setPositionsEinzelpreis(vorschlag.einzelpreis());
        dto.setPreisHinweis(vorschlag.hinweis().name());
        return dto;
    }

    @PostMapping("/{id}/externe-nummer")
    public ResponseEntity<Void> setzeExterneNummer(@PathVariable("id") Long artikelId,
            @RequestBody ExterneNummerDto dto) {
        Lieferanten lieferant = null;
        if (dto.getLieferantId() != null) {
            lieferant = lieferantenRepository.findById(dto.getLieferantId()).orElse(null);
        }
        artikelService.fuegeExterneNummerHinzu(artikelId, lieferant, dto.getNummer());
        return ResponseEntity.ok().build();
    }

    /**
     * Pflegt die Felder, mit denen der Artikel als Position in einem
     * Kundendokument auftauchen kann: Kurzbeschreibung (Innensicht),
     * Beschreibung (Kundentext) und Verkaufsaufschlag.
     *
     * <p>Bedient zwei Aufrufer: die Detailseite im Frontend und das
     * Backfill-Skript unter {@code scripts/artikel_dokumenttexte_backfill.py}.
     *
     * <p>Unbekannte ID meldet der Service ueber {@code NotFoundException}, die
     * dank {@code @ResponseStatus(HttpStatus.NOT_FOUND)} von Spring automatisch
     * in 404 uebersetzt wird - ohne dass dieser Controller sie faengt.
     */
    @PatchMapping("/{id}/dokumenttexte")
    @Transactional
    public ResponseEntity<ArtikelResponseDto> aktualisiereDokumenttexte(
            @PathVariable Long id,
            @RequestBody ArtikelDokumenttexteRequest request) {
        try {
            Artikel aktualisiert = artikelService.aktualisiereDokumenttexte(id, request);
            // mappeArtikelZuDto statt toDto: toDto uebernimmt den Preis nur als
            // Durchreiche-Parameter - mit null antwortete der Endpoint fuer
            // jeden Artikel mit preisHinweis=KEIN_PREIS und leerer
            // Lieferantenliste, egal wie viele Preise gepflegt sind.
            return ResponseEntity.ok(mappeArtikelZuDto(aktualisiert, null));
        }
        catch (IllegalArgumentException e) {
            // Unzulaessige Eingabe (Grenzwerte, Aufschlag ausserhalb des Bereichs).
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Vollstaendige Sicht auf einen Artikel: Stammdaten, alle Lieferanten mit
     * ihrer Artikelnummer und ihrem Preis sowie der Preisverlauf.
     */
    @GetMapping("/{id}/detail")
    @Transactional(readOnly = true)
    public ResponseEntity<ArtikelDetailDto> detail(@PathVariable Long id) {
        Artikel artikel = artikelService.findeById(id).orElse(null);
        if (artikel == null) {
            return ResponseEntity.notFound().build();
        }

        ArtikelDetailDto dto = new ArtikelDetailDto();
        dto.setArtikel(mappeArtikelZuDto(artikel, null));
        dto.getArtikel().setVorschaubildUrl(artikelDokumentService.ladeVorschaubildUrl(id));

        List<LieferantenArtikelPreise> aktuelle = artikel.getArtikelpreis().stream()
                .filter(LieferantenArtikelPreise::isAktuell)
                .filter(p -> p.getLieferant() != null)
                .sorted(Comparator.comparing(LieferantenArtikelPreise::getPreis,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        BigDecimal bester = aktuelle.stream()
                .map(LieferantenArtikelPreise::getPreis)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        for (LieferantenArtikelPreise p : aktuelle) {
            ArtikelDetailDto.LieferantEintragDto eintrag = new ArtikelDetailDto.LieferantEintragDto();
            eintrag.setPreisId(p.getId());
            eintrag.setLieferantId(p.getLieferant().getId());
            eintrag.setLieferantName(p.getLieferant().getLieferantenname());
            eintrag.setExterneArtikelnummer(p.getExterneArtikelnummer());
            eintrag.setPreis(p.getPreis());
            eintrag.setPreisDatum(p.getPreisAenderungsdatum());
            eintrag.setQuelle(p.getQuelle());
            eintrag.setNotiz(p.getNotiz());
            eintrag.setGuenstigster(bester != null && bester.equals(p.getPreis()));
            // Um wie viel Prozent liegt dieser Lieferant ueber dem guenstigsten?
            if (bester != null && bester.signum() > 0 && p.getPreis() != null) {
                eintrag.setAufschlagProzent(p.getPreis().subtract(bester)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(bester, 1, java.math.RoundingMode.HALF_UP));
            }
            dto.getLieferanten().add(eintrag);
        }

        dto.setPreisverlauf(artikel.getArtikelpreis().stream()
                .filter(p -> p.getPreis() != null)
                .sorted(Comparator.comparing(LieferantenArtikelPreise::getPreisAenderungsdatum,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> {
                    ArtikelDetailDto.PreisstandDto stand = new ArtikelDetailDto.PreisstandDto();
                    stand.setPreisId(p.getId());
                    if (p.getLieferant() != null) {
                        stand.setLieferantId(p.getLieferant().getId());
                        stand.setLieferantName(p.getLieferant().getLieferantenname());
                    }
                    stand.setPreis(p.getPreis());
                    stand.setPreisDatum(p.getPreisAenderungsdatum());
                    stand.setQuelle(p.getQuelle());
                    stand.setNotiz(p.getNotiz());
                    stand.setAktuell(p.isAktuell());
                    return stand;
                })
                .collect(Collectors.toCollection(ArrayList::new)));

        return ResponseEntity.ok(dto);
    }

    /**
     * Laedt ein Vorschaubild oder Zusatzdokument (Zulassung, Zeichnung,
     * Datenblatt, Montageanleitung, Sonstiges) zu einem Artikel hoch.
     *
     * <p>Alle Pruefungen (Endungs-Whitelist, Path-Traversal, Groessenlimit,
     * Ersetzen eines vorhandenen Vorschaubilds) uebernimmt der
     * {@link ArtikelDokumentService} - dieser Endpoint nimmt nur entgegen und
     * antwortet.
     */
    @PostMapping(value = "/{id}/dokumente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArtikelDokumentDto> ladeDokumentHoch(
            @PathVariable Long id,
            @RequestPart("datei") MultipartFile datei,
            @RequestPart("typ") String typ,
            @RequestPart(value = "beschreibung", required = false) String beschreibung) {
        ArtikelDokumentTyp dokumentTyp;
        try {
            dokumentTyp = ArtikelDokumentTyp.valueOf(typ.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        try {
            ArtikelDokumentDto ergebnis = artikelDokumentService.ladeHoch(id, datei, dokumentTyp, beschreibung);
            return ResponseEntity.status(HttpStatus.CREATED).body(ergebnis);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Alle Dokumente eines Artikels, inklusive Vorschaubild. */
    @GetMapping("/{id}/dokumente")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ArtikelDokumentDto>> listeDokumente(@PathVariable Long id) {
        return ResponseEntity.ok(artikelDokumentService.listeDokumente(id));
    }

    /** Liefert die Datei eines Artikel-Dokuments mit passendem Content-Type aus. */
    @GetMapping("/dokumente/{dokumentId}/datei")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> ladeDokumentDatei(@PathVariable Long dokumentId) {
        ArtikelDokumentService.ArtikelDokumentDatei datei = artikelDokumentService.ladeDatei(dokumentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(datei.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + datei.originalDateiname() + "\"")
                .body(datei.resource());
    }

    /** Loescht ein Artikel-Dokument - Datenbankeintrag und Datei. */
    @DeleteMapping("/dokumente/{dokumentId}")
    public ResponseEntity<Void> loescheDokument(@PathVariable Long dokumentId) {
        artikelDokumentService.loescheDokument(dokumentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Auswahlmoeglichkeiten fuer die Filterleiste, jeweils mit Klartext-Namen
     * und Erklaerung. Damit muss die Oberflaeche die Begriffe nicht doppelt
     * pflegen und der Anwender sieht, was ein Verfahren bedeutet.
     */
    @GetMapping("/filteroptionen")
    public Map<String, Object> filteroptionen() {
        Map<String, Object> optionen = new HashMap<>();
        optionen.put("herstellverfahren", java.util.Arrays.stream(Herstellverfahren.values())
                .map(v -> Map.of("wert", v.name(), "name", v.getAnzeigename(), "erklaerung", v.getErklaerung()))
                .toList());
        optionen.put("fertigungszustand", java.util.Arrays.stream(Fertigungszustand.values())
                .map(v -> Map.of("wert", v.name(), "name", v.getAnzeigename(), "erklaerung", v.getErklaerung()))
                .toList());
        optionen.put("profilform", java.util.Arrays.stream(Profilform.values())
                .map(v -> Map.of("wert", v.name(), "name", v.getAnzeigename(), "erklaerung", v.getErklaerung()))
                .toList());
        return optionen;
    }

    /**
     * Bildet einen Artikel auf genau eine Zeile ab.
     *
     * <p>Frueher entstand hier je Lieferant eine eigene Zeile, wodurch derselbe
     * Artikel in der Trefferliste mehrfach auftauchte. Gesucht wird aber der
     * Artikel, nicht das Angebot - deshalb steht jetzt der guenstigste gueltige
     * Preis in der Zeile und die uebrigen Lieferanten stehen auf der Detailseite.
     *
     * <p>Ist nach einem Lieferanten gefiltert, zaehlt nur dessen Preis.
     */
    private ArtikelResponseDto mappeArtikelZuDto(Artikel artikel, String lieferantFilter) {
        List<LieferantenArtikelPreise> aktuelle = artikel.getArtikelpreis().stream()
                .filter(LieferantenArtikelPreise::isAktuell)
                .filter(p -> p.getPreis() != null)
                .filter(p -> p.getLieferant() != null)
                .filter(p -> !StringUtils.hasText(lieferantFilter)
                        || lieferantFilter.trim().equalsIgnoreCase(p.getLieferant().getLieferantenname()))
                .toList();

        LieferantenArtikelPreise guenstigster = aktuelle.stream()
                .min(Comparator.comparing(LieferantenArtikelPreise::getPreis))
                .orElse(null);

        ArtikelResponseDto dto = toDto(artikel, guenstigster);
        dto.setAnzahlLieferanten(aktuelle.size());
        if (guenstigster != null) {
            dto.setGuenstigsterPreis(guenstigster.getPreis());
            dto.setGuenstigsterPreisDatum(guenstigster.getPreisAenderungsdatum());
            dto.setGuenstigsterLieferantId(guenstigster.getLieferant().getId());
            dto.setGuenstigsterLieferantName(guenstigster.getLieferant().getLieferantenname());
        }
        // Alle Lieferanten mit gueltigem Preis, guenstigster zuerst - fuer die
        // Aufklapp-Ansicht in der Trefferliste.
        dto.setLieferantenpreise(aktuelle.stream()
                .sorted(Comparator.comparing(LieferantenArtikelPreise::getPreis))
                .map(p -> {
                    LieferantPreisDto lp = new LieferantPreisDto();
                    lp.setLieferantId(p.getLieferant().getId());
                    lp.setLieferantName(p.getLieferant().getLieferantenname());
                    lp.setPreis(p.getPreis());
                    return lp;
                })
                .toList());
        return dto;
    }

    /**
     * Zerlegt eine Sucheingabe in die Woerter, die alle vorkommen muessen.
     *
     * <p>Neben Leerzeichen trennt auch das Malzeichen zwischen zwei Zahlen, damit
     * "42.4x2" zu "42.4" und "2" wird - Anwender schreiben Abmessungen mal mit
     * und mal ohne Leerzeichen. Ebenso wird ein dem Mass vorangestelltes "x"
     * abgetrennt, denn "42.4 x2" ist eine ebenso uebliche Schreibweise. Ein
     * allein stehendes "x" faellt weg.
     */
    static List<String> zerlegeSuchbegriff(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        return java.util.Arrays.stream(query.trim().toLowerCase(Locale.GERMAN).split("[\\s,;]+"))
                .flatMap(teil -> java.util.Arrays.stream(teil.split("(?<=\\d)[x*](?=\\d)")))
                // "x2" als eigenes Wort: das Malzeichen gehoert nicht zur Zahl.
                .map(w -> w.replaceFirst("^[x*](?=\\d)", ""))
                .map(String::trim)
                .filter(w -> !w.isEmpty() && !"x".equals(w) && !"*".equals(w))
                .distinct()
                .toList();
    }

    private Specification<Artikel> buildArtikelSpecification(String query,
            String lieferant,
            String produktlinie,
            String werkstoff,
            Integer kategorieId,
            List<Integer> kategorieIds,
            boolean nurMitLieferantenpreis,
            String herstellverfahren,
            String fertigungszustand,
            String profilform,
            Boolean verzinkbar,
            Boolean pulverbeschichtbar) {
        Specification<Artikel> specification = Specification.where((root, cq, cb) -> cb.conjunction());

        if (StringUtils.hasText(query)) {
            // Die Eingabe wird in einzelne Woerter zerlegt, von denen jedes
            // vorkommen muss. "rundrohr 42.4 x2" trifft damit genau den
            // richtigen Artikel, statt alles zu liefern, was irgendein Wort
            // enthaelt. Trennzeichen sind Leerzeichen und das Malzeichen "x",
            // damit "42.4x2" wie "42.4 2" behandelt wird.
            for (String wort : zerlegeSuchbegriff(query)) {
                final String likeValue = wrapLike(wort).toLowerCase(Locale.GERMAN);
                specification = specification.and((root, cq, cb) -> {
                    Join<Artikel, Werkstoff> werkstoffJoin = root.join("werkstoff", JoinType.LEFT);
                    Subquery<Long> preisSubquery = cq.subquery(Long.class);
                    Root<LieferantenArtikelPreise> subRoot = preisSubquery.from(LieferantenArtikelPreise.class);
                    Join<LieferantenArtikelPreise, Lieferanten> subLieferant = subRoot.join("lieferant", JoinType.LEFT);
                    preisSubquery.select(cb.literal(1L));
                    preisSubquery.where(
                            cb.equal(subRoot.get("artikel"), root),
                            cb.or(
                                    cb.like(cb.lower(subRoot.get("externeArtikelnummer")), likeValue),
                                    cb.like(cb.lower(subLieferant.get("lieferantenname")), likeValue)));
                    return cb.or(
                            // Der Suchtext buendelt alle Schreibweisen und Synonyme.
                            cb.like(cb.lower(root.get("suchtext")), likeValue),
                            cb.like(cb.lower(root.get("artikelnummer")), likeValue),
                            cb.like(cb.lower(root.get("produktname")), likeValue),
                            cb.like(cb.lower(root.get("produktlinie")), likeValue),
                            cb.like(cb.lower(root.get("produkttext")), likeValue),
                            cb.like(cb.lower(werkstoffJoin.get("name")), likeValue),
                            cb.exists(preisSubquery));
                });
            }
        }

        if (StringUtils.hasText(lieferant)) {
            final String normalized = lieferant.trim().toLowerCase(Locale.GERMAN);
            specification = specification.and((root, cq, cb) -> {
                Subquery<Long> priceSubquery = cq.subquery(Long.class);
                Root<LieferantenArtikelPreise> subRoot = priceSubquery.from(LieferantenArtikelPreise.class);
                Join<LieferantenArtikelPreise, Lieferanten> subLieferant = subRoot.join("lieferant", JoinType.LEFT);
                priceSubquery.select(cb.literal(1L));
                priceSubquery.where(
                        cb.equal(subRoot.get("artikel"), root),
                        cb.equal(cb.lower(subLieferant.get("lieferantenname")), normalized),
                        cb.isNotNull(subRoot.get("preis")));
                return cb.exists(priceSubquery);
            });
        }

        if (StringUtils.hasText(produktlinie)) {
            final String likeValue = wrapLike(produktlinie);
            specification = specification.and((root, cq, cb) -> cb.like(cb.lower(root.get("produktlinie")),
                    likeValue.toLowerCase(Locale.GERMAN)));
        }

        if (StringUtils.hasText(werkstoff)) {
            final String normalized = werkstoff.trim().toLowerCase(Locale.GERMAN);
            specification = specification.and((root, cq, cb) -> {
                Join<Artikel, Werkstoff> werkstoffJoin = root.join("werkstoff", JoinType.LEFT);
                return cb.equal(cb.lower(werkstoffJoin.get("name")), normalized);
            });
        }

        if (kategorieId != null) {
            if (kategorieIds == null || kategorieIds.isEmpty()) {
                specification = specification.and((root, cq, cb) -> cb.disjunction());
            } else {
                specification = specification
                        .and((root, cq, cb) -> root.join("kategorie", JoinType.LEFT).get("id").in(kategorieIds));
            }
        }

        if (nurMitLieferantenpreis) {
            specification = specification.and((root, cq, cb) -> {
                Subquery<Long> preisSubquery = cq.subquery(Long.class);
                Root<LieferantenArtikelPreise> subRoot = preisSubquery.from(LieferantenArtikelPreise.class);
                preisSubquery.select(cb.literal(1L));
                preisSubquery.where(
                        cb.equal(subRoot.get("artikel"), root),
                        cb.isNotNull(subRoot.get("preis")),
                        cb.isNotNull(subRoot.get("lieferant")),
                        // Nur gueltige Preisstaende - die Tabelle enthaelt seit
                        // V338 auch vergangene.
                        cb.isTrue(subRoot.get("aktuell")));
                return cb.exists(preisSubquery);
            });
        }

        // Filter auf die technischen Merkmale. Sie sind fuer Anwender greifbarer
        // als die Norm: "nahtlos" und "kaltgezogen" sagen mehr als "EN 10305-1".
        specification = ergaenzeEnumFilter(specification, "herstellverfahren", herstellverfahren);
        specification = ergaenzeEnumFilter(specification, "fertigungszustand", fertigungszustand);
        specification = ergaenzeEnumFilter(specification, "profilform", profilform);

        // Eignungen: Der Werkstoff gibt den Standard vor, der Artikel darf
        // abweichen. Beides muss der Filter beruecksichtigen - deshalb wird
        // sowohl das Artikelfeld als auch der Werkstoff geprueft.
        if (verzinkbar != null) {
            specification = specification.and(eignungFilter("verzinkungsgeeignet", verzinkbar));
        }
        if (pulverbeschichtbar != null) {
            specification = specification.and(eignungFilter("pulverbeschichtungsgeeignet", pulverbeschichtbar));
        }

        return specification;
    }

    /** Filtert auf einen oder mehrere Enum-Werte, komma-getrennt uebergeben. */
    private Specification<Artikel> ergaenzeEnumFilter(Specification<Artikel> specification,
            String feld, String werte) {
        if (!StringUtils.hasText(werte)) {
            return specification;
        }
        List<String> gewaehlt = java.util.Arrays.stream(werte.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.GERMAN))
                .toList();
        if (gewaehlt.isEmpty()) {
            return specification;
        }
        return specification.and((root, cq, cb) -> root.get(feld).as(String.class).in(gewaehlt));
    }

    /**
     * Eignung fuer eine Beschichtung. Ein am Artikel gesetzter Wert geht vor,
     * sonst gilt der Standard des Werkstoffs.
     */
    private Specification<Artikel> eignungFilter(String feld, boolean erwartet) {
        return (root, cq, cb) -> {
            Join<Artikel, Werkstoff> werkstoffJoin = root.join("werkstoff", JoinType.LEFT);
            return cb.or(
                    cb.equal(root.get(feld), erwartet),
                    cb.and(cb.isNull(root.get(feld)), cb.equal(werkstoffJoin.get(feld), erwartet)));
        };
    }

    private Sort buildSort(String sort, String direction) {
        SortField field = SORT_FIELDS.getOrDefault(sort, SORT_FIELDS.get("produktname"));
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort.Order order = new Sort.Order(dir, field.property());
        if (field.ignoreCase()) {
            order = order.ignoreCase();
        }
        return Sort.by(order);
    }

    private String wrapLike(String value) {
        return "%" + value.trim() + "%";
    }

    private boolean istKategorieEinsOderUnterkategorie(Kategorie k) {
        Kategorie current = k;
        while (current != null) {
            if (current.getId() != null && current.getId() == 1) {
                return true;
            }
            current = current.getParentKategorie();
        }
        return false;
    }

    private String buildPfad(Kategorie kategorie) {
        List<String> parts = new ArrayList<>();
        Kategorie current = kategorie;
        while (current != null) {
            parts.add(current.getBeschreibung());
            current = current.getParentKategorie();
        }
        Collections.reverse(parts);
        return String.join(" > ", parts);
    }

    private record SortField(String property, boolean ignoreCase) {
    }
}
