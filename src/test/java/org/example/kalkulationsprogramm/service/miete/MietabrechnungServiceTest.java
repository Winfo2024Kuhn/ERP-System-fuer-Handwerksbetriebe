package org.example.kalkulationsprogramm.service.miete;

import org.example.kalkulationsprogramm.domain.miete.*;
import org.example.kalkulationsprogramm.repository.miete.*;
import org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MietabrechnungServiceTest {

    @Mock
    private MietobjektRepository mietobjektRepository;
    @Mock
    private MieteKostenstelleRepository kostenstelleRepository;
    @Mock
    private KostenpositionRepository kostenpositionRepository;
    @Mock
    private VerbrauchsgegenstandRepository verbrauchsgegenstandRepository;
    @Mock
    private ZaehlerstandRepository zaehlerstandRepository;
    @Mock
    private KostenpositionBerechner kostenpositionBerechner;

    @InjectMocks
    private MietabrechnungService service;

    private Mietobjekt mietobjekt;
    private Verbrauchsgegenstand gegenstand;

    @BeforeEach
    void setUp() {
        mietobjekt = new Mietobjekt();
        mietobjekt.setId(1L);
        mietobjekt.setName("Test Objekt");
        mietobjekt.setStrasse("Teststraße 1");
        mietobjekt.setPlz("12345");
        mietobjekt.setOrt("Testort");

        gegenstand = new Verbrauchsgegenstand();
        gegenstand.setId(10L);
        gegenstand.setName("Wasserzähler");
        gegenstand.setVerbrauchsart(Verbrauchsart.WASSER);
        gegenstand.setEinheit("m³");
    }

    @Test
    void berechneJahresabrechnung_shouldCalculateVorjahrConsumption_whenReadingsExist() {
        // GIVEN
        int year = 2023;

        // Mock simple entity lookups
        when(mietobjektRepository.findById(1L)).thenReturn(Optional.of(mietobjekt));
        when(kostenpositionRepository.findByKostenstelleMietobjektIdAndAbrechnungsJahr(anyLong(), anyInt()))
                .thenReturn(List.of());
        when(kostenstelleRepository.findByMietobjektIdOrderByNameAsc(anyLong())).thenReturn(List.of());
        when(verbrauchsgegenstandRepository.findByRaumMietobjektId(1L)).thenReturn(List.of(gegenstand));

        // Setup Zählerstände for 2023, 2022, 2021
        // 2021: 100
        // 2022: 200 (Diff 100)
        // 2023: 350 (Diff 150)

        Zaehlerstand z2023 = createZaehlerstand(2023, new BigDecimal("350"));
        Zaehlerstand z2022 = createZaehlerstand(2022, new BigDecimal("200"));
        Zaehlerstand z2021 = createZaehlerstand(2021, new BigDecimal("100")); // PrevPrev

        // Mock repository calls for Zählerstand
        when(zaehlerstandRepository.findByVerbrauchsgegenstandInAndAbrechnungsJahr(anyList(), eq(2023)))
                .thenReturn(List.of(z2023));
        when(zaehlerstandRepository.findByVerbrauchsgegenstandInAndAbrechnungsJahr(anyList(), eq(2022)))
                .thenReturn(List.of(z2022));

        // This call is the critical one for "Vorjahr" calculation
        when(zaehlerstandRepository.findByVerbrauchsgegenstandAndAbrechnungsJahr(eq(gegenstand), eq(2021)))
                .thenReturn(Optional.of(z2021));

        // WHEN
        AnnualAccountingResult result = service.berechneJahresabrechnung(1L, year);

        // THEN
        assertThat(result.getVerbrauchsvergleiche()).hasSize(1);
        AnnualAccountingResult.Verbrauchsvergleich vv = result.getVerbrauchsvergleiche().getFirst();

        System.out.println("Aktuell: " + vv.getVerbrauchJahr());
        System.out.println("Vorjahr: " + vv.getVerbrauchVorjahr());
        System.out.println("Differenz: " + vv.getDifferenz());

        assertThat(vv.getVerbrauchJahr()).isEqualByComparingTo(new BigDecimal("150")); // 350 - 200
        assertThat(vv.getVerbrauchVorjahr()).isEqualByComparingTo(new BigDecimal("100")); // 200 - 100
        assertThat(vv.getDifferenz()).isEqualByComparingTo(new BigDecimal("50")); // 150 - 100
    }

    private Zaehlerstand createZaehlerstand(int year, BigDecimal stand) {
        Zaehlerstand z = new Zaehlerstand();
        z.setId((long) year);
        z.setVerbrauchsgegenstand(gegenstand);
        z.setAbrechnungsJahr(year);
        z.setStand(stand);
        z.setStichtag(LocalDate.of(year, 12, 31));
        return z;
    }
}
