package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** Zahlengenaue Charakterisierung der delegierenden, rein datumsbasierten Soll-API. */
@ExtendWith(MockitoExtension.class)
class TagesSollCharakterisierungZeitkontoTest {
    @Mock private TagesSollService tagesSollService;
    @InjectMocks private ZeitkontoService zeitkontoService;

    @Test
    void tagestypenBehaltenIhreSollwerte() {
        assertSoll(LocalDate.of(2026, 6, 1), "8.00");
        assertSoll(LocalDate.of(2026, 6, 6), "0.00");
        assertSoll(LocalDate.of(2026, 1, 1), "8.00");
        assertSoll(LocalDate.of(2026, 12, 24), "4.00");
        assertSoll(LocalDate.of(2026, 12, 26), "0.00");
    }

    @Test
    void volleWocheMitVollemFeiertagBleibtBeiVierzigStunden() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 7);
        when(tagesSollService.periodenSollSumme(1L, von, bis)).thenReturn(new BigDecimal("40.00"));
        assertEquals(0, new BigDecimal("40.00").compareTo(
                zeitkontoService.berechneSollstundenFuerZeitraum(1L, von, bis)));
    }

    private void assertSoll(LocalDate tag, String erwartet) {
        when(tagesSollService.periodenSollSumme(1L, tag, tag)).thenReturn(new BigDecimal(erwartet));
        assertEquals(0, new BigDecimal(erwartet).compareTo(
                zeitkontoService.berechneSollstundenFuerZeitraum(1L, tag, tag)));
    }
}
