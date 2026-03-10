package org.example.kalkulationsprogramm.service.miete;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.miete.Kostenposition;
import org.example.kalkulationsprogramm.domain.miete.KostenpositionBerechnung;
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle;
import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel;
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselEintrag;
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselTyp;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand;
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand;
import org.example.kalkulationsprogramm.exception.MietabrechnungValidationException;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository;
import org.example.kalkulationsprogramm.repository.miete.VerteilungsschluesselRepository;
import org.example.kalkulationsprogramm.repository.miete.ZaehlerstandRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KostenpositionBerechner {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final VerteilungsschluesselRepository verteilungsschluesselRepository;
    private final ZaehlerstandRepository zaehlerstandRepository;
    private final VerbrauchsgegenstandRepository verbrauchsgegenstandRepository;

    public KostenpositionVerteilErgebnis berechne(Kostenposition position, int jahr,
            Map<Long, Verteilungsschluessel> cache) {
        Map<Long, Verteilungsschluessel> schluesselCache = cache != null ? cache : new LinkedHashMap<>();
        Verteilungsschluessel schluessel = ermittleSchluessel(position, schluesselCache);
        if (schluessel == null) {
            Kostenstelle kostenstelle = position.getKostenstelle();
            String kostenpositionName = kostenpositionLabel(position);
            String kostenstellenName = kostenstelleLabel(kostenstelle);
            String userMessage = "Fuer die Kostenposition \"" + kostenpositionName + "\""
                    + " in der Kostenstelle \"" + kostenstellenName + "\" ist kein Verteilungsschluessel hinterlegt."
                    + " Bitte waehlen Sie in der Kostenposition einen Verteilungsschluessel oder hinterlegen Sie einen Standardschluessel in der Kostenstelle.";
            String detail = "Kostenposition ID " + position.getId() + " (" + kostenpositionName + "), Kostenstelle "
                    + kostenstelleDetailName(kostenstelle) + ", Jahr "
                    + (position.getAbrechnungsJahr() != null ? position.getAbrechnungsJahr() : "unbekannt")
                    + " verfuegt ueber keinen Override, und die Kostenstelle hat keinen Standardschluessel.";
            throw new MietabrechnungValidationException(userMessage, detail);
        }

        List<VerteilungsschluesselEintrag> eintraege = schluessel.getEintraege();
        if (eintraege == null || eintraege.isEmpty()) {
            String schluesselName = verteilungsschluesselLabel(schluessel);
            String userMessage = "Der Verteilungsschluessel \"" + schluesselName + "\" enthaelt keine Eintraege."
                    + " Oeffnen Sie den Verteilungsschluessel und fuegen Sie mindestens eine Mietpartei mit einem Anteil hinzu.";
            String detail = "Verteilungsschluessel " + verteilungsschluesselDetailName(schluessel)
                    + " hat keine Eintraege.";
            throw new MietabrechnungValidationException(userMessage, detail);
        }

        List<VerteilungsschluesselEintrag> aktiveEintraege = eintraege.stream()
                .filter(e -> e.getMietpartei() != null)
                .collect(Collectors.toList());
        if (aktiveEintraege.isEmpty()) {
            String schluesselName = verteilungsschluesselLabel(schluessel);
            String userMessage = "Dem Verteilungsschluessel \"" + schluesselName
                    + "\" sind keine Mietparteien zugeordnet."
                    + " Bitte weisen Sie Mietparteien zu oder waehlen Sie einen anderen Verteilungsschluessel.";
            String detail = "Verteilungsschluessel " + verteilungsschluesselDetailName(schluessel)
                    + " enthaelt ausschliesslich Eintraege ohne Mietpartei.";
            throw new MietabrechnungValidationException(userMessage, detail);
        }

        // Verteilungsgewichte (wer zahlt wie viel) — abhängig vom Schlüssel-Typ
        BigDecimal verteilVerbrauchsSumme = null;
        List<BigDecimal> gewichte = switch (schluessel.getTyp()) {
            case PROZENTUAL, FLAECHE -> aktiveEintraege.stream()
                    .map(e -> Optional.ofNullable(e.getAnteil()).orElse(BigDecimal.ZERO))
                    .collect(Collectors.toList());
            case VERBRAUCH -> {
                List<BigDecimal> werte = ermittleVerbrauchsgewichte(aktiveEintraege, jahr);
                verteilVerbrauchsSumme = werte.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                yield werte;
            }
        };

        BigDecimal sumGewichte = gewichte.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> verteilGewichte = gewichte;
        if (sumGewichte.compareTo(BigDecimal.ZERO) <= 0) {
            int count = aktiveEintraege.size();
            verteilGewichte = aktiveEintraege.stream().map(e -> BigDecimal.ONE).toList();
            sumGewichte = BigDecimal.valueOf(count);
        }

        // Betrags-Berechnung — unabhängig vom Verteilungsschlüssel-Typ
        BigDecimal verbrauchsSumme = verteilVerbrauchsSumme;
        KostenpositionBerechnung berechnung = Optional.ofNullable(position.getBerechnung())
                .orElse(KostenpositionBerechnung.BETRAG);
        if (berechnung == KostenpositionBerechnung.VERBRAUCHSFAKTOR
                && schluessel.getTyp() != VerteilungsschluesselTyp.VERBRAUCH) {
            // Bei VERBRAUCHSFAKTOR werden nur die Verbrauchsgegenstände herangezogen,
            // die im Verteilungsschlüssel referenziert sind.
            verbrauchsSumme = berechneVerbrauchAusSchluessel(schluessel, jahr);
        }

        BigDecimal betrag = berechneBetrag(position, verbrauchsSumme);
        Map<Mietpartei, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal rest = betrag;
        for (int i = 0; i < aktiveEintraege.size(); i++) {
            VerteilungsschluesselEintrag eintrag = aktiveEintraege.get(i);
            Mietpartei partei = eintrag.getMietpartei();
            BigDecimal gewicht = verteilGewichte.get(i);
            BigDecimal anteil = gewicht.divide(sumGewichte, MC);
            BigDecimal anteilsbetrag;
            if (i == aktiveEintraege.size() - 1) {
                anteilsbetrag = rest.setScale(2, RoundingMode.HALF_UP);
            } else {
                anteilsbetrag = betrag.multiply(anteil, MC).setScale(2, RoundingMode.HALF_UP);
                rest = rest.subtract(anteilsbetrag, MC);
            }
            result.merge(partei, anteilsbetrag, BigDecimal::add);
        }

        return new KostenpositionVerteilErgebnis(betrag, result, verbrauchsSumme);
    }

    private BigDecimal berechneBetrag(Kostenposition position, BigDecimal verbrauchsSumme) {
        KostenpositionBerechnung berechnung = Optional.ofNullable(position.getBerechnung())
                .orElse(KostenpositionBerechnung.BETRAG);
        if (berechnung == KostenpositionBerechnung.VERBRAUCHSFAKTOR) {
            BigDecimal faktor = position.getVerbrauchsfaktor();
            if (faktor == null) {
                String userMessage = "Bitte hinterlegen Sie einen Faktor fuer die Kostenposition \""
                        + kostenpositionLabel(position) + "\".";
                String detail = "Kostenposition ID " + position.getId()
                        + " ist als VERBRAUCHSFAKTOR markiert, besitzt jedoch keinen Faktor.";
                throw new MietabrechnungValidationException(userMessage, detail);
            }
            BigDecimal basis = Optional.ofNullable(verbrauchsSumme).orElse(BigDecimal.ZERO);
            return basis.multiply(faktor, MC).setScale(2, RoundingMode.HALF_UP);
        }
        return Optional.ofNullable(position.getBetrag()).orElse(BigDecimal.ZERO);
    }

    /**
     * Berechnet den Gesamtverbrauch nur der Verbrauchsgegenstände,
     * die im Verteilungsschlüssel referenziert sind, für ein Jahr.
     * Wird verwendet wenn eine VERBRAUCHSFAKTOR-Position mit einem
     * nicht-VERBRAUCH-Schlüssel verteilt wird.
     */
    private BigDecimal berechneVerbrauchAusSchluessel(Verteilungsschluessel schluessel, int jahr) {
        if (schluessel == null || schluessel.getEintraege() == null) {
            return BigDecimal.ZERO;
        }
        // Sammle alle eindeutigen Verbrauchsgegenstände aus den Einträgen des Schlüssels
        List<Verbrauchsgegenstand> gegenstaende = schluessel.getEintraege().stream()
                .map(VerteilungsschluesselEintrag::getVerbrauchsgegenstand)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        BigDecimal summe = BigDecimal.ZERO;
        for (Verbrauchsgegenstand gegenstand : gegenstaende) {
            Zaehlerstand current = zaehlerstandRepository
                    .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr)
                    .orElse(null);
            Zaehlerstand previous = zaehlerstandRepository
                    .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr - 1)
                    .orElse(null);
            BigDecimal verbrauch = berechneVerbrauch(current, previous);
            summe = summe.add(verbrauch.max(BigDecimal.ZERO));
        }
        return summe;
    }

    private Verteilungsschluessel ermittleSchluessel(Kostenposition position, Map<Long, Verteilungsschluessel> cache) {
        if (position.getVerteilungsschluesselOverride() != null) {
            Long id = position.getVerteilungsschluesselOverride().getId();
            if (id != null) {
                return cache.computeIfAbsent(id, this::ladeSchluessel);
            }
            return position.getVerteilungsschluesselOverride();
        }
        Kostenstelle kostenstelle = position.getKostenstelle();
        if (kostenstelle == null) {
            return null;
        }
        if (kostenstelle.getStandardSchluessel() != null && kostenstelle.getStandardSchluessel().getId() != null) {
            Long id = kostenstelle.getStandardSchluessel().getId();
            return cache.computeIfAbsent(id, this::ladeSchluessel);
        }
        return kostenstelle.getStandardSchluessel();
    }

    private Verteilungsschluessel ladeSchluessel(Long id) {
        return verteilungsschluesselRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Verteilungsschluessel " + id + " nicht gefunden"));
    }

    private List<BigDecimal> ermittleVerbrauchsgewichte(List<VerteilungsschluesselEintrag> eintraege, int jahr) {
        List<BigDecimal> werte = new ArrayList<>(eintraege.size());
        for (VerteilungsschluesselEintrag eintrag : eintraege) {
            Verbrauchsgegenstand gegenstand = eintrag.getVerbrauchsgegenstand();
            BigDecimal verbrauch = BigDecimal.ZERO;
            if (gegenstand != null) {
                Zaehlerstand current = zaehlerstandRepository
                        .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr)
                        .orElse(null);
                Zaehlerstand previous = zaehlerstandRepository
                        .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr - 1)
                        .orElse(null);
                verbrauch = berechneVerbrauch(current, previous);
            }
            werte.add(verbrauch.max(BigDecimal.ZERO));
        }
        return werte;
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

    private String kostenpositionLabel(Kostenposition position) {
        if (position == null) {
            return "unbekannte Kostenposition";
        }
        if (position.getBeschreibung() != null && !position.getBeschreibung().isBlank()) {
            return position.getBeschreibung();
        }
        if (position.getBelegNummer() != null && !position.getBelegNummer().isBlank()) {
            return "Beleg " + position.getBelegNummer();
        }
        return "Kostenposition ohne Beschreibung";
    }

    private String kostenstelleLabel(Kostenstelle kostenstelle) {
        if (kostenstelle == null) {
            return "unbekannte Kostenstelle";
        }
        if (kostenstelle.getName() != null && !kostenstelle.getName().isBlank()) {
            return kostenstelle.getName();
        }
        return "Kostenstelle ohne Namen";
    }

    private String kostenstelleDetailName(Kostenstelle kostenstelle) {
        if (kostenstelle == null) {
            return "unbekannt";
        }
        String name = kostenstelle.getName();
        Long id = kostenstelle.getId();
        if (name != null && !name.isBlank()) {
            return id != null ? name + " (ID " + id + ")" : name;
        }
        if (id != null) {
            return "ID " + id;
        }
        return "ohne Namen";
    }

    private String verteilungsschluesselLabel(Verteilungsschluessel schluessel) {
        if (schluessel == null) {
            return "unbekannter Verteilungsschluessel";
        }
        if (schluessel.getName() != null && !schluessel.getName().isBlank()) {
            return schluessel.getName();
        }
        return "Verteilungsschluessel ohne Namen";
    }

    private String verteilungsschluesselDetailName(Verteilungsschluessel schluessel) {
        if (schluessel == null) {
            return "unbekannt";
        }
        String name = schluessel.getName();
        Long id = schluessel.getId();
        if (name != null && !name.isBlank()) {
            return id != null ? name + " (ID " + id + ")" : name;
        }
        if (id != null) {
            return "ID " + id;
        }
        return "ohne Namen";
    }

    public static record KostenpositionVerteilErgebnis(BigDecimal betrag,
            Map<Mietpartei, BigDecimal> anteile,
            BigDecimal verbrauchsSumme) {
    }
}
