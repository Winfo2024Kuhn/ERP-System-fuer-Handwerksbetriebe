package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.junit.jupiter.api.Test;

/**
 * Halbe Urlaubstage zählen 0,5 (Issue #269, Punkt 5). Dummy-Daten ohne Personenbezug.
 */
class UrlaubstageRechnerTest {

    private final UrlaubstageRechner rechner = new UrlaubstageRechner();

    private static final LocalDate MONTAG = LocalDate.of(2026, 10, 12);
    private static final LocalDate DIENSTAG = MONTAG.plusDays(1);

    private static Abwesenheit eintrag(AbwesenheitsTyp typ, LocalDate datum, String stunden) {
        Abwesenheit a = new Abwesenheit();
        a.setTyp(typ);
        a.setDatum(datum);
        a.setStunden(stunden == null ? null : new BigDecimal(stunden));
        return a;
    }

    @Test
    void anteil_halbesSollIstHalberTag_vollesSollGanzerTag() {
        BigDecimal soll = new BigDecimal("8.00");
        assertEquals(new BigDecimal("0.5"), rechner.anteil(eintrag(AbwesenheitsTyp.URLAUB, MONTAG, "4.00"), soll));
        assertEquals(BigDecimal.ONE, rechner.anteil(eintrag(AbwesenheitsTyp.URLAUB, MONTAG, "8.00"), soll));
    }

    @Test
    void anteil_ungeradesSoll_halbeRundetWieBeimBuchen() {
        // 7,75 h Soll → halber Tag wird mit 3,88 h gebucht (HALF_UP), muss 0,5 bleiben.
        assertEquals(new BigDecimal("0.5"),
                rechner.anteil(eintrag(AbwesenheitsTyp.URLAUB, MONTAG, "3.88"), new BigDecimal("7.75")));
    }

    @Test
    void anteil_ohneSollOderStunden_zaehltGanzenTag() {
        assertEquals(BigDecimal.ONE, rechner.anteil(eintrag(AbwesenheitsTyp.URLAUB, MONTAG, "0.00"), null));
        assertEquals(BigDecimal.ONE, rechner.anteil(eintrag(AbwesenheitsTyp.URLAUB, MONTAG, "0.00"), BigDecimal.ZERO));
        assertEquals(BigDecimal.ONE, rechner.anteil(eintrag(AbwesenheitsTyp.URLAUB, MONTAG, null), new BigDecimal("8")));
    }

    @Test
    void urlaubstage_summiertNurUrlaub_undNutztSollJeTag() {
        List<Abwesenheit> eintraege = List.of(
                eintrag(AbwesenheitsTyp.URLAUB, MONTAG, "8.00"),
                eintrag(AbwesenheitsTyp.URLAUB, DIENSTAG, "4.00"),
                eintrag(AbwesenheitsTyp.KRANKHEIT, DIENSTAG, "4.00"));
        Map<LocalDate, BigDecimal> soll = Map.of(MONTAG, new BigDecimal("8.00"), DIENSTAG, new BigDecimal("8.00"));

        assertEquals(new BigDecimal("1.5"), rechner.urlaubstage(eintraege, soll));
    }

    @Test
    void urlaubstage_ohneSollMap_zaehltJedenEintragGanz() {
        List<Abwesenheit> eintraege = List.of(
                eintrag(AbwesenheitsTyp.URLAUB, MONTAG, "4.00"),
                eintrag(AbwesenheitsTyp.URLAUB, DIENSTAG, "4.00"));

        assertEquals(new BigDecimal("2"), rechner.urlaubstage(eintraege, (Map<LocalDate, BigDecimal>) null));
    }

    @Test
    void resturlaub_rechnetKorrekturOhneAbschneiden_undNieUnterNull() {
        assertEquals(new BigDecimal("28.0"),
                rechner.resturlaub(30, new BigDecimal("1.5"), BigDecimal.ONE, new BigDecimal("0.5")));
        assertEquals(new BigDecimal("29.5"), rechner.resturlaub(30, new BigDecimal("0.5"), BigDecimal.ZERO, null));
        assertEquals(BigDecimal.ZERO, rechner.resturlaub(2, new BigDecimal("5"), BigDecimal.ZERO, null));
    }

    @Test
    void korrekturTage_nullWirdNull() {
        assertEquals(BigDecimal.ZERO, rechner.korrekturTage(null));
        assertEquals(new BigDecimal("0.5"), rechner.korrekturTage(new BigDecimal("0.5")));
    }

    @Test
    void formatTage_mitKommaUndOhneUeberfluessigeNullen() {
        assertEquals("12,5", UrlaubstageRechner.formatTage(new BigDecimal("12.50")));
        assertEquals("12", UrlaubstageRechner.formatTage(new BigDecimal("12.0")));
        assertEquals("30", UrlaubstageRechner.formatTage(new BigDecimal("3E+1")));
        assertEquals("0", UrlaubstageRechner.formatTage(BigDecimal.ZERO));
    }

    @Test
    void urlaubstageMitLader_laedtSollNurFuerUrlaubszeitraum_undGarNichtOhneUrlaub() {
        java.util.List<LocalDate[]> aufrufe = new java.util.ArrayList<>();
        java.util.function.BiFunction<LocalDate, LocalDate, Map<LocalDate, BigDecimal>> lader = (von, bis) -> {
            aufrufe.add(new LocalDate[] { von, bis });
            return Map.of(MONTAG, new BigDecimal("8.00"), DIENSTAG, new BigDecimal("8.00"));
        };

        assertEquals(BigDecimal.ZERO,
                rechner.urlaubstage(List.of(eintrag(AbwesenheitsTyp.KRANKHEIT, MONTAG, "8.00")), lader));
        assertEquals(0, aufrufe.size());

        BigDecimal tage = rechner.urlaubstage(List.of(
                eintrag(AbwesenheitsTyp.URLAUB, DIENSTAG, "4.00"),
                eintrag(AbwesenheitsTyp.URLAUB, MONTAG, "8.00")), lader);

        assertEquals(new BigDecimal("1.5"), tage);
        assertEquals(1, aufrufe.size());
        assertEquals(MONTAG, aufrufe.get(0)[0]);
        assertEquals(DIENSTAG, aufrufe.get(0)[1]);
    }

    @Test
    void sollLader_geschaeftsfuehrerOhneSoll_sonstTagesSollService() {
        TagesSollService tagesSoll = org.mockito.Mockito.mock(TagesSollService.class);
        org.example.kalkulationsprogramm.domain.Mitarbeiter gf = new org.example.kalkulationsprogramm.domain.Mitarbeiter();
        gf.setId(1L);
        gf.setIstGeschaeftsfuehrer(true);
        org.example.kalkulationsprogramm.domain.Mitarbeiter max = new org.example.kalkulationsprogramm.domain.Mitarbeiter();
        max.setId(2L);
        org.mockito.Mockito.when(tagesSoll.arbeitsSollJeTag(2L, MONTAG, DIENSTAG))
                .thenReturn(Map.of(MONTAG, new BigDecimal("8.00")));

        assertEquals(Map.of(), UrlaubstageRechner.sollLader(gf, tagesSoll).apply(MONTAG, DIENSTAG));
        assertEquals(Map.of(MONTAG, new BigDecimal("8.00")),
                UrlaubstageRechner.sollLader(max, tagesSoll).apply(MONTAG, DIENSTAG));
        org.mockito.Mockito.verify(tagesSoll, org.mockito.Mockito.never()).arbeitsSollJeTag(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
