package org.example.kalkulationsprogramm.service;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class MonatsSaldoWarmupServiceTest {
    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock ZeitbuchungRepository zeitbuchungRepository;
    @Mock MonatsSaldoRepository monatsSaldoRepository;
    @Mock MonatsSaldoService monatsSaldoService;
    @InjectMocks MonatsSaldoWarmupService warmup;
    @Test void festeMonateUndKontoloseUndSystemWerdenUebersprungen() {
        var m = new Mitarbeiter(); m.setId(1L); m.setEintrittsdatum(YearMonth.now().minusMonths(1).atDay(1));
        var ohne = new Mitarbeiter(); ohne.setFuehrtZeitkonto(false);
        var system = new Mitarbeiter(); system.setArt(MitarbeiterArt.SYSTEM);
        when(mitarbeiterRepository.findByAktivTrue()).thenReturn(List.of(m, ohne, system));
        var ms = new MonatsSaldo(); ms.setFestgeschrieben(true); ms.setGueltig(false);
        when(monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(1L, m.getEintrittsdatum().getYear(), m.getEintrittsdatum().getMonthValue())).thenReturn(Optional.of(ms));
        warmup.warmupCache();
        verifyNoInteractions(monatsSaldoService);
        verify(zeitbuchungRepository).findFirstByMitarbeiterIdOrderByStartZeitAsc(1L);
        verifyNoMoreInteractions(zeitbuchungRepository);
    }
}
