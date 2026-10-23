package org.example.kalkulationsprogramm.service;

import static org.mockito.Mockito.mock;

import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProduktkategorieServiceByIdTest {

    @Mock
    private ProduktkategorieRepository produktkategorieRepository;
    @Mock
    private ProjektRepository projektRepository;
    @Mock
    private DateiSpeicherService dateiSpeicherService;

    private ProduktkategorieService service;

    @BeforeEach
    void setUp() {
        service = new ProduktkategorieService(produktkategorieRepository, projektRepository,
                new ProduktkategorieMapper(), dateiSpeicherService,
                mock(LeistungRepository.class));
    }

    private static Produktkategorie edelstahlUnterGelaender() {
        Produktkategorie gelaender = new Produktkategorie();
        gelaender.setId(1L);
        gelaender.setBezeichnung("Geländer");
        gelaender.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        Produktkategorie edelstahl = new Produktkategorie();
        edelstahl.setId(2L);
        edelstahl.setBezeichnung("Edelstahl");
        edelstahl.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        edelstahl.setUebergeordneteKategorie(gelaender);
        return edelstahl;
    }

    @Test
    void lightLiefertParentIdOhneProjektzaehlung() {
        when(produktkategorieRepository.findById(2L)).thenReturn(Optional.of(edelstahlUnterGelaender()));

        ProduktkategorieResponseDto dto = service.findeKategorieById(2L, true);

        assertThat(dto.getParentId()).isEqualTo(1L);
        assertThat(dto.getProjektAnzahl()).isNull();
        verify(projektRepository, never()).getKategorieProjektPairs();
    }

    @Test
    void ohneLightWerdenProjekteGezaehlt() {
        Produktkategorie edelstahl = edelstahlUnterGelaender();
        when(produktkategorieRepository.findById(2L)).thenReturn(Optional.of(edelstahl));
        when(projektRepository.getKategorieProjektPairs()).thenReturn(List.of());
        when(produktkategorieRepository.findAllWithParent()).thenReturn(List.of(edelstahl));

        ProduktkategorieResponseDto dto = service.findeKategorieById(2L);

        assertThat(dto.getProjektAnzahl()).isZero();
        verify(projektRepository).getKategorieProjektPairs();
    }

    @Test
    void unbekannteKategorieWirftFehler() {
        when(produktkategorieRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findeKategorieById(99L, true)).isInstanceOf(RuntimeException.class);
    }
}
