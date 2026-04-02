package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ArbeitsgangAnalyseDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieAnalyseDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieErstellenDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProjektAnalyseDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProjektArbeitsgangAnalyseDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProduktkategorieService {
    private final ProduktkategorieRepository produktkategorieRepository;
    private final ProjektRepository projektRepository;
    private final ProduktkategorieMapper produktkategorieMapper;
    private final DateiSpeicherService dateiSpeicherService;

    public List<ProduktkategorieResponseDto> findeHauptkategorien(boolean light) {
        Map<Long, Long> counts = light ? Map.of() : berechneProjektAnzahlen();
        return this.produktkategorieRepository.findByUebergeordneteKategorieIsNull().stream()
                .map(kat -> {
                    ProduktkategorieResponseDto dto = this.produktkategorieMapper.toProduktkategorieResponseDto(kat);
                    if (!light) dto.setProjektAnzahl(counts.getOrDefault(kat.getId(), 0L));
                    return dto;
                })
                .toList();
    }

    public List<ProduktkategorieResponseDto> findeAlleKategorien() {
        Map<Long, Long> counts = berechneProjektAnzahlen();
        return this.produktkategorieRepository.findAll().stream()
                .map(kat -> {
                    ProduktkategorieResponseDto dto = this.produktkategorieMapper.toProduktkategorieResponseDto(kat);
                    dto.setProjektAnzahl(counts.getOrDefault(kat.getId(), 0L));
                    return dto;
                })
                .toList();
    }

    public List<ProduktkategorieResponseDto> findeUnterkategorie(Long parentId, boolean light) {
        Map<Long, Long> counts = light ? Map.of() : berechneProjektAnzahlen();
        return this.produktkategorieRepository.findByUebergeordneteKategorieId(parentId).stream()
                .map(kat -> {
                    ProduktkategorieResponseDto dto = this.produktkategorieMapper.toProduktkategorieResponseDto(kat);
                    if (!light) dto.setProjektAnzahl(counts.getOrDefault(kat.getId(), 0L));
                    return dto;
                })
                .toList();
    }

    public ProduktkategorieResponseDto findeKategorieById(Long id) {
        Map<Long, Long> counts = berechneProjektAnzahlen();
        return produktkategorieRepository.findById(id)
                .map(kat -> {
                    ProduktkategorieResponseDto dto = produktkategorieMapper.toProduktkategorieResponseDto(kat);
                    dto.setProjektAnzahl(counts.getOrDefault(kat.getId(), 0L));
                    return dto;
                })
                .orElseThrow(() -> new RuntimeException("Kategorie mit ID " + id + " nicht gefunden."));
    }

    public List<ProduktkategorieResponseDto> sucheLeafKategorien(String suchbegriff) {
        if (suchbegriff == null || suchbegriff.isBlank()) {
            return List.of();
        }
        return produktkategorieRepository.sucheLeafKategorienNachBezeichnung(suchbegriff.trim()).stream()
                .map(produktkategorieMapper::toProduktkategorieResponseDto)
                .toList();
    }

    @Transactional
    public ProduktkategorieResponseDto erstelleKategorie(ProduktkategorieErstellenDto dto, MultipartFile bild) {
        Produktkategorie neueKategorie = new Produktkategorie();
        neueKategorie.setBezeichnung(dto.getBezeichnung());
        neueKategorie.setVerrechnungseinheit(dto.getVerrechnungseinheit()); // Enum wird hier gesetzt
        neueKategorie.setBeschreibung(dto.getBeschreibung());

        if (dto.getParentId() != null) {
            Produktkategorie parent = produktkategorieRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Eltern-Kategorie nicht gefunden"));
            neueKategorie.setUebergeordneteKategorie(parent);
        }

        if (bild != null && !bild.isEmpty()) {
            String bildWebPfad = dateiSpeicherService.speichereBild(bild);
            neueKategorie.setBildUrl(bildWebPfad);
        }

        Produktkategorie gespeicherteKategorie = produktkategorieRepository.save(neueKategorie);
        return produktkategorieMapper.toProduktkategorieResponseDto(gespeicherteKategorie);
    }

    @Transactional
    public ProduktkategorieResponseDto aktualisiereBeschreibung(Long kategorieId, String beschreibung) {
        Produktkategorie kategorie = produktkategorieRepository.findById(kategorieId)
                .orElseThrow(() -> new RuntimeException("Kategorie mit ID " + kategorieId + " nicht gefunden."));
        kategorie.setBeschreibung(beschreibung);
        Produktkategorie gespeichert = produktkategorieRepository.save(kategorie);
        return produktkategorieMapper.toProduktkategorieResponseDto(gespeichert);
    }

    @Transactional
    public ProduktkategorieResponseDto aktualisiereKategorie(Long id, ProduktkategorieErstellenDto dto,
            MultipartFile bild) {
        Produktkategorie kategorie = produktkategorieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kategorie mit ID " + id + " nicht gefunden."));

        kategorie.setBezeichnung(dto.getBezeichnung());
        kategorie.setVerrechnungseinheit(dto.getVerrechnungseinheit());
        kategorie.setBeschreibung(dto.getBeschreibung());

        // Bild-Update Logik
        if (bild != null && !bild.isEmpty()) {
            // Altes Bild löschen
            if (kategorie.getBildUrl() != null && !kategorie.getBildUrl().isEmpty()) {
                dateiSpeicherService.loescheBild(kategorie.getBildUrl());
            }
            // Neues Bild speichern
            String bildWebPfad = dateiSpeicherService.speichereBild(bild);
            kategorie.setBildUrl(bildWebPfad);
        }

        Produktkategorie gespeichert = produktkategorieRepository.save(kategorie);
        return produktkategorieMapper.toProduktkategorieResponseDto(gespeichert);
    }

    @Transactional
    public void loescheKategorie(Long kategorieId) {
        Produktkategorie kategorie = produktkategorieRepository.findById(kategorieId)
                .orElseThrow(() -> new RuntimeException("Kategorie mit ID " + kategorieId + " nicht gefunden."));
        if (!kategorie.getUnterkategorien().isEmpty()) {
            throw new IllegalStateException(
                    "Kategorie kann nicht gelöscht werden, da sie noch Unterkategorien enthält.");
        }
        long projektAnzahl = projektRepository.countByProduktkategorieId(kategorieId);
        if (projektAnzahl > 0) {
            throw new IllegalStateException("Kategorie kann nicht gelöscht werden, da ihr noch " + projektAnzahl
                    + " Projekte zugeordnet sind.");
        }
        if (kategorie.getBildUrl() != null && !kategorie.getBildUrl().isEmpty()) {
            dateiSpeicherService.loescheBild(kategorie.getBildUrl());
        }
        produktkategorieRepository.delete(kategorie);
    }

    private void sammleUnterkategorien(Produktkategorie kategorie, List<Produktkategorie> gesammelteKategorien,
            Verrechnungseinheit vergleichsEinheit) {
        if (kategorie.getVerrechnungseinheit() != vergleichsEinheit) {
            throw new IllegalStateException(
                    "Die Verrechnungseinheiten in den Unterkategorien sind nicht konsistent. Analyse nicht möglich.");
        }
        gesammelteKategorien.add(kategorie);
        // Explizites Laden der Unterkategorien, falls LAZY
        produktkategorieRepository.findById(kategorie.getId()).ifPresent(k -> {
            for (Produktkategorie unterkategorie : k.getUnterkategorien()) {
                sammleUnterkategorien(unterkategorie, gesammelteKategorien, vergleichsEinheit);
            }
        });
    }

    public ProduktkategorieAnalyseDto analysiereKategorie(Long kategorieId, Integer jahr) {
        Produktkategorie startKategorie = produktkategorieRepository.findById(kategorieId)
                .orElseThrow(() -> new EntityNotFoundException("Kategorie mit ID " + kategorieId + " nicht gefunden."));

        List<Produktkategorie> alleZuAnalysierendenKategorien = new ArrayList<>();
        sammleUnterkategorien(startKategorie, alleZuAnalysierendenKategorien, startKategorie.getVerrechnungseinheit());

        List<Long> kategorieIds = alleZuAnalysierendenKategorien.stream().map(Produktkategorie::getId)
                .collect(Collectors.toList());

        List<Projekt> projekte;
        if (jahr != null) {
            LocalDate start = LocalDate.of(jahr, 1, 1);
            LocalDate end = LocalDate.of(jahr, 12, 31);
            projekte = projektRepository.findByProduktkategorieIdsAndAbschlussdatumBetween(kategorieIds, start, end);
        } else {
            projekte = projektRepository.findByProduktkategorieIds(kategorieIds);
        }

        class Aggregat {
            double stunden = 0;
            double einheiten = 0;
            String beschreibung;
        }

        java.util.Map<Long, Aggregat> arbeitsgangMap = new java.util.HashMap<>();
        List<ProjektAnalyseDto> projektDtos = new java.util.ArrayList<>();
        double gesamtStundenMitZeiten = 0;
        double gesamtEinheitenMitZeiten = 0;
        double sumXMitZeiten = 0;
        double sumYMitZeiten = 0;
        double sumXYMitZeiten = 0;
        double sumX2MitZeiten = 0;
        int datenpunkte = 0;

        for (var projekt : projekte) {
            double einheiten = projekt.getProjektProduktkategorien().stream()
                    .filter(ppk -> kategorieIds.contains(ppk.getProduktkategorie().getId()))
                    .map(ppk -> ppk.getMenge())
                    .mapToDouble(java.math.BigDecimal::doubleValue)
                    .sum();

            java.util.Map<Long, ProjektArbeitsgangAnalyseDto> projektAgMap = new java.util.HashMap<>();
            double projektStunden = 0;
            for (var zeit : projekt.getZeitbuchungen()) {
                var ppk = zeit.getProjektProduktkategorie();
                if (ppk == null || ppk.getProduktkategorie() == null ||
                        !kategorieIds.contains(ppk.getProduktkategorie().getId())) {
                    continue;
                }

                double stunden = zeit.getAnzahlInStunden() != null ? zeit.getAnzahlInStunden().doubleValue() : 0;
                projektStunden += stunden;
                var ag = zeit.getArbeitsgang();

                var projAgg = projektAgMap.computeIfAbsent(ag.getId(), k -> {
                    ProjektArbeitsgangAnalyseDto a = new ProjektArbeitsgangAnalyseDto();
                    a.setArbeitsgangBeschreibung(ag.getBeschreibung());
                    return a;
                });
                projAgg.setStundenProEinheit(projAgg.getStundenProEinheit() + stunden);

                var globalAgg = arbeitsgangMap.computeIfAbsent(ag.getId(), k -> {
                    Aggregat a = new Aggregat();
                    a.beschreibung = ag.getBeschreibung();
                    return a;
                });
                globalAgg.stunden += stunden;
                globalAgg.einheiten += einheiten;
            }

            // Projekte ohne Zeiterfassung werden nicht angezeigt und nicht berechnet
            if (projektStunden <= 0) {
                continue;
            }

            ProjektAnalyseDto dto = new ProjektAnalyseDto();
            dto.setId(projekt.getId());
            dto.setProjektname(projekt.getBauvorhaben());
            dto.setAuftragsnummer(projekt.getAuftragsnummer());
            dto.setKunde(projekt.getKunde());
            dto.setBildUrl(projekt.getBildUrl());
            dto.setMasseinheit(einheiten);

            projektAgMap.values()
                    .forEach(a -> a.setStundenProEinheit(einheiten != 0 ? a.getStundenProEinheit() / einheiten : 0));
            dto.setArbeitsgaenge(new java.util.ArrayList<>(projektAgMap.values()));
            dto.setZeitGesamt(projektStunden);
            projektDtos.add(dto);

            if (einheiten > 0) {
                gesamtStundenMitZeiten += projektStunden;
                gesamtEinheitenMitZeiten += einheiten;
                sumXMitZeiten += einheiten;
                sumYMitZeiten += projektStunden;
                sumXYMitZeiten += einheiten * projektStunden;
                sumX2MitZeiten += einheiten * einheiten;
                datenpunkte++;
            }
        }

        List<ArbeitsgangAnalyseDto> arbeitsgangAnalysen = arbeitsgangMap.values().stream()
                .map(agg -> {
                    ArbeitsgangAnalyseDto dto = new ArbeitsgangAnalyseDto();
                    dto.setArbeitsgangBeschreibung(agg.beschreibung);
                    dto.setDurchschnittStundenProEinheit(agg.einheiten != 0 ? agg.stunden / agg.einheiten : 0);
                    return dto;
                })
                .collect(Collectors.toList());

        double durchschnitt = gesamtEinheitenMitZeiten != 0 ? gesamtStundenMitZeiten / gesamtEinheitenMitZeiten : 0;
        double steigung = 0;
        double fixzeit = 0;
        int n = datenpunkte;
        if (n > 0) {
            double denominator = n * sumX2MitZeiten - sumXMitZeiten * sumXMitZeiten;
            if (denominator != 0) {
                steigung = (n * sumXYMitZeiten - sumXMitZeiten * sumYMitZeiten) / denominator;
                fixzeit = (sumYMitZeiten - steigung * sumXMitZeiten) / n;
            } else if (sumXMitZeiten != 0) {
                steigung = sumYMitZeiten / sumXMitZeiten;
            }
        }
        if (fixzeit < 0) {
            fixzeit = 0;
        }

        // R² und Residual-Standardabweichung berechnen
        double rQuadrat = 0;
        double residualStdAbweichung = 0;
        if (n >= 2) {
            double yMean = sumYMitZeiten / n;
            double ssTot = 0;
            double ssRes = 0;
            double sumResiduals2 = 0;
            for (var dto : projektDtos) {
                if (dto.getMasseinheit() <= 0) continue;
                double yHat = fixzeit + steigung * dto.getMasseinheit();
                double residual = dto.getZeitGesamt() - yHat;
                ssTot += Math.pow(dto.getZeitGesamt() - yMean, 2);
                ssRes += residual * residual;
                sumResiduals2 += residual * residual;
            }
            rQuadrat = ssTot != 0 ? 1.0 - (ssRes / ssTot) : 0;
            if (rQuadrat < 0) rQuadrat = 0;
            residualStdAbweichung = Math.sqrt(sumResiduals2 / (n - 2));
        }

        ProduktkategorieAnalyseDto analyseDto = new ProduktkategorieAnalyseDto();
        analyseDto.setProjektAnzahl(projektDtos.size());
        analyseDto.setDurchschnittlicheZeit(durchschnitt);
        analyseDto.setFixzeit(fixzeit);
        analyseDto.setSteigung(steigung);
        analyseDto.setVerrechnungseinheit(startKategorie.getVerrechnungseinheit().getAnzeigename());
        analyseDto.setProjekte(projektDtos);
        analyseDto.setArbeitsgangAnalysen(arbeitsgangAnalysen);
        analyseDto.setDatenpunkte(n);
        analyseDto.setRQuadrat(rQuadrat);
        analyseDto.setResidualStdAbweichung(residualStdAbweichung);
        return analyseDto;
    }

    private Map<Long, Long> berechneProjektAnzahlen() {
        List<Object[]> pairs = projektRepository.getKategorieProjektPairs();
        Map<Long, Set<Long>> mapping = new HashMap<>();
        for (Object[] pair : pairs) {
            mapping.computeIfAbsent((Long) pair[0], k -> new HashSet<>()).add((Long) pair[1]);
        }

        List<Produktkategorie> alle = produktkategorieRepository.findAll();
        Map<Long, Long> cumulativeCounts = new HashMap<>();

        for (Produktkategorie kat : alle) {
            Set<Long> allProjectIds = new HashSet<>();
            sammleProjektIdsRekursiv(kat, mapping, allProjectIds);
            cumulativeCounts.put(kat.getId(), (long) allProjectIds.size());
        }
        return cumulativeCounts;
    }

    private void sammleProjektIdsRekursiv(Produktkategorie kat, Map<Long, Set<Long>> mapping, Set<Long> allProjectIds) {
        if (mapping.containsKey(kat.getId())) {
            allProjectIds.addAll(mapping.get(kat.getId()));
        }
        for (Produktkategorie sub : kat.getUnterkategorien()) {
            sammleProjektIdsRekursiv(sub, mapping, allProjectIds);
        }
    }
}
