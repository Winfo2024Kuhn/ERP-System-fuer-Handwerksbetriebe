package org.example.kalkulationsprogramm.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProduktkategorieServiceSucheTest {

    @Mock
    private ProduktkategorieRepository produktkategorieRepository;

    @Mock
    private ProjektRepository projektRepository;

    @Mock
    private DateiSpeicherService dateiSpeicherService;

    private ProduktkategorieMapper produktkategorieMapper;
    private ProduktkategorieService service;

    @BeforeEach
    void setUp() {
        produktkategorieMapper = new ProduktkategorieMapper();
        service = new ProduktkategorieService(
                produktkategorieRepository,
                projektRepository,
                produktkategorieMapper,
                dateiSpeicherService
        );
    }

    @Test
    void sucheLeafKategorienGibtErgebnisseZurueck() {
        Produktkategorie stahl = new Produktkategorie();
        stahl.setId(1L);
        stahl.setBezeichnung("Edelstahl");
        stahl.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);

        when(produktkategorieRepository.sucheLeafKategorienNachBezeichnung("stahl"))
                .thenReturn(List.of(stahl));

        List<ProduktkategorieResponseDto> ergebnis = service.sucheLeafKategorien("stahl");

        assertThat(ergebnis).hasSize(1);
        assertThat(ergebnis.get(0).getBezeichnung()).isEqualTo("Edelstahl");
        assertThat(ergebnis.get(0).isLeaf()).isTrue();
    }

    @Test
    void sucheLeafKategorienGibtLeereListeBeiLeeremSuchbegriff() {
        List<ProduktkategorieResponseDto> ergebnis = service.sucheLeafKategorien("");

        assertThat(ergebnis).isEmpty();
        verify(produktkategorieRepository, never()).sucheLeafKategorienNachBezeichnung(anyString());
    }

    @Test
    void sucheLeafKategorienGibtLeereListeBeiNull() {
        List<ProduktkategorieResponseDto> ergebnis = service.sucheLeafKategorien(null);

        assertThat(ergebnis).isEmpty();
        verify(produktkategorieRepository, never()).sucheLeafKategorienNachBezeichnung(anyString());
    }

    @Test
    void sucheLeafKategorienTrimmtSuchbegriff() {
        when(produktkategorieRepository.sucheLeafKategorienNachBezeichnung("stahl"))
                .thenReturn(List.of());

        service.sucheLeafKategorien("  stahl  ");

        verify(produktkategorieRepository).sucheLeafKategorienNachBezeichnung("stahl");
    }
}
