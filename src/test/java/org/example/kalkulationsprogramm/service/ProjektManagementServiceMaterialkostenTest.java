package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Materialkosten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektArt;
import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenErfassenDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto;
import org.example.kalkulationsprogramm.mapper.ProjektMapper;
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Manuell erfasste Materialkosten: Erfassen hängt an, Entfernen nimmt genau einen
 * Eintrag heraus – und der Lieferanten-Zähler läuft passend mit.
 * Dazu: Bearbeiten ohne Projektart lässt die gespeicherte Projektart stehen.
 */
@ExtendWith(MockitoExtension.class)
class ProjektManagementServiceMaterialkostenTest {

    @Mock private ProjektRepository projektRepository;
    @Mock private AnfrageNotizRepository anfrageNotizRepository;
    @Mock private ProjektNotizRepository projektNotizRepository;
    @Mock private ProduktkategorieRepository produktkategorieRepository;
    @Mock private KundeRepository kundeRepository;
    @Mock private DateiSpeicherService dateiSpeicherService;
    @Mock private ProjektMapper projektMapper;
    @Mock private AnfrageRepository anfrageRepository;
    @Mock private AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private ArtikelRepository artikelRepository;
    @Mock private ArtikelInProjektRepository artikelInProjektRepository;
    @Mock private ProjektPersistenceService projektPersistenceService;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private KalenderEintragRepository kalenderEintragRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ProjektManagementService service;

