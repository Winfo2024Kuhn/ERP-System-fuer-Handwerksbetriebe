package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegPositionRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests fuer die Bar-Saldo-Validierung in {@code BelegService.updateBeleg} und
 * {@code BelegService.createUmbuchung}: wenn der Beleg validiert wird und eine
 * Bar-Kategorie hat, darf der projizierte Saldo nicht unter den Mindestbestand
 * fallen — sonst HTTP 409.
 *
 * DSGVO-Dummy: nur "Max Mustermann".
 */
@ExtendWith(MockitoExtension.class)
class BelegServiceKasseValidationTest {

    @Mock private BelegRepository belegRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;
    @Mock private AbteilungDokumentBerechtigungRepository berechtigungRepository;
    @Mock private SachkontoRepository sachkontoRepository;
    @Mock private KostenstelleRepository kostenstelleRepository;
    @Mock private BelegKiAnalyseService kiAnalyseService;
    @Mock private LieferantDokumentRepository lieferantDokumentRepository;
    @Mock private FrontendUserProfileRepository frontendUserProfileRepository;
    @Mock private BelegSplitService belegSplitService;
    @Mock private BelegPositionRepository belegPositionRepository;
    @Mock private KasseSaldoService kasseSaldoService;
    @Mock private BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    // Seit der Festschreibung schreibt jeder Vorgang einen Protokolleintrag und
    // prueft, ob der Monat schon abgeschlossen ist. Beides wird hier gemockt --
    // getestet wird es in KassenbuchFestschreibungTest.
    @Mock private BelegAuditService auditService;
    @Mock private KassenbuchSchreibschutz schreibschutz;
    @Mock private org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository monatsabschlussRepository;

    @InjectMocks
    private BelegService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(belegRepository.save(any(Beleg.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("updateBeleg: Validierung einer KASSE_AUSGABE unter Mindestbestand -> KasseUnterdeckungException")
    void updateBeleg_kasseAusgabeUnterdeckung_wirftException() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.KASSE_AUSGABE, "1000.00");
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));
        given(kasseSaldoService.projiziereSaldo(any(), any(), any(), any()))
                .willReturn(new BigDecimal("-500.00"));
        org.mockito.Mockito.doThrow(new KasseUnterdeckungException(
                        new BigDecimal("-500.00"), new BigDecimal("0.00")))
                .when(kasseSaldoService).assertSaldoMindestensMindestbestand(any());

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setStatus("VALIDIERT");

        assertThatThrownBy(() -> service.updateBeleg(1L, req, mitarbeiter()))
                .isInstanceOf(KasseUnterdeckungException.class);
        verify(belegRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateBeleg: Unkritische Aenderung (Status bleibt NEU) -> keine Saldo-Pruefung")
    void updateBeleg_statusBleibtNeu_keinePruefung() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.KASSE_AUSGABE, "1000.00");
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setBeschreibung("neuer Text");
        // Status NICHT auf VALIDIERT setzen -> keine Pruefung

        service.updateBeleg(1L, req, mitarbeiter());

