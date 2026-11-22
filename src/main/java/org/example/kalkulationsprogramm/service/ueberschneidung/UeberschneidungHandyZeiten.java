package org.example.kalkulationsprogramm.service.ueberschneidung;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidungHandyZeit;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungUeberschneidungHandyZeitRepository;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Die Handy-Zeiten einer Überschneidungs-Meldung ({@link ZeitbuchungUeberschneidungHandyZeit}):
 * festhalten, wenn eine Handy-Buchung Vorrang hatte, und für die Anzeige gesammelt laden.
 *
 * <p>Festgehalten wird der Stand beim Auflösen, nicht live: Ändert das Büro die Handy-Buchung
 * später, zeigt die Meldung weiter, was das Handy damals geschickt hat. Transaktionen kommen vom
 * Aufrufer ({@code ZeitbuchungUeberschneidungService}).
 */
@Component
@RequiredArgsConstructor
public class UeberschneidungHandyZeiten {

    private final ZeitbuchungUeberschneidungHandyZeitRepository handyZeitRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;

    /**
     * Hält die Handy-Buchung in der Meldung fest. Dieselbe Handy-Buchung erneut → Zeile
     * aktualisieren, nicht doppeln. Ein bekanntes Ende bleibt stehen, wenn die Buchung (noch) ohne
     * Ende kommt.
     */
    public void festhalten(ZeitbuchungUeberschneidung meldung, Zeitbuchung handyBuchung) {
        if (meldung == null || meldung.getId() == null || handyBuchung == null || handyBuchung.getId() == null
                || handyBuchung.getStartZeit() == null) {
            return;
        }
        ZeitbuchungUeberschneidungHandyZeit zeit = handyZeitRepository
                .findByUeberschneidungIdAndHandyBuchungId(meldung.getId(), handyBuchung.getId())
                .orElseGet(() -> {
                    ZeitbuchungUeberschneidungHandyZeit neu = new ZeitbuchungUeberschneidungHandyZeit();
                    neu.setUeberschneidung(meldung);
                    neu.setHandyBuchungId(handyBuchung.getId());
                    return neu;
                });
        uebernehmen(zeit, handyBuchung);
    }

    /** Die Handy-Buchung ist abgeschlossen: in allen Meldungen, die sie schon festhalten, das Ende eintragen. */
    public void endeNachtragen(Zeitbuchung handyBuchung) {
        if (handyBuchung.getId() == null || handyBuchung.getStartZeit() == null) {
            return;
        }
        for (ZeitbuchungUeberschneidungHandyZeit zeit : handyZeitRepository.findByHandyBuchungId(
                handyBuchung.getId())) {
            uebernehmen(zeit, handyBuchung);
        }
    }

    /** Übernimmt Art und Zeitraum; gespeichert wird nur, wenn sich etwas ändert. */
    private void uebernehmen(ZeitbuchungUeberschneidungHandyZeit zeit, Zeitbuchung handyBuchung) {
        LocalDateTime bis = handyBuchung.getEndeZeit() != null ? handyBuchung.getEndeZeit() : zeit.getBis();
        if (zeit.getId() != null && zeit.getArt() == handyBuchung.getTyp()
                && Objects.equals(zeit.getVon(), handyBuchung.getStartZeit()) && Objects.equals(zeit.getBis(), bis)) {
            return;
        }
        zeit.setArt(handyBuchung.getTyp());
        zeit.setVon(handyBuchung.getStartZeit());
        zeit.setBis(bis);
        handyZeitRepository.save(zeit);
    }

    /**
     * Lädt, was zum Anzeigen der Meldungen gebraucht wird, in zwei Abfragen für die ganze Liste
     * (keine Abfrage je Meldung): die festgehaltenen Handy-Zeiten und die Buchungen, die es noch
     * gibt (Büro-Eintrag, abgeteilter Rest, Handy-Buchungen).
     */
    public UeberschneidungStand stand(Collection<ZeitbuchungUeberschneidung> meldungen) {
        List<Long> meldungIds = meldungen.stream().map(ZeitbuchungUeberschneidung::getId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, List<ZeitbuchungUeberschneidungHandyZeit>> handyZeiten = meldungIds.isEmpty() ? Map.of()
                : handyZeitRepository.findZuMeldungen(meldungIds).stream()
                        .collect(Collectors.groupingBy(h -> h.getUeberschneidung().getId()));
        List<Long> buchungIds = meldungen.stream()
                .flatMap(m -> UeberschneidungStand.benoetigteBuchungIds(m,
                        handyZeiten.getOrDefault(m.getId(), List.of())))
                .distinct().toList();
        Map<Long, Zeitbuchung> buchungen = buchungIds.isEmpty() ? Map.of()
                : zeitbuchungRepository.findAllById(buchungIds).stream()
                        .collect(Collectors.toMap(Zeitbuchung::getId, Function.identity(), (a, b) -> a));
        return new UeberschneidungStand(handyZeiten, buchungen);
    }
}
