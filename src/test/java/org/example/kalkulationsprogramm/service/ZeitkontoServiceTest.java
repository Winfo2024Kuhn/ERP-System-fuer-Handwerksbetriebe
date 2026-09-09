package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZeitkontoServiceTest {
    @Mock private TagesSollService tagesSollService;
    @InjectMocks private ZeitkontoService zeitkontoService;

    @Test
    void berechneSollstundenFuerZeitraumDelegiertMitMitarbeiterUndZeitraum() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);
        when(tagesSollService.periodenSollSumme(1L, von, bis)).thenReturn(new BigDecimal("184.00"));

        assertEquals(new BigDecimal("184.00"), zeitkontoService.berechneSollstundenFuerZeitraum(1L, von, bis));
        verify(tagesSollService).periodenSollSumme(1L, von, bis);
    }

    @Test
    void berechneSollstundenFuerMonatDelegiertMitDemVollstaendigenMonat() {
        LocalDate ersterTag = LocalDate.of(2024, 1, 1);
        LocalDate letzterTag = LocalDate.of(2024, 1, 31);
        when(tagesSollService.periodenSollSumme(1L, ersterTag, letzterTag)).thenReturn(new BigDecimal("180.00"));

        assertEquals(new BigDecimal("180.00"), zeitkontoService.berechneSollstundenFuerMonat(1L, 2024, 1));
        verify(tagesSollService).periodenSollSumme(1L, ersterTag, letzterTag);
    }
}
