package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufMengenUmrechnung;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EinkaufMengenUmrechnungTest {
    private final EinkaufMengenUmrechnung service = new EinkaufMengenUmrechnung();

    @Test
    void wandeltTonnenInKilogrammUm() {
        var result = service.normalisieren(new Mengenbasis(new BigDecimal("1.25"), Einheit.TONNE, null, null, null, null), Einheit.KILOGRAMM);
        assertEquals(new BigDecimal("1250.000000"), result.menge());
        assertTrue(result.vollstaendig());
    }

    @Test
    void wandeltStueckNurMitBelegterEinzellaengeInMeterUm() {
        var result = service.normalisieren(new Mengenbasis(new BigDecimal("4"), Einheit.STUECK, new BigDecimal("4"), new BigDecimal("6000"), null, "Artikelmaß"), Einheit.METER);
        assertEquals(new BigDecimal("24.000000"), result.menge());
        assertEquals("Artikelmaß", result.quelle());
    }

    @Test
    void unbekannteEinheitErhaeltKeinenScheinbarenEinheitsfaktor() {
        var result = service.normalisieren(new Mengenbasis(BigDecimal.TEN, null, null, null, null, null), Einheit.KILOGRAMM);
        assertFalse(result.vollstaendig());
        assertEquals(null, result.menge());
    }

    @Test
    void wandeltKilogrammUndMeterNurMitGewichtJeMeterUm() {
        var result = service.normalisieren(new Mengenbasis(new BigDecimal("15"), Einheit.KILOGRAMM, null, null, new BigDecimal("2.5"), "Werkstoffdatenblatt"), Einheit.METER);
        assertEquals(new BigDecimal("6.000000"), result.menge());
        assertEquals("Werkstoffdatenblatt", result.quelle());
    }

    @Test
    void kgJeMeterOhneQuelleReichtNichtAlsBeleg() {
        var result = service.normalisieren(new Mengenbasis(new BigDecimal("15"), Einheit.KILOGRAMM,
                null, null, new BigDecimal("2.5"), null), Einheit.METER);
        assertFalse(result.vollstaendig());
        assertEquals(null, result.menge());
    }
}
