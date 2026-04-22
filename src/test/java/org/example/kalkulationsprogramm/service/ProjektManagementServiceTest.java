package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.BestellQuelle;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Materialkosten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelMengeDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto;
import org.example.kalkulationsprogramm.dto.Projekt.MaterialKilogrammDto;
import org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto;
import org.example.kalkulationsprogramm.mapper.ProjektMapper;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
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
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.context.ApplicationEventPublisher;
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
    private ArbeitsgangRepository arbeitsgangRepository;
    @Mock
    private KundeRepository kundeRepository;
    @Mock
    private DateiSpeicherService dateiSpeicherService;
    @Mock
    private ProjektMapper projektMapper;
    @Mock
    private AnfrageRepository anfrageRepository;
    @Mock
    private ZeitbuchungRepository ZeitbuchungRepository;
    @Mock
    private ArbeitsgangStundensatzRepository stundensatzRepository;
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
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;
    @Mock
    private SchnittbilderRepository schnittbilderRepository;

    private ProjektManagementService service;

    @BeforeEach
    void setup() {
        when(artikelInProjektRepository.sumKilogrammByProjektGroupedByWerkstoff(any()))
                .thenReturn(List.of());
        service = new ProjektManagementService(projektRepository,
                anfrageNotizRepository,
                projektNotizRepository,
                produktkategorieRepository,
                arbeitsgangRepository, kundeRepository, dateiSpeicherService, projektMapper,
                anfrageRepository, ZeitbuchungRepository, stundensatzRepository,
                artikelRepository, artikelInProjektRepository, projektPersistenceService,
                lieferantenRepository, emailRepository, schnittbilderRepository, eventPublisher);
        service.setAusgangsGeschaeftsDokumentService(ausgangsGeschaeftsDokumentService);
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
        // preisProStueck ist nach A2 bei OFFEN bewusst null (kommt später aus Rechnung)
        assertEquals(0, gespeicherter.getKilogramm().compareTo(new BigDecimal("6")));
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
        // preisProStueck ist nach A2 bei OFFEN bewusst null (kommt später aus Rechnung)
    }

    @Test
    void cleansUpDuplicateZeitEntriesBeforeSaving() throws Exception {
        Projekt projekt = new Projekt();
        projekt.setId(1L);
        projekt.setAnlegedatum(LocalDate.of(2024, 1, 1));
        projekt.setKundenId(new Kunde());

        Produktkategorie produktkategorie = new Produktkategorie();
        produktkategorie.setId(10L);

        ProjektProduktkategorie projektProduktkategorie = new ProjektProduktkategorie();
        projektProduktkategorie.setId(20L);
        projektProduktkategorie.setProjekt(projekt);
        projektProduktkategorie.setProduktkategorie(produktkategorie);
        projekt.getProjektProduktkategorien().add(projektProduktkategorie);

        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setId(30L);

        ArbeitsgangStundensatz stundensatz = new ArbeitsgangStundensatz();
        stundensatz.setId(40L);

        Zeitbuchung ersterEintrag = new Zeitbuchung();
        ersterEintrag.setProjekt(projekt);
        ersterEintrag.setProjektProduktkategorie(projektProduktkategorie);
        ersterEintrag.setArbeitsgang(arbeitsgang);
        ersterEintrag.setAnzahlInStunden(new BigDecimal("1.50"));
        ersterEintrag.setArbeitsgangStundensatz(stundensatz);
        ersterEintrag.setId(100L);

        Zeitbuchung doppelterEintrag = new Zeitbuchung();
        doppelterEintrag.setProjekt(projekt);
        doppelterEintrag.setProjektProduktkategorie(projektProduktkategorie);
        doppelterEintrag.setArbeitsgang(arbeitsgang);
        doppelterEintrag.setAnzahlInStunden(new BigDecimal("2.25"));
        doppelterEintrag.setArbeitsgangStundensatz(stundensatz);
        doppelterEintrag.setId(101L);

        projekt.getZeitbuchungen().add(ersterEintrag);
        projekt.getZeitbuchungen().add(doppelterEintrag);

        when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
        when(projektPersistenceService.saveProjektWithRetry(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());
        when(arbeitsgangRepository.findById(arbeitsgang.getId())).thenReturn(Optional.of(arbeitsgang));
        when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(eq(arbeitsgang.getId()), anyInt()))
                .thenReturn(Optional.of(stundensatz));

        ProjektErstellenDto dto = new ProjektErstellenDto();
        ZeitErfassenDto zeitDto = new ZeitErfassenDto();
        zeitDto.setArbeitsgangID(arbeitsgang.getId());
        zeitDto.setProduktkategorieID(produktkategorie.getId());
        zeitDto.setAnzahlInStunden(new BigDecimal("4.75"));
        dto.setZeitPositionen(List.of(zeitDto));

        ProjektResponseDto response = service.aktualisiereProjekt(1L, dto, "Teststrasse", "12345", "Testort", null,
                null);

        assertNotNull(response);
        ArgumentCaptor<Projekt> projektCaptor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektPersistenceService).saveProjektWithRetry(projektCaptor.capture());
        Projekt gespeichertesProjekt = projektCaptor.getValue();
        assertEquals(1, gespeichertesProjekt.getZeitbuchungen().size());
        Zeitbuchung gespeicherteZeit = gespeichertesProjekt.getZeitbuchungen().getFirst();
        assertSame(ersterEintrag, gespeicherteZeit);
        assertEquals(0, gespeicherteZeit.getAnzahlInStunden().compareTo(new BigDecimal("4.75")));

        assertFalse(gespeichertesProjekt.getZeitbuchungen().contains(doppelterEintrag));

    }

    @Test
    void reusesPersistedZeitEntryIfNotLoadedInProjekt() throws Exception {
        Projekt projekt = new Projekt();
        projekt.setId(3L);
        projekt.setAnlegedatum(LocalDate.of(2024, 1, 1));
        projekt.setKundenId(new Kunde());

        Produktkategorie produktkategorie = new Produktkategorie();
        produktkategorie.setId(66L);

        ProjektProduktkategorie projektProduktkategorie = new ProjektProduktkategorie();
        projektProduktkategorie.setId(77L);
        projektProduktkategorie.setProjekt(projekt);
        projektProduktkategorie.setProduktkategorie(produktkategorie);
        projekt.getProjektProduktkategorien().add(projektProduktkategorie);

        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setId(88L);

        ArbeitsgangStundensatz stundensatz = new ArbeitsgangStundensatz();
        stundensatz.setId(99L);

        Zeitbuchung vorhandeneZeit = new Zeitbuchung();
        vorhandeneZeit.setId(123L);
        vorhandeneZeit.setProjekt(projekt);
        vorhandeneZeit.setProjektProduktkategorie(projektProduktkategorie);
        vorhandeneZeit.setArbeitsgang(arbeitsgang);
        vorhandeneZeit.setAnzahlInStunden(new BigDecimal("2.00"));
        vorhandeneZeit.setArbeitsgangStundensatz(stundensatz);

        when(projektRepository.findById(3L)).thenReturn(Optional.of(projekt));
        when(projektPersistenceService.saveProjektWithRetry(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());
        when(arbeitsgangRepository.findById(arbeitsgang.getId())).thenReturn(Optional.of(arbeitsgang));
        when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(eq(arbeitsgang.getId()), anyInt()))
                .thenReturn(Optional.of(stundensatz));
        when(ZeitbuchungRepository.findByProjektIdAndArbeitsgangIdAndProjektProduktkategorieId(
                projekt.getId(), arbeitsgang.getId(), projektProduktkategorie.getId()))
                .thenReturn(Optional.of(vorhandeneZeit));

        ProjektErstellenDto dto = new ProjektErstellenDto();
        ZeitErfassenDto zeitDto = new ZeitErfassenDto();
        zeitDto.setArbeitsgangID(arbeitsgang.getId());
        zeitDto.setProduktkategorieID(produktkategorie.getId());
        zeitDto.setAnzahlInStunden(new BigDecimal("5.00"));
        dto.setZeitPositionen(List.of(zeitDto));

        ProjektResponseDto response = service.aktualisiereProjekt(3L, dto, null, null, null, null, null);

        assertNotNull(response);
        verify(ZeitbuchungRepository).findByProjektIdAndArbeitsgangIdAndProjektProduktkategorieId(
                projekt.getId(), arbeitsgang.getId(), projektProduktkategorie.getId());

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektPersistenceService).saveProjektWithRetry(captor.capture());
        Projekt gespeichertesProjekt = captor.getValue();
        assertEquals(1, gespeichertesProjekt.getZeitbuchungen().size());
        Zeitbuchung gespeicherteZeit = gespeichertesProjekt.getZeitbuchungen().getFirst();
        assertSame(vorhandeneZeit, gespeicherteZeit);
        assertEquals(0, gespeicherteZeit.getAnzahlInStunden().compareTo(new BigDecimal("5.00")));
    }

    @Test
    void mergesDuplicateZeitDtosIntoSingleEntry() throws Exception {
        Projekt projekt = new Projekt();
        projekt.setId(2L);
        projekt.setAnlegedatum(LocalDate.of(2024, 1, 1));
        projekt.setKundenId(new Kunde());

        Produktkategorie produktkategorie = new Produktkategorie();
        produktkategorie.setId(11L);

        ProjektProduktkategorie projektProduktkategorie = new ProjektProduktkategorie();
        projektProduktkategorie.setId(21L);
        projektProduktkategorie.setProjekt(projekt);
        projektProduktkategorie.setProduktkategorie(produktkategorie);
        projekt.getProjektProduktkategorien().add(projektProduktkategorie);

        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setId(31L);

        ArbeitsgangStundensatz stundensatz = new ArbeitsgangStundensatz();
        stundensatz.setId(41L);

        when(projektRepository.findById(2L)).thenReturn(Optional.of(projekt));
        when(projektPersistenceService.saveProjektWithRetry(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());
        when(arbeitsgangRepository.findById(arbeitsgang.getId())).thenReturn(Optional.of(arbeitsgang));
        when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(eq(arbeitsgang.getId()), anyInt()))
                .thenReturn(Optional.of(stundensatz));

        ProjektErstellenDto dto = new ProjektErstellenDto();
        ZeitErfassenDto erster = new ZeitErfassenDto();
        erster.setArbeitsgangID(arbeitsgang.getId());
        erster.setProduktkategorieID(produktkategorie.getId());
        erster.setAnzahlInStunden(new BigDecimal("1.25"));

        ZeitErfassenDto zweiter = new ZeitErfassenDto();
        zweiter.setArbeitsgangID(arbeitsgang.getId());
        zweiter.setProduktkategorieID(produktkategorie.getId());
        zweiter.setAnzahlInStunden(new BigDecimal("0.75"));

        dto.setZeitPositionen(List.of(erster, zweiter));

        ProjektResponseDto response = service.aktualisiereProjekt(2L, dto, null, null, null, null, null);

        assertNotNull(response);
        ArgumentCaptor<Projekt> projektCaptor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektPersistenceService).saveProjektWithRetry(projektCaptor.capture());
        Projekt gespeichertesProjekt = projektCaptor.getValue();
        assertEquals(1, gespeichertesProjekt.getZeitbuchungen().size());
        Zeitbuchung gespeicherteZeit = gespeichertesProjekt.getZeitbuchungen().getFirst();
        assertEquals(0, gespeicherteZeit.getAnzahlInStunden().compareTo(new BigDecimal("2.00")));
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
        // preisProStueck ist nach A2 bei OFFEN bewusst null (kommt später aus Rechnung)
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
        // Nach Stufe A2: AiP hat keinen Lieferanten und keinen
        // LieferantenArtikelPreis mehr — preisProStueck bleibt null, weil
        // Lager-Haken in diesem Zweig nicht gesetzt wird.
        assertNull(gespeicherter.getPreisProStueck());
        assertEquals(BestellQuelle.OFFEN, gespeicherter.getQuelle());
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
        assertEquals("uuid-test.jpg", savedNotiz.getBilder().getFirst().getGespeicherterDateiname()); // Verify filename
                                                                                                  // reuse
        assertEquals(projekt, savedNotiz.getProjekt()); // Verify linkage to new projekt
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

    @Test
    void removingZeitEntryReliesOnOrphanRemoval() throws Exception {
        Produktkategorie pk = new Produktkategorie();
        pk.setBezeichnung("Kategorie");
        pk.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        produktkategorieRepository.save(pk);

        Kunde kunde = createIntegrationKunde("KNR-2");

        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Bau");
        projekt.setAuftragsnummer("A-2");
        projekt.setAnlegedatum(java.time.LocalDate.now());
        projekt.setBruttoPreis(java.math.BigDecimal.TEN);
        projekt.setBezahlt(false);
        projekt.setKundenId(kunde);

        ProjektProduktkategorie ppk = new ProjektProduktkategorie();
        ppk.setProjekt(projekt);
        ppk.setProduktkategorie(pk);
        ppk.setMenge(java.math.BigDecimal.ONE);
        projekt.getProjektProduktkategorien().add(ppk);

        Arbeitsgang arbeitsgang = new Arbeitsgang();
        arbeitsgang.setBeschreibung("Arbeitsgang");
        Abteilung abteilung = new Abteilung();
        abteilung.setName("Test Abteilung 2");
        abteilungRepository.save(abteilung);
        arbeitsgang.setAbteilung(abteilung);
        arbeitsgangRepository.save(arbeitsgang);

        ArbeitsgangStundensatz stundensatz = new ArbeitsgangStundensatz();
        stundensatz.setArbeitsgang(arbeitsgang);
        stundensatz.setJahr(projekt.getAnlegedatum().getYear());
        stundensatz.setSatz(new java.math.BigDecimal("75"));
        stundensatzRepository.save(stundensatz);

        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setVorname("Test2");
        mitarbeiter.setNachname("User2");
        mitarbeiterRepository.save(mitarbeiter);

        Zeitbuchung zeit = new Zeitbuchung();
        zeit.setProjekt(projekt);
        zeit.setMitarbeiter(mitarbeiter);
        zeit.setStartZeit(java.time.LocalDateTime.now());
        zeit.setArbeitsgang(arbeitsgang);
        zeit.setProjektProduktkategorie(ppk);
        zeit.setArbeitsgangStundensatz(stundensatz);
        zeit.setAnzahlInStunden(new java.math.BigDecimal("2"));
        projekt.getZeitbuchungen().add(zeit);

        Projekt gespeichertesProjekt = projektRepository.save(projekt);

        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setZeitPositionen(Collections.emptyList());
        dto.setKundenId(kunde.getId());

        ProjektResponseDto response = service.aktualisiereProjekt(gespeichertesProjekt.getId(), dto, null, null, null,
                null, null);

        assertNotNull(response);
        Projekt nachUpdate = projektRepository.findWithZeitenInProjektById(gespeichertesProjekt.getId())
                .orElseThrow();
        assertTrue(nachUpdate.getZeitbuchungen().isEmpty());
        assertEquals(0, ZeitbuchungRepository.count());
    }

    private Kunde createIntegrationKunde(String kundennummer) {
        Kunde kunde = new Kunde();
        kunde.setKundennummer(kundennummer);
        kunde.setName("Kunde " + kundennummer);
        return kundeRepository.save(kunde);
    }
}
