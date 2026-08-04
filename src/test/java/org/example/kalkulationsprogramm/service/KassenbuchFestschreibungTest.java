package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegPositionRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests fuer die Festschreibung in {@code BelegService}.
 *
 * <p>Das ist der Kern von § 146 Abs. 4 AO: Was einmal festgeschrieben ist,
 * darf nicht mehr still veraendert werden. Geprueft wird beides -- dass die
 * gesperrten Felder wirklich blockiert sind und dass die Kontierung
 * weiterhin durchgeht, weil sonst jeder Buchungsfehler des Steuerberaters
 * zwei zusaetzliche Zeilen im Kassenbuch kostet.</p>
 *
 * <p>DSGVO: nur Dummy-Daten (Max Mustermann).</p>
 */
@ExtendWith(MockitoExtension.class)
class KassenbuchFestschreibungTest {

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
    @Mock private BelegAuditService auditService;
    @Mock private KassenbuchSchreibschutz schreibschutz;
    @Mock private KassenbuchMonatsabschlussRepository monatsabschlussRepository;

    @InjectMocks private BelegService service;

    private Mitarbeiter maxMustermann;

    @BeforeEach
    void setUp() {
        maxMustermann = new Mitarbeiter();
        maxMustermann.setId(1L);
        maxMustermann.setVorname("Max");
        maxMustermann.setNachname("Mustermann");
        lenient().when(belegRepository.save(any(Beleg.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(kasseSaldoService.projiziereSaldo(any(), any(), any(), any()))
                .thenReturn(new BigDecimal("1000.00"));
    }

    // ===================== Gesperrte Felder =====================

    @Test
    @DisplayName("Betrag eines festgeschriebenen Belegs lässt sich nicht ändern")
    void betragIstGesperrt() {
        Beleg beleg = festgeschriebenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setBetragBrutto(new BigDecimal("999.00"));

        assertThatThrownBy(() -> service.updateBeleg(1L, req, maxMustermann))
                .isInstanceOf(KassenbuchGesperrtException.class)
                .hasMessageContaining("Brutto-Betrag");

        // Nichts angefasst, nichts gespeichert.
        assertThat(beleg.getBetragBrutto()).isEqualByComparingTo("19.99");
        verify(belegRepository, never()).save(any());
    }

    @Test
    @DisplayName("Datum, MwSt, Kategorie, Zahlungsart und Verwendungszweck sind ebenfalls gesperrt")
    void weitereFelderSindGesperrt() {
        pruefeGesperrt(r -> r.setBelegDatum(LocalDate.of(2020, 1, 1)), "Datum");
        pruefeGesperrt(r -> r.setMwstSatz(new BigDecimal("7.00")), "MwSt-Satz");
        pruefeGesperrt(r -> r.setBelegKategorie("KASSE_EINNAHME"), "Art der Buchung");
        pruefeGesperrt(r -> r.setZahlungsart("EC"), "Zahlungsart");
        pruefeGesperrt(r -> r.setBeschreibung("Etwas ganz anderes"), "Verwendungszweck");
        pruefeGesperrt(r -> r.setBelegNummer("ANDERS-1"), "Belegnummer");
        pruefeGesperrt(r -> r.setStatus("VERWORFEN"), "Status");
    }

    @Test
    @DisplayName("Die Fehlermeldung sagt, was der Nutzer stattdessen tun kann")
    void fehlermeldungNenntDenAusweg() {
        Beleg beleg = festgeschriebenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));
        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setBetragBrutto(new BigDecimal("50.00"));

        assertThatThrownBy(() -> service.updateBeleg(1L, req, maxMustermann))
                .isInstanceOf(KassenbuchGesperrtException.class)
                .satisfies(e -> assertThat(((KassenbuchGesperrtException) e).getLoesungshinweis())
                        .contains("Storniere"));
    }

    @Test
    @DisplayName("Unveränderte Werte mitzusenden ist erlaubt – das Frontend schickt immer alles")
    void unveraenderteWerteSindErlaubt() {
        Beleg beleg = festgeschriebenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setBetragBrutto(new BigDecimal("19.99"));
        req.setBelegDatum(beleg.getBelegDatum());
        req.setBeschreibung(beleg.getBeschreibung());
        req.setStatus("VALIDIERT");

        assertThatCode(() -> service.updateBeleg(1L, req, maxMustermann)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ein anderer Nachkommastellen-Aufbau desselben Betrags gilt nicht als Änderung")
    void gleicherBetragMitAndererSkalierung() {
        Beleg beleg = festgeschriebenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setBetragBrutto(new BigDecimal("19.9900"));

        assertThatCode(() -> service.updateBeleg(1L, req, maxMustermann)).doesNotThrowAnyException();
    }

    // ===================== Offene Kontierung =====================

    @Test
    @DisplayName("Sachkonto und Kostenstelle bleiben auch nach der Festschreibung änderbar")
    void kontierungBleibtOffen() {
        Beleg beleg = festgeschriebenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        Sachkonto neuesKonto = new Sachkonto();
        neuesKonto.setId(9L);
        neuesKonto.setNummer("4930");
        given(sachkontoRepository.findById(9L)).willReturn(Optional.of(neuesKonto));

        Kostenstelle werkstatt = new Kostenstelle();
        werkstatt.setId(3L);
        werkstatt.setBezeichnung("Werkstatt");
        given(kostenstelleRepository.findById(3L)).willReturn(Optional.of(werkstatt));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setSachkontoId(9L);
        req.setKostenstelleId(3L);
        req.setNotiz("Vom Steuerberater umkontiert");

        service.updateBeleg(1L, req, maxMustermann);

        assertThat(beleg.getSachkonto()).isSameAs(neuesKonto);
        assertThat(beleg.getKostenstelle()).isSameAs(werkstatt);
        assertThat(beleg.getNotiz()).isEqualTo("Vom Steuerberater umkontiert");
    }

    @Test
    @DisplayName("Jede Änderung an der Kontierung landet mit altem und neuem Wert im Protokoll")
    void kontierungsaenderungWirdProtokolliert() {
        Beleg beleg = festgeschriebenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        Kostenstelle werkstatt = new Kostenstelle();
        werkstatt.setId(3L);
        werkstatt.setBezeichnung("Werkstatt");
        given(kostenstelleRepository.findById(3L)).willReturn(Optional.of(werkstatt));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setKostenstelleId(3L);

        service.updateBeleg(1L, req, maxMustermann);

        org.mockito.ArgumentCaptor<String> grund = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(auditService).protokolliereAenderung(any(), any(), grund.capture(), any());
        assertThat(grund.getValue()).contains("Kostenstelle").contains("Werkstatt");
    }

    @Test
    @DisplayName("Ein Update ohne echte Änderung erzeugt keinen Protokolleintrag")
    void leerlaufErzeugtKeinRauschen() {
        Beleg beleg = festgeschriebenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        service.updateBeleg(1L, new BelegDto.UpdateRequest(), maxMustermann);

        verify(auditService, never()).protokolliereAenderung(any(), any(), anyString(), any());
    }

    // ===================== Offene Belege =====================

    @Test
    @DisplayName("Ein noch nicht festgeschriebener Beleg lässt sich frei korrigieren")
    void offenerBelegBleibtFreiAenderbar() {
        Beleg beleg = offenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setBetragBrutto(new BigDecimal("25.00"));
        req.setBeschreibung("Korrigiert");

        service.updateBeleg(1L, req, maxMustermann);

        assertThat(beleg.getBetragBrutto()).isEqualByComparingTo("25.00");
        assertThat(beleg.getBeschreibung()).isEqualTo("Korrigiert");
    }

    @Test
    @DisplayName("Das Prüfen eines Belegs wird als eigener Vorgang protokolliert")
    void pruefenWirdProtokolliert() {
        Beleg beleg = offenerBeleg();
        beleg.setStatus(BelegStatus.NEU);
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setStatus("VALIDIERT");

        service.updateBeleg(1L, req, maxMustermann);

        verify(auditService).protokolliereValidierung(any(), any(), any());
        assertThat(beleg.getValidiertVon()).isSameAs(maxMustermann);
        assertThat(beleg.getValidiertAm()).isNotNull();
    }

    // ===================== Verwerfen =====================

    @Test
    @DisplayName("Ein festgeschriebener Beleg kann nicht verworfen werden")
    void festgeschriebenerBelegWirdNichtVerworfen() {
        Beleg beleg = festgeschriebenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        assertThatThrownBy(() -> service.deleteBeleg(1L, "Passt nicht", maxMustermann))
                .isInstanceOf(KassenbuchGesperrtException.class)
                .hasMessageContaining("festgeschrieben");

        assertThat(beleg.getStatus()).isEqualTo(BelegStatus.VALIDIERT);
    }

    @Test
    @DisplayName("Ein offener Beleg wird verworfen und der Vorgang protokolliert")
    void offenerBelegWirdVerworfen() {
        Beleg beleg = offenerBeleg();
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        boolean ok = service.deleteBeleg(1L, "Doppelt gescannt", maxMustermann);

        assertThat(ok).isTrue();
        assertThat(beleg.getStatus()).isEqualTo(BelegStatus.VERWORFEN);
        verify(auditService).protokolliereVerwerfen(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Zweimal verwerfen erzeugt keinen zweiten Protokolleintrag")
    void doppeltesVerwerfenIstFolgenlos() {
        Beleg beleg = offenerBeleg();
        beleg.setStatus(BelegStatus.VERWORFEN);
        given(belegRepository.findById(1L)).willReturn(Optional.of(beleg));

        assertThat(service.deleteBeleg(1L, "Nochmal", maxMustermann)).isTrue();
        verify(auditService, never()).protokolliereVerwerfen(any(), any(), anyString(), any());
    }

    // ===================== Abgeschlossener Monat =====================

    @Test
    @DisplayName("In einen abgeschlossenen Monat kann keine Umbuchung mehr gebucht werden")
    void umbuchungInAbgeschlossenenMonat() {
        org.mockito.Mockito.doThrow(new KassenbuchGesperrtException(
                        "Der Monat dieses Datums ist bereits abgeschlossen."))
                .when(schreibschutz).assertMonatOffen(LocalDate.of(2026, 1, 15));

        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("PRIVATENTNAHME");
        req.setBetragBrutto(new BigDecimal("100.00"));
        req.setBelegDatum(LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> service.createUmbuchung(req, maxMustermann))
                .isInstanceOf(KassenbuchGesperrtException.class)
                .hasMessageContaining("bereits abgeschlossen");

        verify(belegRepository, never()).save(any());
    }

    // ===================== Hilfen =====================

    private void pruefeGesperrt(java.util.function.Consumer<BelegDto.UpdateRequest> aenderung,
                                String erwartetesFeld) {
        Beleg beleg = festgeschriebenerBeleg();
        lenient().when(belegRepository.findById(1L)).thenReturn(Optional.of(beleg));

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        aenderung.accept(req);

        assertThatThrownBy(() -> service.updateBeleg(1L, req, maxMustermann))
                .isInstanceOf(KassenbuchGesperrtException.class)
                .hasMessageContaining(erwartetesFeld);
    }

    private Beleg festgeschriebenerBeleg() {
        Beleg b = offenerBeleg();
        b.setFestgeschrieben(true);
        b.setFestgeschriebenAm(LocalDateTime.now().minusDays(5));
        b.setLaufendeNummer(12L);
        return b;
    }

    private Beleg offenerBeleg() {
        Beleg b = new Beleg();
        b.setId(1L);
        b.setStatus(BelegStatus.VALIDIERT);
        b.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
        b.setBelegDatum(LocalDate.of(2026, 3, 14));
        b.setBelegNummer("BON-4711");
        b.setBeschreibung("Schrauben und Dübel");
        b.setBetragBrutto(new BigDecimal("19.99"));
        b.setBetragNetto(new BigDecimal("16.80"));
        b.setMwstSatz(new BigDecimal("19.00"));
        b.setZahlungsart("BAR");
        b.setUploadDatum(LocalDateTime.now());
        b.setGespeicherterDateiname("uuid_bon.jpg");
        return b;
    }
}
