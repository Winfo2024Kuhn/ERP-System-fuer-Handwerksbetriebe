package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.example.kalkulationsprogramm.repository.KassenzaehlungRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.test.util.ReflectionTestUtils;

/** Erzeugt das Kassenbuch eines leeren Monats mit OpenPDF und prüft Datei-Kopf und Inhalt. Nur Dummy-Daten. */
@ExtendWith(MockitoExtension.class)
class BelegeKasseExportPdfServiceTest {

    @Mock
    private BelegRepository belegRepository;
    @Mock
    private FirmeninformationRepository firmeninformationRepository;
    @Mock
    private KassenbuchMonatsabschlussRepository abschlussRepository;
    @Mock
    private KassenzaehlungRepository zaehlungRepository;
    @Mock
    private BelegAuditChainVerifier verifier;

    @InjectMocks
    private BelegeKasseExportPdfService service;

    @TempDir
    private Path uploads;

    @Test
    void erzeugtLesbaresPdf17FuerLeerenMonat() throws Exception {
        ReflectionTestUtils.setField(service, "uploadPath", uploads.toString());
        when(belegRepository.findGeprueftImZeitraumNachNummer(any(), any())).thenReturn(List.of());
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        when(abschlussRepository.findByJahrAndMonat(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(zaehlungRepository.findByStichtagBetweenOrderByStichtagAscIdAsc(any(), any())).thenReturn(List.of());
        BelegAuditChainVerifier.Bericht bericht = mock(BelegAuditChainVerifier.Bericht.class);
        when(bericht.isIntakt()).thenReturn(true);
        when(verifier.verify()).thenReturn(bericht);
        Mitarbeiter ersteller = new Mitarbeiter();
        ersteller.setVorname("Max");
        ersteller.setNachname("Mustermann");

        Path datei = service.generatePdf(2026, 3, ersteller, false);

        byte[] pdf = Files.readAllBytes(datei);
        assertThat(new String(pdf, 0, 9, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-1.7\n");
        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(text).contains("Max Mustermann");
    }
}
