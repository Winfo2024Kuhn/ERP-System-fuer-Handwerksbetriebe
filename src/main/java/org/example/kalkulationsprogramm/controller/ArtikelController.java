package org.example.kalkulationsprogramm.controller;

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
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelResponseDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelSearchResponseDto;
import org.example.kalkulationsprogramm.dto.Artikel.ExterneNummerDto;
import org.example.kalkulationsprogramm.dto.Artikel.LieferantPreisDto;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelImportService;
import org.example.kalkulationsprogramm.service.ArtikelMatchingService;
import org.example.kalkulationsprogramm.service.ArtikelServiceContract;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "sort", defaultValue = "produktname") String sort,
            @RequestParam(value = "dir", defaultValue = "asc") String direction) {
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<Integer> kategorieIds = kategorieService.findeKategorieUndUnterkategorieIds(kategorieId);
        Specification<Artikel> specification = buildArtikelSpecification(query, lieferant, produktlinie, werkstoff,
                kategorieId, kategorieIds);
        Page<Artikel> result = artikelService.suche(specification,
                PageRequest.of(pageIndex, pageSize, buildSort(sort, direction)));

        List<ArtikelResponseDto> daten = result.stream()
                .flatMap(this::mappeArtikelZuDtos)
                .filter(Objects::nonNull)
                .toList();

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
        if (artikel instanceof ArtikelWerkstoffe aw && aw.getMasse() != null) {
            dto.setKgProMeter(aw.getMasse());
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

    private Stream<ArtikelResponseDto> mappeArtikelZuDtos(Artikel artikel) {
        var gruppiert = artikel.getArtikelpreis().stream()
                .filter(p -> p.getPreis() != null)
                .collect(Collectors.groupingBy(LieferantenArtikelPreise::getLieferant));
        if (gruppiert.isEmpty()) {
            return Stream.of(toDto(artikel, null));
        }
        return gruppiert.entrySet().stream()
                .map(entry -> {
                    var preis = entry.getValue().stream()
                            .max(Comparator.comparing(
                                    LieferantenArtikelPreise::getPreisAenderungsdatum,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .orElse(null);
                    return toDto(artikel, preis);
                });
    }

    private Specification<Artikel> buildArtikelSpecification(String query,
            String lieferant,
            String produktlinie,
            String werkstoff,
            Integer kategorieId,
            List<Integer> kategorieIds) {
        Specification<Artikel> specification = Specification.where((root, cq, cb) -> cb.conjunction());

        if (StringUtils.hasText(query)) {
            final String likeValue = wrapLike(query).toLowerCase(Locale.GERMAN);
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
                        cb.like(cb.lower(root.get("produktname")), likeValue),
                        cb.like(cb.lower(root.get("produktlinie")), likeValue),
                        cb.like(cb.lower(root.get("produkttext")), likeValue),
                        cb.like(cb.lower(werkstoffJoin.get("name")), likeValue),
                        cb.exists(preisSubquery));
            });
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

        return specification;
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
