package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UrlaubsantragService {

    private final UrlaubsantragRepository repository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final FeiertagService feiertagService;
    private final ZeitkontoService zeitkontoService;
    private final MonatsSaldoService monatsSaldoService;
    private final ZeitkontoKorrekturService zeitkontoKorrekturService;
    private final TagesSollService tagesSollService;
    private final LangzeitkrankmeldungService langzeitkrankmeldungService;

    /**
     * Erstellt einen neuen Urlaubsantrag.
     */
    @Transactional
    public Urlaubsantrag createAntrag(Long mitarbeiterId, LocalDate von, LocalDate bis, String bemerkung,
            Urlaubsantrag.Typ typ) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden"));

        boolean istGf = Boolean.TRUE.equals(mitarbeiter.getIstGeschaeftsfuehrer());

        // Urlaubskontingent prüfen (nur für URLAUB-Typ bei normalen Mitarbeitern)
        if (!istGf && typ == Urlaubsantrag.Typ.URLAUB) {
            int jahr = von.getYear();
            int verbleibend = getResturlaub(mitarbeiterId, jahr);
            long beantragteTage = zaehleArbeitstage(von, bis);
            if (beantragteTage > verbleibend) {
                throw new IllegalStateException(
                        String.format("Nicht genügend Urlaubstage. Verbleibend: %d, Beantragt: %d",
                                verbleibend, beantragteTage));
            }
        }

        // Überlappungsprüfung: Keine überschneidenden Anträge erlauben
        List<Urlaubsantrag> ueberlappend = repository.findOverlapping(mitarbeiterId, von, bis);
        if (!ueberlappend.isEmpty()) {
            Urlaubsantrag erster = ueberlappend.getFirst();
            throw new IllegalStateException(
                    String.format("Es existiert bereits ein %s-Antrag vom %s bis %s in diesem Zeitraum",
                            erster.getTyp().name(), erster.getVonDatum(), erster.getBisDatum()));
        }

        Urlaubsantrag antrag = new Urlaubsantrag();
        antrag.setMitarbeiter(mitarbeiter);
        antrag.setVonDatum(von);
        antrag.setBisDatum(bis);
        antrag.setBemerkung(bemerkung);
        antrag.setTyp(typ != null ? typ : Urlaubsantrag.Typ.URLAUB);
        antrag.setStatus(istGf ? Urlaubsantrag.Status.GENEHMIGT : Urlaubsantrag.Status.OFFEN);

        Urlaubsantrag savedAntrag = repository.save(antrag);

        // Bei Geschäftsführern wird der Urlaub direkt genehmigt und Abwesenheiten mit 0 Stunden erfasst
        if (istGf) {
            AbwesenheitsTyp abwesenheitsTyp = toAbwesenheitsTyp(savedAntrag.getTyp());
            List<LocalDate> buchungstage = von.datesUntil(bis.plusDays(1))
                    .filter(tag -> tag.getDayOfWeek() != DayOfWeek.SATURDAY && tag.getDayOfWeek() != DayOfWeek.SUNDAY)
                    .filter(tag -> !feiertagService.istFeiertag(tag))
                    .toList();

            for (LocalDate date : buchungstage) {
                if (!abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(
                        mitarbeiterId, date, abwesenheitsTyp)) {
                    Abwesenheit abwesenheit = new Abwesenheit();
                    abwesenheit.setMitarbeiter(mitarbeiter);
                    abwesenheit.setUrlaubsantrag(savedAntrag);
                    abwesenheit.setTyp(abwesenheitsTyp);
                    abwesenheit.setDatum(date);
                    abwesenheit.setStunden(BigDecimal.ZERO);
                    abwesenheit.setNotiz(savedAntrag.getTyp().name() + " (GF-Eintrag)");
                    abwesenheitRepository.save(abwesenheit);
                }
            }
            invalidiereBetroffeneMonate(mitarbeiterId, von, bis);
        }

        return savedAntrag;
    }

    /**
     * Konvertiert Urlaubsantrag.Typ zu AbwesenheitsTyp.
     */
    private AbwesenheitsTyp toAbwesenheitsTyp(Urlaubsantrag.Typ typ) {
        return switch (typ) {
            case URLAUB -> AbwesenheitsTyp.URLAUB;
            case KRANKHEIT -> AbwesenheitsTyp.KRANKHEIT;
            case FORTBILDUNG -> AbwesenheitsTyp.FORTBILDUNG;
            case ZEITAUSGLEICH -> AbwesenheitsTyp.ZEITAUSGLEICH;
            default -> AbwesenheitsTyp.URLAUB; // Fallback für ARBEIT, PAUSE (sollte nicht vorkommen)
        };
    }

    /**
     * Genehmigt einen Urlaubsantrag und erstellt entsprechende
     * Abwesenheits-Einträge.
     *
     * Die Urlaubsstunden je Tag sind die Gegenbuchung zum Tagessoll (siehe
     * {@link TagesSollService}) und folgen deshalb während einer laufenden
     * Wiedereingliederung dem Stufenplan statt der vollen Sollstunden — sonst
     * entstünden Phantom-Überstunden. Das Urlaubs<b>kontingent</b> zählt
     * davon unberührt weiterhin volle Tage, nicht Stunden ({@link #getResturlaub}).
     */
    @Transactional
    public Urlaubsantrag approveAntrag(Long antragId) {
        Urlaubsantrag antrag = repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden"));

        if (antrag.getStatus() != Urlaubsantrag.Status.OFFEN) {
            throw new IllegalStateException("Nur offene Anträge können genehmigt werden");
        }

        // AbwesenheitsTyp ermitteln
        AbwesenheitsTyp abwesenheitsTyp = toAbwesenheitsTyp(antrag.getTyp());

        boolean istGf = Boolean.TRUE.equals(antrag.getMitarbeiter().getIstGeschaeftsfuehrer());

        List<LocalDate> buchungstage = antrag.getVonDatum().datesUntil(antrag.getBisDatum().plusDays(1))
                .filter(tag -> tag.getDayOfWeek() != DayOfWeek.SATURDAY && tag.getDayOfWeek() != DayOfWeek.SUNDAY)
                .filter(tag -> !feiertagService.istFeiertag(tag))
                .toList();

        if (!istGf) {
            // Vertragsdaten einmal laden und alle Buchungstage vor der ersten Änderung prüfen.
            // Der heutige Kontoschalter darf historische Buchungen nicht verhindern.
            List<ZeitkontoVersion> versionen = zeitkontoService.versionenImZeitraum(
                    antrag.getMitarbeiter().getId(), antrag.getVonDatum(), antrag.getBisDatum());
            for (LocalDate tag : buchungstage) {
                boolean konfiguriert = versionen.stream().anyMatch(v -> !v.getGueltigVon().isAfter(tag)
                        && (v.getGueltigBis() == null || !v.getGueltigBis().isBefore(tag)));
                if (!konfiguriert) {
                    throw new IllegalStateException("Für den " + tag
                            + " ist noch keine Arbeitszeit hinterlegt. Bitte zuerst Arbeitszeit zuweisen.");
                }
            }
        }

        // Soll-Stunden für den GESAMTEN Zeitraum auf einmal laden
        Map<LocalDate, BigDecimal> sollStundenJeTag = istGf
                ? Map.of()
                : tagesSollService.arbeitsSollJeTag(
                        antrag.getMitarbeiter().getId(), antrag.getVonDatum(), antrag.getBisDatum());

        // Zeitraum iterieren und Abwesenheiten erstellen
        for (LocalDate date : buchungstage) {
            BigDecimal sollStunden = istGf ? BigDecimal.ZERO : sollStundenJeTag.getOrDefault(date, BigDecimal.ZERO);

            if (istGf || sollStunden.compareTo(BigDecimal.ZERO) > 0) {
                // Prüfen ob bereits Abwesenheit für diesen Tag existiert
                if (!abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(
                        antrag.getMitarbeiter().getId(), date, abwesenheitsTyp)) {

                    Abwesenheit abwesenheit = new Abwesenheit();
                    abwesenheit.setMitarbeiter(antrag.getMitarbeiter());
                    abwesenheit.setUrlaubsantrag(antrag);
                    abwesenheit.setTyp(abwesenheitsTyp);
                    abwesenheit.setDatum(date);
                    abwesenheit.setStunden(sollStunden);
                    abwesenheit.setNotiz(antrag.getTyp().name() + (istGf ? " (GF-Eintrag)" : (" (Antrag #" + antrag.getId() + ")")));

                    abwesenheitRepository.save(abwesenheit);
                }
            }
        }

        antrag.setStatus(Urlaubsantrag.Status.GENEHMIGT);

        // MonatsSaldo-Cache invalidieren für alle betroffenen Monate
        invalidiereBetroffeneMonate(antrag.getMitarbeiter().getId(),
                antrag.getVonDatum(), antrag.getBisDatum());

        return repository.save(antrag);
    }

    /**
     * Lehnt einen Urlaubsantrag ab.
     */
    @Transactional
    public Urlaubsantrag rejectAntrag(Long antragId) {
        Urlaubsantrag antrag = repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden"));

        antrag.setStatus(Urlaubsantrag.Status.ABGELEHNT);
        return repository.save(antrag);
    }

    /**
     * Storniert einen Urlaubsantrag und löscht zugehörige Abwesenheiten.
     */
    @Transactional
    public Urlaubsantrag stornoAntrag(Long antragId) {
        Urlaubsantrag antrag = repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden"));

        // Lösche alle zugehörigen Abwesenheiten
        abwesenheitRepository.deleteByUrlaubsantragId(antragId);

        // MonatsSaldo-Cache invalidieren für alle betroffenen Monate
        invalidiereBetroffeneMonate(antrag.getMitarbeiter().getId(),
                antrag.getVonDatum(), antrag.getBisDatum());

        antrag.setStatus(Urlaubsantrag.Status.STORNIERT);
        return repository.save(antrag);
    }

    /**
     * Invalidiert MonatsSaldo-Cache für alle Monate in einem Datumsbereich.
     */
    private void invalidiereBetroffeneMonate(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        java.time.YearMonth start = java.time.YearMonth.from(von);
        java.time.YearMonth end = java.time.YearMonth.from(bis);
        for (java.time.YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            monatsSaldoService.invalidiereMonat(mitarbeiterId, ym.getYear(), ym.getMonthValue());
        }
    }

    public List<Urlaubsantrag> getOffeneAntraege() {
        return repository.findByStatus(Urlaubsantrag.Status.OFFEN);
    }

    /**
     * Gibt alle Anträge mit einem bestimmten Status zurück.
     */
    public List<Urlaubsantrag> getAntraegeByStatus(Urlaubsantrag.Status status) {
        return repository.findByStatus(status);
    }

    public List<Urlaubsantrag> getAntraegeByMitarbeiter(Long mitarbeiterId) {
        return repository.findByMitarbeiterIdOrderByVonDatumDesc(mitarbeiterId);
    }

    /**
     * Gibt Anträge eines Mitarbeiters für ein bestimmtes Jahr zurück.
     */
    public List<Urlaubsantrag> getAntraegeByMitarbeiterAndYear(Long mitarbeiterId, int jahr) {
        LocalDate start = LocalDate.of(jahr, 1, 1);
        LocalDate end = LocalDate.of(jahr, 12, 31);
        return repository.findByMitarbeiterIdAndVonDatumBetweenOrderByVonDatumDesc(mitarbeiterId, start, end);
    }

    /**
     * Gibt Anträge eines Mitarbeiters mit einem bestimmten Status zurück.
     */
    public List<Urlaubsantrag> getAntraegeByMitarbeiterAndStatus(Long mitarbeiterId, Urlaubsantrag.Status status) {
        return repository.findByMitarbeiterIdAndStatusOrderByVonDatumDesc(mitarbeiterId, status);
    }

    /**
     * Berechnet die verbleibenden Urlaubstage eines Mitarbeiters für ein Jahr.
     * Formel: Jahresanspruch - genommen - geplant + Korrekturen
     */
    @Transactional(readOnly = true)
    public int getResturlaub(Long mitarbeiterId, int jahr) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden"));

        int jahresUrlaub = mitarbeiter.getJahresUrlaub() != null ? mitarbeiter.getJahresUrlaub() : 30;

        LocalDate jahresanfang = LocalDate.of(jahr, 1, 1);
        LocalDate jahresende = LocalDate.of(jahr, 12, 31);

        List<Abwesenheit> abwesenheiten = abwesenheitRepository
                .findByMitarbeiterIdAndDatumBetween(mitarbeiterId, jahresanfang, jahresende);

        long genommen = abwesenheiten.stream()
                .filter(a -> a.getTyp() == AbwesenheitsTyp.URLAUB)
                .count();

        BigDecimal korrekturBD = zeitkontoKorrekturService.summiereAktiveUrlaubsKorrekturen(mitarbeiterId, jahr);
        int korrektur = korrekturBD != null ? korrekturBD.intValue() : 0;

        return Math.max(0, jahresUrlaub - (int) genommen + korrektur);
    }

    /**
     * Prüft, ob der angefragte Urlaubszeitraum in eine laufende Krankmeldung
     * fällt, und liefert dazu Hinweistexte fürs Büro. Das ist eine Warnung,
     * keine Sperre — der Antrag wird dadurch nicht blockiert, das Büro
     * entscheidet.
     */
    @Transactional(readOnly = true)
    public List<String> pruefeHinweise(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return langzeitkrankmeldungService.pruefeUrlaubsHinweise(mitarbeiterId, von, bis);
    }

    /**
     * Zählt Arbeitstage (Mo–Fr, ohne Feiertage) in einem Zeitraum.
     */
    private long zaehleArbeitstage(LocalDate von, LocalDate bis) {
        long count = 0;
        for (LocalDate d = von; !d.isAfter(bis); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (feiertagService.istFeiertag(d)) continue;
            count++;
        }
        return count;
    }
}
