package org.example.kalkulationsprogramm.service.pdf;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.springframework.stereotype.Service;

/**
 * Liest Logo, Firmenfarbe und Firmenname aus den Firmendaten (Vorlagen &amp; Stammdaten →
 * Firma) für Berichts-PDFs. Ein gemeinsamer Baustein, damit nicht jedes PDF seine
 * Farbe selbst festlegt.
 */
@Service
@RequiredArgsConstructor
public class PdfFirmenDesign {

    private final FirmeninformationService firmeninformationService;

    public PdfDesign laden() {
        FirmeninformationDto firma = firmeninformationService.getFirmeninformation();
        String farbe = firma == null ? null : firma.getFirmenfarbe();
        String name = firma == null ? null : firma.getFirmenname();
        return PdfDesign.aus(farbe, firmeninformationService.loadLogoImage(), name);
    }
}
