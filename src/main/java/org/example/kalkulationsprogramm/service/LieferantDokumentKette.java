package org.example.kalkulationsprogramm.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.dto.Bestellung.Verbindung;

/**
 * Die Kette eines Lieferanten-Dokuments: alle Belege, die über Verknüpfungen
 * zusammenhängen (Angebot, AB, Lieferscheine, Werkstoffzeugnisse, Rechnungen …).
 *
 * <p>Reine Hilfsfunktionen ohne Zustand und ohne Datenbankzugriff. Die
 * Verknüpfungen laden lazy nach – Aufrufer brauchen einen offenen
 * EntityManager (Web-Request oder Transaktion).</p>
 */
public final class LieferantDokumentKette {

    private LieferantDokumentKette() {
    }

    /**
     * Alle Dokumente, die mit {@code start} über Verknüpfungen zusammenhängen –
     * in beide Richtungen und auch über Umwege (Zeugnis → Lieferschein → AB →
     * Rechnung), {@code start} eingeschlossen.
     */
    public static List<LieferantDokument> sammle(LieferantDokument start) {
        // Iterativ statt rekursiv: lange Ketten (viele Teillieferungen) sprengen sonst den Stack.
        // Verknüpfungen in beide Richtungen durchlaufen: Gespeichert wird nur
        // Nachfolger -> Vorgänger (Rechnung -> AB). Ohne die Rückrichtung bildete eine
        // AB, die vor ihrer Rechnung an der Reihe war, eine eigene Kette ohne Rechnung.
        Map<Long, LieferantDokument> gefunden = new LinkedHashMap<>();
        ArrayDeque<LieferantDokument> offen = new ArrayDeque<>();
        if (start != null) {
            offen.push(start);
        }
        while (!offen.isEmpty()) {
            LieferantDokument dok = offen.pop();
            if (gefunden.putIfAbsent(dok.getId(), dok) != null) {
                continue;
            }
            if (dok.getVerknuepfteDokumente() != null) {
                dok.getVerknuepfteDokumente().stream().filter(Objects::nonNull).forEach(offen::push);
            }
            if (dok.getVerknuepftVon() != null) {
                dok.getVerknuepftVon().stream().filter(Objects::nonNull).forEach(offen::push);
            }
        }
        return new ArrayList<>(gefunden.values());
    }

    /**
     * Die Kette ohne Dokumente, deren Typ der Aufrufer nicht sehen darf, sortiert
     * nach Ablauf (Angebot, AB, Lieferschein, Zeugnis, Sonstiges, Rechnung,
     * Gutschrift), dann nach Dokumentdatum (ohne Datum zuletzt), dann nach ID.
     */
    public static List<LieferantDokument> sichtbar(LieferantDokument start, Set<LieferantDokumentTyp> sichtbareTypen) {
        return sammle(start).stream()
                .filter(d -> d.getTyp() != null && sichtbareTypen.contains(d.getTyp()))
                .sorted(Comparator.comparingInt((LieferantDokument d) -> stufe(d.getTyp()))
                        .thenComparing(d -> d.getGeschaeftsdaten() != null
                                ? d.getGeschaeftsdaten().getDokumentDatum()
                                : null, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LieferantDokument::getId))
                .toList();
    }

    /**
     * Alle Verknüpfungen zwischen den Dokumenten einer Kette, jede Kante einmal:
     * von = Nachfolger (z. B. Rechnung, Zeugnis), zu = Vorgänger (z. B. Lieferschein).
     * Verknüpfungen auf Dokumente außerhalb von {@code kette} fallen weg.
     */
    public static List<Verbindung> verbindungen(Collection<LieferantDokument> kette) {
        Set<Long> ids = kette.stream().map(LieferantDokument::getId).collect(Collectors.toSet());
        Set<String> gesehen = new HashSet<>();
        List<Verbindung> verbindungen = new ArrayList<>();
        for (LieferantDokument d : kette) {
            if (d.getVerknuepfteDokumente() == null) {
                continue;
            }
            for (LieferantDokument vorgaenger : d.getVerknuepfteDokumente()) {
                Long zu = vorgaenger == null ? null : vorgaenger.getId();
                if (zu == null || zu.equals(d.getId()) || !ids.contains(zu)) {
                    continue;
                }
                String kante = Math.min(d.getId(), zu) + ":" + Math.max(d.getId(), zu);
                if (gesehen.add(kante)) {
                    verbindungen.add(new Verbindung(d.getId(), zu));
                }
            }
        }
        verbindungen.sort(Comparator.comparing(Verbindung::vonId).thenComparing(Verbindung::zuId));
        return verbindungen;
    }

    /** Ablauf-Stufe wie in der Linie im Frontend ({@code kettenLinieLogik.ts}). */
    private static int stufe(LieferantDokumentTyp typ) {
        return switch (typ) {
            case ANGEBOT -> 0;
            case AUFTRAGSBESTAETIGUNG -> 1;
            case LIEFERSCHEIN -> 2;
            case WERKSTOFFZEUGNIS -> 3;
            case SONSTIG -> 4;
            case RECHNUNG -> 5;
            case GUTSCHRIFT -> 6;
            case BELEG -> 7;
        };
    }
}
