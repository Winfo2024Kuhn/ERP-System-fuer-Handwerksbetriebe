package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.LieferantReklamation;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantBildRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.LieferantDateiablage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/** Reklamationsfotos kommen vom Handy und landen später im Browser des Büros – nur echte Fotos. */
@ExtendWith(MockitoExtension.class)
class LieferantReklamationBildUploadTest {

    @Mock private LieferantReklamationRepository reklamationRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private LieferantDokumentRepository dokumentRepository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;
    @Mock private LieferantBildRepository bildRepository;
    @Mock private LieferantDateiablage dateiablage;
    @InjectMocks private LieferantReklamationController controller;

    @ParameterizedTest
    @ValueSource(strings = { "image/svg+xml", "text/html", "application/pdf", "application/octet-stream" })
    void keineSvgHtmlOderSonstigenDateien(String typ) throws Exception {
        var lieferant = new Lieferanten();
        lieferant.setId(3L);
        var reklamation = new LieferantReklamation();
        reklamation.setLieferant(lieferant);
        given(reklamationRepository.findById(5L)).willReturn(Optional.of(reklamation));

        var datei = new MockMultipartFile("datei", "../../angriff.svg", typ, "<svg onload=alert(1)>".getBytes());
        assertThat(controller.uploadBild(5L, datei, null).getStatusCode().value()).isEqualTo(400);

        verify(bildRepository, never()).save(any());
        verify(dateiablage, never()).speichereBild(any(), any(), any());
    }

    /**
     * Reklamationsfotos landen im Bilder-Ordner des Lieferanten unter dem konfigurierten
     * upload.path – dort, wo die Bild-Endpunkte im LieferantenController sie suchen.
     */
    @Test
    void fotoLandetImBilderOrdnerDesLieferanten(@TempDir Path ablageOrdner) throws Exception {
        var echt = new LieferantDateiablage(ablageOrdner.toString());
        given(dateiablage.speichereBild(any(), any(), any())).willAnswer(
                aufruf -> echt.speichereBild(aufruf.getArgument(0), aufruf.getArgument(1), aufruf.getArgument(2)));
        var lieferant = new Lieferanten();
        lieferant.setId(3L);
        var reklamation = new LieferantReklamation();
        reklamation.setLieferant(lieferant);
        given(reklamationRepository.findById(5L)).willReturn(Optional.of(reklamation));
        given(bildRepository.save(any())).willAnswer(aufruf -> aufruf.getArgument(0));

        var datei = new MockMultipartFile("datei", "../../Kratzer", "image/jpeg", new byte[] { 1, 2, 3 });
        var antwort = controller.uploadBild(5L, datei, null);

        assertThat(antwort.getStatusCode().value()).isEqualTo(201);
        assertThat(antwort.getBody().originalDateiname).isEqualTo("Kratzer");
        Path bilder = ablageOrdner.resolve("lieferanten").resolve("3").resolve("bilder");
        try (var dateien = Files.list(bilder)) {
            var gespeichert = dateien.toList();
            assertThat(gespeichert).singleElement()
                    .satisfies(pfad -> assertThat(pfad.getFileName().toString()).endsWith("_Kratzer.jpg"))
                    .satisfies(pfad -> assertThat(pfad).hasBinaryContent(new byte[] { 1, 2, 3 }));
        }
    }

    @Test
    void unbekannteReklamation404() {
        given(reklamationRepository.findById(9L)).willReturn(Optional.empty());
        var datei = new MockMultipartFile("datei", "foto.jpg", "image/jpeg", new byte[] { 1 });
        assertThat(controller.uploadBild(9L, datei, null).getStatusCode().value()).isEqualTo(404);
    }
}
