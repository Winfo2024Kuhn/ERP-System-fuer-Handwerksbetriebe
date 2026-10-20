package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Bundesland;
import org.example.kalkulationsprogramm.domain.Feiertag;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.example.kalkulationsprogramm.domain.Bundesland.*;

/**
 * Eingebaute Liste der gesetzlichen Feiertage je Bundesland. Sie greift nur, wenn
 * die Feiertags-API nicht erreichbar ist ({@link FeiertagService}).
 * <p>
 * Feiertage, die nur in Teilen eines Landes gelten (Fronleichnam in Teilen Sachsens
 * und Thüringens, das Augsburger Friedensfest), stehen bewusst nicht in der Liste:
 * Betriebe legen sie als eigene Feiertage an. Mariä Himmelfahrt gilt in Bayern nur in
 * Gemeinden mit überwiegend katholischer Bevölkerung, steht aber wie bisher drin und
 * lässt sich auf der Feiertage-Seite abschalten.
 */
final class GesetzlicheFeiertage {

    private static final Set<Bundesland> HEILIGE_DREI_KOENIGE = EnumSet.of(BW, BY, ST);
    private static final Set<Bundesland> FRONLEICHNAM = EnumSet.of(BW, BY, HE, NW, RP, SL);
    private static final Set<Bundesland> MARIAE_HIMMELFAHRT = EnumSet.of(BY, SL);
    private static final Set<Bundesland> REFORMATIONSTAG = EnumSet.of(BB, HB, HH, MV, NI, SN, ST, SH, TH);
    private static final Set<Bundesland> ALLERHEILIGEN = EnumSet.of(BW, BY, NW, RP, SL);

    private GesetzlicheFeiertage() {
    }

    static List<Feiertag> fuer(int jahr, Bundesland land) {
        List<Feiertag> liste = new ArrayList<>();
        String kuerzel = land.name();
        LocalDate ostersonntag = ostersonntag(jahr);

        liste.add(new Feiertag(LocalDate.of(jahr, 1, 1), "Neujahr", kuerzel));
        if (HEILIGE_DREI_KOENIGE.contains(land)) {
            liste.add(new Feiertag(LocalDate.of(jahr, 1, 6), "Heilige Drei Könige", kuerzel));
        }
        if ((land == BE && jahr >= 2019) || (land == MV && jahr >= 2023)) {
            liste.add(new Feiertag(LocalDate.of(jahr, 3, 8), "Internationaler Frauentag", kuerzel));
        }
        liste.add(new Feiertag(ostersonntag.minusDays(2), "Karfreitag", kuerzel));
        if (land == BB) {
            liste.add(new Feiertag(ostersonntag, "Ostersonntag", kuerzel));
        }
        liste.add(new Feiertag(ostersonntag.plusDays(1), "Ostermontag", kuerzel));
        liste.add(new Feiertag(LocalDate.of(jahr, 5, 1), "Tag der Arbeit", kuerzel));
        liste.add(new Feiertag(ostersonntag.plusDays(39), "Christi Himmelfahrt", kuerzel));
        if (land == BB) {
            liste.add(new Feiertag(ostersonntag.plusDays(49), "Pfingstsonntag", kuerzel));
        }
        liste.add(new Feiertag(ostersonntag.plusDays(50), "Pfingstmontag", kuerzel));
        if (FRONLEICHNAM.contains(land)) {
            liste.add(new Feiertag(ostersonntag.plusDays(60), "Fronleichnam", kuerzel));
        }
        if (MARIAE_HIMMELFAHRT.contains(land)) {
            liste.add(new Feiertag(LocalDate.of(jahr, 8, 15), "Mariä Himmelfahrt", kuerzel));
        }
        if (land == TH && jahr >= 2019) {
            liste.add(new Feiertag(LocalDate.of(jahr, 9, 20), "Weltkindertag", kuerzel));
        }
        liste.add(new Feiertag(LocalDate.of(jahr, 10, 3), "Tag der Deutschen Einheit", kuerzel));
        if (REFORMATIONSTAG.contains(land) && (jahr >= 2018 || !EnumSet.of(HB, HH, NI, SH).contains(land))) {
            liste.add(new Feiertag(LocalDate.of(jahr, 10, 31), "Reformationstag", kuerzel));
        }
        if (ALLERHEILIGEN.contains(land)) {
            liste.add(new Feiertag(LocalDate.of(jahr, 11, 1), "Allerheiligen", kuerzel));
        }
        if (land == SN) {
            liste.add(new Feiertag(bussUndBettag(jahr), "Buß- und Bettag", kuerzel));
        }
        liste.add(new Feiertag(LocalDate.of(jahr, 12, 25), "1. Weihnachtstag", kuerzel));
        liste.add(new Feiertag(LocalDate.of(jahr, 12, 26), "2. Weihnachtstag", kuerzel));

        liste.sort(Comparator.comparing(Feiertag::getDatum));
        return liste;
    }

    /** Mittwoch vor dem 23. November. */
    static LocalDate bussUndBettag(int jahr) {
        return LocalDate.of(jahr, 11, 22).with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
    }

    /** Ostersonntag nach der Gaußschen Osterformel. */
    static LocalDate ostersonntag(int jahr) {
        int a = jahr % 19;
        int b = jahr / 100;
        int c = jahr % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int monat = (h + l - 7 * m + 114) / 31;
        int tag = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(jahr, monat, tag);
    }
}
