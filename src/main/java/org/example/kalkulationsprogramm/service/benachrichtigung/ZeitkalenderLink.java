package org.example.kalkulationsprogramm.service.benachrichtigung;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Link aus der Glocke in den Zeitkalender: direkt zum Tag der Meldung, die betroffenen
 * Buchungen markiert.
 *
 * <p>{@code /zeitbuchungen?mitarbeiterId=7&jahr=2026&monat=11&tag=2026-11-18&anlass=ueberschneidung&markiert=12,31}
 * – {@code monat} ohne führende Null; {@code markiert} höchstens {@value #MAX_MARKIERT} positive
 * IDs, aufsteigend nach Startzeit, ohne Doppelte, fehlt ganz, wenn keine übrig bleibt.
 * Reine Funktion ohne Spring-Kontext.
 */
public final class ZeitkalenderLink {

    public static final String ANLASS_UEBERSCHNEIDUNG = "ueberschneidung";
    public static final String ANLASS_OHNE_FEIERABEND = "ohne-feierabend";

    /** So viele Buchungen markiert der Link höchstens (die Zeile im Kalender bleibt lesbar). */
    public static final int MAX_MARKIERT = 10;

    private static final Comparator<Zeitbuchung> NACH_START = Comparator
            .comparing(Zeitbuchung::getStartZeit, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(Zeitbuchung::getId);

    private ZeitkalenderLink() {
    }

    /**
     * @param tag      Tag der Buchung; {@code null} → nur der Mitarbeiter (wie früher, aktueller Monat)
     * @param markiert die betroffenen, noch vorhandenen Buchungen (Reihenfolge egal)
     */
    public static String zumTag(Long mitarbeiterId, LocalDate tag, String anlass,
            Collection<Zeitbuchung> markiert) {
        UriComponentsBuilder link = UriComponentsBuilder.fromPath("/zeitbuchungen");
        if (mitarbeiterId != null) {
            link.queryParam("mitarbeiterId", mitarbeiterId);
        }
        if (tag != null) {
            link.queryParam("jahr", tag.getYear())
                    .queryParam("monat", tag.getMonthValue())
                    .queryParam("tag", tag.toString())
                    .queryParam("anlass", anlass);
            String ids = markierteIds(markiert);
            if (!ids.isEmpty()) {
                link.queryParam("markiert", ids);
            }
        }
        return link.encode().build().toUriString();
    }

    private static String markierteIds(Collection<Zeitbuchung> markiert) {
        if (markiert == null) {
            return "";
        }
        List<Zeitbuchung> gueltig = markiert.stream()
                .filter(Objects::nonNull)
                .filter(z -> z.getId() != null && z.getId() > 0)
                .sorted(NACH_START)
                .toList();
        return gueltig.stream()
                .map(Zeitbuchung::getId)
                .distinct()
                .limit(MAX_MARKIERT)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
