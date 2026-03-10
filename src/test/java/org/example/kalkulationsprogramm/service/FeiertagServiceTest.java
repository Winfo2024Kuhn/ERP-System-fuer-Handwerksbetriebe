package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.repository.FeiertagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für FeiertagService.
 * Testet die Feiertag-Erkennung und -Generierung.
 */
@ExtendWith(MockitoExtension.class)
class FeiertagServiceTest {

    @Mock
    private FeiertagRepository feiertagRepository;

    @InjectMocks
    private FeiertagService feiertagService;

    @BeforeEach
    void setUp() {
        // Set the self reference for transactional methods
        ReflectionTestUtils.setField(feiertagService, "self", feiertagService);
    }

    @Test
    void istFeiertag_WennFeiertagExistiert_GibtTrueZurueck() {
        // Arrange
        LocalDate weihnachten = LocalDate.of(2024, 12, 25);
        when(feiertagRepository.findByJahr(2024))
                .thenReturn(List.of(new Feiertag(weihnachten, "1. Weihnachtstag", "BY")));
        when(feiertagRepository.existsByDatumAndBundesland(weihnachten, "BY")).thenReturn(true);

        // Act
        boolean result = feiertagService.istFeiertag(weihnachten);

        // Assert
        assertTrue(result, "Weihnachten sollte als Feiertag erkannt werden");
    }

    @Test
    void istFeiertag_WennKeinFeiertag_GibtFalseZurueck() {
        // Arrange
        LocalDate normalerTag = LocalDate.of(2024, 7, 15);
        when(feiertagRepository.findByJahr(2024)).thenReturn(Collections.emptyList());
        when(feiertagRepository.saveAll(any())).thenReturn(Collections.emptyList());
        when(feiertagRepository.existsByDatumAndBundesland(normalerTag, "BY")).thenReturn(false);

        // Act
        boolean result = feiertagService.istFeiertag(normalerTag);

        // Assert
        assertFalse(result, "Ein normaler Arbeitstag sollte kein Feiertag sein");
    }

    @Test
    void istFeiertag_Neujahr_GibtTrueZurueck() {
        // Arrange
        LocalDate neujahr = LocalDate.of(2025, 1, 1);
        when(feiertagRepository.findByJahr(2025))
                .thenReturn(List.of(new Feiertag(neujahr, "Neujahr", "BY")));
        when(feiertagRepository.existsByDatumAndBundesland(neujahr, "BY")).thenReturn(true);

        // Act
        boolean result = feiertagService.istFeiertag(neujahr);

        // Assert
        assertTrue(result, "Neujahr sollte als Feiertag erkannt werden");
    }

    @Test
    void getFeiertageZwischen_GibtKorrekteFeiertageListe() {
        // Arrange
        LocalDate von = LocalDate.of(2024, 12, 1);
        LocalDate bis = LocalDate.of(2024, 12, 31);

        List<Feiertag> dezemberFeiertage = Arrays.asList(
                new Feiertag(LocalDate.of(2024, 12, 25), "1. Weihnachtstag", "BY"),
                new Feiertag(LocalDate.of(2024, 12, 26), "2. Weihnachtstag", "BY"));

        when(feiertagRepository.findByJahr(2024)).thenReturn(dezemberFeiertage);
        when(feiertagRepository.findByDatumBetween(von, bis)).thenReturn(dezemberFeiertage);

        // Act
        List<Feiertag> result = feiertagService.getFeiertageZwischen(von, bis);

        // Assert
        assertEquals(2, result.size(), "Dezember sollte 2 Feiertage haben");
        assertEquals("1. Weihnachtstag", result.getFirst().getBezeichnung());
        assertEquals("2. Weihnachtstag", result.get(1).getBezeichnung());
    }

    @Test
    void getFeiertageForJahr_WennNichtVorhanden_LaedtVonApiOderGeneriertLokal() {
        // Arrange
        when(feiertagRepository.findByJahr(2025)).thenReturn(Collections.emptyList());
        when(feiertagRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<Feiertag> result = feiertagService.getFeiertageForJahr(2025);

        // Assert
        // Sollte entweder von API laden oder lokal generieren
        // Mindestens die wichtigsten Feiertage sollten vorhanden sein
        verify(feiertagRepository).saveAll(any());
    }
}
