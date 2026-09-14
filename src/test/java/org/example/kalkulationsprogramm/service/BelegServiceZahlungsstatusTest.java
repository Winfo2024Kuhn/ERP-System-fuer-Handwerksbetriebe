package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BelegServiceZahlungsstatusTest {
    @Mock private BelegRepository belegRepository;
    @Mock private LieferantDokumentRepository dokumentRepository;
    @Mock private BelegAuditService auditService;
    @Mock private SachkontoRepository sachkontoRepository;
    @Mock private KostenstelleRepository kostenstelleRepository;
    private BelegService service;
    private Beleg beleg;
    private LieferantGeschaeftsdokument rechnung;
    private final Mitarbeiter pruefer = new Mitarbeiter();

    @BeforeEach
    void setUp() {
        service = new BelegService(belegRepository, mock(LieferantenRepository.class),
                mock(MitarbeiterRepository.class), mock(AbteilungDokumentBerechtigungRepository.class),
                sachkontoRepository, kostenstelleRepository,
                mock(BelegKiAnalyseService.class), dokumentRepository, mock(FrontendUserProfileRepository.class),
                mock(BelegSplitService.class), mock(BelegPositionRepository.class), mock(KasseSaldoService.class),
                mock(BelegKostenstellenAnteilRepository.class), auditService, mock(KassenbuchSchreibschutz.class),
                mock(KassenbuchMonatsabschlussRepository.class),
                new BelegVorschlagService(belegRepository, sachkontoRepository, kostenstelleRepository));
        beleg = new Beleg();
        beleg.setId(1L);
        beleg.setDokumentTyp(LieferantDokumentTyp.RECHNUNG);
        rechnung = new LieferantGeschaeftsdokument();
        rechnung.setId(90L);
        pruefer.setId(7L);
    }

    @Test
    void bezahltSetztStatusDatumUndProtokoll() {
        verknuepfteRechnung();
        var req = zahlung("BEZAHLT", LocalDate.of(2026, 9, 12));

        var dto = service.updateBeleg(1L, req, pruefer);

        assertThat(rechnung.getBezahlt()).isTrue();
        assertThat(rechnung.getBezahltAm()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(dto.getEingangsrechnungBezahlt()).isTrue();
        assertThat(dto.getEingangsrechnungBezahltAm()).isEqualTo(LocalDate.of(2026, 9, 12));
        verify(auditService).protokolliereAenderung(eq(beleg), eq(pruefer),
                contains("Bezahlt: false → true"), isNull());
    }

    @Test
    void bezahltOhneDatumNimmtHeute() {
        verknuepfteRechnung();
        LocalDate vorher = LocalDate.now();
        service.updateBeleg(1L, zahlung("BEZAHLT", null), pruefer);
        assertThat(rechnung.getBezahlt()).isTrue();
        assertThat(rechnung.getBezahltAm()).isBetween(vorher, LocalDate.now());
    }

    @Test
    void offenUeberschreibtVorauskasseUndEntferntDatum() {
        verknuepfteRechnung();
        rechnung.setBezahlt(true);
        rechnung.setBereitsGezahlt(true);
        rechnung.setZahlungsart("VORAUSKASSE");
        rechnung.setBezahltAm(LocalDate.of(2026, 9, 10));
        service.updateBeleg(1L, zahlung("OFFEN", LocalDate.of(2026, 9, 12)), pruefer);
        assertThat(rechnung.getBezahlt()).isFalse();
        assertThat(rechnung.getBereitsGezahlt()).isFalse();
        assertThat(rechnung.getBezahltAm()).isNull();
        verify(auditService).protokolliereAenderung(eq(beleg), eq(pruefer),
                contains("Bezahlt: true → false"), isNull());
    }

    @Test
    void datumskorrekturWirdAuchBeiBereitsBezahlterRechnungProtokolliert() {
        verknuepfteRechnung();
        rechnung.setBezahlt(true);
        rechnung.setBezahltAm(LocalDate.of(2026, 9, 10));
        service.updateBeleg(1L, zahlung("BEZAHLT", LocalDate.of(2026, 9, 12)), pruefer);
        assertThat(rechnung.getBezahltAm()).isEqualTo(LocalDate.of(2026, 9, 12));
        verify(auditService).protokolliereAenderung(eq(beleg), eq(pruefer),
                contains("Bezahlt am: 2026-09-10 → 2026-09-12"), isNull());
    }

    @Test
    void kiErkannteBarzahlungIstImPruefDialogBereitsBezahlt() {
        LieferantDokument dokument = new LieferantDokument();
        dokument.setGeschaeftsdaten(rechnung);
        when(dokumentRepository.findByBelegId(1L)).thenReturn(Optional.of(dokument));
        rechnung.setBereitsGezahlt(true);
        rechnung.setBezahlt(false);
        assertThat(service.toDto(beleg, true).getEingangsrechnungBezahlt()).isTrue();
    }

    @Test
    void ohneVerknuepfteRechnungPassiertNichts() {
        when(belegRepository.findById(1L)).thenReturn(Optional.of(beleg));
        var dto = service.updateBeleg(1L, zahlung("BEZAHLT", null), pruefer);
        assertThat(dto.getEingangsrechnungId()).isNull();
        assertThat(dto.getEingangsrechnungBezahlt()).isNull();
        verifyNoInteractions(auditService);
    }

    @Test
    void ohneGeschaeftsdatenPassiertNichts() {
        when(belegRepository.findById(1L)).thenReturn(Optional.of(beleg));
        when(dokumentRepository.findByBelegId(1L)).thenReturn(Optional.of(new LieferantDokument()));
        assertThatCode(() -> service.updateBeleg(1L, zahlung("OFFEN", null), pruefer))
                .doesNotThrowAnyException();
        verifyNoInteractions(auditService);
    }

    @Test
    void ungueltigerZahlungsstatusWirdVorAenderungenAbgewiesen() {
        when(belegRepository.findById(1L)).thenReturn(Optional.of(beleg));
        var req = zahlung("UNBEKANNT", null);
        req.setBeschreibung("Geändert");
        assertThatThrownBy(() -> service.updateBeleg(1L, req, pruefer))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Zahlungsstatus");
        assertThat(beleg.getBeschreibung()).isNull();
        verifyNoInteractions(dokumentRepository, auditService);
    }

    @Test
    void festschreibungBlockiertKernaenderungenVorZahlung() {
        when(belegRepository.findById(1L)).thenReturn(Optional.of(beleg));
        beleg.setFestgeschrieben(true);
        beleg.setBetragBrutto(new BigDecimal("25.00"));
        var req = zahlung("BEZAHLT", null);
        req.setBetragBrutto(new BigDecimal("30.00"));
        assertThatThrownBy(() -> service.updateBeleg(1L, req, pruefer))
                .isInstanceOf(KassenbuchGesperrtException.class);
        verifyNoInteractions(dokumentRepository, auditService);
        assertThat(beleg.getBetragBrutto()).isEqualByComparingTo("25.00");
    }

    @Test
    void listeNutztBestehendenRechnungsLookupAuchFuerZahlungsdaten() {
        LieferantDokument dokument = new LieferantDokument();
        dokument.setGeschaeftsdaten(rechnung);
        rechnung.setBezahlt(true);
        rechnung.setBezahltAm(LocalDate.of(2026, 9, 12));
        when(dokumentRepository.findByBelegId(1L)).thenReturn(Optional.of(dokument));
        var dto = service.toDto(beleg, false);
        assertThat(dto.getEingangsrechnungId()).isEqualTo(90L);
        assertThat(dto.getEingangsrechnungBezahlt()).isTrue();
        assertThat(dto.getEingangsrechnungBezahltAm()).isEqualTo(LocalDate.of(2026, 9, 12));
        verify(dokumentRepository, times(1)).findByBelegId(1L);
    }

    @Test
    void detailLiefertLieferantenStandardAuchOhneKi() {
        beleg.setDokumentTyp(null);
        var lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Musterbaustoffe GmbH");
        var kostenstelle = new Kostenstelle();
        kostenstelle.setId(8L);
        kostenstelle.setBezeichnung("Werkstatt");
        lieferant.setStandardKostenstelle(kostenstelle);
        beleg.setLieferant(lieferant);
        var dto = service.toDto(beleg, true);
        assertThat(dto.getVorschlagKostenstelle()).isNotNull();
        assertThat(dto.getVorschlagKostenstelle().getId()).isEqualTo(8L);
        assertThat(dto.getVorschlagKostenstelle().getBezeichnung()).isEqualTo("Werkstatt");
        assertThat(dto.getVorschlagKostenstelle().getQuelle()).isEqualTo("LIEFERANT_STANDARD");
        assertThat(dto.getVorschlagKostenstelle().getBegruendung()).contains("Musterbaustoffe GmbH");
        assertThat(beleg.getKostenstelle()).isNull();
    }

    @Test
    void listeLaedtKeineVorschlaegeNach() {
        beleg.setDokumentTyp(null);
        var lieferant = new Lieferanten();
        lieferant.setId(7L);
        beleg.setLieferant(lieferant);
        var dto = service.toDto(beleg, false);
        assertThat(dto.getVorschlagSachkonto()).isNull();
        assertThat(dto.getVorschlagKostenstelle()).isNull();
        verifyNoInteractions(belegRepository, sachkontoRepository, kostenstelleRepository);
    }

    @Test
    void vergleichsdatenSindAuchInDerListeVerfuegbar() {
        beleg.setDokumentTyp(null);
        beleg.setQuelle(BelegQuelle.QUITTUNG);
        beleg.setGegenpartei("Max Mustermann");
        beleg.setAusgangsrechnungId(15L);
        beleg.setKiZahlungsart("SEPA");
        beleg.setKiBelegdatum(LocalDate.of(2026, 9, 12));
        beleg.setKiBetragBrutto(new BigDecimal("24.99"));
        beleg.setKiKostenkontoHinweis("Bitte prüfen");
        var dto = service.toDto(beleg, false);
        assertThat(dto.getQuelle()).isEqualTo("QUITTUNG");
        assertThat(dto.getGegenpartei()).isEqualTo("Max Mustermann");
        assertThat(dto.getAusgangsrechnungId()).isEqualTo(15L);
        assertThat(dto.getKiZahlungsart()).isEqualTo("SEPA");
        assertThat(dto.getKiBelegdatum()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(dto.getKiBetragBrutto()).isEqualByComparingTo("24.99");
        assertThat(dto.getKiKostenkontoHinweis()).isEqualTo("Bitte prüfen");
        verifyNoInteractions(belegRepository, sachkontoRepository, kostenstelleRepository, dokumentRepository);
    }

    private void verknuepfteRechnung() {
        when(belegRepository.findById(1L)).thenReturn(Optional.of(beleg));
        LieferantDokument dokument = new LieferantDokument();
        dokument.setBeleg(beleg);
        dokument.setGeschaeftsdaten(rechnung);
        rechnung.setDokument(dokument);
        when(dokumentRepository.findByBelegId(1L)).thenReturn(Optional.of(dokument));
    }

    private BelegDto.UpdateRequest zahlung(String status, LocalDate datum) {
        var req = new BelegDto.UpdateRequest();
        req.setZahlungsstatus(status);
        req.setBezahltAm(datum);
        return req;
    }
}
