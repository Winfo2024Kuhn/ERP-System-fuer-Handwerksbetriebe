package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Kosten;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVergleichService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EinkaufVergleichServiceTest {
    @Test
    void leeresPaketKannNichtAlsVollstaendigerNullEuroVergleichDurchgehen() {
        var service = new EinkaufVergleichService(
                org.mockito.Mockito.mock(org.example.kalkulationsprogramm.repository.EinkaufsanfrageRepository.class),
                org.mockito.Mockito.mock(org.example.kalkulationsprogramm.repository.EinkaufAngebotRepository.class),
                org.mockito.Mockito.mock(org.example.kalkulationsprogramm.repository.AngebotVersionRepository.class),
                org.mockito.Mockito.mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufAngebotService.class));
        var offer = new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.VersionDto(
                1L, 1L, 1, 0, 1L, "GEPRUEFT", null, null, null, "EUR", List.of(), List.of(), null,
                null, null, null, null, 1L, null, null, null);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.berechne(offer, List.of(), java.time.LocalDate.of(2026, 9, 23)));
    }

    @Test
    void addiertJedenZuschlagEinmalUndZeigtEnthalteneKostenOhneDoppeladdition() {
        var costs = List.of(
                new Kosten("M", "MATERIAL", new BigDecimal("3"), "KG", BigDecimal.ONE, null, false, false, "Angebot"),
                new Kosten("Z", "ZUSCHNITT", new BigDecimal("2"), "KG", BigDecimal.ONE, null, true, false, "Angebot"));
        var result = EinkaufVergleichService.berechneKosten(costs, new BigDecimal("10"), Einheit.KILOGRAMM);
        assertEquals(new BigDecimal("30.00"), result.nettoGesamt());
        assertEquals(2, result.rechnung().size());
        assertTrue(result.vollstaendig());
    }

    @Test
    void spezifikationsbeispielRanktA1060VorB1080UndHaeltCOffen() {
        var a = EinkaufVergleichService.berechneKosten(List.of(
                new Kosten("A_M", "MATERIAL", new BigDecimal("1000"), "STUECK", BigDecimal.ONE, null, false, false, "Angebot A"),
                new Kosten("A_F", "FRACHT", new BigDecimal("40"), "PAUSCHAL", BigDecimal.ONE, null, false, false, "Angebot A"),
                new Kosten("A_Z", "ZEUGNIS", new BigDecimal("20"), "PAUSCHAL", BigDecimal.ONE, null, false, false, "Angebot A")),
                BigDecimal.ONE, Einheit.STUECK);
        var b = EinkaufVergleichService.berechneKosten(List.of(
                new Kosten("B_M", "MATERIAL", new BigDecimal("970"), "STUECK", BigDecimal.ONE, null, false, false, "Angebot B"),
                new Kosten("B_F", "FRACHT", new BigDecimal("110"), "PAUSCHAL", BigDecimal.ONE, null, false, false, "Angebot B"),
                new Kosten("B_Z", "ZEUGNIS", new BigDecimal("20"), "PAUSCHAL", BigDecimal.ONE, null, true, false, "Angebot B")),
                BigDecimal.ONE, Einheit.STUECK);
        var c = EinkaufVergleichService.berechneKosten(List.of(
                new Kosten("C_M", "MATERIAL", new BigDecimal("920"), "STUECK", BigDecimal.ONE, null, false, false, "Angebot C"),
                new Kosten("C_F", "FRACHT", null, "PAUSCHAL", BigDecimal.ONE, null, false, false, "Angebot C"),
                new Kosten("C_Z", "ZEUGNIS", null, "PAUSCHAL", BigDecimal.ONE, null, false, false, "Angebot C")),
                BigDecimal.ONE, Einheit.STUECK);

        assertEquals(new BigDecimal("1060.00"), a.nettoGesamt());
        assertEquals(new BigDecimal("1080.00"), b.nettoGesamt());
        assertTrue(a.nettoGesamt().compareTo(b.nettoGesamt()) < 0);
        assertFalse(c.vollstaendig());
        assertEquals(null, c.nettoGesamt());
        var sumA = new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVergleichDto.AngebotSumme(
                1L, a.nettoGesamt(), true, true, true, List.of(), a.rechnung());
        var sumB = new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVergleichDto.AngebotSumme(
                2L, b.nettoGesamt(), true, true, true, List.of(), b.rechnung());
        var sumC = new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVergleichDto.AngebotSumme(
                3L, c.nettoGesamt(), false, true, true, List.of("KOSTEN_OFFEN"), c.rechnung());
        assertTrue(EinkaufVergleichService.rankingZulaessig("EUR", sumA, true));
        assertTrue(EinkaufVergleichService.rankingZulaessig("EUR", sumB, true));
        assertFalse(EinkaufVergleichService.rankingZulaessig("EUR", sumC, true));
        assertFalse(EinkaufVergleichService.rankingZulaessig("USD", sumA, true));
    }

    @Test
    void pauschaleFrachtWirdJeLiefergruppeBerechnet() {
        var freight = List.of(new Kosten("F", "FRACHT", new BigDecimal("25"), "PAUSCHAL", BigDecimal.ONE, null, false, false, "Angebot"));
        var groups = java.util.Map.of(
                "Projekt\u0000Adresse A", new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis(BigDecimal.ONE, Einheit.STUECK, BigDecimal.ONE, null, null, null),
                "Lager", new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis(BigDecimal.ONE, Einheit.STUECK, BigDecimal.ONE, null, null, null));

        var result = EinkaufVergleichService.berechneKostenJeLiefergruppe(freight, groups);

        assertEquals(new BigDecimal("50.00"), result.nettoGesamt());
        assertEquals(2, result.rechnung().size());
        assertTrue(result.rechnung().stream().noneMatch(step -> step.key().contains("\u0000")));
    }

    @Test
    void preisJeHundertStueckWirdAufStueckbasisUmgelegt() {
        var price = List.of(new Kosten("M100", "MATERIAL", new BigDecimal("5"), "100STUECK", BigDecimal.ONE, null, false, false, "Angebot"));
        var result = EinkaufVergleichService.berechneKosten(price, new BigDecimal("10"), Einheit.STUECK);
        assertEquals(new BigDecimal("0.50"), result.nettoGesamt());
        assertTrue(result.vollstaendig());
    }

    @Test
    void preisJeHundertKilogrammUndVariableUnbekannteBasisBleibenKorrekt() {
        var hundredKg = EinkaufVergleichService.berechneKosten(List.of(
                new Kosten("M100", "MATERIAL", new BigDecimal("5"), "100KG", BigDecimal.ONE, null, false, false, "Angebot")),
                new BigDecimal("1"), Einheit.TONNE);
        var variable = EinkaufVergleichService.berechneKosten(List.of(
                new Kosten("V", "ENERGIE", new BigDecimal("4"), "KG", BigDecimal.ONE, null, false, true, "variabel")),
                BigDecimal.TEN, Einheit.KILOGRAMM);
        var unknown = EinkaufVergleichService.berechneKosten(List.of(
                new Kosten("U", "MATERIAL", new BigDecimal("4"), "UNBEKANNT", BigDecimal.ONE, null, false, false, "Angebot")),
                BigDecimal.TEN, Einheit.STUECK);

        assertEquals(new BigDecimal("50.00"), hundredKg.nettoGesamt());
        assertFalse(variable.vollstaendig());
        assertFalse(unknown.vollstaendig());
    }

    @Test
    void skontoBleibtEinSeparaterHinweisUndVerfaelltAmStichtag() {
        var discount = EinkaufVergleichService.berechneSkonto(new BigDecimal("100.00"), new BigDecimal("2"), 10);

        assertEquals(new BigDecimal("100.00"), discount.basis());
        assertEquals(new BigDecimal("2.00"), discount.ergebnis());
        assertTrue(EinkaufVergleichService.istGueltig(java.time.LocalDate.of(2026, 9, 24), java.time.LocalDate.of(2026, 9, 24)));
        assertFalse(EinkaufVergleichService.istGueltig(java.time.LocalDate.of(2026, 9, 23), java.time.LocalDate.of(2026, 9, 24)));
    }

    @Test
    void paketrabattEntfaelltNurBeiTeilpaket() {
        var costs = List.of(
                new Kosten("M", "MATERIAL", BigDecimal.TEN, "STUECK", BigDecimal.ONE, null, false, false, "Angebot"),
                new Kosten("R", "RABATT", BigDecimal.ONE, "STUECK", BigDecimal.ONE, null, false, false, "Paketpreis"));
        assertEquals(List.of(costs.getFirst()), EinkaufVergleichService.kostenFuerPaket(costs, false));
        assertEquals(costs, EinkaufVergleichService.kostenFuerPaket(costs, true));
    }

    @Test
    void rabattSenktVollstaendigenPaketpreis() {
        var costs = List.of(
                new Kosten("M", "MATERIAL", new BigDecimal("100"), "STUECK", BigDecimal.ONE, null, false, false, "Angebot"),
                new Kosten("R", "RABATT", new BigDecimal("10"), "STUECK", BigDecimal.ONE, null, false, false, "Paketpreis"));

        var result = EinkaufVergleichService.berechneKosten(costs, BigDecimal.ONE, Einheit.STUECK);

        assertEquals(new BigDecimal("90.00"), result.nettoGesamt());
    }

    @Test
    void mindestmengeUndVerpackungWerdenWarnungOhneAutomatischesAufrunden() {
        var issues = EinkaufVergleichService.pruefeMindestmengeVerpackung(
                new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("6"));
        assertEquals(List.of("MINDESTMENGE_NICHT_ERREICHT", "VERPACKUNGSEINHEIT_NICHT_ERFUELLT"), issues);
        assertEquals(List.of(), EinkaufVergleichService.pruefeMindestmengeVerpackung(
                new BigDecimal("12"), new BigDecimal("12"), new BigDecimal("6")));
    }

    @Test
    void technischeAbweichungBlockiertBisZurMenschlichenBestaetigung() {
        assertFalse(EinkaufVergleichService.technischeAbweichungBestaetigt("ERFASST", 17L));
        assertFalse(EinkaufVergleichService.technischeAbweichungBestaetigt("GEPRUEFT", null));
        assertTrue(EinkaufVergleichService.technischeAbweichungBestaetigt("GEPRUEFT", 17L));
    }

    @Test
    void fehlenderPreisBleibtOffenStattAlsNullEuroZuRangieren() {
        var costs = List.of(new Kosten("M", "MATERIAL", null, "KG", BigDecimal.ONE, null, false, false, "Angebot"));
        var result = EinkaufVergleichService.berechneKosten(costs, new BigDecimal("10"), Einheit.KILOGRAMM);
        assertEquals(null, result.nettoGesamt());
        assertFalse(result.vollstaendig());
    }
}
