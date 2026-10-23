package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Materialkosten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelMengeDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto;
import org.example.kalkulationsprogramm.dto.Projekt.MaterialKilogrammDto;
import org.example.kalkulationsprogramm.mapper.ProjektMapper;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjektManagementServiceTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock
    private ProjektRepository projektRepository;
    @Mock
    private AnfrageNotizRepository anfrageNotizRepository;
    @Mock
    private ProjektNotizRepository projektNotizRepository;
    @Mock
    private ProduktkategorieRepository produktkategorieRepository;
    @Mock
    private KundeRepository kundeRepository;
    @Mock
    private DateiSpeicherService dateiSpeicherService;
    @Mock
    private ProjektMapper projektMapper;
    @Mock
    private AnfrageRepository anfrageRepository;
    @Mock
    private AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    @Mock
    private ZeitbuchungRepository ZeitbuchungRepository;
    @Mock
    private ArtikelRepository artikelRepository;
    @Mock
    private ArtikelInProjektRepository artikelInProjektRepository;
    @Mock
    private ProjektPersistenceService projektPersistenceService;
    @Mock
    private LieferantenRepository lieferantenRepository;
    @Mock
    private EmailRepository emailRepository;
    @Mock
    private org.example.kalkulationsprogramm.repository.KalenderEintragRepository kalenderEintragRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    private ProjektManagementService service;

    @BeforeEach
    void setup() {
        lenient().when(artikelInProjektRepository.sumKilogrammByProjektGroupedByWerkstoff(any()))
                .thenReturn(List.of());
        service = new ProjektManagementService(projektRepository,
                anfrageNotizRepository,
                projektNotizRepository,
                produktkategorieRepository,
                kundeRepository, dateiSpeicherService, projektMapper,
                anfrageRepository, ausgangsGeschaeftsDokumentRepository, ZeitbuchungRepository,
                artikelRepository, artikelInProjektRepository, projektPersistenceService,
                lieferantenRepository, emailRepository, kalenderEintragRepository, eventPublisher);
        service.setAusgangsGeschaeftsDokumentService(ausgangsGeschaeftsDokumentService);
    }

    @Test
    void verfuegbareAnlegeJahreReichtDieJahreAusDemRepositoryDurch() {
        when(projektRepository.findDistinctAnlegedatumJahre()).thenReturn(List.of(2026, 2025, 2024));

        assertEquals(List.of(2026, 2025, 2024), service.verfuegbareAnlegeJahre());
    }

    @Test
    void verfuegbareAnlegeJahreLiefertLeereListeStattNull() {
        // Absicherung des null-Fallbacks: Das Jahres-Dropdown im Frontend erwartet
        // immer eine Liste, nie null.
        when(projektRepository.findDistinctAnlegedatumJahre()).thenReturn(null);

        assertEquals(List.of(), service.verfuegbareAnlegeJahre());
    }

    @Test
    void calculatesPricePerMeterForKgArticle() {
        Projekt projekt = new Projekt();
        projekt.setArtikelInProjekt(new ArrayList<>());
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
        when(projektRepository.save(any(Projekt.class))).thenAnswer(i -> i.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        ArtikelWerkstoffe artikel = new ArtikelWerkstoffe();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        artikel.setMasse(new BigDecimal("2"));
        artikel.setArtikelpreis(new ArrayList<>());
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setPreis(new BigDecimal("5"));
        artikel.getArtikelpreis().add(preis);
        when(artikelRepository.findById(2L)).thenReturn(Optional.of(artikel));

        ArtikelMengeDto dto = new ArtikelMengeDto();
        dto.setArtikelId(2L);
        dto.setMenge(new BigDecimal("3"));
        dto.setEinheit("METER");

        service.fuegeArtikelMaterialkosten(1L, List.of(dto));

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektRepository).save(captor.capture());
        ArtikelInProjekt gespeicherter = captor.getValue().getArtikelInProjekt().getFirst();
        assertEquals(0, gespeicherter.getPreisProStueck().compareTo(new BigDecimal("10")));
        assertEquals(0, gespeicherter.getKilogramm().compareTo(new BigDecimal("6")));
    }

    /**
     * V361 (analog zu V359 auf lieferanten_artikel_preise): Ein Kleinteil-Preis
     * wie 0,0183 EUR je Stueck (1,83 EUR je 100 Schrauben) darf beim Uebernehmen
     * in die Projektposition nicht auf 0,02 EUR gerundet werden - das waeren
     * 9 % Aufschlag auf jede Kalkulation, in der solche Teile vorkommen.
     */
    @Test
    void preisMitVierNachkommastellenWirdBeimUebernehmenInProjektpositionNichtGerundet() {
        Projekt projekt = new Projekt();
        projekt.setArtikelInProjekt(new ArrayList<>());
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
        when(projektRepository.save(any(Projekt.class))).thenAnswer(i -> i.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        artikel.setArtikelpreis(new ArrayList<>());
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setPreis(new BigDecimal("0.0183"));
        preis.setPreisAenderungsdatum(new java.util.Date());
        artikel.getArtikelpreis().add(preis);
        when(artikelRepository.findById(3L)).thenReturn(Optional.of(artikel));

        ArtikelMengeDto dto = new ArtikelMengeDto();
        dto.setArtikelId(3L);
        dto.setEinheit("STUECK");
        dto.setStueckzahl(100);

        service.fuegeArtikelMaterialkosten(1L, List.of(dto));

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektRepository).save(captor.capture());
        ArtikelInProjekt gespeicherter = captor.getValue().getArtikelInProjekt().getFirst();
        assertEquals(0, new BigDecimal("0.0183").compareTo(gespeicherter.getPreisProStueck()),
                "0,0183 EUR je Stueck duerfen nicht auf 0,02 EUR gerundet werden");
    }

    @Test
    void derivesMeterFromPiecesUsingPackagingUnit() {
        Projekt projekt = new Projekt();
        projekt.setArtikelInProjekt(new ArrayList<>());
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
        when(projektRepository.save(any(Projekt.class))).thenAnswer(i -> i.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        ArtikelWerkstoffe artikel = new ArtikelWerkstoffe();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        artikel.setMasse(new BigDecimal("2"));
        artikel.setVerpackungseinheit(6L);
        artikel.setArtikelpreis(new ArrayList<>());
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setPreis(new BigDecimal("5"));
        artikel.getArtikelpreis().add(preis);
        when(artikelRepository.findById(3L)).thenReturn(Optional.of(artikel));

        ArtikelMengeDto dto = new ArtikelMengeDto();
        dto.setArtikelId(3L);
        dto.setMenge(new BigDecimal("4"));
        dto.setEinheit("STUECK");

        service.fuegeArtikelMaterialkosten(1L, List.of(dto));

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektRepository).save(captor.capture());
        ArtikelInProjekt gespeicherter = captor.getValue().getArtikelInProjekt().getFirst();
        assertEquals(4, gespeicherter.getStueckzahl());
        assertEquals(0, gespeicherter.getMeter().compareTo(new BigDecimal("24")));
        assertEquals(0, gespeicherter.getKilogramm().compareTo(new BigDecimal("48")));
    }

    @Test
    void calculatesKilogrammForKgPricedArticleWhenAddingPieces() {
        Projekt projekt = new Projekt();
        projekt.setArtikelInProjekt(new ArrayList<>());
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
        when(projektRepository.save(any(Projekt.class))).thenAnswer(i -> i.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        ArtikelWerkstoffe artikel = new ArtikelWerkstoffe();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        artikel.setMasse(new BigDecimal("0.57"));
        artikel.setVerpackungseinheit(6L);
        artikel.setArtikelpreis(new ArrayList<>());
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setPreis(new BigDecimal("5"));
        artikel.getArtikelpreis().add(preis);
        when(artikelRepository.findById(4L)).thenReturn(Optional.of(artikel));

        ArtikelMengeDto dto = new ArtikelMengeDto();
        dto.setArtikelId(4L);
        dto.setMenge(new BigDecimal("30"));
        dto.setEinheit("STUECK");

        service.fuegeArtikelMaterialkosten(1L, List.of(dto));

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektRepository).save(captor.capture());
        ArtikelInProjekt gespeicherter = captor.getValue().getArtikelInProjekt().getFirst();
        assertEquals(30, gespeicherter.getStueckzahl());
        assertEquals(0, gespeicherter.getMeter().compareTo(new BigDecimal("180")));
        assertEquals(0, gespeicherter.getKilogramm().compareTo(new BigDecimal("102.60")));
        assertEquals(0, gespeicherter.getPreisProStueck().compareTo(new BigDecimal("17.10")));
    }

    // --- Fehlercodes: fehlt etwas → 404 mit Meldung, Konflikt → 409 (siehe RestExceptionHandler) ---

    @Test
    void unbekanntesProjektGibt404MitVerstaendlicherMeldung() {
        when(projektRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException beimOeffnen = assertThrows(ResponseStatusException.class,
                () -> service.findeProjektById(999L));
        ResponseStatusException beimBeenden = assertThrows(ResponseStatusException.class,
                () -> service.setzeAbgeschlossen(999L, true));
        ResponseStatusException beimLoeschen = assertThrows(ResponseStatusException.class,
                () -> service.loescheProjekt(999L));

        for (ResponseStatusException fehler : List.of(beimOeffnen, beimBeenden, beimLoeschen)) {
            assertEquals(HttpStatus.NOT_FOUND, fehler.getStatusCode());
            assertEquals(ProjektManagementService.PROJEKT_NICHT_GEFUNDEN, fehler.getReason());
        }
        verify(projektRepository, never()).save(any());
        verify(projektRepository, never()).delete(any(Projekt.class));
    }

    @Test
    void produktkategorieMitZeitbuchungenLaesstSichNichtLoeschen409() {
        Projekt projekt = new Projekt();
        projekt.setId(5L);
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
        when(ZeitbuchungRepository.existsByProjektProduktkategorieId(8L)).thenReturn(true);

        ResponseStatusException fehler = assertThrows(ResponseStatusException.class,
                () -> service.loescheProjektProduktkategorie(5L, 8L));

        assertEquals(HttpStatus.CONFLICT, fehler.getStatusCode());
        assertEquals("Die Produktkategorie kann nicht gelöscht werden, da bereits Zeitbuchungen dafür existieren.",
                fehler.getReason());
        verify(projektRepository, never()).save(any());
    }

    @Test
    void produktkategorieDieNichtZumProjektGehoertGibt404() {
        Projekt projekt = new Projekt();
        projekt.setId(5L);
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
        when(ZeitbuchungRepository.existsByProjektProduktkategorieId(8L)).thenReturn(false);

        ResponseStatusException fehler = assertThrows(ResponseStatusException.class,
                () -> service.loescheProjektProduktkategorie(5L, 8L));

        assertEquals(HttpStatus.NOT_FOUND, fehler.getStatusCode());
        verify(projektRepository, never()).save(any());
    }

    @Test
    void setzeAbgeschlossenMerktSichDieEntscheidungDesBenutzers() {
        Projekt projekt = new Projekt();
        projekt.setId(5L);
        // Das System hatte das Projekt automatisch beendet.
        projekt.setAbgeschlossen(true);

        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
        when(projektRepository.save(any(Projekt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        service.setzeAbgeschlossen(5L, false);

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektRepository).save(captor.capture());
        Projekt gespeichertesProjekt = captor.getValue();
        assertFalse(gespeichertesProjekt.isAbgeschlossen());
        assertTrue(gespeichertesProjekt.isAbgeschlossenManuell());
    }

    @Test
    void aktualisiereProjektLaesstDenHakenBeendetUnangetastetWennDasFeldFehlt() throws Exception {
        Projekt projekt = new Projekt();
        projekt.setId(6L);
        projekt.setAnlegedatum(LocalDate.of(2024, 1, 1));
        projekt.setKundenId(new Kunde());
        // Automatisch beendet – ein Teil-Update darf das nicht aufreißen.
        projekt.setAbgeschlossen(true);

        when(projektRepository.findById(6L)).thenReturn(Optional.of(projekt));
        when(projektPersistenceService.saveProjektWithRetry(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setBauvorhaben("Dachsanierung Musterweg");
        // abgeschlossen bewusst nicht gesetzt → bleibt null

        service.aktualisiereProjekt(6L, dto, null, null, null, null, null);

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektPersistenceService).saveProjektWithRetry(captor.capture());
        Projekt gespeichertesProjekt = captor.getValue();
        assertTrue(gespeichertesProjekt.isAbgeschlossen());
        assertFalse(gespeichertesProjekt.isAbgeschlossenManuell());
    }

    @Test
    void aktualisiereProjektSetztDasManuellFlagNichtWennDerHakenMitgeschicktWird() throws Exception {
        Projekt projekt = new Projekt();
        projekt.setId(7L);
        projekt.setAnlegedatum(LocalDate.of(2024, 1, 1));
        projekt.setKundenId(new Kunde());
        projekt.setAbgeschlossen(true);

        when(projektRepository.findById(7L)).thenReturn(Optional.of(projekt));
        when(projektPersistenceService.saveProjektWithRetry(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        ProjektErstellenDto dto = new ProjektErstellenDto();
        // Veralteter Formularstand: Das Projekt wurde zwischenzeitlich automatisch beendet.
        dto.setAbgeschlossen(false);

        service.aktualisiereProjekt(7L, dto, null, null, null, null, null);

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektPersistenceService).saveProjektWithRetry(captor.capture());
        // Wert wird übernommen, gilt aber NICHT als bewusste Entscheidung –
        // die Automatik bleibt für dieses Projekt zuständig.
        assertFalse(captor.getValue().isAbgeschlossenManuell());
    }

    @Test
    void supportsFixLengthAndQuantityForProfileCategory() {
        Projekt projekt = new Projekt();
        projekt.setArtikelInProjekt(new ArrayList<>());
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
        when(projektRepository.save(any(Projekt.class))).thenAnswer(i -> i.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        // Kategorie 2 with parent 1 -> treated as meterware/profile
        Kategorie parent = new Kategorie();
        parent.setId(1);
        Kategorie child = new Kategorie();
        child.setId(2);
        child.setParentKategorie(parent);

        ArtikelWerkstoffe artikel = new ArtikelWerkstoffe();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        artikel.setMasse(new BigDecimal("0.5")); // kg per meter
        artikel.setVerpackungseinheit(6L);
        artikel.setKategorie(child);
        artikel.setArtikelpreis(new ArrayList<>());
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setPreis(new BigDecimal("2.00")); // EUR per kg
        artikel.getArtikelpreis().add(preis);
        when(artikelRepository.findById(99L)).thenReturn(Optional.of(artikel));

        ArtikelMengeDto dto = new ArtikelMengeDto();
        dto.setArtikelId(99L);
        dto.setEinheit("STUECK");
        dto.setStueckzahl(3);
        dto.setLaengeProStueck(new BigDecimal("0.35")); // 350mm

        service.fuegeArtikelMaterialkosten(1L, List.of(dto));

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektRepository).save(captor.capture());
        ArtikelInProjekt saved = captor.getValue().getArtikelInProjekt().getFirst();
        // stueckzahl 3
        assertEquals(3, saved.getStueckzahl());
        // meter = 0.35 * 3 = 1.05
        assertEquals(0, saved.getMeter().compareTo(new BigDecimal("1.05")));
        // kg = 0.5 * 1.05 = 0.525
        assertEquals(0, saved.getKilogramm().compareTo(new BigDecimal("0.525")));
        // preis pro Stueck = 2.00 EUR/kg * 0.5 kg/m * 0.35 m = 0.35 EUR
        assertEquals(0, saved.getPreisProStueck().compareTo(new BigDecimal("0.35")));
    }

    @Test
    void ignoresSupplierEntriesWithoutPrice() {
        Projekt projekt = new Projekt();
        projekt.setArtikelInProjekt(new ArrayList<>());
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
        when(projektRepository.save(any(Projekt.class))).thenAnswer(i -> i.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        Artikel artikel = new Artikel();
        artikel.setArtikelpreis(new ArrayList<>());
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(3L);
        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(artikel);
        lap.setLieferant(lieferant);
        artikel.getArtikelpreis().add(lap);
        when(artikelRepository.findById(2L)).thenReturn(Optional.of(artikel));

        ArtikelMengeDto dto = new ArtikelMengeDto();
        dto.setArtikelId(2L);
        dto.setMenge(BigDecimal.ONE);
        dto.setEinheit("STUECK");

        service.fuegeArtikelMaterialkosten(1L, List.of(dto));

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektRepository).save(captor.capture());
        ArtikelInProjekt gespeicherter = captor.getValue().getArtikelInProjekt().getFirst();
        assertNull(gespeicherter.getLieferant());
        assertNull(gespeicherter.getLieferantenArtikelPreis());
    }

    @Test
    void aggregatesKilogrammProMaterial() {
        Projekt projekt = new Projekt();
        projekt.setId(5L);
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
        MaterialKilogrammDto mk = new MaterialKilogrammDto("Stahl", new BigDecimal("4.2"));
        when(artikelInProjektRepository.sumKilogrammByProjektGroupedByWerkstoff(5L))
                .thenReturn(List.of(mk));
        ProjektResponseDto baseDto = new ProjektResponseDto();
        when(projektMapper.toProjektResponseDto(projekt)).thenReturn(baseDto);

        ProjektResponseDto result = service.findeProjektById(5L);

        assertEquals(1, result.getKilogrammProMaterial().size());
        assertEquals("Stahl", result.getKilogrammProMaterial().getFirst().getWerkstoffName());
        assertEquals(0, result.getKilogrammProMaterial().getFirst().getKilogramm()
                .compareTo(new BigDecimal("4.2")));
        assertEquals(0, result.getGesamtKilogramm().compareTo(new BigDecimal("4.2")));
    }

    @Test
    void removesArtikelFromProjektWithoutExplicitDelete() {
        Projekt projekt = new Projekt();
        projekt.setId(7L);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(9L);
        aip.setProjekt(projekt);
        projekt.setArtikelInProjekt(new ArrayList<>(List.of(aip)));

        when(projektRepository.findById(7L)).thenReturn(Optional.of(projekt));
        when(artikelInProjektRepository.findById(9L)).thenReturn(Optional.of(aip));
        when(projektRepository.save(any(Projekt.class))).thenAnswer(i -> i.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        service.entferneArtikelMaterialkosten(7L, 9L);

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektRepository).save(captor.capture());
        assertTrue(captor.getValue().getArtikelInProjekt().isEmpty());
        verify(artikelInProjektRepository, never()).deleteById(anyLong());
    }

    @Test
    void transfersNotesAndImagesFromAnfrageToProjekt() {
        // Given
        Long anfrageId = 1L;
        Long projektId = 100L;
        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setAnfrageIds(List.of(anfrageId));
        dto.setAuftragsnummer("2024/01/00001");

        Anfrage anfrage = new Anfrage();
        anfrage.setId(anfrageId);
        anfrage.setDokumente(new ArrayList<>());
        Kunde kunde = new Kunde();
        kunde.setId(10L);
        anfrage.setKunde(kunde);
        when(kundeRepository.findById(10L)).thenReturn(Optional.of(kunde));

        when(anfrageRepository.findAllById(dto.getAnfrageIds())).thenReturn(List.of(anfrage));

        // Mock Notizen
        AnfrageNotiz notiz = new AnfrageNotiz();
        notiz.setNotiz("Test Notiz");
        notiz.setMobileSichtbar(true);
        notiz.setErstelltAm(java.time.LocalDateTime.now());
        notiz.setMitarbeiter(new Mitarbeiter());

        AnfrageNotizBild bild = new AnfrageNotizBild();
        bild.setOriginalDateiname("test.jpg");
        bild.setGespeicherterDateiname("uuid-test.jpg");
        bild.setErstelltAm(java.time.LocalDateTime.now());
        notiz.setBilder(List.of(bild));

        when(anfrageNotizRepository.findByAnfrageIdOrderByErstelltAmDesc(anfrageId)).thenReturn(List.of(notiz));

        // Bild wird beim Transfer physisch vom bilder- in den dokumenten-Speicherplatz
        // kopiert und bekommt dabei einen neuen Dateinamen.
        when(dateiSpeicherService.kopiereBildZuDokumenten("uuid-test.jpg"))
                .thenReturn("uuid-projekt-test.jpg");

        // Mock Projekt creation
        Projekt projekt = new Projekt();
        projekt.setId(projektId);
        when(projektRepository.findById(projektId)).thenReturn(Optional.of(projekt));
        when(projektPersistenceService.saveProjektWithRetry(any(Projekt.class))).thenReturn(projekt);
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        // When
        try {
            service.erstelleProjekt(dto, null, null, null, null, null, null);
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }

        // Then
        ArgumentCaptor<ProjektNotiz> notizCaptor = ArgumentCaptor.forClass(ProjektNotiz.class);
        verify(projektNotizRepository).save(notizCaptor.capture());

        ProjektNotiz savedNotiz = notizCaptor.getValue();
        assertEquals("Test Notiz", savedNotiz.getNotiz());
        assertTrue(savedNotiz.isMobileSichtbar());
        assertEquals(1, savedNotiz.getBilder().size());
        assertEquals("test.jpg", savedNotiz.getBilder().getFirst().getOriginalDateiname());
        // Neuer Dateiname aus DateiSpeicherService – Datei wurde physisch kopiert und
        // liegt jetzt im Dokumenten-Speicherplatz, damit /api/dokumente/<name> sie findet.
        assertEquals("uuid-projekt-test.jpg",
                savedNotiz.getBilder().getFirst().getGespeicherterDateiname());
        assertEquals(projekt, savedNotiz.getProjekt()); // Verify linkage to new projekt
        verify(dateiSpeicherService).kopiereBildZuDokumenten("uuid-test.jpg");
    }

    /** Anfrage mit Kunde und Beschreibung, wie sie "Von Anfrage übernehmen" findet. */
    private Anfrage anfrageMitBeschreibung(Long id, String bauvorhaben, String beschreibung) {
        Anfrage anfrage = new Anfrage();
        anfrage.setId(id);
        anfrage.setBauvorhaben(bauvorhaben);
        anfrage.setKurzbeschreibung(beschreibung);
        anfrage.setDokumente(new ArrayList<>());
        Kunde kunde = new Kunde();
        kunde.setId(10L);
        kunde.setName("Max Mustermann");
        anfrage.setKunde(kunde);
        return anfrage;
    }

    private Projekt erstelleProjektAusAnfragen(ProjektErstellenDto dto, List<Anfrage> anfragen) {
        stubbeProjektAnlageAusAnfragen(dto, anfragen);

        service.erstelleProjekt(dto, null, null, null, null, null, null);

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektPersistenceService).saveProjektWithRetry(captor.capture());
        return captor.getValue();
    }

    private void stubbeProjektAnlageAusAnfragen(ProjektErstellenDto dto, List<Anfrage> anfragen) {
        when(kundeRepository.findById(10L)).thenReturn(Optional.of(anfragen.getFirst().getKunde()));
        when(anfrageRepository.findAllById(dto.getAnfrageIds())).thenReturn(anfragen);
        when(projektPersistenceService.saveProjektWithRetry(any(Projekt.class)))
                .thenAnswer(inv -> {
                    Projekt p = inv.getArgument(0);
                    p.setId(100L);
                    return p;
                });
        lenient().when(projektRepository.findById(100L)).thenAnswer(inv -> Optional.of(new Projekt()));
        lenient().when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());
    }

    private ProjektErstellenDto projektAusAnfrage(Long anfrageId) {
        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setAnfrageIds(List.of(anfrageId));
        dto.setAuftragsnummer("2026/10/00001");
        return dto;
    }

    @Test
    void erstelleProjektBrichtAbUndBehaeltAnfrageWennAngeboteNichtUmziehen() {
        Anfrage anfrage = anfrageMitBeschreibung(1L, "Geländer Musterweg", "Geländer 12 m");
        ProjektErstellenDto dto = projektAusAnfrage(1L);
        stubbeProjektAnlageAusAnfragen(dto, List.of(anfrage));
        doThrow(new IllegalStateException("Dokumentkette defekt"))
                .when(ausgangsGeschaeftsDokumentService).migrateFromAnfrageToProjekt(eq(1L), any(Projekt.class));

        ResponseStatusException fehler = assertThrows(ResponseStatusException.class,
                () -> service.erstelleProjekt(dto, null, null, null, null, null, null));

        assertEquals(ProjektManagementService.ANFRAGE_UEBERNAHME_FEHLGESCHLAGEN, fehler.getReason());
        verify(anfrageRepository, never()).delete(any(Anfrage.class));
    }

    @Test
    void erstelleProjektBrichtAbWennEmailsNichtUmziehen() {
        Anfrage anfrage = anfrageMitBeschreibung(1L, "Geländer Musterweg", "Geländer 12 m");
        ProjektErstellenDto dto = projektAusAnfrage(1L);
        stubbeProjektAnlageAusAnfragen(dto, List.of(anfrage));
        when(emailRepository.findByAnfrageOrderBySentAtDesc(anfrage))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Verbindung weg"));

        assertThrows(ResponseStatusException.class,
                () -> service.erstelleProjekt(dto, null, null, null, null, null, null));

        verify(anfrageRepository, never()).delete(any(Anfrage.class));
    }

    @Test
    void erstelleProjektBrichtAbWennNotizBildNichtKopiertWerdenKann() {
        Anfrage anfrage = anfrageMitBeschreibung(1L, "Geländer Musterweg", "Geländer 12 m");
        ProjektErstellenDto dto = projektAusAnfrage(1L);
        stubbeProjektAnlageAusAnfragen(dto, List.of(anfrage));
        AnfrageNotiz notiz = new AnfrageNotiz();
        notiz.setNotiz("Aufmaß mit Foto");
        AnfrageNotizBild bild = new AnfrageNotizBild();
        bild.setGespeicherterDateiname("uuid-foto.jpg");
        notiz.setBilder(List.of(bild));
        when(anfrageNotizRepository.findByAnfrageIdOrderByErstelltAmDesc(1L)).thenReturn(List.of(notiz));
        // Datei da, aber Kopieren scheitert (z. B. Platte voll) – das darf nicht still verloren gehen
        when(dateiSpeicherService.kopiereBildZuDokumenten("uuid-foto.jpg"))
                .thenThrow(new RuntimeException("Notiz-Bild konnte nicht kopiert werden: uuid-foto.jpg"));

        assertThrows(ResponseStatusException.class,
                () -> service.erstelleProjekt(dto, null, null, null, null, null, null));

        verify(projektNotizRepository, never()).save(any(ProjektNotiz.class));
        verify(anfrageRepository, never()).delete(any(Anfrage.class));
    }

    @Test
    void erstelleProjektBehaeltPrivateNotizenPrivatUndUebernimmtKontaktAdressen() {
        Anfrage anfrage = anfrageMitBeschreibung(1L, "Geländer Musterweg", "Geländer 12 m");
        anfrage.setKundenEmails(new ArrayList<>(List.of("bauleitung@example.com")));
        ProjektErstellenDto dto = projektAusAnfrage(1L);
        AnfrageNotiz notiz = new AnfrageNotiz();
        notiz.setNotiz("Nur für mich: Kunde zahlt spät");
        notiz.setNurFuerErsteller(true);
        when(anfrageNotizRepository.findByAnfrageIdOrderByErstelltAmDesc(1L)).thenReturn(List.of(notiz));

        Projekt projekt = erstelleProjektAusAnfragen(dto, List.of(anfrage));

        ArgumentCaptor<ProjektNotiz> notizCaptor = ArgumentCaptor.forClass(ProjektNotiz.class);
        verify(projektNotizRepository).save(notizCaptor.capture());
        assertTrue(notizCaptor.getValue().isNurFuerErsteller());
        assertTrue(projekt.getKundenEmails().contains("bauleitung@example.com"));
        verify(anfrageRepository).delete(anfrage);
    }

    @Test
    void erstelleProjektUebernimmtBeschreibungDerAnfrageBevorSieGeloeschtWird() {
        Anfrage anfrage = anfrageMitBeschreibung(1L, "Geländer Musterweg",
                "Text aus dem Webseiten-Formular: Geländer 12 m, verzinkt");
        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setAnfrageIds(List.of(1L));
        dto.setAuftragsnummer("2026/10/00001");

        Projekt projekt = erstelleProjektAusAnfragen(dto, List.of(anfrage));

        assertEquals("Text aus dem Webseiten-Formular: Geländer 12 m, verzinkt", projekt.getKurzbeschreibung());
        verify(anfrageRepository).delete(anfrage);
    }

    @Test
    void erstelleProjektHaengtAnfrageBeschreibungAnEingegebeneBeschreibungAn() {
        Anfrage anfrage = anfrageMitBeschreibung(1L, "Geländer Musterweg", "Geländer 12 m, verzinkt");
        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setAnfrageIds(List.of(1L));
        dto.setAuftragsnummer("2026/10/00001");
        dto.setKurzbeschreibung("Aufmaß am Montag");

        Projekt projekt = erstelleProjektAusAnfragen(dto, List.of(anfrage));

        assertEquals("""
                Aufmaß am Montag
                
                --- Übernommen aus Anfrage: Geländer Musterweg ---
                Geländer 12 m, verzinkt""", projekt.getKurzbeschreibung());
    }

    @Test
    void notizBildWirdUebersprungenWennDateiKopierenFehlschlaegt() {
        // Reproduziert den Bug-Fall, falls die Quelldatei nicht (mehr) existiert:
        // Die Notiz wird trotzdem übernommen, das nicht kopierbare Bild ausgelassen –
        // statt die ganze Notiz-Übernahme abzubrechen.
        Long anfrageId = 1L;
        Long projektId = 100L;
        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setAnfrageIds(List.of(anfrageId));
        dto.setAuftragsnummer("2024/01/00001");

        Anfrage anfrage = new Anfrage();
        anfrage.setId(anfrageId);
        anfrage.setDokumente(new ArrayList<>());
        Kunde kunde = new Kunde();
        kunde.setId(10L);
        anfrage.setKunde(kunde);
        when(kundeRepository.findById(10L)).thenReturn(Optional.of(kunde));
        when(anfrageRepository.findAllById(dto.getAnfrageIds())).thenReturn(List.of(anfrage));

        AnfrageNotiz notiz = new AnfrageNotiz();
        notiz.setNotiz("Notiz mit kaputter Bilddatei");
        notiz.setErstelltAm(java.time.LocalDateTime.now());
        notiz.setMitarbeiter(new Mitarbeiter());
        AnfrageNotizBild bild = new AnfrageNotizBild();
        bild.setOriginalDateiname("weg.jpg");
        bild.setGespeicherterDateiname("uuid-weg.jpg");
        bild.setErstelltAm(java.time.LocalDateTime.now());
        notiz.setBilder(List.of(bild));
        when(anfrageNotizRepository.findByAnfrageIdOrderByErstelltAmDesc(anfrageId)).thenReturn(List.of(notiz));

        when(dateiSpeicherService.kopiereBildZuDokumenten("uuid-weg.jpg"))
                .thenThrow(new NotFoundException("Quelldatei für Notiz-Bild nicht gefunden: uuid-weg.jpg"));

        Projekt projekt = new Projekt();
        projekt.setId(projektId);
        when(projektRepository.findById(projektId)).thenReturn(Optional.of(projekt));
        when(projektPersistenceService.saveProjektWithRetry(any(Projekt.class))).thenReturn(projekt);
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());

        service.erstelleProjekt(dto, null, null, null, null, null, null);

        ArgumentCaptor<ProjektNotiz> notizCaptor = ArgumentCaptor.forClass(ProjektNotiz.class);
        verify(projektNotizRepository).save(notizCaptor.capture());
        ProjektNotiz saved = notizCaptor.getValue();
        assertEquals("Notiz mit kaputter Bilddatei", saved.getNotiz());
        assertTrue(saved.getBilder().isEmpty(),
                "Bild ohne Quelldatei darf nicht mit altem (nicht auflösbarem) Dateinamen gespeichert werden.");
    }

    @Test
    void fuehrtAnfrageVollstaendigMitBestehendemProjektZusammen() {
        Projekt projekt = new Projekt();
        projekt.setId(100L);
        projekt.setKurzbeschreibung("Bestehende Projektbeschreibung");
        Kunde kunde = new Kunde();
        kunde.setId(5L);
        projekt.setKundenId(kunde);

        Anfrage anfrage = new Anfrage();
        anfrage.setId(10L);
        anfrage.setKunde(kunde);
        anfrage.setBauvorhaben("Carport");
        anfrage.setKurzbeschreibung("Beschreibung aus der Anfrage");
        anfrage.setKundenEmails(new ArrayList<>(List.of("projektkontakt@example.de")));
        AnfrageDokument datei = new AnfrageDokument();
        datei.setAnfrage(anfrage);
        anfrage.setDokumente(new ArrayList<>(List.of(datei)));

        Email email = new Email();
        AnfrageNotiz notiz = new AnfrageNotiz();
        notiz.setNotiz("Bautagebuch-Eintrag");
        notiz.setMitarbeiter(new Mitarbeiter());
        notiz.setErstelltAm(java.time.LocalDateTime.now());
        notiz.setNurFuerErsteller(true);
        notiz.setBilder(new ArrayList<>());

        when(projektRepository.findById(100L)).thenReturn(Optional.of(projekt));
        when(anfrageRepository.findById(10L)).thenReturn(Optional.of(anfrage));
        when(ausgangsGeschaeftsDokumentRepository.existsByProjektId(100L)).thenReturn(false);
        when(emailRepository.findByAnfrageOrderBySentAtDesc(anfrage)).thenReturn(List.of(email));
        when(anfrageNotizRepository.findByAnfrageIdOrderByErstelltAmDesc(10L)).thenReturn(List.of(notiz));
        when(projektMapper.toProjektResponseDto(projekt)).thenReturn(new ProjektResponseDto());

        ProjektResponseDto result = service.fuehreAnfrageZusammen(100L, 10L);

        assertNotNull(result);
        assertSame(projekt, email.getProjekt());
        assertNull(email.getAnfrage());
        assertTrue(projekt.getKurzbeschreibung().contains("Beschreibung aus der Anfrage"));
        assertEquals(List.of("projektkontakt@example.de"), projekt.getKundenEmails());
        verify(dateiSpeicherService).verschiebeAnfragesDatei(datei, projekt);
        verify(emailRepository).saveAll(List.of(email));
        verify(ausgangsGeschaeftsDokumentService).migrateFromAnfrageToProjekt(10L, projekt);
        verify(anfrageRepository).delete(anfrage);
        verify(anfrageRepository).flush();

        ArgumentCaptor<ProjektNotiz> notizCaptor = ArgumentCaptor.forClass(ProjektNotiz.class);
        verify(projektNotizRepository).save(notizCaptor.capture());
        assertEquals("Bautagebuch-Eintrag", notizCaptor.getValue().getNotiz());
        assertTrue(notizCaptor.getValue().isNurFuerErsteller());
    }

    @Test
    void lehntZusammenfuehrenBeiVorhandenemAusgangsgeschaeftsdokumentAb() {
        Projekt projekt = new Projekt();
        projekt.setId(100L);
        Kunde kunde = new Kunde();
        kunde.setId(5L);
        projekt.setKundenId(kunde);
        Anfrage anfrage = new Anfrage();
        anfrage.setId(10L);
        anfrage.setKunde(kunde);

        when(projektRepository.findById(100L)).thenReturn(Optional.of(projekt));
        when(anfrageRepository.findById(10L)).thenReturn(Optional.of(anfrage));
        when(ausgangsGeschaeftsDokumentRepository.existsByProjektId(100L)).thenReturn(true);
        // Mischfall: Auch mit Legacy-Datei daneben entscheidet allein die neue Tabelle.
        lenient().when(dateiSpeicherService.holeDokumenteZuProjekt(100L))
                .thenReturn(List.of(new ProjektGeschaeftsdokument()));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.fuehreAnfrageZusammen(100L, 10L));

        assertEquals(
                "Zusammenführen nicht möglich, da im Projekt bereits Ausgangsgeschäftsdokumente vorhanden sind.",
                exception.getMessage());
        verify(dateiSpeicherService, never()).verschiebeAnfragesDatei(any(), any());
        verify(anfrageRepository, never()).delete(any());
    }

    /**
     * Produktentscheidung: Alt-Einträge im Dateien-Tab (nacherfasste Rechnungen,
     * ZUGFeRD-Uploads, Zeichnungen) dürfen das Zusammenführen nicht mehr blockieren.
     * "Rechnung" ist bewusst der schärfste Fall — blockiert der bewusst nicht,
     * blockieren die harmloseren Arten erst recht nicht.
     */
    @Test
    void erlaubtZusammenfuehrenTrotzNacherfassterRechnungImDateienTab() {
        Projekt projekt = new Projekt();
        projekt.setId(100L);
        Kunde kunde = new Kunde();
        kunde.setId(5L);
        projekt.setKundenId(kunde);
        Anfrage anfrage = new Anfrage();
        anfrage.setId(10L);
        anfrage.setKunde(kunde);

        ProjektGeschaeftsdokument nacherfassteRechnung = new ProjektGeschaeftsdokument();
        nacherfassteRechnung.setGeschaeftsdokumentart("Rechnung");

        when(projektRepository.findById(100L)).thenReturn(Optional.of(projekt));
        when(anfrageRepository.findById(10L)).thenReturn(Optional.of(anfrage));
        when(ausgangsGeschaeftsDokumentRepository.existsByProjektId(100L)).thenReturn(false);
        when(projektMapper.toProjektResponseDto(projekt)).thenReturn(new ProjektResponseDto());
        // Lenient, weil der Service diese Liste bewusst nicht mehr abfragt — kehrt der
        // Legacy-Check zurück, wirft der Service und dieser Test wird rot.
        lenient().when(dateiSpeicherService.holeDokumenteZuProjekt(100L))
                .thenReturn(List.of(nacherfassteRechnung));

        assertNotNull(service.fuehreAnfrageZusammen(100L, 10L));

        verify(dateiSpeicherService, never()).holeDokumenteZuProjekt(100L);
        verify(ausgangsGeschaeftsDokumentService).migrateFromAnfrageToProjekt(10L, projekt);
        verify(anfrageRepository).delete(anfrage);
    }

    @Test
    void lehntZusammenfuehrenBeiAnderemKundenAb() {
        Projekt projekt = new Projekt();
        projekt.setId(100L);
        Kunde projektKunde = new Kunde();
        projektKunde.setId(5L);
        projekt.setKundenId(projektKunde);

        Anfrage anfrage = new Anfrage();
        anfrage.setId(10L);
        Kunde anfrageKunde = new Kunde();
        anfrageKunde.setId(6L);
        anfrage.setKunde(anfrageKunde);

        when(projektRepository.findById(100L)).thenReturn(Optional.of(projekt));
        when(anfrageRepository.findById(10L)).thenReturn(Optional.of(anfrage));
        when(ausgangsGeschaeftsDokumentRepository.existsByProjektId(100L)).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.fuehreAnfrageZusammen(100L, 10L));

        assertEquals(
                "Zusammenführen nicht möglich, da Projekt und Anfrage nicht demselben Kunden zugewiesen sind.",
                exception.getMessage());
        verify(anfrageRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------------------------------
    // generiereKundenAuftragsnummer – Auftragsnummer-Logik nach digitaler Angebots-Annahme.
    // Format YYYY/MM/NNNCC: NNN = Kunden-Slot im Jahr, CC = Auftrags-Zähler dieses Kunden im Jahr.
    // ---------------------------------------------------------------------------------------

    @Test
    void generiereKundenAuftragsnummer_neuerKundeOhneVorAuftraege_startetMitErstemSlotUndCcNull() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(42L, "2026/"))
                .thenReturn(List.of());
        when(projektRepository.findAuftragsnummernByYearPrefix("2026/"))
                .thenReturn(List.of());
        when(projektRepository.existsByAuftragsnummer("2026/05/00100")).thenReturn(false);

        String nummer = service.generiereKundenAuftragsnummer(datum, 42L);

        assertEquals("2026/05/00100", nummer);
    }

    @Test
    void generiereKundenAuftragsnummer_bestehenderKundeImSelbenJahr_behaeltSlotUndZaehltCcHoch() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(42L, "2026/"))
                .thenReturn(List.of("2026/01/00100"));
        when(projektRepository.existsByAuftragsnummer("2026/05/00101")).thenReturn(false);

        String nummer = service.generiereKundenAuftragsnummer(datum, 42L);

        assertEquals("2026/05/00101", nummer);
    }

    @Test
    void generiereKundenAuftragsnummer_neuerKundeBeiBelegtenSlots_vergibtNaechstenFreienSlot() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(43L, "2026/"))
                .thenReturn(List.of());
        when(projektRepository.findAuftragsnummernByYearPrefix("2026/"))
                .thenReturn(List.of("2026/01/00100", "2026/05/00101"));
        when(projektRepository.existsByAuftragsnummer("2026/05/00200")).thenReturn(false);

        String nummer = service.generiereKundenAuftragsnummer(datum, 43L);

        assertEquals("2026/05/00200", nummer);
    }

    @Test
    void generiereKundenAuftragsnummer_ohneKundeId_faelltAufAlteFortlaufendeNummerZurueck() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByPrefix("2026/05/"))
                .thenReturn(List.of());

        String nummer = service.generiereKundenAuftragsnummer(datum, null);

        assertEquals("2026/05/00001", nummer);
    }

    @Test
    void generiereKundenAuftragsnummer_ccUeberlauf_faelltAufAlteFortlaufendeNummerZurueck() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(42L, "2026/"))
                .thenReturn(List.of("2026/01/00199"));
        when(projektRepository.findAuftragsnummernByPrefix("2026/05/"))
                .thenReturn(List.of("2026/05/00050"));

        String nummer = service.generiereKundenAuftragsnummer(datum, 42L);

        assertEquals("2026/05/00051", nummer);
    }

    @Test
    void generiereKundenAuftragsnummer_kollidierendeNummer_faelltAufAlteFortlaufendeNummerZurueck() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(42L, "2026/"))
                .thenReturn(List.of());
        when(projektRepository.findAuftragsnummernByYearPrefix("2026/"))
                .thenReturn(List.of());
        when(projektRepository.existsByAuftragsnummer("2026/05/00100")).thenReturn(true);
        when(projektRepository.findAuftragsnummernByPrefix("2026/05/"))
                .thenReturn(List.of("2026/05/00100"));

        String nummer = service.generiereKundenAuftragsnummer(datum, 42L);

        assertEquals("2026/05/00101", nummer);
    }

    @Test
    void generiereKundenAuftragsnummer_nnnUeberlauf_faelltAufAlteFortlaufendeNummerZurueck() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(99L, "2026/"))
                .thenReturn(List.of());
        when(projektRepository.findAuftragsnummernByYearPrefix("2026/"))
                .thenReturn(List.of("2026/01/99900"));
        when(projektRepository.findAuftragsnummernByPrefix("2026/05/"))
                .thenReturn(List.of("2026/05/00010"));

        String nummer = service.generiereKundenAuftragsnummer(datum, 99L);

        assertEquals("2026/05/00011", nummer);
    }

    @Test
    void generiereKundenAuftragsnummer_ignoriertNichtParsbareBestandsnummern() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        // Bestandsdaten in unerwarteten Formaten dürfen die Slot-Vergabe nicht stören.
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(42L, "2026/"))
                .thenReturn(List.of());
        when(projektRepository.findAuftragsnummernByYearPrefix("2026/"))
                .thenReturn(List.of("2026/01/ABCDE", "2026/01/1234", "2026/01/00200"));
        when(projektRepository.existsByAuftragsnummer("2026/05/00300")).thenReturn(false);

        String nummer = service.generiereKundenAuftragsnummer(datum, 42L);

        assertEquals("2026/05/00300", nummer);
    }

    @Test
    void generiereKundenAuftragsnummer_kundeMitAbweichendenSlots_nimmtSlotDerHoechstenNummer() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        // Repository liefert absteigend sortiert. Weichen die Slots eines Kunden voneinander ab
        // (z.B. weil eine Nummer von Hand vergeben wurde), muss die höchste gewinnen — sonst
        // springt der Vorschlag je nach DB-Reihenfolge zwischen den Slots.
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(42L, "2026/"))
                .thenReturn(List.of("2026/03/01201", "2026/01/00700"));
        when(projektRepository.existsByAuftragsnummer("2026/05/01202")).thenReturn(false);

        String nummer = service.generiereKundenAuftragsnummer(datum, 42L);

        assertEquals("2026/05/01202", nummer);
    }

    @Test
    void ermittleKundenAuftragsnummer_neuerKunde_liefertHerleitungFuerHinweistext() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(42L, "2026/"))
                .thenReturn(List.of());
        when(projektRepository.findAuftragsnummernByYearPrefix("2026/"))
                .thenReturn(List.of("2026/01/00100"));
        when(projektRepository.existsByAuftragsnummer("2026/05/00200")).thenReturn(false);

        var vorschlag = service.ermittleKundenAuftragsnummer(datum, 42L);

        assertEquals("2026/05/00200", vorschlag.auftragsnummer());
        assertTrue(vorschlag.kundenLogik());
        assertTrue(vorschlag.neuerKundeImJahr());
        assertEquals(2, vorschlag.kundenSlot());
        assertEquals(1, vorschlag.auftragImJahr());
    }

    @Test
    void ermittleKundenAuftragsnummer_bestandskunde_zaehltAuftragImJahrHoch() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByKundeAndYearPrefix(42L, "2026/"))
                .thenReturn(List.of("2026/03/00701", "2026/01/00700"));
        when(projektRepository.existsByAuftragsnummer("2026/05/00702")).thenReturn(false);

        var vorschlag = service.ermittleKundenAuftragsnummer(datum, 42L);

        assertEquals("2026/05/00702", vorschlag.auftragsnummer());
        assertTrue(vorschlag.kundenLogik());
        assertFalse(vorschlag.neuerKundeImJahr());
        assertEquals(7, vorschlag.kundenSlot());
        // Zwei Aufträge vorhanden → das hier ist der dritte.
        assertEquals(3, vorschlag.auftragImJahr());
    }

    @Test
    void existiertKunde_lehntUngueltigeIdsOhneDatenbankzugriffAb() {
        // Negative Werte und 0 dürfen gar nicht erst zur Datenbank durchgereicht werden.
        assertFalse(service.existiertKunde(null));
        assertFalse(service.existiertKunde(0L));
        assertFalse(service.existiertKunde(-1L));
        verify(kundeRepository, never()).existsById(any());
    }

    @Test
    void existiertKunde_pruefteUnbekannteUndBekannteIdsInDerDatenbank() {
        when(kundeRepository.existsById(Long.MAX_VALUE)).thenReturn(false);
        when(kundeRepository.existsById(42L)).thenReturn(true);

        assertFalse(service.existiertKunde(Long.MAX_VALUE));
        assertTrue(service.existiertKunde(42L));
    }

    @Test
    void ermittleKundenAuftragsnummer_ohneKunde_meldetKeineKundenlogik() {
        LocalDate datum = LocalDate.of(2026, 5, 20);
        when(projektRepository.findAuftragsnummernByPrefix("2026/05/"))
                .thenReturn(List.of());

        var vorschlag = service.ermittleKundenAuftragsnummer(datum, null);

        assertEquals("2026/05/00001", vorschlag.auftragsnummer());
        assertFalse(vorschlag.kundenLogik());
        assertEquals(0, vorschlag.kundenSlot());
        assertEquals(0, vorschlag.auftragImJahr());
    }
}

