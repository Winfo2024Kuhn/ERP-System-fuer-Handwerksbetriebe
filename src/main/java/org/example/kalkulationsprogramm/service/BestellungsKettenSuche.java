package org.example.kalkulationsprogramm.service;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentRef;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentenKette;

/**
 * Suche und Alter einer Dokumenten-Kette der Bestellübersicht – serverseitig, damit die Suche
 * alle Bestellungen findet, auch die noch nicht geladenen. Gleiche Regeln wie
 * {@code passtZurSuche} und {@code istAlt} in {@code react-pc-frontend/src/features/bestellungen/bestellungenListe.ts}.
 */
final class BestellungsKettenSuche {

    /** Ab so vielen Tagen ohne neues Dokument gilt eine Kette als alt (Frontend: {@code ALTER_GRENZE_TAGE}). */
    static final int ALTER_GRENZE_TAGE = 60;

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private BestellungsKettenSuche() {
    }

    /** Alle Suchwörter müssen irgendwo in Lieferant, Art, Nummer, Dateiname, Betrag oder Datum vorkommen. */
    static boolean passt(DokumentenKette kette, String suchbegriff) {
        // (?U): wie JavaScripts \s auch geschützte und andere Unicode-Leerzeichen
        List<String> woerter = Arrays.stream(normalisiere(suchbegriff).split("(?U)\\s++"))
                .filter(w -> !w.isEmpty())
                .toList();
        if (woerter.isEmpty()) {
            return true;
        }
        String text = suchText(kette);
        return woerter.stream().allMatch(text::contains);
    }

    /** Ohne bekanntes Datum gilt eine Kette nie als alt – lieber sichtbar lassen als verstecken. */
    static boolean istAlt(DokumentenKette kette, LocalDate heute) {
        LocalDate letzte = kette.dokumente().stream()
                .map(BestellungsKettenSuche::datum)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        return letzte != null && ChronoUnit.DAYS.between(letzte, heute) > ALTER_GRENZE_TAGE;
    }

    private static LocalDate datum(DokumentRef dok) {
        return dok.dokumentDatum != null ? dok.dokumentDatum : dok.eingangsDatum;
    }

    private static String normalisiere(String text) {
        return text == null ? "" : text.toLowerCase(Locale.GERMAN).replace('€', ' ').trim();
    }

    private static String suchText(DokumentenKette kette) {
        List<String> teile = new ArrayList<>();
        teile.add(Objects.requireNonNullElse(kette.lieferantName(), ""));
        for (DokumentRef dok : kette.dokumente()) {
            teile.add(bezeichnung(dok.typ));
            teile.add(Objects.requireNonNullElse(dok.dokumentNummer, ""));
            teile.add(Objects.requireNonNullElse(dok.dateiname, ""));
            if (dok.betragBrutto != null && Double.isFinite(dok.betragBrutto)) {
                String betrag = betrag(dok.betragBrutto);
                // „1.234,50“ und „1234,50“ sollen beide treffen
                teile.add(betrag);
                teile.add(betrag.replace(".", ""));
            }
            LocalDate datum = datum(dok);
            if (datum != null) {
                teile.add(datum.format(DATUM));
            }
        }
        return normalisiere(String.join(" ", teile));
    }

    private static String betrag(double wert) {
        // DecimalFormat ist nicht threadsicher: je Aufruf neu.
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        format.setRoundingMode(RoundingMode.HALF_UP); // wie toLocaleString im Browser
        return format.format(wert);
    }

    /** Wie {@code TYP_LABELS} im Frontend. */
    private static String bezeichnung(LieferantDokumentTyp typ) {
        if (typ == null) {
            return "";
        }
        return switch (typ) {
            case ANGEBOT -> "Angebot";
            case AUFTRAGSBESTAETIGUNG -> "AB";
            case LIEFERSCHEIN -> "Lieferschein";
            case WERKSTOFFZEUGNIS -> "Werkstoffzeugnis";
            case RECHNUNG -> "Rechnung";
            case GUTSCHRIFT -> "Gutschrift";
            case SONSTIG, BELEG -> "Sonstiges";
        };
    }
}