    @BeforeEach
    void setup() {
        lenient().when(artikelInProjektRepository.sumKilogrammByProjektGroupedByWerkstoff(any()))
                .thenReturn(List.of());
        lenient().when(projektRepository.save(any(Projekt.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(projektMapper.toProjektResponseDto(any())).thenReturn(new ProjektResponseDto());
        service = new ProjektManagementService(projektRepository, anfrageNotizRepository,
                projektNotizRepository, produktkategorieRepository, kundeRepository, dateiSpeicherService,
                projektMapper, anfrageRepository, ausgangsGeschaeftsDokumentRepository, zeitbuchungRepository,
                artikelRepository, artikelInProjektRepository, projektPersistenceService,
                lieferantenRepository, emailRepository, kalenderEintragRepository, eventPublisher);
    }

    // ==================== Erfassen ====================

    @Test
    void erfassenHaengtAnUndLaesstVorhandeneEintraegeStehen() {
        Lieferanten alterLieferant = lieferant(1L, 3);
        Projekt projekt = projektMit(eintrag(10L, "Kleinmaterial", "12.50", alterLieferant),
                eintrag(11L, "Schrauben", "4.20", null));
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        service.fuegeMaterialkostenHinzu(5L, dto("Dübel", "7.99"));

        List<Materialkosten> liste = gespeichertesProjekt().getMaterialkosten();
        assertEquals(3, liste.size());
        assertEquals(List.of("Kleinmaterial", "Schrauben", "Dübel"),
                liste.stream().map(Materialkosten::getBeschreibung).toList());
        Materialkosten neu = liste.get(2);
        assertSame(projekt, neu.getProjekt());
        assertEquals(0, neu.getBetrag().compareTo(new BigDecimal("7.99")));
        // Der Zähler eines älteren Eintrags bleibt, wie er war.
        assertEquals(3, alterLieferant.getBestellungen());
        verify(lieferantenRepository, never()).save(alterLieferant);
    }

    @Test
    void erfassenUebernimmtAlleFelderUndZaehltDenLieferantenHoch() {
        Projekt projekt = projektMit();
        Lieferanten lieferant = lieferant(9L, 2);
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
        when(lieferantenRepository.findById(9L)).thenReturn(Optional.of(lieferant));
        MaterialkostenErfassenDto dto = dto("  Kleinmaterial  ", "12.50");
        dto.setRechnungsnummer(" RE-2026-001 ");
        dto.setExterneArtikelnummer("ART-1");
        dto.setMonat(10);
        dto.setLieferantId(9L);

        service.fuegeMaterialkostenHinzu(5L, dto);

        Materialkosten neu = gespeichertesProjekt().getMaterialkosten().getFirst();
        assertEquals("Kleinmaterial", neu.getBeschreibung());
        assertEquals("RE-2026-001", neu.getRechnungsnummer());
        assertEquals("ART-1", neu.getExterneArtikelnummer());
        assertEquals(10, neu.getMonat());
        assertSame(lieferant, neu.getLieferant());
        assertEquals(3, lieferant.getBestellungen());
        verify(lieferantenRepository).save(lieferant);
    }

    @Test
    void erfassenZaehltAbNullWennDerLieferantNochKeinenZaehlerHat() {
        Projekt projekt = projektMit();
        Lieferanten lieferant = lieferant(9L, null);
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
        when(lieferantenRepository.findById(9L)).thenReturn(Optional.of(lieferant));
        MaterialkostenErfassenDto dto = dto("Kleinmaterial", "1");
        dto.setLieferantId(9L);

        service.fuegeMaterialkostenHinzu(5L, dto);

        assertEquals(1, lieferant.getBestellungen());
    }

    @Test
    void erfassenSpeichertEineLeereRechnungsnummerAlsLeer() {
        Projekt projekt = projektMit();
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
        MaterialkostenErfassenDto dto = dto("Kleinmaterial", "0");
        dto.setRechnungsnummer("   ");

        service.fuegeMaterialkostenHinzu(5L, dto);

        Materialkosten neu = gespeichertesProjekt().getMaterialkosten().getFirst();
        assertNull(neu.getRechnungsnummer());
        assertNull(neu.getLieferant());
        verify(lieferantenRepository, never()).save(any());
    }

    @Test
    void erfassenMitUnbekanntemLieferantenWirdAbgelehntUndSpeichertNichts() {
        Projekt projekt = projektMit(eintrag(10L, "Kleinmaterial", "12.50", null));
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
        when(lieferantenRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());
        MaterialkostenErfassenDto dto = dto("Dübel", "7.99");
        dto.setLieferantId(Long.MAX_VALUE);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.fuegeMaterialkostenHinzu(5L, dto));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Der gewählte Lieferant wurde nicht gefunden.", ex.getReason());
        assertEquals(1, projekt.getMaterialkosten().size());
        verify(projektRepository, never()).save(any());
    }

    @Test
    void erfassenAufUnbekanntesProjektGibt404() {
        when(projektRepository.findById(-1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.fuegeMaterialkostenHinzu(-1L, dto("Kleinmaterial", "1")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals(ProjektManagementService.PROJEKT_NICHT_GEFUNDEN, ex.getReason());
    }

    @Test
    void erfassenOhneDatenWirdAbgelehnt() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.fuegeMaterialkostenHinzu(5L, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Bitte eine Beschreibung angeben.", ex.getReason());
        verify(projektRepository, never()).findById(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void erfassenOhneBeschreibungWirdAbgelehnt(String beschreibung) {
        assertAbgelehnt(dto(beschreibung, "1"), "Bitte eine Beschreibung angeben.");
    }

    @Test
    void erfassenOhneBeschreibungsfeldWirdAbgelehnt() {
        assertAbgelehnt(dto(null, "1"), "Bitte eine Beschreibung angeben.");
    }

    @Test
    void erfassenMitUeberlangerBeschreibungWirdAbgelehnt() {
        assertAbgelehnt(dto("x".repeat(10_001), "1"), "Die Beschreibung darf höchstens 255 Zeichen lang sein.");
    }

    @Test
    void erfassenMitUeberlangerRechnungsnummerWirdAbgelehnt() {
        assertAbgelehnt(mit(dto("Kleinmaterial", "1"), d -> d.setRechnungsnummer("1".repeat(256))),
                "Die Rechnungsnummer darf höchstens 255 Zeichen lang sein.");
    }

    @Test
    void erfassenMitUeberlangerArtikelnummerWirdAbgelehnt() {
        assertAbgelehnt(mit(dto("Kleinmaterial", "1"), d -> d.setExterneArtikelnummer("1".repeat(256))),
                "Die Artikelnummer darf höchstens 255 Zeichen lang sein.");
    }

    @Test
    void erfassenOhneBetragWirdAbgelehnt() {
        assertAbgelehnt(dto("Kleinmaterial", null), "Bitte einen Betrag angeben.");
    }

    @Test
    void erfassenMitNegativemBetragWirdAbgelehnt() {
        assertAbgelehnt(dto("Kleinmaterial", "-0.01"), "Der Betrag darf nicht negativ sein.");
    }

    @Test
    void erfassenMitDreiNachkommastellenWirdAbgelehnt() {
        assertAbgelehnt(dto("Kleinmaterial", "1.005"), "Der Betrag darf höchstens zwei Nachkommastellen haben.");
    }

    @Test
    void erfassenMitBetragJenseitsDerSpalteWirdAbgelehnt() {
        // numeric(19,2): 18 Stellen vor dem Komma passen nicht mehr hinein.
        assertAbgelehnt(dto("Kleinmaterial", "100000000000000000"), "Der Betrag ist zu groß.");
    }

    @Test
    void erfassenMitGroesstemMoeglichenBetragGehtDurch() {
        Projekt projekt = projektMit();
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        service.fuegeMaterialkostenHinzu(5L, dto("Kleinmaterial", "99999999999999999.99"));

        assertEquals(1, gespeichertesProjekt().getMaterialkosten().size());
    }

    @Test
    void erfassenSpeichertEineLeereArtikelnummerAlsLeerUndTrimmtSie() {
        Projekt projekt = projektMit();
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        service.fuegeMaterialkostenHinzu(5L, mit(dto("Kleinmaterial", "1"), d -> d.setExterneArtikelnummer("  ")));
        service.fuegeMaterialkostenHinzu(5L, mit(dto("Schrauben", "1"), d -> d.setExterneArtikelnummer(" ART-1 ")));

        assertNull(projekt.getMaterialkosten().get(0).getExterneArtikelnummer());
        assertEquals("ART-1", projekt.getMaterialkosten().get(1).getExterneArtikelnummer());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 13, -1 })
    void erfassenMitUngueltigemMonatWirdAbgelehnt(int monat) {
        assertAbgelehnt(mit(dto("Kleinmaterial", "1"), d -> d.setMonat(monat)),
                "Der Monat muss zwischen 1 und 12 liegen.");
    }

    @Test
    void erfassenSpeichertSkriptTextUnveraendertAlsText() {
        // Kein HTML-Umbau im Backend – das Frontend zeigt die Beschreibung als Text an.
        Projekt projekt = projektMit();
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        service.fuegeMaterialkostenHinzu(5L, dto("<script>alert(1)</script>'; DROP TABLE x; --", "1"));

        assertEquals("<script>alert(1)</script>'; DROP TABLE x; --",
                gespeichertesProjekt().getMaterialkosten().getFirst().getBeschreibung());
    }

    // ==================== Entfernen ====================

    @Test
    void entfernenNimmtNurDenGewaehltenEintragHerausUndZaehltDenLieferantenRunter() {
        Lieferanten lieferant = lieferant(9L, 4);
        Projekt projekt = projektMit(eintrag(10L, "Kleinmaterial", "12.50", lieferant),
                eintrag(11L, "Schrauben", "4.20", null));
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        service.entferneMaterialkosten(5L, 10L);

        List<Materialkosten> liste = gespeichertesProjekt().getMaterialkosten();
        assertEquals(1, liste.size());
        assertEquals(11L, liste.getFirst().getId());
        assertEquals(3, lieferant.getBestellungen());
        verify(lieferantenRepository).save(lieferant);
    }

    @Test
    void entfernenOhneLieferantenFasstKeinenZaehlerAn() {
        Projekt projekt = projektMit(eintrag(11L, "Schrauben", "4.20", null));
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        service.entferneMaterialkosten(5L, 11L);

        assertEquals(0, gespeichertesProjekt().getMaterialkosten().size());
        verify(lieferantenRepository, never()).save(any());
    }

    @Test
    void entfernenZaehltNieUnterNull() {
        Lieferanten lieferant = lieferant(9L, 0);
        Projekt projekt = projektMit(eintrag(10L, "Kleinmaterial", "12.50", lieferant));
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        service.entferneMaterialkosten(5L, 10L);

        assertEquals(0, lieferant.getBestellungen());
    }

    @Test
    void entfernenEinesUnbekanntenEintragsGibt404UndLaesstDieListeStehen() {
        Projekt projekt = projektMit(eintrag(10L, "Kleinmaterial", "12.50", null));
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.entferneMaterialkosten(5L, 0L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Diese Materialkosten gibt es in diesem Projekt nicht mehr.", ex.getReason());
        assertEquals(1, projekt.getMaterialkosten().size());
        verify(projektRepository, never()).save(any());
    }

    @Test
    void entfernenOhneEintragsIdGibt404() {
        Projekt projekt = projektMit(eintrag(10L, "Kleinmaterial", "12.50", null));
        when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.entferneMaterialkosten(5L, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ==================== Projektart beim Bearbeiten ====================

    @Test
    void bearbeitenOhneProjektartLaesstRegieStehen() {
        Projekt projekt = bearbeitbaresProjekt(ProjektArt.REGIE);
        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setBauvorhaben("Treppengeländer Max Mustermann");

        assertEquals(ProjektArt.REGIE, nachBearbeiten(projekt, dto).getProjektArt());
    }

    @Test
    void bearbeitenMitProjektartUebernimmtSie() {
        Projekt projekt = bearbeitbaresProjekt(ProjektArt.PAUSCHAL);
        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setProjektArt("GARANTIE");

        assertEquals(ProjektArt.GARANTIE, nachBearbeiten(projekt, dto).getProjektArt());
    }

    @Test
    void bearbeitenMitUnbekannterProjektartLaesstSieStehen() {
        Projekt projekt = bearbeitbaresProjekt(ProjektArt.INTERN);
        ProjektErstellenDto dto = new ProjektErstellenDto();
        dto.setProjektArt("UNBEKANNT");

        assertEquals(ProjektArt.INTERN, nachBearbeiten(projekt, dto).getProjektArt());
    }

    // ==================== Hilfen ====================

    private void assertAbgelehnt(MaterialkostenErfassenDto dto, String meldung) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.fuegeMaterialkostenHinzu(5L, dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(meldung, ex.getReason());
        verify(projektRepository, never()).save(any());
    }

    private Projekt gespeichertesProjekt() {
        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektRepository).save(captor.capture());
        return captor.getValue();
    }

    private Projekt nachBearbeiten(Projekt projekt, ProjektErstellenDto dto) {
        when(projektRepository.findById(6L)).thenReturn(Optional.of(projekt));
        when(projektPersistenceService.saveProjektWithRetry(any())).thenAnswer(i -> i.getArgument(0));
        try {
            service.aktualisiereProjekt(6L, dto, null, null, null, null, null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(projektPersistenceService).saveProjektWithRetry(captor.capture());
        return captor.getValue();
    }

    private static Projekt bearbeitbaresProjekt(ProjektArt art) {
        Projekt projekt = new Projekt();
        projekt.setId(6L);
        projekt.setAnlegedatum(LocalDate.of(2026, 1, 1));
        projekt.setKundenId(new Kunde());
        projekt.setProjektArt(art);
        return projekt;
    }

    private static Projekt projektMit(Materialkosten... eintraege) {
        Projekt projekt = new Projekt();
        projekt.setId(5L);
        projekt.setMaterialkosten(new ArrayList<>(List.of(eintraege)));
        for (Materialkosten mk : eintraege) {
            mk.setProjekt(projekt);
        }
        return projekt;
    }

    private static Materialkosten eintrag(Long id, String beschreibung, String betrag, Lieferanten lieferant) {
        Materialkosten mk = new Materialkosten();
        mk.setId(id);
        mk.setBeschreibung(beschreibung);
        mk.setBetrag(new BigDecimal(betrag));
        mk.setLieferant(lieferant);
        return mk;
    }

    private static Lieferanten lieferant(Long id, Integer bestellungen) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname("Max Mustermann Metallhandel");
        l.setBestellungen(bestellungen);
        return l;
    }

    private static MaterialkostenErfassenDto dto(String beschreibung, String betrag) {
        MaterialkostenErfassenDto dto = new MaterialkostenErfassenDto();
        dto.setBeschreibung(beschreibung);
        dto.setBetrag(betrag == null ? null : new BigDecimal(betrag));
        return dto;
    }

    private static MaterialkostenErfassenDto mit(MaterialkostenErfassenDto dto, Consumer<MaterialkostenErfassenDto> aenderung) {
        aenderung.accept(dto);
        return dto;
    }
}