@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "file.mail-attachment-dir=attachments" })
class ProjektManagementServiceIntegrationTest {

    @Autowired
    private ProjektManagementService service;
    @Autowired
    private ProjektRepository projektRepository;
    @Autowired
    private ProduktkategorieRepository produktkategorieRepository;
    @Autowired
    private ArbeitsgangRepository arbeitsgangRepository;
    @Autowired
    private ArbeitsgangStundensatzRepository stundensatzRepository;
    @Autowired
    private ZeitbuchungRepository ZeitbuchungRepository;
    @Autowired
    private KundeRepository kundeRepository;
    @Autowired
    private AbteilungRepository abteilungRepository;
    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Test
    void savingWithoutChangesKeepsExistingData() throws Exception {
        Produktkategorie pk = new Produktkategorie();
        pk.setBezeichnung("Kategorie");
        pk.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        produktkategorieRepository.save(pk);

        Kunde kunde = createIntegrationKunde("KNR");

        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Bau");
        projekt.setAuftragsnummer("A-1");
        projekt.setAnlegedatum(java.time.LocalDate.now());
        projekt.setBruttoPreis(java.math.BigDecimal.ONE);
        projekt.setBezahlt(false);
        projekt.setKundenId(kunde);

        ProjektProduktkategorie ppk = new ProjektProduktkategorie();
        ppk.setProjekt(projekt);
        ppk.setProduktkategorie(pk);
        ppk.setMenge(java.math.BigDecimal.ONE);
        projekt.getProjektProduktkategorien().add(ppk);

        Arbeitsgang ag = new Arbeitsgang();
        ag.setBeschreibung("AG");
        Abteilung abteilung = new Abteilung();
        abteilung.setName("Test Abteilung");
        abteilungRepository.save(abteilung);
        ag.setAbteilung(abteilung);
        arbeitsgangRepository.save(ag);
        ArbeitsgangStundensatz satz = new ArbeitsgangStundensatz();
        satz.setArbeitsgang(ag);
        satz.setJahr(projekt.getAnlegedatum().getYear());
        satz.setSatz(java.math.BigDecimal.ONE);
        stundensatzRepository.save(satz);

        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setVorname("Test");
        mitarbeiter.setNachname("User");
        mitarbeiterRepository.save(mitarbeiter);

        Zeitbuchung zip = new Zeitbuchung();
        zip.setProjekt(projekt);
        zip.setMitarbeiter(mitarbeiter);
        zip.setStartZeit(java.time.LocalDateTime.now());
        zip.setArbeitsgang(ag);
        zip.setProjektProduktkategorie(ppk);
        zip.setAnzahlInStunden(java.math.BigDecimal.ONE);
        zip.setArbeitsgangStundensatz(satz);
        projekt.getZeitbuchungen().add(zip);

        Materialkosten mk = new Materialkosten();
        mk.setProjekt(projekt);
        mk.setBeschreibung("Mat");
        mk.setBetrag(java.math.BigDecimal.ONE);
        projekt.getMaterialkosten().add(mk);

        projektRepository.save(projekt);

        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setKundenId(kunde.getId());
        service.aktualisiereProjekt(projekt.getId(), dto, null, null, null, null, null);

        ProjektResponseDto geladen = service.findeProjektById(projekt.getId());
        assertEquals(1, geladen.getProduktkategorien().size());
        assertEquals(1, geladen.getZeiten().size());
        assertEquals(1, geladen.getMaterialkosten().size());
    }

    private Kunde createIntegrationKunde(String kundennummer) {
        Kunde kunde = new Kunde();
        kunde.setKundennummer(kundennummer);
        kunde.setName("Kunde " + kundennummer);
        return kundeRepository.save(kunde);
    }
}
