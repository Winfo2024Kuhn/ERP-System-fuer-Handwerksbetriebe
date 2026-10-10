package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Gewerk;
import org.example.kalkulationsprogramm.dto.GewerkDto;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.GewerkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GewerkServiceTest {

    private GewerkRepository repository;
    private FirmeninformationRepository firmeninformationRepository;
    private GewerkService service;

    @BeforeEach
    void setUp() {
        repository = mock(GewerkRepository.class);
        firmeninformationRepository = mock(FirmeninformationRepository.class);
        service = new GewerkService(repository, firmeninformationRepository);
        when(repository.save(any(Gewerk.class))).thenAnswer(inv -> {
            Gewerk g = inv.getArgument(0);
            if (g.getId() == null) g.setId(33L);
            return g;
        });
    }

    @Test
    void leererNameWirdAbgewiesen() {
        GewerkDto dto = new GewerkDto();
        dto.setBgName("BG BAU");
        dto.setBgSatzProzent(new BigDecimal("3.30"));

        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name");
    }

    @Test
    void leererBgNameWirdAbgewiesen() {
        GewerkDto dto = new GewerkDto();
        dto.setName("Tischler");
        dto.setBgSatzProzent(new BigDecimal("1.13"));

        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BG-Name");
    }

    @Test
    void fehlenderBgSatzWirdAbgewiesen() {
        GewerkDto dto = new GewerkDto();
        dto.setName("Tischler");
        dto.setBgName("BGHM");

        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BG-Satz");
    }

    @Test
    void neuesGewerkWirdGespeichert() {
        GewerkDto dto = new GewerkDto();
        dto.setName("Tischler");
        dto.setBgName("BGHM");
        dto.setBgSatzProzent(new BigDecimal("1.13"));

        GewerkDto saved = service.save(dto);

        assertThat(saved.getId()).isEqualTo(33L);
        assertThat(saved.getName()).isEqualTo("Tischler");
        assertThat(saved.getBgName()).isEqualTo("BGHM");
        assertThat(saved.getBgSatzProzent()).isEqualByComparingTo("1.13");
        assertThat(saved.getAktiv()).isTrue();
    }

    @Test
    void gewerkDerFirmaLaesstSichNichtLoeschen() {
        when(firmeninformationRepository.existsByGewerkId(7L)).thenReturn(true);

        // Sonst leerte die Datenbank das Gewerk der Firma und der BG-Satz fiele still weg
        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("in den Firmendaten eingetragen")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).deleteById(7L);
    }

    @Test
    void unbenutztesGewerkWirdGeloescht() {
        when(firmeninformationRepository.existsByGewerkId(8L)).thenReturn(false);

        service.delete(8L);

        verify(repository).deleteById(8L);
    }
}
