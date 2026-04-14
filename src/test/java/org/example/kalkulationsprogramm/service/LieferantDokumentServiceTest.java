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
        assertThat(ref.getKostenstelleId()).isEqualTo(4L);
        assertThat(ref.getKostenstelleName()).isEqualTo("Werkstatt");
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

    @Test
    @DisplayName("toDto mappt zugeordnetVon-User korrekt auf zugeordnetVonName und zugeordnetAm")
    void getDokumenteByLieferant_mitZugeordnetVon_korrektGemappt() {
        // Arrange
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(400L);
        lieferant.setLieferantenname("Zuordnung Test GmbH");

        Projekt projekt = new Projekt();
        projekt.setId(10L);
        projekt.setBauvorhaben("Testprojekt Zuordnung");
        projekt.setAuftragsnummer("A-2025-099");

        FrontendUserProfile user = new FrontendUserProfile();
        user.setId(7L);
        user.setDisplayName("Max Mustermann");

        LocalDateTime zugeordnetAm = LocalDateTime.of(2025, 3, 15, 10, 30, 0);

        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(700L);
        dokument.setLieferant(lieferant);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        dokument.setOriginalDateiname("zuordnung_test.pdf");
        dokument.setGespeicherterDateiname("stored_zuordnung_test.pdf");
        dokument.setUploadDatum(LocalDateTime.now());

        LieferantDokumentProjektAnteil anteil = new LieferantDokumentProjektAnteil();
        anteil.setId(200L);
        anteil.setDokument(dokument);
        anteil.setProjekt(projekt);
        anteil.setProzent(100);
        anteil.setBerechneterBetrag(new BigDecimal("500.00"));
        anteil.setZugeordnetVon(user);
        anteil.setZugeordnetAm(zugeordnetAm);

        dokument.setProjektAnteile(Set.of(anteil));
        dokument.setVerknuepfteDokumente(new HashSet<>());

        given(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(400L))
                .willReturn(List.of(dokument));

        // Act
        List<LieferantDokumentDto.Response> result = service.getDokumenteByLieferant(400L, null);

        // Assert
        assertThat(result).hasSize(1);
        LieferantDokumentDto.ProjektAnteilRef ref = result.get(0).getProjektAnteile().get(0);
        assertThat(ref.getZugeordnetVonName()).isEqualTo("Max Mustermann");
        assertThat(ref.getZugeordnetAm()).isEqualTo(zugeordnetAm);
        assertThat(ref.getProjektId()).isEqualTo(10L);
        assertThat(ref.getBerechneterBetrag()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("toDto mappt zugeordnetVonName als null wenn kein User gesetzt")
    void getDokumenteByLieferant_ohneZugeordnetVon_nameIstNull() {
        // Arrange
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(401L);
        lieferant.setLieferantenname("Ohne User Test GmbH");

        Projekt projekt = new Projekt();
        projekt.setId(11L);
        projekt.setBauvorhaben("Testprojekt ohne User");
        projekt.setAuftragsnummer("A-2025-100");

        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(701L);
        dokument.setLieferant(lieferant);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        dokument.setOriginalDateiname("ohne_user.pdf");
        dokument.setGespeicherterDateiname("stored_ohne_user.pdf");
        dokument.setUploadDatum(LocalDateTime.now());

        LieferantDokumentProjektAnteil anteil = new LieferantDokumentProjektAnteil();
        anteil.setId(201L);
        anteil.setDokument(dokument);
        anteil.setProjekt(projekt);
        anteil.setProzent(100);
        anteil.setZugeordnetVon(null); // kein User

        dokument.setProjektAnteile(Set.of(anteil));
        dokument.setVerknuepfteDokumente(new HashSet<>());

        given(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(401L))
                .willReturn(List.of(dokument));

        // Act
        List<LieferantDokumentDto.Response> result = service.getDokumenteByLieferant(401L, null);

        // Assert
        assertThat(result).hasSize(1);
        LieferantDokumentDto.ProjektAnteilRef ref = result.get(0).getProjektAnteile().get(0);
        assertThat(ref.getZugeordnetVonName()).isNull();
        assertThat(ref.getProjektId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("toDto mappt Projekt UND Kostenstelle gleichzeitig korrekt")
    void getDokumenteByLieferant_mitProjektUndKostenstelle_beideGemappt() {
        // Arrange
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(402L);
        lieferant.setLieferantenname("Kombi Test GmbH");

        Projekt projekt = new Projekt();
        projekt.setId(20L);
        projekt.setBauvorhaben("Kombiprojekt");
        projekt.setAuftragsnummer("A-2025-200");

        Kostenstelle kostenstelle = new Kostenstelle();
        kostenstelle.setId(8L);
        kostenstelle.setBezeichnung("Verwaltung");

        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(702L);
        dokument.setLieferant(lieferant);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        dokument.setOriginalDateiname("kombi.pdf");
        dokument.setGespeicherterDateiname("stored_kombi.pdf");
        dokument.setUploadDatum(LocalDateTime.now());

        LieferantDokumentProjektAnteil anteilProjekt = new LieferantDokumentProjektAnteil();
        anteilProjekt.setId(301L);
        anteilProjekt.setDokument(dokument);
        anteilProjekt.setProjekt(projekt);
        anteilProjekt.setKostenstelle(null);
        anteilProjekt.setProzent(60);
        anteilProjekt.setBerechneterBetrag(new BigDecimal("300.00"));

        LieferantDokumentProjektAnteil anteilKostenstelle = new LieferantDokumentProjektAnteil();
        anteilKostenstelle.setId(302L);
        anteilKostenstelle.setDokument(dokument);
        anteilKostenstelle.setProjekt(null);
        anteilKostenstelle.setKostenstelle(kostenstelle);
        anteilKostenstelle.setProzent(40);
        anteilKostenstelle.setBerechneterBetrag(new BigDecimal("200.00"));

        dokument.setProjektAnteile(Set.of(anteilProjekt, anteilKostenstelle));
        dokument.setVerknuepfteDokumente(new HashSet<>());

        given(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(402L))
                .willReturn(List.of(dokument));

        // Act
        List<LieferantDokumentDto.Response> result = service.getDokumenteByLieferant(402L, null);

        // Assert
        assertThat(result).hasSize(1);
        List<LieferantDokumentDto.ProjektAnteilRef> anteile = result.get(0).getProjektAnteile();
        assertThat(anteile).hasSize(2);

        // Projekt-Anteil finden
        LieferantDokumentDto.ProjektAnteilRef projRef = anteile.stream()
                .filter(a -> a.getProjektId() != null)
                .findFirst().orElseThrow();
        assertThat(projRef.getProjektId()).isEqualTo(20L);
        assertThat(projRef.getProjektName()).isEqualTo("Kombiprojekt");
        assertThat(projRef.getAuftragsnummer()).isEqualTo("A-2025-200");
        assertThat(projRef.getKostenstelleId()).isNull();
        assertThat(projRef.getProzent()).isEqualTo(60);

        // Kostenstelle-Anteil finden
        LieferantDokumentDto.ProjektAnteilRef kstRef = anteile.stream()
                .filter(a -> a.getKostenstelleId() != null)
                .findFirst().orElseThrow();
        assertThat(kstRef.getKostenstelleId()).isEqualTo(8L);
        assertThat(kstRef.getKostenstelleName()).isEqualTo("Verwaltung");
        assertThat(kstRef.getProjektId()).isNull();
        assertThat(kstRef.getProzent()).isEqualTo(40);
    }
}
