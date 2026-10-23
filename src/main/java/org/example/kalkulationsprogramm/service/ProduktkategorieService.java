package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Leistung;
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
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class ProduktkategorieService {
    /** Laenge der Spalte {@code produktkategorie.bezeichnung} (VARCHAR(255)). */
    static final int MAX_NAME_LAENGE = 255;

    private final ProduktkategorieRepository produktkategorieRepository;
    private final ProjektRepository projektRepository;
    private final ProduktkategorieMapper produktkategorieMapper;
    private final DateiSpeicherService dateiSpeicherService;
    private final LeistungRepository leistungRepository;

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

    /**
     * Alle Ordner. Die Projektanzahl ist immer gefüllt (auch bei {@code light}): sie kommt aus
     * zwei Abfragen und wird im Speicher über den Baum aufsummiert.
     */
    public List<ProduktkategorieResponseDto> findeAlleKategorien(boolean light) {
        Map<Long, Long> counts = berechneProjektAnzahlen();
        // Alle ParentIds in einem separaten Query – kein Collection-Load für isLeaf nötig
        Set<Long> parentIds = new HashSet<>(produktkategorieRepository.findAllParentIds());
        // JOIN FETCH auf uebergeordneteKategorie – kein N+1 bei bauePfad/parentId
        return produktkategorieRepository.findAllWithParent().stream()
                .map(kat -> {
                    ProduktkategorieResponseDto dto = produktkategorieMapper.toProduktkategorieResponseDtoWithLeaf(kat, !parentIds.contains(kat.getId()));
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
        return findeKategorieById(id, false);
    }

    /** {@code light = true} spart die teure Projektzaehlung (z. B. fuer den Ordnerpfad im Dokumenteditor). */
    public ProduktkategorieResponseDto findeKategorieById(Long id, boolean light) {
        Map<Long, Long> counts = light ? Map.of() : berechneProjektAnzahlen();
        return produktkategorieRepository.findById(id)
                .map(kat -> {
                    ProduktkategorieResponseDto dto = produktkategorieMapper.toProduktkategorieResponseDto(kat);
                    if (!light) dto.setProjektAnzahl(counts.getOrDefault(kat.getId(), 0L));
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
        String name = pruefeName(dto.getBezeichnung());
        Produktkategorie parent = null;
        if (dto.getParentId() != null) {
            parent = produktkategorieRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Der übergeordnete Ordner existiert nicht mehr."));
        }
        // Leistungen gibt es nur in Ordnern ohne Unterordner. Bekommt ein Ordner mit Leistungen
        // seinen ersten Unterordner, wandern sie dorthin – aber nur, wenn das so gewollt ist.
        List<Leistung> leistungenImParent = parent != null
                ? leistungRepository.findByKategorieId(parent.getId())
                : List.of();
        if (!leistungenImParent.isEmpty() && !dto.isLeistungenMitnehmen()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "In „" + parent.getBezeichnung() + "“ liegen " + leistungenImParent.size()
                            + " Leistungen. Ein Unterordner geht nur, wenn sie mit in den neuen Ordner wandern.");
        }

        Produktkategorie neueKategorie = new Produktkategorie();
        neueKategorie.setBezeichnung(name);
        neueKategorie.setVerrechnungseinheit(dto.getVerrechnungseinheit() != null
                ? dto.getVerrechnungseinheit()
                : parent != null ? parent.getVerrechnungseinheit() : Verrechnungseinheit.STUECK);
        neueKategorie.setBeschreibung(dto.getBeschreibung());
        neueKategorie.setUebergeordneteKategorie(parent);
        if (bild != null && !bild.isEmpty()) {
            neueKategorie.setBildUrl(speichereBild(bild));
            raeumeBeiRollbackAuf(neueKategorie.getBildUrl());
        }

        Produktkategorie gespeicherteKategorie = produktkategorieRepository.save(neueKategorie);
        if (!leistungenImParent.isEmpty()) {
            leistungenImParent.forEach(l -> l.setKategorie(gespeicherteKategorie));
            leistungRepository.saveAll(leistungenImParent);
        }
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Diesen Ordner gibt es nicht mehr."));

        kategorie.setBezeichnung(pruefeName(dto.getBezeichnung()));
        if (dto.getVerrechnungseinheit() != null) {
            kategorie.setVerrechnungseinheit(dto.getVerrechnungseinheit());
        }
        kategorie.setBeschreibung(dto.getBeschreibung());

        if (bild != null && !bild.isEmpty()) {
            String neuesBild = speichereBild(bild);
            raeumeBeiRollbackAuf(neuesBild);
            loescheBildDatei(kategorie.getBildUrl());
            kategorie.setBildUrl(neuesBild);
        } else if (dto.isBildEntfernen()) {
            loescheBildDatei(kategorie.getBildUrl());
            kategorie.setBildUrl(null);
        }

        Produktkategorie gespeichert = produktkategorieRepository.save(kategorie);
        return produktkategorieMapper.toProduktkategorieResponseDto(gespeichert);
    }

    /**
     * Löscht einen Ordner ohne Bezüge. Hängen Unterordner, Leistungen oder Projekte daran,
     * entscheidet der Nutzer im Lösch-Dialog ({@link ProduktkategorieVerwaltungService}).
     */
    @Transactional
    public void loescheKategorie(Long kategorieId) {
        Produktkategorie kategorie = produktkategorieRepository.findById(kategorieId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Diesen Ordner gibt es nicht mehr."));
        if (!kategorie.getUnterkategorien().isEmpty()
                || leistungRepository.existsByKategorieId(kategorieId)
                || projektRepository.countByProduktkategorieId(kategorieId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "„" + kategorie.getBezeichnung() + "“ wird noch verwendet (Unterordner, Leistungen oder Projekte). "
                            + "Bitte wählen Sie, ob der Inhalt umziehen oder mitgelöscht werden soll.");
        }
        loescheBildDatei(kategorie.getBildUrl());
        produktkategorieRepository.delete(kategorie);
    }

    static String pruefeName(String bezeichnung) {
        if (bezeichnung == null || bezeichnung.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte geben Sie dem Ordner einen Namen.");
        }
        String name = bezeichnung.strip();
        if (name.length() > MAX_NAME_LAENGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Name des Ordners ist zu lang (höchstens " + MAX_NAME_LAENGE + " Zeichen).");
        }
        return name;
    }

    private String speichereBild(MultipartFile bild) {
        try {
            return dateiSpeicherService.speichereBild(bild);
        } catch (RuntimeException e) {
            log.warn("Ordnerbild nicht gespeichert: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Das Bild konnte nicht übernommen werden. Erlaubt sind JPEG, PNG, GIF und WebP.");
        }
    }

    /**
     * Löscht die Bilddatei eines Ordners – innerhalb einer Transaktion erst nach dem Commit.
     * Scheitert das Speichern (Rollback), zeigt die Datenbank weiter auf das Bild, also muss die
     * Datei dann noch da sein. Eine fehlende oder nicht löschbare Datei verhindert nichts.
     */
    public void loescheBildDatei(String bildUrl) {
        if (bildUrl == null || bildUrl.isBlank()) {
            return;
        }
        nachAbschluss(true, () -> loescheBildSofort(bildUrl));
    }

    /** Ein gerade gespeichertes Bild wieder entfernen, wenn die Transaktion zurückgerollt wird. */
    private void raeumeBeiRollbackAuf(String neuesBild) {
        if (neuesBild != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            nachAbschluss(false, () -> loescheBildSofort(neuesBild));
        }
    }

    /** {@code beiCommit}: nach erfolgreichem Commit; sonst nach einem Rollback. Ohne Transaktion sofort. */
    private static void nachAbschluss(boolean beiCommit, Runnable aufgabe) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            aufgabe.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (beiCommit ? status == STATUS_COMMITTED : status == STATUS_ROLLED_BACK) {
                    aufgabe.run();
                }
            }
        });
    }

    private void loescheBildSofort(String bildUrl) {
        try {
            dateiSpeicherService.loescheBild(bildUrl);
        } catch (RuntimeException e) {
            log.warn("Ordnerbild konnte nicht gelöscht werden: {}", e.getMessage());
        }
    }

    private void sammleUnterkategorien(Produktkategorie kategorie, List<Produktkategorie> gesammelteKategorien,
            Verrechnungseinheit vergleichsEinheit) {
        if (kategorie.getVerrechnungseinheit() != vergleichsEinheit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "„" + kategorie.getBezeichnung() + "“ hat eine andere Einheit als der gewählte Ordner. "
                            + "Die genaue Analyse geht nur, wenn alle Unterordner dieselbe Einheit haben.");
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

    /** Projektanzahl je Ordner inkl. aller Unterordner (jedes Projekt zählt einmal). */
    private Map<Long, Long> berechneProjektAnzahlen() {
        Map<Long, Set<Long>> projekteJeKategorie = new HashMap<>();
        for (Object[] pair : projektRepository.getKategorieProjektPairs()) {
            projekteJeKategorie.computeIfAbsent((Long) pair[0], k -> new HashSet<>()).add((Long) pair[1]);
        }
        KategorieBaum baum = KategorieBaum.aus(produktkategorieRepository.findAllWithParent());
        Map<Long, Long> anzahlen = new HashMap<>();
        for (Long id : baum.ids()) {
            Set<Long> projekte = new HashSet<>();
            for (Long unterordner : baum.mitUnterordnern(id)) {
                projekte.addAll(projekteJeKategorie.getOrDefault(unterordner, Set.of()));
            }
            anzahlen.put(id, (long) projekte.size());
        }
        return anzahlen;
    }
}
