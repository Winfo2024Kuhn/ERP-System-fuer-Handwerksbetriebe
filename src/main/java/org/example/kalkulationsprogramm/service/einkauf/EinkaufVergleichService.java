package org.example.kalkulationsprogramm.service.einkauf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.kalkulationsprogramm.domain.einkauf.AngebotKostenbestandteil;
import org.example.kalkulationsprogramm.domain.einkauf.AngebotPosition;
import org.example.kalkulationsprogramm.domain.einkauf.AngebotVersion;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAngebot;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Kosten;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.VersionDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVergleichDto.*;
import org.example.kalkulationsprogramm.repository.AngebotVersionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufsanfrageRepository;
import org.example.kalkulationsprogramm.repository.EinkaufAngebotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EinkaufVergleichService {
    private final EinkaufsanfrageRepository anfragen;
    private final EinkaufAngebotRepository angebote;
    private final AngebotVersionRepository versionen;
    private final EinkaufAngebotService angebotService;
    private final EinkaufMengenUmrechnung umrechnung = new EinkaufMengenUmrechnung();

    public EinkaufVergleichService(EinkaufsanfrageRepository anfragen, EinkaufAngebotRepository angebote,
            AngebotVersionRepository versionen, EinkaufAngebotService angebotService) {
        this.anfragen = anfragen; this.angebote = angebote; this.versionen = versionen; this.angebotService = angebotService;
    }

    public Vergleich vergleiche(Long anfrageId, LocalDate stichtag) {
        if (anfrageId == null || anfrageId <= 0 || stichtag == null) throw new IllegalArgumentException("Anfrage und Stichtag sind erforderlich.");
        var anfrage = anfragen.findById(anfrageId).filter(a -> !a.isGeloescht())
                .orElseThrow(() -> new java.util.NoSuchElementException("Anfrage nicht gefunden."));
        if (anfrage.getAktuelleRevision() == null) return new Vergleich(anfrageId, stichtag, List.of(), null);
        long currentRevisionId = anfrage.getAktuelleRevision().getId();
        List<AngebotSumme> sums = new ArrayList<>();
        List<AngebotVersion> rankable = new ArrayList<>();
        for (EinkaufAngebot offer : angebote.findAllByBeteiligungRevisionAnfrageIdOrderById(anfrageId)) {
            AngebotVersion latest = versionen.findFirstByAngebotIdOrderByNummerDesc(offer.getId()).orElse(null);
            if (latest == null) continue;
            VersionDto dto = angebotService.laden(offer.getId()).versionen().stream()
                    .filter(v -> v.id().equals(latest.getId())).findFirst().orElseThrow();
            List<Herkunft> packageAmounts = anfrage.getAktuelleRevision().getPositionen().stream()
                    .flatMap(p -> p.getHerkuenfte().stream()).map(h -> new Herkunft(h.getBedarf().getId(), h.getBedarfVersion(), h.getMenge())).toList();
            AngebotSumme calculated = berechne(dto, packageAmounts, stichtag);
            if (!latest.getAnfrageRevision().getId().equals(currentRevisionId)) {
                List<String> blockers = new ArrayList<>(calculated.hindernisse());
                blockers.add("ANFRAGEFASSUNG_ABWEICHEND");
                calculated = new AngebotSumme(calculated.angebotVersionId(), null, false, calculated.technischGeeignet(),
                        calculated.gueltig(), blockers, calculated.rechnung());
            }
            sums.add(calculated);
            if (rankable(offer, latest, calculated)) rankable.add(latest);
        }
        rankable.sort(Comparator.comparing((AngebotVersion v) -> sumFor(sums, v.getId())).thenComparing(AngebotVersion::getId));
        Long best = rankable.isEmpty() ? null : rankable.getFirst().getId();
        sums.sort(Comparator.comparing((AngebotSumme s) -> rankable.stream().noneMatch(v -> v.getId().equals(s.angebotVersionId())))
                .thenComparing(AngebotSumme::nettoGesamt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AngebotSumme::angebotVersionId, Comparator.nullsLast(Comparator.naturalOrder())));
        return new Vergleich(anfrageId, stichtag, sums, best);
    }

    public AngebotSumme berechne(VersionDto angebot, List<Herkunft> paket, LocalDate stichtag) {
        if (angebot == null || angebot.id() == null || paket == null || paket.isEmpty() || stichtag == null)
            throw new IllegalArgumentException("Angebot, Paket und Stichtag sind erforderlich.");
        AngebotVersion version = versionen.findById(angebot.id()).orElseThrow(() -> new java.util.NoSuchElementException("Angebotsversion nicht gefunden."));
        List<String> blockers = new ArrayList<>();
        List<Rechenschritt> steps = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean complete = true;
        Map<Long, Herkunft> selected = new HashMap<>();
        for (Herkunft h : paket) {
            if (h == null || h.bedarfId() == null || h.menge() == null || h.menge().signum() <= 0) {
                complete = false; blockers.add("PAKETMENGE_UNGUELTIG"); continue;
            }
            if (selected.putIfAbsent(h.bedarfId(), h) != null) { complete = false; blockers.add("PAKETMENGE_DOPPELT"); }
        }
        Set<Long> matched = new HashSet<>();
        for (AngebotPosition offered : version.getPositionen()) {
            var requestPosition = offered.getAnfragePosition();
            var base = requestPosition.getSnapshot().basis();
            Map<String, org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis> packageBases = new LinkedHashMap<>();
            for (var origin : requestPosition.getHerkuenfte()) {
                Herkunft selectedAmount = selected.get(origin.getBedarf().getId());
                if (selectedAmount == null) continue;
                if (selectedAmount.version() != origin.getBedarfVersion() || selectedAmount.menge().compareTo(origin.getMenge()) > 0) {
                    complete = false;
                    blockers.add("PAKET_HERKUNFT_VERALTET:" + origin.getBedarf().getId());
                    continue;
                }
                matched.add(origin.getBedarf().getId());
                var group = origin.getBedarf().getLiefergruppe();
                if (group == null) {
                    complete = false;
                    blockers.add("LIEFERGRUPPE_FEHLT:" + origin.getBedarf().getId());
                    continue;
                }
                String groupKey = java.util.Objects.toString(group.projektId(), "") + "\u0000"
                        + java.util.Objects.toString(group.lieferadresse(), "") + "\u0000"
                        + java.util.Objects.toString(group.bedarfstermin(), "") + "\u0000"
                        + java.util.Objects.toString(group.lagerzweck(), "");
                var existing = packageBases.get(groupKey);
                BigDecimal groupQuantity = selectedAmount.menge().add(existing == null ? BigDecimal.ZERO : existing.menge());
                packageBases.put(groupKey, new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis(
                        groupQuantity, base.einheit(), base.einheit() == Einheit.STUECK ? groupQuantity : base.stueckzahl(),
                        base.einzelLaengeMm(), base.kgJeMeter(), base.faktorQuelle()));
            }
            if (packageBases.isEmpty()) continue;
            BigDecimal packageQuantity = packageBases.values().stream()
                    .map(org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis::menge)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            blockers.addAll(pruefeMindestmengeVerpackung(packageQuantity, offered.getMindestmenge(), offered.getVerpackungseinheit()));
            List<Kosten> positionCosts = version.getKosten().stream().filter(c -> offered.getId().equals(c.getPositionId()))
                    .map(EinkaufVergleichService::dto).toList();
            var calc = berechneKostenJeLiefergruppe(positionCosts, packageBases);
            steps.addAll(calc.rechnung());
            if (!calc.vollstaendig()) { complete = false; blockers.addAll(calc.hindernisse()); }
            else if (calc.nettoGesamt() != null) total = total.add(calc.nettoGesamt());
            if (offered.getAbweichungen() != null && !offered.getAbweichungen().isEmpty()
                    && !technischeAbweichungBestaetigt(version.getStatus(), version.getAbweichungBestaetigtVon())) blockers.add("TECHNISCHE_ABWEICHUNG_BESTAETIGEN");
            var required = requestPosition.getSnapshot().dokumente();
            for (var document : required) {
                boolean available = offered.getZeugnisse().stream().anyMatch(z -> z.art() == document.art()
                        && ("ENTHALTEN".equals(z.status()) || "AUFPREIS".equals(z.status())));
                if (!available) { blockers.add("ZEUGNIS_OFFEN:" + document.art()); }
            }
        }
        if (matched.size() < selected.size()) { complete = false; blockers.add("PAKET_HERKUNFT_NICHT_IM_ANGEBOT"); }
        List<Kosten> headerCosts = angebot.kosten();
        boolean fullPackage = version.getAnfrageRevision().getPositionen().stream().flatMap(p -> p.getHerkuenfte().stream())
                .allMatch(origin -> selected.containsKey(origin.getBedarf().getId())
                        && selected.get(origin.getBedarf().getId()).version() == origin.getBedarfVersion()
                        && selected.get(origin.getBedarf().getId()).menge().compareTo(origin.getMenge()) == 0);
        if (!fullPackage && headerCosts.stream().anyMatch(c -> "RABATT".equals(c.art()))) {
            blockers.add("PAKETRABATT_ENTFAELLT_BEI_TEILMENGE");
        }
        headerCosts = kostenFuerPaket(headerCosts, fullPackage);
        if (!headerCosts.isEmpty()) {
            List<Kosten> freight = headerCosts.stream().filter(c -> "FRACHT".equals(c.art())).toList();
            List<Kosten> otherHeaderCosts = headerCosts.stream().filter(c -> !"FRACHT".equals(c.art())).toList();
            Map<String, org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis> freightGroups = new LinkedHashMap<>();
            for (Herkunft item : selected.values()) {
                var requestOrigin = version.getAnfrageRevision().getPositionen().stream().flatMap(p -> p.getHerkuenfte().stream())
                        .filter(h -> h.getBedarf().getId().equals(item.bedarfId())).findFirst().orElse(null);
                if (requestOrigin == null || requestOrigin.getBedarf().getLiefergruppe() == null) continue;
                var group = requestOrigin.getBedarf().getLiefergruppe();
                String key = java.util.Objects.toString(group.projektId(), "") + "\u0000"
                        + java.util.Objects.toString(group.lieferadresse(), "") + "\u0000"
                        + java.util.Objects.toString(group.bedarfstermin(), "") + "\u0000"
                        + java.util.Objects.toString(group.lagerzweck(), "");
                freightGroups.putIfAbsent(key, new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis(
                        BigDecimal.ONE, Einheit.STUECK, BigDecimal.ONE, null, null, null));
            }
            if (!freight.isEmpty()) {
                KostenBerechnung freightTotal = berechneKostenJeLiefergruppe(freight, freightGroups);
                steps.addAll(freightTotal.rechnung());
                if (!freightTotal.vollstaendig()) { complete = false; blockers.addAll(freightTotal.hindernisse()); }
                else if (freightTotal.nettoGesamt() != null) total = total.add(freightTotal.nettoGesamt());
            }
            if (!otherHeaderCosts.isEmpty()) {
                KostenBerechnung head = berechneKosten(otherHeaderCosts, BigDecimal.ONE, Einheit.STUECK, umrechnung);
                steps.addAll(head.rechnung());
                if (!head.vollstaendig()) { complete = false; blockers.addAll(head.hindernisse()); }
                else if (head.nettoGesamt() != null) total = total.add(head.nettoGesamt());
            }
        }
        boolean valid = istGueltig(version.getGueltigBis(), stichtag);
        if (!valid) blockers.add("ANGEBOT_ABGELAUFEN");
        if (!"EUR".equals(version.getWaehrung())) blockers.add("WAEHRUNG_NICHT_RANKBAR");
        Rechenschritt skonto = berechneSkonto(total, version.getSkontoProzent(), version.getSkontoTage());
        if (skonto != null) steps.add(skonto);
        boolean technicallyEligible = "GEPRUEFT".equals(version.getStatus()) && blockers.stream().noneMatch(b ->
                b.startsWith("TECHNISCHE_ABWEICHUNG") || b.startsWith("ZEUGNIS_OFFEN")
                        || b.startsWith("MINDESTMENGE_") || b.startsWith("VERPACKUNGSEINHEIT_"));
        if (!technicallyEligible && !"GEPRUEFT".equals(version.getStatus())) blockers.add("ANGEBOT_NOCH_NICHT_GEPRUEFT");
        return new AngebotSumme(version.getId(), complete ? total.setScale(2, RoundingMode.HALF_UP) : null, complete,
                technicallyEligible, valid, blockers.stream().distinct().toList(), steps);
    }

    public static List<String> pruefeMindestmengeVerpackung(BigDecimal menge, BigDecimal mindestmenge,
            BigDecimal verpackungseinheit) {
        List<String> issues = new ArrayList<>();
        if (menge == null || menge.signum() <= 0) return List.of("PAKETMENGE_UNGUELTIG");
        if (mindestmenge != null && menge.compareTo(mindestmenge) < 0) issues.add("MINDESTMENGE_NICHT_ERREICHT");
        if (verpackungseinheit != null && verpackungseinheit.signum() > 0
                && menge.remainder(verpackungseinheit).compareTo(BigDecimal.ZERO) != 0)
            issues.add("VERPACKUNGSEINHEIT_NICHT_ERFUELLT");
        return List.copyOf(issues);
    }

    public static boolean istGueltig(LocalDate gueltigBis, LocalDate stichtag) {
        return stichtag != null && (gueltigBis == null || !gueltigBis.isBefore(stichtag));
    }

    public static Rechenschritt berechneSkonto(BigDecimal netto, BigDecimal prozent, Integer tage) {
        if (netto == null || netto.signum() <= 0 || prozent == null || prozent.signum() <= 0) return null;
        BigDecimal basis = netto.setScale(2, RoundingMode.HALF_UP);
        BigDecimal skonto = basis.multiply(prozent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Rechenschritt("SKONTO_SEPARAT", prozent + "% binnen " + tage + " Tagen", basis, skonto, "Zahlungsbedingungen");
    }

    public static List<Kosten> kostenFuerPaket(List<Kosten> kosten, boolean vollstaendigesPaket) {
        if (kosten == null || kosten.isEmpty()) return List.of();
        return kosten.stream().filter(k -> vollstaendigesPaket || !"RABATT".equals(k.art())).toList();
    }

    public static boolean technischeAbweichungBestaetigt(String status, Long bestaetigtVon) {
        return "GEPRUEFT".equals(status) && bestaetigtVon != null && bestaetigtVon > 0;
    }

    public static KostenBerechnung berechneKostenJeLiefergruppe(List<Kosten> costs,
            Map<String, org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis> gruppen) {
        if (gruppen == null || gruppen.isEmpty())
            return new KostenBerechnung(null, false, List.of("LIEFERGRUPPE_FEHLT"), List.of());
        BigDecimal total = BigDecimal.ZERO;
        boolean complete = true;
        List<String> issues = new ArrayList<>();
        List<Rechenschritt> steps = new ArrayList<>();
        int groupNumber = 0;
        for (var group : new java.util.TreeMap<>(gruppen).entrySet()) {
            groupNumber++;
            KostenBerechnung calculation = berechneKosten(costs, group.getValue(), new EinkaufMengenUmrechnung());
            if (!calculation.vollstaendig()) { complete = false; issues.addAll(calculation.hindernisse()); }
            else if (calculation.nettoGesamt() != null) total = total.add(calculation.nettoGesamt());
            for (Rechenschritt step : calculation.rechnung()) {
                steps.add(new Rechenschritt(step.key() + "@Liefergruppe-" + groupNumber, step.formel(), step.basis(), step.ergebnis(), step.quellenbezug()));
            }
        }
        return new KostenBerechnung(complete ? total.setScale(2, RoundingMode.HALF_UP) : null, complete,
                issues.stream().distinct().toList(), steps);
    }

    public static KostenBerechnung berechneKosten(List<Kosten> costs, BigDecimal quantity, Einheit quantityUnit) {
        return berechneKosten(costs, quantity, quantityUnit, new EinkaufMengenUmrechnung());
    }

    private static KostenBerechnung berechneKosten(List<Kosten> costs, BigDecimal quantity, Einheit quantityUnit,
            EinkaufMengenUmrechnung converter) {
        return berechneKosten(costs, new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis(
                quantity, quantityUnit, null, null, null, null), converter);
    }

    public static KostenBerechnung berechneKosten(List<Kosten> costs,
            org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis basis) {
        return berechneKosten(costs, basis, new EinkaufMengenUmrechnung());
    }

    private static KostenBerechnung berechneKosten(List<Kosten> costs,
            org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis basis,
            EinkaufMengenUmrechnung converter) {
        BigDecimal quantity = basis == null ? null : basis.menge();
        Einheit quantityUnit = basis == null ? null : basis.einheit();
        if (costs == null || costs.isEmpty() || quantity == null || quantity.signum() <= 0 || quantityUnit == null)
            return new KostenBerechnung(null, false, List.of("KOSTEN_ODER_MENGE_FEHLT"), List.of());
        Map<String, Kosten> byKey = new LinkedHashMap<>();
        for (Kosten c : costs) {
            if (c == null || c.schluessel() == null || byKey.putIfAbsent(c.schluessel(), c) != null)
                return new KostenBerechnung(null, false, List.of("KOSTEN_SCHLUESSEL_UNGUELTIG"), List.of());
        }
        Map<String, BigDecimal> calculated = new HashMap<>();
        Set<String> active = new HashSet<>();
        Set<String> incomplete = new HashSet<>();
        for (Kosten cost : byKey.values()) calcCost(cost, byKey, calculated, active, incomplete, basis, converter);
        List<Rechenschritt> steps = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Kosten cost : byKey.values()) {
            BigDecimal value = calculated.get(cost.schluessel());
            if (value == null) { incomplete.add(cost.schluessel()); continue; }
            String formula = "PROZENT".equals(cost.basis()) ? cost.betrag() + "% von " + cost.prozentBasisSchluessel()
                    : (cost.betrag() + " je " + cost.basisMenge() + " " + cost.basis());
            steps.add(new Rechenschritt(cost.schluessel(), formula, quantity, value, cost.quelle()));
            if (!cost.enthalten()) {
                total = "RABATT".equals(cost.art()) ? total.subtract(value) : total.add(value);
            }
        }
        if (total.signum() < 0) incomplete.add("RABATT");
        return new KostenBerechnung(incomplete.isEmpty() ? total.setScale(2, RoundingMode.HALF_UP) : null,
                incomplete.isEmpty(), incomplete.stream().map(key -> "KOSTEN_UNVOLLSTAENDIG:" + key).toList(), steps);
    }

    private static BigDecimal calcCost(Kosten cost, Map<String, Kosten> byKey, Map<String, BigDecimal> done,
            Set<String> active, Set<String> incomplete,
            org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis basis,
            EinkaufMengenUmrechnung converter) {
        if (done.containsKey(cost.schluessel())) return done.get(cost.schluessel());
        if (!active.add(cost.schluessel()) || cost.betrag() == null || cost.variabel()) { incomplete.add(cost.schluessel()); return null; }
        BigDecimal result = null;
        if ("PROZENT".equals(cost.basis())) {
            Kosten base = byKey.get(cost.prozentBasisSchluessel());
            BigDecimal baseValue = base == null ? null : calcCost(base, byKey, done, active, incomplete, basis, converter);
            if (baseValue != null) result = baseValue.multiply(cost.betrag()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        } else if ("PAUSCHAL".equals(cost.basis())) {
            if (cost.basisMenge() != null && cost.basisMenge().signum() > 0) result = cost.betrag().divide(cost.basisMenge(), 6, RoundingMode.HALF_UP);
        } else if (cost.basisMenge() != null && cost.basisMenge().signum() > 0) {
            Einheit target = switch (cost.basis()) {
                case "STUECK", "100STUECK" -> Einheit.STUECK;
                case "M" -> Einheit.METER;
                case "KG", "100KG", "T" -> Einheit.KILOGRAMM;
                default -> null;
            };
            var conversion = target == null ? null : converter.normalisieren(basis, target);
            if (conversion != null && conversion.vollstaendig()) {
                BigDecimal basisFactor = switch (cost.basis()) {
                    case "100KG", "100STUECK" -> BigDecimal.valueOf(100);
                    case "T" -> BigDecimal.valueOf(1000);
                    default -> BigDecimal.ONE;
                };
                result = cost.betrag().multiply(conversion.menge()).divide(cost.basisMenge().multiply(basisFactor), 6, RoundingMode.HALF_UP);
            } else incomplete.add(cost.schluessel());
        }
        active.remove(cost.schluessel());
        if (result != null) done.put(cost.schluessel(), result.setScale(2, RoundingMode.HALF_UP));
        else incomplete.add(cost.schluessel());
        return result == null ? null : done.get(cost.schluessel());
    }

    private static Kosten dto(AngebotKostenbestandteil c) { return new Kosten(c.getSchluessel(), c.getArt(), c.getBetrag(), c.getBasis(),
            c.getBasisMenge(), c.getProzentBasisSchluessel(), c.isEnthalten(), c.isVariabel(), c.getQuelle()); }
    private boolean rankable(EinkaufAngebot offer, AngebotVersion version, AngebotSumme sum) {
        return rankingZulaessig(version.getWaehrung(), sum,
                version.getAnfrageRevision().getId().equals(offer.getBeteiligung().getRevision().getId()));
    }

    public static boolean rankingZulaessig(String currency, AngebotSumme sum, boolean currentRevision) {
        return "EUR".equals(currency) && sum != null && currentRevision && sum.vollstaendig()
                && sum.technischGeeignet() && sum.gueltig() && sum.nettoGesamt() != null;
    }
    private static BigDecimal sumFor(List<AngebotSumme> sums, Long versionId) {
        return sums.stream().filter(s -> versionId.equals(s.angebotVersionId())).map(AngebotSumme::nettoGesamt).findFirst().orElse(BigDecimal.ZERO);
    }
    public record KostenBerechnung(BigDecimal nettoGesamt, boolean vollstaendig, List<String> hindernisse, List<Rechenschritt> rechnung) {
        public KostenBerechnung { hindernisse = List.copyOf(hindernisse); rechnung = List.copyOf(rechnung); }
    }
}
