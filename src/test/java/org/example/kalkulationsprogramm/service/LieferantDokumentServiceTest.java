package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LieferantDokumentServiceTest {

    @Mock
    private LieferantDokumentRepository dokumentRepository;
    @Mock
    private AbteilungDokumentBerechtigungRepository berechtigungRepository;
    @Mock
    private LieferantenRepository lieferantenRepository;
    @Mock
    private ProjektRepository projektRepository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;
    @Mock
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Mock
    private GeminiDokumentAnalyseService geminiService;

    @InjectMocks
    private LieferantDokumentService service;

    @Test
    @DisplayName("getDokumenteByLieferant gibt Dokumente mit Kostenstellen-Zuordnung ohne NPE zurück")
    void getDokumenteByLieferant_mitKostenstelleStattProjekt_keineNPE() {
        // Arrange: Dokument mit ProjektAnteil, der nur Kostenstelle hat (kein Projekt)
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(222L);
        lieferant.setLieferantenname("Test Lieferant GmbH");

        Kostenstelle kostenstelle = new Kostenstelle();
        kostenstelle.setId(4L);
        kostenstelle.setBezeichnung("Werkstatt");

        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(887L);
        dokument.setLieferant(lieferant);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        dokument.setOriginalDateiname("Rechnung_392676.pdf");
        dokument.setGespeicherterDateiname("test_392676.pdf");
        dokument.setUploadDatum(LocalDateTime.now());

        LieferantDokumentProjektAnteil anteil = new LieferantDokumentProjektAnteil();
        anteil.setId(144L);
        anteil.setDokument(dokument);
        anteil.setProjekt(null); // Kein Projekt!
        anteil.setKostenstelle(kostenstelle);
        anteil.setProzent(100);
        anteil.setBerechneterBetrag(new BigDecimal("161.90"));

        dokument.setProjektAnteile(Set.of(anteil));
        dokument.setVerknuepfteDokumente(new HashSet<>());

        given(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(222L))
                .willReturn(List.of(dokument));

        // Act – vorher NPE hier
        List<LieferantDokumentDto.Response> result = service.getDokumenteByLieferant(222L, null);

        // Assert
        assertThat(result).hasSize(1);
        LieferantDokumentDto.Response dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(887L);
        assertThat(dto.getProjektAnteile()).hasSize(1);

        LieferantDokumentDto.ProjektAnteilRef ref = dto.getProjektAnteile().get(0);
        assertThat(ref.getProjektId()).isNull();
        assertThat(ref.getProjektName()).contains("Werkstatt");
        assertThat(ref.getProzent()).isEqualTo(100);
    }

    @Test
    @DisplayName("getDokumenteByLieferant gibt Dokumente mit Projekt-Zuordnung korrekt zurück")
    void getDokumenteByLieferant_mitProjekt_korrekteZuordnung() {
        // Arrange
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(100L);
        lieferant.setLieferantenname("Muster Stahl AG");

        Projekt projekt = new Projekt();
        projekt.setId(95L);
        projekt.setBauvorhaben("Musterprojekt Musterstraße");
        projekt.setAuftragsnummer("A-2025-001");

        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(500L);
        dokument.setLieferant(lieferant);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        dokument.setOriginalDateiname("Rechnung_test.pdf");
        dokument.setGespeicherterDateiname("test.pdf");
        dokument.setUploadDatum(LocalDateTime.now());

        LieferantDokumentProjektAnteil anteil = new LieferantDokumentProjektAnteil();
        anteil.setId(1L);
        anteil.setDokument(dokument);
        anteil.setProjekt(projekt);
        anteil.setProzent(100);

        dokument.setProjektAnteile(Set.of(anteil));
        dokument.setVerknuepfteDokumente(new HashSet<>());

        given(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(100L))
                .willReturn(List.of(dokument));

        // Act
        List<LieferantDokumentDto.Response> result = service.getDokumenteByLieferant(100L, null);

        // Assert
        assertThat(result).hasSize(1);
        LieferantDokumentDto.ProjektAnteilRef ref = result.get(0).getProjektAnteile().get(0);
        assertThat(ref.getProjektId()).isEqualTo(95L);
        assertThat(ref.getProjektName()).isEqualTo("Musterprojekt Musterstraße");
        assertThat(ref.getAuftragsnummer()).isEqualTo("A-2025-001");
    }

    @Test
    @DisplayName("getDokumenteByLieferant filtert Anteile ohne Projekt und ohne Kostenstelle")
    void getDokumenteByLieferant_ohneAlles_wirdGefiltert() {
        // Arrange
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(300L);
        lieferant.setLieferantenname("Test GmbH");

        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(600L);
        dokument.setLieferant(lieferant);
        dokument.setTyp(LieferantDokumentTyp.SONSTIG);
        dokument.setOriginalDateiname("test.pdf");
        dokument.setGespeicherterDateiname("stored_test.pdf");
        dokument.setUploadDatum(LocalDateTime.now());

        LieferantDokumentProjektAnteil anteil = new LieferantDokumentProjektAnteil();
        anteil.setId(999L);
        anteil.setDokument(dokument);
        anteil.setProjekt(null);
        anteil.setKostenstelle(null);
        anteil.setProzent(100);

        dokument.setProjektAnteile(Set.of(anteil));
        dokument.setVerknuepfteDokumente(new HashSet<>());

        given(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(300L))
                .willReturn(List.of(dokument));

        // Act
        List<LieferantDokumentDto.Response> result = service.getDokumenteByLieferant(300L, null);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProjektAnteile()).isEmpty();
    }
}
