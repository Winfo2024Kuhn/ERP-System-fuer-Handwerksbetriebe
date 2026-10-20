package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Bundesland;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Eingebaute Feiertagsliste je Bundesland (Issue #222). */
class GesetzlicheFeiertageTest {

    private static Set<String> namen(int jahr, Bundesland land) {
        return GesetzlicheFeiertage.fuer(jahr, land).stream().map(Feiertag::getBezeichnung).collect(Collectors.toSet());
    }

    @ParameterizedTest
    @EnumSource(Bundesland.class)
    void jedesLandHatDieBundesweitenFeiertageImRichtigenLand(Bundesland land) {
        List<Feiertag> liste = GesetzlicheFeiertage.fuer(2026, land);
        Set<String> namen = namen(2026, land);
        assertTrue(namen.containsAll(Set.of("Neujahr", "Karfreitag", "Ostermontag", "Tag der Arbeit",
                "Christi Himmelfahrt", "Pfingstmontag", "Tag der Deutschen Einheit", "1. Weihnachtstag",
                "2. Weihnachtstag")), land + ": " + namen);
        assertTrue(liste.stream().allMatch(f -> land.name().equals(f.getBundesland())));
        assertTrue(liste.stream().noneMatch(Feiertag::isHalbTag));
        // nach Datum sortiert, keine doppelten Tage
        assertEquals(liste.stream().map(Feiertag::getDatum).sorted().toList(),
                liste.stream().map(Feiertag::getDatum).toList());
        assertEquals(liste.size(), liste.stream().map(Feiertag::getDatum).distinct().count());
    }

    @ParameterizedTest
    @CsvSource({
            "BY, 13", "BW, 12", "SL, 12", "NW, 11", "RP, 11", "HE, 10", "ST, 11", "SN, 11", "TH, 11",
            "BE, 10", "MV, 11", "BB, 12", "HB, 10", "HH, 10", "NI, 10", "SH, 10"})
    void anzahl2026JeLand(Bundesland land, int anzahl) {
        assertEquals(anzahl, GesetzlicheFeiertage.fuer(2026, land).size(), land + ": " + namen(2026, land));
    }

    @Test
    void regionaleFeiertage() {
        assertTrue(namen(2026, Bundesland.BY).containsAll(Set.of("Heilige Drei Könige", "Fronleichnam",
                "Mariä Himmelfahrt", "Allerheiligen")));
        assertFalse(namen(2026, Bundesland.BY).contains("Reformationstag"));
        assertTrue(namen(2026, Bundesland.SN).containsAll(Set.of("Reformationstag", "Buß- und Bettag")));
        assertTrue(namen(2026, Bundesland.TH).contains("Weltkindertag"));
        assertFalse(namen(2018, Bundesland.TH).contains("Weltkindertag"));
        assertTrue(namen(2026, Bundesland.BE).contains("Internationaler Frauentag"));
        assertFalse(namen(2018, Bundesland.BE).contains("Internationaler Frauentag"));
        assertTrue(namen(2023, Bundesland.MV).contains("Internationaler Frauentag"));
        assertFalse(namen(2022, Bundesland.MV).contains("Internationaler Frauentag"));
        assertTrue(namen(2026, Bundesland.BB).containsAll(Set.of("Ostersonntag", "Pfingstsonntag")));
        assertTrue(namen(2018, Bundesland.HH).contains("Reformationstag"));
        assertFalse(namen(2017, Bundesland.HH).contains("Reformationstag"));
        assertTrue(namen(2017, Bundesland.SN).contains("Reformationstag"));
    }

    @Test
    void bewegligeFeiertageUndBussUndBettag() {
        assertEquals(LocalDate.of(2026, 4, 5), GesetzlicheFeiertage.ostersonntag(2026));
        assertEquals(LocalDate.of(2025, 4, 20), GesetzlicheFeiertage.ostersonntag(2025));
        assertEquals(LocalDate.of(2026, 11, 18), GesetzlicheFeiertage.bussUndBettag(2026));
        assertEquals(LocalDate.of(2028, 11, 22), GesetzlicheFeiertage.bussUndBettag(2028));
        assertTrue(GesetzlicheFeiertage.fuer(2026, Bundesland.BW).stream()
                .anyMatch(f -> f.getBezeichnung().equals("Fronleichnam") && f.getDatum().equals(LocalDate.of(2026, 6, 4))));
    }
}
