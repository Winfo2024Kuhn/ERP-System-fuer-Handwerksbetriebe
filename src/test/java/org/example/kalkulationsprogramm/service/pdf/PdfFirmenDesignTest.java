package org.example.kalkulationsprogramm.service.pdf;

import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfFirmenDesignTest {

    @Mock
    FirmeninformationService firmeninformationService;

    @InjectMocks
    PdfFirmenDesign design;

    @Test
    void nimmtFirmenfarbeUndFirmennameAusDenFirmendaten() {
        FirmeninformationDto firma = new FirmeninformationDto();
        firma.setFirmenfarbe("#500010");
        firma.setFirmenname(" Musterbetrieb GmbH ");
        when(firmeninformationService.getFirmeninformation()).thenReturn(firma);

        PdfDesign ergebnis = design.laden();

        assertThat(ergebnis.akzent()).isEqualTo(new Color(0x50, 0x00, 0x10));
        assertThat(ergebnis.firmenname()).isEqualTo("Musterbetrieb GmbH");
        assertThat(ergebnis.logo()).isNull();
    }

    @Test
    void ohneFirmendatenBleibtDasBisherigeRot() {
        when(firmeninformationService.getFirmeninformation()).thenReturn(null);

        PdfDesign ergebnis = design.laden();

        assertThat(ergebnis.akzent()).isEqualTo(PdfFarben.STANDARD_AKZENT);
        assertThat(ergebnis.firmenname()).isEmpty();
    }
}
