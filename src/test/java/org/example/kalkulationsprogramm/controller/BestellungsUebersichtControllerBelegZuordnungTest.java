package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BestellungsUebersichtControllerBelegZuordnungTest {

    private LieferantDokumentRepository dokumentRepository;
    private LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    private KostenstelleRepository kostenstelleRepository;
    private BelegRepository belegRepository;
    private BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    private BelegService belegService;
    private BestellungsUebersichtController controller;

    @BeforeEach
    void setUp() {
        dokumentRepository = mock(LieferantDokumentRepository.class);
        projektAnteilRepository = mock(LieferantDokumentProjektAnteilRepository.class);
        kostenstelleRepository = mock(KostenstelleRepository.class);
        belegRepository = mock(BelegRepository.class);
        belegKostenstellenAnteilRepository = mock(BelegKostenstellenAnteilRepository.class);
        belegService = mock(BelegService.class);
        controller = new BestellungsUebersichtController(
                dokumentRepository,
                mock(LieferantGeschaeftsdokumentRepository.class),
                mock(ProjektRepository.class),
                mock(ProjektDokumentRepository.class),
                projektAnteilRepository,
                kostenstelleRepository,
                mock(FrontendUserProfileRepository.class),
                belegRepository,
                belegKostenstellenAnteilRepository,
                belegService,
                // Kontierungsaenderungen sind protokollpflichtig; hier nur gemockt.
                mock(org.example.kalkulationsprogramm.service.BelegAuditService.class));
    }

    @Test
    void offeneBelegeSindOhneBelegBerechtigungGesperrt() {
        when(belegService.findCaller(isNull(), isNull())).thenReturn(null);

        var response = controller.getOffeneBelegeZurKostenstellenZuordnung(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(belegRepository);
    }

    @Test
    void kostenstellenZuordnungenLeakenOhneBelegBerechtigungKeineBelegdaten() {
        when(belegService.findCaller(isNull(), isNull())).thenReturn(null);
        when(projektAnteilRepository.findByKostenstelleId(7L)).thenReturn(List.of());

        var response = controller.getZuordnungenForKostenstelle(7L, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verifyNoInteractions(belegKostenstellenAnteilRepository, belegRepository);
    }

    @Test
    void lieferantenDokumentBelegWirdNichtUeberschrieben() {
        autorisiereBearbeitung();
        Beleg beleg = beleg(11L);
        when(belegRepository.findById(11L)).thenReturn(Optional.of(beleg));
        when(dokumentRepository.findByBelegId(11L)).thenReturn(Optional.of(new LieferantDokument()));

        var response = controller.zuordnenBelegKostenstellen(requestMitKostenstelle(11L, 99L), null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(belegKostenstellenAnteilRepository, never()).deleteByBelegId(any());
        verify(belegRepository, never()).save(any());
    }

    @Test
    void ungueltigeKostenstelleLoeschtKeineBestehendenSplits() {
        autorisiereBearbeitung();
        Beleg beleg = beleg(12L);
        when(belegRepository.findById(12L)).thenReturn(Optional.of(beleg));
        when(dokumentRepository.findByBelegId(12L)).thenReturn(Optional.empty());
        when(kostenstelleRepository.findById(99L)).thenReturn(Optional.empty());

        var response = controller.zuordnenBelegKostenstellen(requestMitKostenstelle(12L, 99L), null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(belegKostenstellenAnteilRepository, never()).deleteByBelegId(any());
        verify(belegRepository, never()).save(any());
    }

    @Test
    void projektZuordnungWirdImBelegPfadAbgelehntOhneAlteSplitsZuLoeschen() {
        autorisiereBearbeitung();
        Beleg beleg = beleg(14L);
        when(belegRepository.findById(14L)).thenReturn(Optional.of(beleg));
        when(dokumentRepository.findByBelegId(14L)).thenReturn(Optional.empty());

        var request = requestMitKostenstelle(14L, 5L);
        request.projektAnteile.get(0).projektId = 77L;
        var response = controller.zuordnenBelegKostenstellen(request, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(belegKostenstellenAnteilRepository, never()).deleteByBelegId(any());
        verify(belegRepository, never()).save(any());
    }

    @Test
    void prozentUndBetragGleichzeitigWirdAbgelehntOhneAlteSplitsZuLoeschen() {
        autorisiereBearbeitung();
        Beleg beleg = beleg(15L);
        Kostenstelle kostenstelle = kostenstelle(5L);
        when(belegRepository.findById(15L)).thenReturn(Optional.of(beleg));
        when(dokumentRepository.findByBelegId(15L)).thenReturn(Optional.empty());
        when(kostenstelleRepository.findById(5L)).thenReturn(Optional.of(kostenstelle));

        var request = requestMitKostenstelle(15L, 5L);
        request.projektAnteile.get(0).betrag = BigDecimal.TEN;
        var response = controller.zuordnenBelegKostenstellen(request, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(belegKostenstellenAnteilRepository, never()).deleteByBelegId(any());
        verify(belegRepository, never()).save(any());
    }

    @Test
    void absoluterBetragUeberBelegNettoWirdAbgelehntOhneAlteSplitsZuLoeschen() {
        autorisiereBearbeitung();
        Beleg beleg = beleg(16L);
        Kostenstelle kostenstelle = kostenstelle(5L);
        when(belegRepository.findById(16L)).thenReturn(Optional.of(beleg));
        when(dokumentRepository.findByBelegId(16L)).thenReturn(Optional.empty());
        when(kostenstelleRepository.findById(5L)).thenReturn(Optional.of(kostenstelle));

        var request = requestMitKostenstelle(16L, 5L);
        request.projektAnteile.get(0).prozentanteil = null;
        request.projektAnteile.get(0).betrag = BigDecimal.valueOf(101);
        var response = controller.zuordnenBelegKostenstellen(request, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(belegKostenstellenAnteilRepository, never()).deleteByBelegId(any());
        verify(belegRepository, never()).save(any());
    }

    @Test
    void vollstaendigeEinzelzuordnungSetztDirekteKostenstelleNachValidierung() {
        autorisiereBearbeitung();
        Beleg beleg = beleg(13L);
        Kostenstelle kostenstelle = kostenstelle(5L);
        when(belegRepository.findById(13L)).thenReturn(Optional.of(beleg));
        when(dokumentRepository.findByBelegId(13L)).thenReturn(Optional.empty());
        when(kostenstelleRepository.findById(5L)).thenReturn(Optional.of(kostenstelle));

        var response = controller.zuordnenBelegKostenstellen(requestMitKostenstelle(13L, 5L), null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(beleg.getKostenstelle()).isSameAs(kostenstelle);
        verify(belegKostenstellenAnteilRepository).deleteByBelegId(13L);
        verify(belegRepository).save(beleg);
    }

    private void autorisiereBearbeitung() {
        Mitarbeiter caller = new Mitarbeiter();
        when(belegService.findCaller(isNull(), isNull())).thenReturn(caller);
        when(belegService.darfScannen(caller)).thenReturn(true);
    }

    private Beleg beleg(Long id) {
        Beleg beleg = new Beleg();
        beleg.setId(id);
        beleg.setBelegDatum(LocalDate.of(2026, 5, 24));
        beleg.setBetragNetto(BigDecimal.valueOf(100));
        beleg.setBetragBrutto(BigDecimal.valueOf(119));
        return beleg;
    }

    private Kostenstelle kostenstelle(Long id) {
        Kostenstelle kostenstelle = new Kostenstelle();
        kostenstelle.setId(id);
        kostenstelle.setBezeichnung("Werkstatt");
        return kostenstelle;
    }

    private BestellungsUebersichtController.BelegZuordnungRequest requestMitKostenstelle(Long belegId, Long kostenstelleId) {
        BestellungsUebersichtController.ProjektAnteil anteil = new BestellungsUebersichtController.ProjektAnteil();
        anteil.kostenstelleId = kostenstelleId;
        anteil.prozentanteil = BigDecimal.valueOf(100);

        BestellungsUebersichtController.BelegZuordnungRequest request =
                new BestellungsUebersichtController.BelegZuordnungRequest();
        request.belegId = belegId;
        request.projektAnteile = List.of(anteil);
        return request;
    }
}
