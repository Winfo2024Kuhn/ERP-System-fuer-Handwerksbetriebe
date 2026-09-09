package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für ZeitkontoService.
 * Prüft das Delegieren der Sollstunden-Berechnung an {@link TagesSollService}.
 * Die eigentlichen Rechenregeln (Feiertage, halbe Feiertage,
 * Wiedereingliederung) stehen in {@link TagesSollServiceTest} und in
 * {@link TagesSollCharakterisierungZeitkontoTest}.
 */
@ExtendWith(MockitoExtension.class)
class ZeitkontoServiceTest {

    @Mock
    private ZeitkontoRepository zeitkontoRepository;

    @Mock
    private MitarbeiterRepository mitarbeiterRepository;

    @Mock
    private TagesSollService tagesSollService;

    @InjectMocks
    private ZeitkontoService zeitkontoService;

    private Mitarbeiter testMitarbeiter;
    private Zeitkonto testZeitkonto;

    @BeforeEach
    void setUp() {
        testMitarbeiter = new Mitarbeiter();
        testMitarbeiter.setId(1L);
        testMitarbeiter.setVorname("Max");
        testMitarbeiter.setNachname("Mustermann");

        testZeitkonto = new Zeitkonto(testMitarbeiter);
        // Standard 40-Stunden-Woche: Mo-Fr je 8 Stunden
        testZeitkonto.setMontagStunden(new BigDecimal("8"));
        testZeitkonto.setDienstagStunden(new BigDecimal("8"));
        testZeitkonto.setMittwochStunden(new BigDecimal("8"));
        testZeitkonto.setDonnerstagStunden(new BigDecimal("8"));
        testZeitkonto.setFreitagStunden(new BigDecimal("8"));
        testZeitkonto.setSamstagStunden(BigDecimal.ZERO);
        testZeitkonto.setSonntagStunden(BigDecimal.ZERO);
    }

    @Test
    void getOrCreateZeitkonto_WennVorhanden_GibtExistierendesZurueck() {
        // Arrange
        when(zeitkontoRepository.findByMitarbeiterId(1L)).thenReturn(Optional.of(testZeitkonto));

        // Act
        Zeitkonto result = zeitkontoService.getOrCreateZeitkonto(1L);

        // Assert
        assertNotNull(result);
        assertEquals(new BigDecimal("8"), result.getMontagStunden());
        verify(zeitkontoRepository, never()).save(any());
    }

    @Test
    void getOrCreateZeitkonto_WennNichtVorhanden_ErstelltNeues() {
        // Arrange
        when(zeitkontoRepository.findByMitarbeiterId(1L)).thenReturn(Optional.empty());
        when(mitarbeiterRepository.findById(1L)).thenReturn(Optional.of(testMitarbeiter));
        when(zeitkontoRepository.save(any(Zeitkonto.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Zeitkonto result = zeitkontoService.getOrCreateZeitkonto(1L);

        // Assert
        assertNotNull(result);
        verify(zeitkontoRepository).save(any(Zeitkonto.class));
    }

    @Test
    void berechneSollstundenFuerZeitraum_DelegiertAnTagesSollServicePeriodenSollSumme() {
        // Arrange
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);
        when(tagesSollService.periodenSollSumme(1L, testZeitkonto, von, bis))
                .thenReturn(new BigDecimal("184.00"));

        // Act
        BigDecimal result = zeitkontoService.berechneSollstundenFuerZeitraum(testZeitkonto, von, bis);

        // Assert - der Service reicht die Berechnung unveraendert durch
        assertEquals(new BigDecimal("184.00"), result);
        verify(tagesSollService).periodenSollSumme(1L, testZeitkonto, von, bis);
    }

    @Test
    void berechneSollstundenFuerZeitraum_KontoOhneMitarbeiter_UebergibtNullAlsMitarbeiterId() {
        // Arrange - Alt-Testdaten ohne Mitarbeiter-Zuordnung am Zeitkonto
        testZeitkonto.setMitarbeiter(null);
        LocalDate tag = LocalDate.of(2024, 1, 1);
        when(tagesSollService.periodenSollSumme(null, testZeitkonto, tag, tag))
                .thenReturn(BigDecimal.ZERO);

        // Act
        BigDecimal result = zeitkontoService.berechneSollstundenFuerZeitraum(testZeitkonto, tag, tag);

        // Assert
        assertEquals(BigDecimal.ZERO, result);
        verify(tagesSollService).periodenSollSumme(null, testZeitkonto, tag, tag);
    }

    @Test
    void berechneSollstundenFuerMonat_UebergibtErstenUndLetztenTagDesMonatsAnTagesSollService() {
        // Arrange
        LocalDate ersterTag = LocalDate.of(2024, 1, 1);
        LocalDate letzterTag = LocalDate.of(2024, 1, 31);
        when(tagesSollService.periodenSollSumme(1L, ersterTag, letzterTag))
                .thenReturn(new BigDecimal("180.00"));

        // Act
        BigDecimal result = zeitkontoService.berechneSollstundenFuerMonat(1L, 2024, 1);

        // Assert
        assertEquals(new BigDecimal("180.00"), result);
        verify(tagesSollService).periodenSollSumme(1L, ersterTag, letzterTag);
    }

    @Test
    void aktualisiereZeitkonto_AktualisiertAlleWerte() {
        // Arrange
        when(zeitkontoRepository.findByMitarbeiterId(1L)).thenReturn(Optional.of(testZeitkonto));
        when(zeitkontoRepository.save(any(Zeitkonto.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Zeitkonto result = zeitkontoService.aktualisiereZeitkonto(
                1L,
                new BigDecimal("7"),
                new BigDecimal("7"),
                new BigDecimal("7"),
                new BigDecimal("7"),
                new BigDecimal("6"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        // Assert
        assertEquals(new BigDecimal("7"), result.getMontagStunden());
        assertEquals(new BigDecimal("6"), result.getFreitagStunden());
        verify(zeitkontoRepository).save(testZeitkonto);
    }
}
