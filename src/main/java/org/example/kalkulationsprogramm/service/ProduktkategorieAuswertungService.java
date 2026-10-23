package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieAuswertungZeileDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Übersicht über mehrere Ordner nebeneinander: Projekte, Stunden, Menge und Stunden je Einheit.
 * Rechnet mit drei Abfragen für alle Ordner zugleich statt Projekt für Projekt.
 * Grundlage sind – wie bei der Einzel-Analyse – nur abgeschlossene Projekte.
 */
@Service
@RequiredArgsConstructor
public class ProduktkategorieAuswertungService {

    static final int JAHR_MIN = 2000;
    static final int JAHR_MAX = 2100;

    private final ProduktkategorieRepository produktkategorieRepository;
    private final ProjektProduktkategorieRepository projektProduktkategorieRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final LeistungRepository leistungRepository;
    private final ProduktkategorieMapper produktkategorieMapper;

    /**
     * @param jahr     Abschlussjahr der Projekte, {@code null} = alle Jahre
     * @param parentId {@code null} = Hauptordner, sonst dessen direkte Unterordner
     */
    @Transactional(readOnly = true)
    public List<KategorieAuswertungZeileDto> werteAus(Integer jahr, Long parentId) {
        if (jahr != null && (jahr < JAHR_MIN || jahr > JAHR_MAX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bitte ein Jahr zwischen " + JAHR_MIN + " und " + JAHR_MAX + " wählen.");
        }
        KategorieBaum baum = KategorieBaum.aus(produktkategorieRepository.findAllWithParent());
        List<Long> zeilen;
        if (parentId == null) {
            zeilen = baum.hauptordner();
        } else if (baum.get(parentId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diesen Ordner gibt es nicht mehr.");
        } else {
            zeilen = baum.kinder(parentId);
        }
        if (zeilen.isEmpty()) {
            return List.of();
        }

        LocalDate von = jahr != null ? LocalDate.of(jahr, 1, 1) : null;
        LocalDate bis = jahr != null ? LocalDate.of(jahr, 12, 31) : null;
        Map<Long, Map<Long, Double>> stunden = jeKategorieUndProjekt(
                zeitbuchungRepository.summiereStundenAbgeschlossenerProjekte(von, bis));
        Map<Long, Map<Long, Double>> mengen = jeKategorieUndProjekt(
                projektProduktkategorieRepository.findeMengenAbgeschlossenerProjekte(von, bis));
        Map<Long, Long> leistungen = new HashMap<>();
        for (Object[] zeile : leistungRepository.zaehleJeKategorie()) {
            leistungen.put((Long) zeile[0], ((Number) zeile[1]).longValue());
        }

        List<KategorieAuswertungZeileDto> ergebnis = new ArrayList<>();
        for (Long id : zeilen) {
            ergebnis.add(zeile(baum, id, stunden, mengen, leistungen));
        }
        ergebnis.sort(Comparator.comparingDouble(KategorieAuswertungZeileDto::stunden).reversed()
                .thenComparing(KategorieAuswertungZeileDto::bezeichnung, String.CASE_INSENSITIVE_ORDER));
        return ergebnis;
    }

    private KategorieAuswertungZeileDto zeile(KategorieBaum baum, Long id, Map<Long, Map<Long, Double>> stunden,
            Map<Long, Map<Long, Double>> mengen, Map<Long, Long> leistungen) {
        Produktkategorie ordner = baum.get(id);
        Verrechnungseinheit einheit = ordner.getVerrechnungseinheit();
        Set<Long> projekte = new HashSet<>();
        double stundenGesamt = 0;
        long leistungAnzahl = 0;
        // Je Projekt nur Ordner mit derselben Einheit – Meter und Stück lassen sich nicht addieren.
        Map<Long, Double> stundenGleicheEinheit = new HashMap<>();
        Map<Long, Double> mengeGleicheEinheit = new HashMap<>();
        for (Long k : baum.mitUnterordnern(id)) {
            Map<Long, Double> stundenK = stunden.getOrDefault(k, Map.of());
            Map<Long, Double> mengenK = mengen.getOrDefault(k, Map.of());
            projekte.addAll(stundenK.keySet());
            projekte.addAll(mengenK.keySet());
            stundenGesamt += stundenK.values().stream().mapToDouble(Double::doubleValue).sum();
            leistungAnzahl += leistungen.getOrDefault(k, 0L);
            if (baum.get(k).getVerrechnungseinheit() == einheit) {
                stundenK.forEach((projekt, h) -> stundenGleicheEinheit.merge(projekt, h, Double::sum));
                mengenK.forEach((projekt, m) -> mengeGleicheEinheit.merge(projekt, m, Double::sum));
            }
        }
        double menge = mengeGleicheEinheit.values().stream().mapToDouble(Double::doubleValue).sum();
        // Wie die Einzel-Analyse: nur Projekte, die sowohl Zeiten als auch eine Menge haben.
        double quoteStunden = 0;
        double quoteMenge = 0;
        for (Map.Entry<Long, Double> e : mengeGleicheEinheit.entrySet()) {
            double h = stundenGleicheEinheit.getOrDefault(e.getKey(), 0d);
            if (h > 0 && e.getValue() > 0) {
                quoteStunden += h;
                quoteMenge += e.getValue();
            }
        }
        String bildUrl = produktkategorieMapper.toProduktkategorieResponseDtoWithLeaf(ordner, true).getBildUrl();
        return new KategorieAuswertungZeileDto(
                id,
                ordner.getBezeichnung(),
                bildUrl,
                einheit != null ? einheit.getAnzeigename() : null,
                !baum.hatKinder(id),
                projekte.size(),
                runde(stundenGesamt),
                runde(menge),
                quoteMenge > 0 ? runde(quoteStunden / quoteMenge) : null,
                leistungAnzahl);
    }

    /** {@code [kategorieId, projektId, wert]} → kategorieId → projektId → Summe. */
    private static Map<Long, Map<Long, Double>> jeKategorieUndProjekt(List<Object[]> zeilen) {
        Map<Long, Map<Long, Double>> ergebnis = new HashMap<>();
        for (Object[] zeile : zeilen) {
            double wert = zeile[2] instanceof Number n ? n.doubleValue() : 0d;
            ergebnis.computeIfAbsent((Long) zeile[0], k -> new HashMap<>())
                    .merge((Long) zeile[1], wert, Double::sum);
        }
        return ergebnis;
    }

    private static double runde(double wert) {
        return Math.round(wert * 100d) / 100d;
    }
}
