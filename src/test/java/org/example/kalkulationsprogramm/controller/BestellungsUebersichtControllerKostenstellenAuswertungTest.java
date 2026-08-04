package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests fuer den Kostenstellen-Auswertungs-Endpoint (Jahr + Vorjahresvergleich).
 * Controller wird direkt mit gemockten Repositories instanziiert (kein Spring-Context).
 */
class BestellungsUebersichtControllerKostenstellenAuswertungTest {

    private LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    private KostenstelleRepository kostenstelleRepository;
    private BelegRepository belegRepository;
    private BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    private BelegService belegService;
    private BestellungsUebersichtController controller;

    @BeforeEach
    void setUp() {
        projektAnteilRepository = mock(LieferantDokumentProjektAnteilRepository.class);
        kostenstelleRepository = mock(KostenstelleRepository.class);
        belegRepository = mock(BelegRepository.class);
        belegKostenstellenAnteilRepository = mock(BelegKostenstellenAnteilRepository.class);
        belegService = mock(BelegService.class);
        controller = new BestellungsUebersichtController(
                mock(LieferantDokumentRepository.class),
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
    void auswertungSummiertNachJahrUndVorjahr() {
        Kostenstelle ks = kostenstelle(5L, "Fuhrpark");
        when(kostenstelleRepository.findByAktivTrueOrderBySortierungAsc()).thenReturn(List.of(ks));
        when(belegService.findCaller(isNull(), isNull())).thenReturn(null); // keine Beleg-Berechtigung
        when(projektAnteilRepository.findByKostenstelleId(5L)).thenReturn(List.of(
                anteil(ks, LocalDate.of(2026, 3, 10), BigDecimal.valueOf(100)),
                anteil(ks, LocalDate.of(2026, 9, 1), BigDecimal.valueOf(50)),
                anteil(ks, LocalDate.of(2025, 4, 4), BigDecimal.valueOf(40))));

        var response = controller.getKostenstellenAuswertung(2026, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        var dto = response.getBody().get(0);
        assertThat(dto.summeDiesesJahr()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(dto.summeVorjahr()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(dto.anzahlDiesesJahr()).isEqualTo(2);
        assertThat(dto.bezeichnung()).isEqualTo("Fuhrpark");
        assertThat(dto.typ()).isEqualTo("GEMEINKOSTEN");
    }

    @Test
    void monatsfilterGrenztAufEinzelnenMonatEin() {
        Kostenstelle ks = kostenstelle(7L, "IT & Software");
        when(kostenstelleRepository.findByAktivTrueOrderBySortierungAsc()).thenReturn(List.of(ks));
        when(belegService.findCaller(isNull(), isNull())).thenReturn(null);
        when(projektAnteilRepository.findByKostenstelleId(7L)).thenReturn(List.of(
                anteil(ks, LocalDate.of(2026, 6, 15), BigDecimal.valueOf(80)),
                anteil(ks, LocalDate.of(2026, 7, 1), BigDecimal.valueOf(20))));

        var response = controller.getKostenstellenAuswertung(2026, 6, null, null);

        var dto = response.getBody().get(0);
        assertThat(dto.summeDiesesJahr()).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat(dto.anzahlDiesesJahr()).isEqualTo(1);
    }

    @Test
    void ohneBelegBerechtigungWerdenKeineBelegquellenAbgefragt() {
        Kostenstelle ks = kostenstelle(9L, "Werkstatt");
        when(kostenstelleRepository.findByAktivTrueOrderBySortierungAsc()).thenReturn(List.of(ks));
        when(belegService.findCaller(isNull(), isNull())).thenReturn(null);
        when(projektAnteilRepository.findByKostenstelleId(9L)).thenReturn(List.of());

        var response = controller.getKostenstellenAuswertung(2026, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).summeDiesesJahr()).isEqualByComparingTo(BigDecimal.ZERO);
        verifyNoInteractions(belegKostenstellenAnteilRepository, belegRepository);
    }

    @Test
    void streckungVerteiltJahresanteilUeberMehrereJahre() {
        // Zertifizierung 3.000 € über 3 Jahre ab 2026 = 1.000 €/Jahr in 2026, 2027, 2028.
        Kostenstelle ks = kostenstelle(11L, "Zertifizierung");
        when(kostenstelleRepository.findByAktivTrueOrderBySortierungAsc()).thenReturn(List.of(ks));
        when(belegService.findCaller(isNull(), isNull())).thenReturn(null);
        when(projektAnteilRepository.findByKostenstelleId(11L)).thenReturn(List.of(
                gestreckterAnteil(ks, LocalDate.of(2026, 5, 4), BigDecimal.valueOf(3000), 3, 2026)));

        // Mittleres Streckungsjahr 2027: dieses Jahr 1.000 €, Vorjahr 2026 ebenfalls 1.000 €.
        var dto2027 = controller.getKostenstellenAuswertung(2027, null, null, null).getBody().get(0);
        assertThat(dto2027.summeDiesesJahr()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(dto2027.summeVorjahr()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(dto2027.anzahlDiesesJahr()).isEqualTo(1);
    }

    @Test
    void streckungEndetNachStreckungsfenster() {
        Kostenstelle ks = kostenstelle(12L, "Zertifizierung");
        when(kostenstelleRepository.findByAktivTrueOrderBySortierungAsc()).thenReturn(List.of(ks));
        when(belegService.findCaller(isNull(), isNull())).thenReturn(null);
        when(projektAnteilRepository.findByKostenstelleId(12L)).thenReturn(List.of(
                gestreckterAnteil(ks, LocalDate.of(2026, 5, 4), BigDecimal.valueOf(3000), 3, 2026)));

        // 2029 liegt außerhalb des Fensters [2026,2028], Vorjahr 2028 ist noch drin.
        var dto2029 = controller.getKostenstellenAuswertung(2029, null, null, null).getBody().get(0);
        assertThat(dto2029.summeDiesesJahr()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto2029.summeVorjahr()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(dto2029.anzahlDiesesJahr()).isEqualTo(0);
    }

    private Kostenstelle kostenstelle(Long id, String bezeichnung) {
        Kostenstelle ks = new Kostenstelle();
        ks.setId(id);
        ks.setBezeichnung(bezeichnung);
        ks.setTyp(KostenstellenTyp.GEMEINKOSTEN);
        return ks;
    }

    private LieferantDokumentProjektAnteil anteil(Kostenstelle ks, LocalDate datum, BigDecimal betrag) {
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokumentDatum(datum);

        LieferantDokument dok = new LieferantDokument();
        dok.setId(1L);
        dok.setGeschaeftsdaten(gd);

        LieferantDokumentProjektAnteil anteil = new LieferantDokumentProjektAnteil();
        anteil.setId(1L);
        anteil.setKostenstelle(ks);
        anteil.setDokument(dok);
        anteil.setProzent(100);
        anteil.setBerechneterBetrag(betrag);
        return anteil;
    }

    private LieferantDokumentProjektAnteil gestreckterAnteil(
            Kostenstelle ks, LocalDate datum, BigDecimal betrag, int streckungJahre, int startJahr) {
        LieferantDokumentProjektAnteil anteil = anteil(ks, datum, betrag);
        anteil.setStreckungJahre(streckungJahre);
        anteil.setStreckungStartJahr(startJahr);
        return anteil;
    }
}
