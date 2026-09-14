package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Vorschlaege fuer die Pruefung, ohne die bestaetigte Zuordnung zu veraendern. */
@Service
@RequiredArgsConstructor
public class BelegVorschlagService {
    private final BelegRepository belegRepository;
    private final SachkontoRepository sachkontoRepository;
    private final KostenstelleRepository kostenstelleRepository;

    /** Reihenfolge: aktive KI-Zuordnung, Lieferanten-Standard, gepruefte Historie. */
    @Transactional(readOnly = true)
    public Vorschlaege ermittle(Beleg beleg) {
        String kiBegruendung = beleg.getKiKostenkontoBegruendung();
        kiBegruendung = kiBegruendung == null || kiBegruendung.isBlank()
                ? "Die KI schlägt das vor."
                : "Die KI schlägt das vor – " + kiBegruendung;
        Sachkonto kiKonto = beleg.getKiVorgeschlagenerSachkontoId() == null ? null
                : sachkontoRepository.findById(beleg.getKiVorgeschlagenerSachkontoId()).orElse(null);
        Kostenstelle kiKostenstelle = beleg.getKiVorgeschlagenerKostenstelleId() == null ? null
                : kostenstelleRepository.findById(beleg.getKiVorgeschlagenerKostenstelleId()).orElse(null);
        Vorschlag sachkonto = kontoVorschlag(kiKonto, Quelle.KI, kiBegruendung);
        Vorschlag kostenstelle = kostenstellenVorschlag(kiKostenstelle, Quelle.KI, kiBegruendung);

        var lieferant = beleg.getLieferant();
        if (lieferant == null) {
            return new Vorschlaege(sachkonto, kostenstelle);
        }
        if (kostenstelle == null) {
            kostenstelle = kostenstellenVorschlag(lieferant.getStandardKostenstelle(),
                    Quelle.LIEFERANT_STANDARD,
                    "Beim Lieferanten " + lieferant.getLieferantenname() + " ist das als Standard hinterlegt.");
        }
        if ((sachkonto == null || kostenstelle == null) && lieferant.getId() != null) {
            // Ein Lookup fuer beide Vorschlaege, hoechstens 20 gepruefte Belege.
            // Ein noch nicht gespeicherter Beleg hat keine ID zum Ausschliessen.
            List<Beleg> historie = belegRepository.findLetzteGepruefteByLieferant(lieferant.getId(),
                    beleg.getId() != null ? beleg.getId() : 0L, PageRequest.of(0, 20));
            if (sachkonto == null) {
                sachkonto = ausHistorie(historie,
                        b -> kontoVorschlag(b.getSachkonto(), Quelle.HISTORIE, null),
                        lieferant.getLieferantenname(), "dieses Konto");
            }
            if (kostenstelle == null) {
                kostenstelle = ausHistorie(historie,
                        b -> kostenstellenVorschlag(b.getKostenstelle(), Quelle.HISTORIE, null),
                        lieferant.getLieferantenname(), "diese Baustelle / diesen Bereich");
            }
        }
        return new Vorschlaege(sachkonto, kostenstelle);
    }

    private Vorschlag kontoVorschlag(Sachkonto konto, Quelle quelle, String begruendung) {
        return konto == null || !konto.isAktiv() ? null
                : new Vorschlag(konto.getId(), konto.getNummer(), konto.getBezeichnung(), quelle, begruendung);
    }

    private Vorschlag kostenstellenVorschlag(Kostenstelle kostenstelle, Quelle quelle, String begruendung) {
        return kostenstelle == null || !kostenstelle.isAktiv() ? null
                : new Vorschlag(kostenstelle.getId(), null, kostenstelle.getBezeichnung(), quelle, begruendung);
    }

    private Vorschlag ausHistorie(List<Beleg> historie, Function<Beleg, Vorschlag> zuordnung,
                                  String lieferantName, String verwendung) {
        List<Vorschlag> vorschlaege = historie.stream().map(zuordnung)
                .filter(Objects::nonNull).filter(v -> v.id() != null).toList();
        Map<Long, Long> haeufigkeiten = vorschlaege.stream().collect(
                Collectors.groupingBy(Vorschlag::id, LinkedHashMap::new, Collectors.counting()));
        Long haeufigsteId = null;
        long anzahl = 0;
        // LinkedHashMap erhaelt die Reihenfolge der absteigend sortierten
        // Historie: bei Gleichstand bleibt die juengste Zuordnung vorne.
        for (var eintrag : haeufigkeiten.entrySet()) {
            if (eintrag.getValue() > anzahl) {
                haeufigsteId = eintrag.getKey();
                anzahl = eintrag.getValue();
            }
        }
        for (Vorschlag vorschlag : vorschlaege) {
            if (Objects.equals(vorschlag.id(), haeufigsteId)) {
                return new Vorschlag(vorschlag.id(), vorschlag.nummer(), vorschlag.bezeichnung(), Quelle.HISTORIE,
                        "Bei " + lieferantName + " hast du zuletzt " + anzahl + "-mal " + verwendung + " genommen.");
            }
        }
        return null;
    }

    public record Vorschlaege(Vorschlag sachkonto, Vorschlag kostenstelle) {}
    public record Vorschlag(Long id, String nummer, String bezeichnung,
                            Quelle quelle, String begruendung) {}
    public enum Quelle { KI, HISTORIE, LIEFERANT_STANDARD }
}