        verify(kasseSaldoService, never()).assertSaldoMindestensMindestbestand(any());
        verify(belegRepository).save(any(Beleg.class));
    }

    @Test
    @DisplayName("updateBeleg: Wechsel auf Nicht-Bar-Kategorie -> keine Unterdeckung moeglich")
    void updateBeleg_wechselAufNichtBar_keinePruefung() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.KASSE_AUSGABE, "1000.00");
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setStatus("VALIDIERT");
        req.setBelegKategorie("BANK");

        service.updateBeleg(1L, req, mitarbeiter());

        verify(kasseSaldoService, never()).assertSaldoMindestensMindestbestand(any());
    }

    @Test
    @DisplayName("updateBeleg: validierte KASSE_EINNAHME mit ausreichend Saldo -> ok")
    void updateBeleg_kasseEinnahmeImLimit_speichert() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.KASSE_EINNAHME, "300.00");
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));
        given(kasseSaldoService.projiziereSaldo(any(), any(), any(), any()))
                .willReturn(new BigDecimal("300.00"));
        // assertSaldoMindestens... wirft nicht — Default-Verhalten

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setStatus("VALIDIERT");

        BelegDto.Response result = service.updateBeleg(1L, req, mitarbeiter());

        assertThat(result).isNotNull();
        verify(belegRepository).save(any(Beleg.class));
    }

    @Test
    @DisplayName("createUmbuchung Bar-Kategorie: ueber Mindestbestand -> Beleg gespeichert")
    void createUmbuchung_barOk_speichert() {
        given(kasseSaldoService.projiziereSaldo(any(), any(), any(), any()))
                .willReturn(new BigDecimal("100.00"));

        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("PRIVATEINLAGE");
        req.setBelegDatum(LocalDate.of(2026, 5, 13));
        req.setBetragBrutto(new BigDecimal("100.00"));

        service.createUmbuchung(req, mitarbeiter());

        verify(belegRepository).save(any(Beleg.class));
    }

    @Test
    @DisplayName("createUmbuchung Bank-Kategorie: keine Saldo-Pruefung")
    void createUmbuchung_bank_keinePruefung() {
        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("BANK");
        req.setBelegDatum(LocalDate.of(2026, 5, 13));
        req.setBetragBrutto(new BigDecimal("100.00"));

        service.createUmbuchung(req, mitarbeiter());

        verify(kasseSaldoService, never()).assertSaldoMindestensMindestbestand(any());
        verify(belegRepository).save(any(Beleg.class));
    }

    @Test
    @DisplayName("createUmbuchung Privatentnahme mit Unterdeckung -> Exception, kein Save")
    void createUmbuchung_privatentnahme_unterdeckung_wirft() {
        given(kasseSaldoService.projiziereSaldo(any(), any(), any(), any()))
                .willReturn(new BigDecimal("-50.00"));
        org.mockito.Mockito.doThrow(new KasseUnterdeckungException(
                        new BigDecimal("-50.00"), new BigDecimal("0.00")))
                .when(kasseSaldoService).assertSaldoMindestensMindestbestand(any());

        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("PRIVATENTNAHME");
        req.setBelegDatum(LocalDate.of(2026, 5, 13));
        req.setBetragBrutto(new BigDecimal("500.00"));

        assertThatThrownBy(() -> service.createUmbuchung(req, mitarbeiter()))
                .isInstanceOf(KasseUnterdeckungException.class);
        verify(belegRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUmbuchung: UNZUGEORDNET wird abgelehnt")
    void createUmbuchung_unzugeordnet_wirftFehler() {
        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("UNZUGEORDNET");
        req.setBelegDatum(LocalDate.of(2026, 5, 13));
        req.setBetragBrutto(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.createUmbuchung(req, mitarbeiter()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===================== Kostenstellen-Splits-Validierung (Issue #60) =====================

    @Test
    @DisplayName("Splits: Summe Prozent > 100 -> IllegalArgumentException, kein Split-Save")
    void splits_prozentSumme_ueberSchreitet100_wirft() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.SONSTIGER_BELEG, "100.00");
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setKostenstellenSplits(java.util.List.of(
                split(7L, 60, null),
                split(8L, 50, null)));

        assertThatThrownBy(() -> service.updateBeleg(1L, req, mitarbeiter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100%");
        verify(belegKostenstellenAnteilRepository, never()).save(any());
    }

    @Test
    @DisplayName("Splits: Prozent UND Absolut gleichzeitig -> IllegalArgumentException")
    void splits_prozentUndAbsolutGleichzeitig_wirft() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.SONSTIGER_BELEG, "100.00");
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setKostenstellenSplits(java.util.List.of(split(7L, 50, new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.updateBeleg(1L, req, mitarbeiter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prozent ODER absoluterBetrag");
        verify(belegKostenstellenAnteilRepository, never()).save(any());
    }

    @Test
    @DisplayName("Splits: weder Prozent NOCH Absolut -> IllegalArgumentException")
    void splits_wederProzentNochAbsolut_wirft() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.SONSTIGER_BELEG, "100.00");
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setKostenstellenSplits(java.util.List.of(split(7L, null, null)));

        assertThatThrownBy(() -> service.updateBeleg(1L, req, mitarbeiter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prozent ODER absoluterBetrag");
    }

    @Test
    @DisplayName("Splits: ungueltige Kostenstellen-ID -> IllegalArgumentException")
    void splits_ungueltigeKostenstelle_wirft() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.SONSTIGER_BELEG, "100.00");
        vorher.setBetragNetto(new BigDecimal("84.03"));
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));
        given(kostenstelleRepository.findById(999L)).willReturn(Optional.empty());

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setKostenstellenSplits(java.util.List.of(split(999L, 100, null)));

        assertThatThrownBy(() -> service.updateBeleg(1L, req, mitarbeiter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existiert nicht");
    }

    @Test
    @DisplayName("Splits: ohne kostenstelleId pro Eintrag -> IllegalArgumentException")
    void splits_ohneKostenstelleId_wirft() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.SONSTIGER_BELEG, "100.00");
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setKostenstellenSplits(java.util.List.of(split(null, 100, null)));

        assertThatThrownBy(() -> service.updateBeleg(1L, req, mitarbeiter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kostenstelle pro Split-Eintrag");
    }

    @Test
    @DisplayName("Splits: gueltige Liste -> delete-then-insert, berechneter Betrag aus Netto")
    void splits_gueltig_speichertUndBerechnet() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.SONSTIGER_BELEG, "119.00");
        vorher.setBetragNetto(new BigDecimal("100.00"));
        vorher.setBelegDatum(LocalDate.of(2026, 3, 15));
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));
        Kostenstelle ks = new Kostenstelle();
        ks.setId(7L);
        ks.setBezeichnung("Werkstatt");
        given(kostenstelleRepository.findById(7L)).willReturn(Optional.of(ks));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setKostenstellenSplits(java.util.List.of(split(7L, 60, null)));

        service.updateBeleg(1L, req, mitarbeiter());

        verify(belegKostenstellenAnteilRepository).deleteByBelegId(eq(1L));
        verify(belegKostenstellenAnteilRepository).save(org.mockito.ArgumentMatchers.argThat(a ->
                a.getKostenstelle() != null
                && a.getKostenstelle().getId().equals(7L)
                && a.getProzent() != null && a.getProzent() == 60
                // 60% von Netto 100 = 60.00
                && a.getBerechneterBetrag() != null
                && a.getBerechneterBetrag().compareTo(new BigDecimal("60.00")) == 0
                // streckungStartJahr default = belegDatum.year
                && a.getStreckungStartJahr() != null && a.getStreckungStartJahr() == 2026));
    }

    @Test
    @DisplayName("Splits: null-Liste -> keine Aenderung, kein delete")
    void splits_nullListe_keineAenderung() {
        Beleg vorher = bar(BelegStatus.NEU, BelegKategorie.SONSTIGER_BELEG, "100.00");
        given(belegRepository.findById(1L)).willReturn(Optional.of(vorher));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setBeschreibung("nur Beschreibung aendern");
        // kostenstellenSplits = null -> Splits unveraendert lassen

        service.updateBeleg(1L, req, mitarbeiter());

        verify(belegKostenstellenAnteilRepository, never()).deleteByBelegId(any());
        verify(belegKostenstellenAnteilRepository, never()).save(any());
    }

    private static BelegDto.KostenstellenSplitDto split(Long ksId, Integer prozent, BigDecimal absolut) {
        BelegDto.KostenstellenSplitDto d = new BelegDto.KostenstellenSplitDto();
        d.setKostenstelleId(ksId);
        d.setProzent(prozent);
        d.setAbsoluterBetrag(absolut);
        d.setStreckungJahre(1);
        return d;
    }

    // ===================== Helpers =====================

    private static Beleg bar(BelegStatus status, BelegKategorie kategorie, String brutto) {
        Beleg b = new Beleg();
        b.setId(1L);
        b.setStatus(status);
        b.setBelegKategorie(kategorie);
        b.setBetragBrutto(new BigDecimal(brutto));
        return b;
    }

    private static Mitarbeiter mitarbeiter() {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(1L);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        return m;
    }
}
