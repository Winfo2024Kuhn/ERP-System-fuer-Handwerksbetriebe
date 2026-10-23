package org.example.kalkulationsprogramm.service.miete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository;
import org.example.kalkulationsprogramm.repository.miete.RaumRepository;
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository;
import org.example.kalkulationsprogramm.repository.miete.ZaehlerstandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

/** Erzeugt das Erfassungsblatt mit OpenPDF und prüft Datei-Kopf und Inhalt. Nur Dummy-Daten. */
@ExtendWith(MockitoExtension.class)
class ZaehlerstandErfassungsPdfServiceTest {

    @Mock
    private MietobjektRepository mietobjektRepository;
    @Mock
    private RaumRepository raumRepository;
    @Mock
    private VerbrauchsgegenstandRepository verbrauchsgegenstandRepository;
    @Mock
    private ZaehlerstandRepository zaehlerstandRepository;

    @InjectMocks
    private ZaehlerstandErfassungsPdfService service;

    @Test
    void erzeugtLesbaresPdf17OhneRaeume() throws Exception {
        Mietobjekt objekt = new Mietobjekt();
        objekt.setName("Musterhaus");
        objekt.setStrasse("Musterweg 7");
        objekt.setPlz("54321");
        objekt.setOrt("Beispielstadt");
        when(mietobjektRepository.findById(1L)).thenReturn(Optional.of(objekt));
        when(raumRepository.findByMietobjektOrderByNameAsc(any())).thenReturn(List.of());

        byte[] pdf = service.generatePdf(1L, 2026);

        assertThat(new String(pdf, 0, 9, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-1.7\n");
        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(text).contains("keine Räume angelegt");
    }
}
