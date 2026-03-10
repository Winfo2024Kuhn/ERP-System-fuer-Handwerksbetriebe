package org.example.kalkulationsprogramm.service.miete;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.miete.Kostenposition;
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle;
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle;
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand;
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.miete.KostenpositionRepository;
import org.example.kalkulationsprogramm.repository.miete.MieteKostenstelleRepository;
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository;
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository;
import org.example.kalkulationsprogramm.repository.miete.ZaehlerstandRepository;
import org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MietabrechnungService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final MietobjektRepository mietobjektRepository;
    private final MieteKostenstelleRepository kostenstelleRepository;
    private final KostenpositionRepository kostenpositionRepository;
    private final VerbrauchsgegenstandRepository verbrauchsgegenstandRepository;
    private final ZaehlerstandRepository zaehlerstandRepository;
    private final KostenpositionBerechner kostenpositionBerechner;

    public AnnualAccountingResult berechneJahresabrechnung(Long mietobjektId, int jahr) {
        Mietobjekt mietobjekt = mietobjektRepository.findById(mietobjektId)
                .orElseThrow(() -> new NotFoundException("Mietobjekt " + mietobjektId + " nicht gefunden"));

        List<Kostenposition> aktuellePositionen = kostenpositionRepository
                .findByKostenstelleMietobjektIdAndAbrechnungsJahr(mietobjektId, jahr);
        List<Kostenposition> vorjahrPositionen = kostenpositionRepository
                .findByKostenstelleMietobjektIdAndAbrechnungsJahr(mietobjektId, jahr - 1);

        JahrAggregation aktuelles = aggregiereKosten(aktuellePositionen, jahr);
        JahrAggregation vorjahr = aggregiereKosten(vorjahrPositionen, jahr - 1);

        List<Kostenstelle> kostenstellen = kostenstelleRepository.findByMietobjektIdOrderByNameAsc(mietobjektId);

        List<AnnualAccountingResult.KostenstellenResult> kostenstellenErgebnisse = new ArrayList<>();
        for (Kostenstelle kostenstelle : kostenstellen) {
            BigDecimal sumAktuell = aktuelles.summeKostenstelle(kostenstelle);
            BigDecimal sumVorjahr = vorjahr.summeKostenstelle(kostenstelle);
            List<Kostenposition> positionen = aktuelles.positionen(kostenstelle);
            List<AnnualAccountingResult.Parteianteil> anteile = aktuelles.parteianteile(kostenstelle).entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().getName()))
                    .map(e -> AnnualAccountingResult.Parteianteil.builder()
                            .mietpartei(e.getKey())
                            .betrag(e.getValue())
                            .build())
                    .toList();
            kostenstellenErgebnisse.add(AnnualAccountingResult.KostenstellenResult.builder()
                    .kostenstelle(kostenstelle)
                    .gesamtkosten(sumAktuell)
                    .gesamtkostenVorjahr(sumVorjahr)
                    .positionen(positionen)
                    .parteianteile(anteile)
                    .build());
        }

        Set<Mietpartei> parteien = new LinkedHashSet<>(aktuelles.getSummePartei().keySet());
        parteien.addAll(vorjahr.getSummePartei().keySet());

        List<AnnualAccountingResult.ParteiErgebnis> parteiErgebnisse = parteien.stream()
                .sorted(Comparator.comparing(Mietpartei::getName))
                .map(partei -> {
                    BigDecimal aktuell = aktuelles.getSummePartei().getOrDefault(partei, BigDecimal.ZERO);
                    BigDecimal prev = vorjahr.getSummePartei().getOrDefault(partei, BigDecimal.ZERO);
                    BigDecimal monat = partei.getRolle() == MietparteiRolle.MIETER && partei.getMonatlicherVorschuss() != null
                            ? partei.getMonatlicherVorschuss()
                            : BigDecimal.ZERO;
                    if (monat.compareTo(BigDecimal.ZERO) < 0) {
                        monat = BigDecimal.ZERO;
                    }
                    monat = monat.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal jahresVorauszahlung = monat.multiply(BigDecimal.valueOf(12), MC).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal saldo = aktuell.subtract(jahresVorauszahlung, MC).setScale(2, RoundingMode.HALF_UP);
                    return AnnualAccountingResult.ParteiErgebnis.builder()
                            .mietpartei(partei)
                            .betrag(aktuell)
                            .betragVorjahr(prev)
                            .differenz(aktuell.subtract(prev, MC))
                            .vorauszahlungMonatlich(monat)
                            .vorauszahlungJahr(jahresVorauszahlung)
                            .saldo(saldo)
                            .build();
                })
                .toList();

        List<AnnualAccountingResult.Verbrauchsvergleich> verbrauchsvergleiche = berechneVerbrauchsvergleiche(mietobjektId, jahr);

        BigDecimal sumAktuell = aktuelles.getGesamtsumme();
        BigDecimal sumVorjahr = vorjahr.getGesamtsumme();
        String strasse = normalize(mietobjekt.getStrasse());
        String plz = normalize(mietobjekt.getPlz());
        String ort = normalize(mietobjekt.getOrt());

        return AnnualAccountingResult.builder()
                .mietobjektId(mietobjekt.getId())
                .mietobjektName(mietobjekt.getName())
                .mietobjektStrasse(strasse)
                .mietobjektPlz(plz)
                .mietobjektOrt(ort)
                .abrechnungsJahr(jahr)
                .gesamtkosten(sumAktuell)
                .gesamtkostenVorjahr(sumVorjahr)
                .gesamtkostenDifferenz(sumAktuell.subtract(sumVorjahr, MC))
                .kostenstellen(kostenstellenErgebnisse)
                .parteien(parteiErgebnisse)
                .verbrauchsvergleiche(verbrauchsvergleiche)
                .build();
    }

    private JahrAggregation aggregiereKosten(List<Kostenposition> positionen, int jahr) {
        JahrAggregation aggregation = new JahrAggregation();
        Map<Long, Verteilungsschluessel> cache = new LinkedHashMap<>();
        for (Kostenposition position : positionen) {
            Kostenstelle kostenstelle = position.getKostenstelle();
            KostenpositionBerechner.KostenpositionVerteilErgebnis ergebnis =
                    kostenpositionBerechner.berechne(position, jahr, cache);
            Map<Mietpartei, BigDecimal> verteilung = ergebnis.anteile();
            BigDecimal betrag = ergebnis.betrag() != null ? ergebnis.betrag() : BigDecimal.ZERO;

            aggregation.addPosition(kostenstelle, position);
            aggregation.addKostenstelleSumme(kostenstelle, betrag);
            aggregation.addGesamtsumme(betrag);
            aggregation.addParteianteile(kostenstelle, verteilung);
        }
        return aggregation;
    }

    private List<AnnualAccountingResult.Verbrauchsvergleich> berechneVerbrauchsvergleiche(Long mietobjektId, int jahr) {
        List<Verbrauchsgegenstand> gegenstaende = verbrauchsgegenstandRepository.findByRaumMietobjektId(mietobjektId);
        if (gegenstaende.isEmpty()) {
            return List.of();
        }
        Map<Long, Zaehlerstand> aktuelle = zaehlerstandRepository
                .findByVerbrauchsgegenstandInAndAbrechnungsJahr(gegenstaende, jahr)
                .stream()
                .collect(Collectors.toMap(z -> z.getVerbrauchsgegenstand().getId(), z -> z));
        Map<Long, Zaehlerstand> vorjahr = zaehlerstandRepository
                .findByVerbrauchsgegenstandInAndAbrechnungsJahr(gegenstaende, jahr - 1)
                .stream()
                .collect(Collectors.toMap(z -> z.getVerbrauchsgegenstand().getId(), z -> z));

        List<AnnualAccountingResult.Verbrauchsvergleich> result = new LinkedList<>();
        for (Verbrauchsgegenstand gegenstand : gegenstaende) {
            Zaehlerstand cur = aktuelle.get(gegenstand.getId());
            Zaehlerstand prev = vorjahr.get(gegenstand.getId());
            BigDecimal verbrauchJahr = berechneVerbrauch(cur, prev);
            Zaehlerstand prevPrev = null;
            if (prev != null && prev.getAbrechnungsJahr() != null) {
                prevPrev = zaehlerstandRepository
                        .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, prev.getAbrechnungsJahr() - 1)
                        .orElse(null);
            }
            BigDecimal verbrauchVorjahr = berechneVerbrauch(prev, prevPrev);
            BigDecimal differenz = verbrauchJahr.subtract(verbrauchVorjahr, MC);
            String raumName = null;
            if (gegenstand.getRaum() != null && gegenstand.getRaum().getName() != null) {
                String name = gegenstand.getRaum().getName().trim();
                if (!name.isEmpty()) {
                    raumName = name;
                }
            }
            result.add(AnnualAccountingResult.Verbrauchsvergleich.builder()
                    .verbrauchsgegenstand(gegenstand)
                    .raumName(raumName)
                    .verbrauchJahr(verbrauchJahr)
                    .verbrauchVorjahr(verbrauchVorjahr)
                    .differenz(differenz)
                    .build());
        }
        result.sort(Comparator.comparing(v -> v.getVerbrauchsgegenstand().getName()));
        return result;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal berechneVerbrauch(Zaehlerstand current, Zaehlerstand previous) {
        if (current == null) {
            return BigDecimal.ZERO;
        }
        if (current.getVerbrauch() != null) {
            return current.getVerbrauch();
        }
        BigDecimal currentStand = current.getStand();
        BigDecimal previousStand = previous != null ? previous.getStand() : null;
        if (currentStand != null && previousStand != null) {
            return currentStand.subtract(previousStand, MC);
        }
        return BigDecimal.ZERO;
    }

    private static class JahrAggregation {
        private final Map<Kostenstelle, BigDecimal> summeJeKostenstelle = new LinkedHashMap<>();
        private final Map<Kostenstelle, Map<Mietpartei, BigDecimal>> anteileJeKostenstelle = new LinkedHashMap<>();
        private final Map<Kostenstelle, List<Kostenposition>> positionenJeKostenstelle = new LinkedHashMap<>();
        private final Map<Mietpartei, BigDecimal> summeJePartei = new LinkedHashMap<>();
        private BigDecimal gesamtsumme = BigDecimal.ZERO;

        void addKostenstelleSumme(Kostenstelle kostenstelle, BigDecimal betrag) {
            summeJeKostenstelle.merge(kostenstelle, betrag, BigDecimal::add);
        }

        void addParteianteile(Kostenstelle kostenstelle, Map<Mietpartei, BigDecimal> anteile) {
            Map<Mietpartei, BigDecimal> map = anteileJeKostenstelle.computeIfAbsent(kostenstelle, k -> new LinkedHashMap<>());
            anteile.forEach((partei, betrag) -> {
                map.merge(partei, betrag, BigDecimal::add);
                summeJePartei.merge(partei, betrag, BigDecimal::add);
            });
        }

        void addPosition(Kostenstelle kostenstelle, Kostenposition position) {
            positionenJeKostenstelle.computeIfAbsent(kostenstelle, k -> new ArrayList<>()).add(position);
        }

        void addGesamtsumme(BigDecimal betrag) {
            gesamtsumme = gesamtsumme.add(betrag);
        }

        BigDecimal summeKostenstelle(Kostenstelle kostenstelle) {
            return summeJeKostenstelle.getOrDefault(kostenstelle, BigDecimal.ZERO);
        }

        Map<Mietpartei, BigDecimal> parteianteile(Kostenstelle kostenstelle) {
            return anteileJeKostenstelle.getOrDefault(kostenstelle, Map.of());
        }

        List<Kostenposition> positionen(Kostenstelle kostenstelle) {
            return positionenJeKostenstelle.getOrDefault(kostenstelle, List.of());
        }

        Map<Mietpartei, BigDecimal> getSummePartei() {
            return summeJePartei;
        }

        BigDecimal getGesamtsumme() {
            return gesamtsumme;
        }
    }
}

