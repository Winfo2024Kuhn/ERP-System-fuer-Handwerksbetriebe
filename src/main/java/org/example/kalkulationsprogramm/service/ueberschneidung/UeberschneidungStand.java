package org.example.kalkulationsprogramm.service.ueberschneidung;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidungHandyZeit;

/**
 * Was zum Anzeigen einer Liste von Überschneidungs-Meldungen gebraucht wird, gesammelt geladen
 * (keine Abfrage je Meldung): die festgehaltenen Handy-Zeiten je Meldung und die Buchungen,
 * die es noch gibt (Büro-Eintrag, abgeteilter Rest, Handy-Buchungen).
 *
 * @param handyZeiten Meldungs-ID → festgehaltene Handy-Zeiten
 * @param buchungen   Buchungs-ID → Buchung, nur noch vorhandene
 */
public record UeberschneidungStand(Map<Long, List<ZeitbuchungUeberschneidungHandyZeit>> handyZeiten,
        Map<Long, Zeitbuchung> buchungen) {

    public static final UeberschneidungStand LEER = new UeberschneidungStand(Map.of(), Map.of());

    public UeberschneidungStand {
        handyZeiten = handyZeiten != null ? handyZeiten : Map.of();
        buchungen = buchungen != null ? buchungen : Map.of();
    }

    /** Die festgehaltenen Handy-Zeiten einer Meldung (leer bei Altmeldungen). */
    public List<ZeitbuchungUeberschneidungHandyZeit> handyZeitenVon(ZeitbuchungUeberschneidung meldung) {
        return handyZeiten.getOrDefault(meldung.getId(), List.of());
    }

    public Optional<Zeitbuchung> buchung(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(buchungen.get(id));
    }

    /** Büro-Eintrag und abgeteilter Rest der Meldung, soweit es sie noch gibt. */
    public List<Zeitbuchung> bueroStand(ZeitbuchungUeberschneidung meldung) {
        return Stream.of(meldung.getBueroBuchungId(), meldung.getRestBuchungId())
                .filter(Objects::nonNull)
                .map(this::buchung)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Alle noch vorhandenen Buchungen der Meldung: Büro-Eintrag, Rest und die Handy-Buchungen
     * (bei Altmeldungen ohne festgehaltene Zeiten die eine gemerkte Handy-Buchung).
     */
    public List<Zeitbuchung> betroffeneBuchungen(ZeitbuchungUeberschneidung meldung) {
        return Stream.concat(bueroStand(meldung).stream(), handyBuchungIds(meldung, handyZeitenVon(meldung))
                .map(this::buchung).flatMap(Optional::stream)).distinct().toList();
    }

    /** IDs aller Buchungen einer Meldung, die zum Anzeigen nachgeladen werden müssen. */
    public static Stream<Long> benoetigteBuchungIds(ZeitbuchungUeberschneidung meldung,
            List<ZeitbuchungUeberschneidungHandyZeit> handyZeiten) {
        return Stream.concat(Stream.of(meldung.getBueroBuchungId(), meldung.getRestBuchungId()),
                handyBuchungIds(meldung, handyZeiten)).filter(Objects::nonNull);
    }

    private static Stream<Long> handyBuchungIds(ZeitbuchungUeberschneidung meldung,
            List<ZeitbuchungUeberschneidungHandyZeit> zeiten) {
        return zeiten.isEmpty()
                ? Stream.ofNullable(meldung.getHandyBuchungId())
                : zeiten.stream().map(ZeitbuchungUeberschneidungHandyZeit::getHandyBuchungId);
    }
}
