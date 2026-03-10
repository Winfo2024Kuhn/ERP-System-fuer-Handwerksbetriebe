package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangResponseDto;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArbeitsgangMapperTest {

    @Mock
    private ArbeitsgangStundensatzRepository stundensatzRepository;

    @InjectMocks
    private ArbeitsgangMapper mapper;

    @Test
    void mapptAlleFelder() {
        Abteilung abteilung = new Abteilung();
        abteilung.setId(3L);
        abteilung.setName("Schlosserei");

        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setId(1L);
        arbeitsgang.setBeschreibung("Schweißen");
        arbeitsgang.setAbteilung(abteilung);

        ArbeitsgangStundensatz stundensatz = new ArbeitsgangStundensatz();
        stundensatz.setSatz(new BigDecimal("72.50"));
        stundensatz.setJahr(2025);

        when(stundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(1L))
                .thenReturn(Optional.of(stundensatz));

        ArbeitsgangResponseDto dto = mapper.toArbeitsgangResponseDto(arbeitsgang);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getBeschreibung()).isEqualTo("Schweißen");
        assertThat(dto.getAbteilungId()).isEqualTo(3L);
        assertThat(dto.getAbteilungName()).isEqualTo("Schlosserei");
        assertThat(dto.getStundensatz()).isEqualByComparingTo(new BigDecimal("72.50"));
        assertThat(dto.getStundensatzJahr()).isEqualTo(2025);
    }

    @Test
    void gibtNullZurueckBeiNullArbeitsgang() {
        assertThat(mapper.toArbeitsgangResponseDto(null)).isNull();
    }

    @Test
    void mapptArbeitsgangOhneAbteilung() {
        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setId(2L);
        arbeitsgang.setBeschreibung("Test");
        arbeitsgang.setAbteilung(null);

        when(stundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(2L))
                .thenReturn(Optional.empty());

        ArbeitsgangResponseDto dto = mapper.toArbeitsgangResponseDto(arbeitsgang);

        assertThat(dto.getAbteilungId()).isNull();
        assertThat(dto.getAbteilungName()).isNull();
    }

    @Test
    void mapptArbeitsgangOhneStundensatz() {
        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setId(3L);
        arbeitsgang.setBeschreibung("Ohne Satz");

        when(stundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(3L))
                .thenReturn(Optional.empty());

        ArbeitsgangResponseDto dto = mapper.toArbeitsgangResponseDto(arbeitsgang);

        assertThat(dto.getStundensatz()).isNull();
        assertThat(dto.getStundensatzJahr()).isNull();
    }
}
