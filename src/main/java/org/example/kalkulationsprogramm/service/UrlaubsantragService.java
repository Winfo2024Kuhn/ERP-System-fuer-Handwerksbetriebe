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

@Service
@RequiredArgsConstructor
public class UrlaubsantragService {

    private final UrlaubsantragRepository repository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final FeiertagService feiertagService;
    private final ZeitkontoService zeitkontoService;
    private final MonatsSaldoService monatsSaldoService;

    /**
     * Erstellt einen neuen Urlaubsantrag.
     */
    @Transactional
    public Urlaubsantrag createAntrag(Long mitarbeiterId, LocalDate von, LocalDate bis, String bemerkung,
            Urlaubsantrag.Typ typ) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden"));

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
        antrag.setStatus(Urlaubsantrag.Status.OFFEN);

        return repository.save(antrag);
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
     */
    @Transactional
    public Urlaubsantrag approveAntrag(Long antragId) {
        Urlaubsantrag antrag = repository.findById(antragId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden"));

        if (antrag.getStatus() != Urlaubsantrag.Status.OFFEN) {
            throw new IllegalStateException("Nur offene Anträge können genehmigt werden");
        }

        antrag.setStatus(Urlaubsantrag.Status.GENEHMIGT);

        // AbwesenheitsTyp ermitteln
        AbwesenheitsTyp abwesenheitsTyp = toAbwesenheitsTyp(antrag.getTyp());

        // Zeitraum iterieren und Abwesenheiten erstellen
        for (LocalDate date = antrag.getVonDatum(); !date.isAfter(antrag.getBisDatum()); date = date.plusDays(1)) {
            // Wochenenden überspringen
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            // Feiertage überspringen
            if (feiertagService.istFeiertag(date)) {
                continue;
            }

            // Soll-Stunden ermitteln
            BigDecimal sollStunden = zeitkontoService.getOrCreateZeitkonto(antrag.getMitarbeiter().getId())
                    .getSollstundenFuerTag(date.getDayOfWeek().getValue());

            if (sollStunden.compareTo(BigDecimal.ZERO) > 0) {
                // Prüfen ob bereits Abwesenheit für diesen Tag existiert
                if (!abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(
                        antrag.getMitarbeiter().getId(), date, abwesenheitsTyp)) {

                    Abwesenheit abwesenheit = new Abwesenheit();
                    abwesenheit.setMitarbeiter(antrag.getMitarbeiter());
                    abwesenheit.setUrlaubsantrag(antrag);
                    abwesenheit.setTyp(abwesenheitsTyp);
                    abwesenheit.setDatum(date);
                    abwesenheit.setStunden(sollStunden);
                    abwesenheit.setNotiz(antrag.getTyp().name() + " (Antrag #" + antrag.getId() + ")");

                    abwesenheitRepository.save(abwesenheit);
                }
            }
        }

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
}
