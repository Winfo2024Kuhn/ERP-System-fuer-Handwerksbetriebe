package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit-Tests fuer den KasseShortcutService — Bequemlichkeits-Buchungen ohne
 * Belegdatei (Bank-Abhebung, Privateinlage, Privatentnahme, Lohnzahlung mit
 * Auto-Privateinlage). DSGVO-Dummy: Empfaenger "Diana Mustermann".
 */
@ExtendWith(MockitoExtension.class)
class KasseShortcutServiceTest {

    @Mock private BelegRepository belegRepository;
    @Mock private KasseEinstellungRepository kasseEinstellungRepository;
    @Mock private KasseSaldoService kasseSaldoService;
    // Seit der Festschreibung laufen alle Schnellbuchungen durch den
    // Schreibschutz (abgeschlossener Monat?) und schreiben einen
    // Protokolleintrag. Beides hier gemockt -- geprueft wird es in
    // KasseShortcutFestschreibungTest.
    @Mock private KassenbuchSchreibschutz schreibschutz;
    @Mock private BelegAuditService auditService;

    private KasseShortcutService service;

    @BeforeEach
    void setUp() {
        service = new KasseShortcutService(belegRepository, kasseEinstellungRepository,
                kasseSaldoService, schreibschutz, auditService);
        // lenient: nicht jeder Test geht in den save-Pfad (Validierungs-Fehler bricht vorher ab).
        org.mockito.Mockito.lenient()
                .when(belegRepository.save(any(Beleg.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("bankAbhebung: erzeugt validierte KASSE_EINNAHME mit istUmbuchung=true")
    void bankAbhebung_happyPath() {
        Beleg result = service.bankAbhebung(
                new BigDecimal("500.00"), LocalDate.of(2026, 5, 13),
                "EC-001", "Bargeld geholt", mitarbeiter());

        assertThat(result.getBelegKategorie()).isEqualTo(BelegKategorie.KASSE_EINNAHME);
        assertThat(result.getStatus()).isEqualTo(BelegStatus.VALIDIERT);
        assertThat(result.getIstUmbuchung()).isTrue();
        assertThat(result.getBetragBrutto()).isEqualByComparingTo("500.00");
        assertThat(result.getBeschreibung()).isEqualTo("Bargeld geholt");
        assertThat(result.getBelegNummer()).isEqualTo("EC-001");
    }

    @Test
    @DisplayName("bankAbhebung: ohne Betrag -> IllegalArgumentException")
    void bankAbhebung_ohneBetrag_wirftFehler() {
        assertThatThrownBy(() -> service.bankAbhebung(
                null, LocalDate.of(2026, 5, 13), "EC-002", "x", mitarbeiter()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(belegRepository, never()).save(any());
    }

    @Test
    @DisplayName("privatEinlage: setzt Sachkonto aus Einstellung")
    void privatEinlage_setztSachkontoAusEinstellung() {
        Sachkonto sk = new Sachkonto();
        sk.setId(99L);
        KasseEinstellung k = new KasseEinstellung();
        k.setPrivateinlageSachkonto(sk);
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));

        Beleg result = service.privatEinlage(new BigDecimal("100.00"),
                LocalDate.of(2026, 5, 13), null, mitarbeiter());

        assertThat(result.getBelegKategorie()).isEqualTo(BelegKategorie.PRIVATEINLAGE);
        assertThat(result.getSachkonto()).isSameAs(sk);
        assertThat(result.getBeschreibung()).isEqualTo("Privateinlage");
    }

    @Test
    @DisplayName("privatEinlage: ohne Einstellung -> Sachkonto bleibt null, Beleg trotzdem ok")
    void privatEinlage_ohneEinstellung_keinSachkonto() {
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.empty());

        Beleg result = service.privatEinlage(new BigDecimal("100.00"),
                LocalDate.of(2026, 5, 13), "custom", mitarbeiter());

        assertThat(result.getSachkonto()).isNull();
        assertThat(result.getBeschreibung()).isEqualTo("custom");
    }

    @Test
    @DisplayName("privatEntnahme: bei Unterdeckung wirft Exception und speichert nichts")
    void privatEntnahme_unterdeckung_wirftException() {
        given(kasseSaldoService.projiziereSaldo(any(), any(), any(), any()))
                .willReturn(new BigDecimal("-50.00"));
        org.mockito.Mockito.doThrow(new KasseUnterdeckungException(
                        new BigDecimal("-50.00"), new BigDecimal("0.00")))
                .when(kasseSaldoService).assertSaldoMindestensMindestbestand(any());

        assertThatThrownBy(() -> service.privatEntnahme(
                new BigDecimal("500.00"), LocalDate.of(2026, 5, 13), null, mitarbeiter()))
                .isInstanceOf(KasseUnterdeckungException.class);
        verify(belegRepository, never()).save(any());
    }

    @Test
    @DisplayName("lohnZahlung ohne Auto-Privateinlage: Saldo reicht, nur Lohnbeleg gespeichert")
    void lohnZahlung_saldoReicht_keineEinlage() {
        given(kasseSaldoService.getMindestbestand()).willReturn(new BigDecimal("0.00"));
        given(kasseSaldoService.berechneAktuellenSaldo())
                .willReturn(new BigDecimal("1000.00"))
                .willReturn(new BigDecimal("700.00"));

        Sachkonto sk = new Sachkonto();
        sk.setId(42L);

        KasseShortcutService.LohnZahlungResult result = service.lohnZahlung(
                new BigDecimal("300.00"), LocalDate.of(2026, 5, 13),
                "Diana Mustermann", sk, null, mitarbeiter());

        assertThat(result.privateinlage()).isNull();
        assertThat(result.lohnBeleg().getBelegKategorie()).isEqualTo(BelegKategorie.KASSE_AUSGABE);
        assertThat(result.lohnBeleg().getBeschreibung()).contains("Diana Mustermann");
        assertThat(result.neuerSaldo()).isEqualByComparingTo("700.00");
        verify(belegRepository, times(1)).save(any(Beleg.class));
    }

    @Test
    @DisplayName("lohnZahlung mit Auto-Privateinlage: Saldo reicht nicht -> vorher Einlage in passender Hoehe")
    void lohnZahlung_unterdeckung_buchtVorherEinlage() {
        given(kasseSaldoService.getMindestbestand()).willReturn(new BigDecimal("50.00"));
        given(kasseSaldoService.berechneAktuellenSaldo())
                .willReturn(new BigDecimal("100.00"))
                .willReturn(new BigDecimal("50.00"));
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.empty());

        Sachkonto sk = new Sachkonto();
        sk.setId(42L);

        KasseShortcutService.LohnZahlungResult result = service.lohnZahlung(
                new BigDecimal("300.00"), LocalDate.of(2026, 5, 13),
                "Diana Mustermann", sk, null, mitarbeiter());

        // Erwartete Einlage = mindestbestand - (saldo - betrag) = 50 - (100 - 300) = 250.
        assertThat(result.privateinlage()).isNotNull();
        assertThat(result.privateinlage().getBelegKategorie()).isEqualTo(BelegKategorie.PRIVATEINLAGE);
        assertThat(result.privateinlage().getBetragBrutto()).isEqualByComparingTo("250.00");

        ArgumentCaptor<Beleg> captor = ArgumentCaptor.forClass(Beleg.class);
        verify(belegRepository, atLeastOnce()).save(captor.capture());
        List<Beleg> all = captor.getAllValues();
        assertThat(all).extracting(Beleg::getBelegKategorie)
                .contains(BelegKategorie.PRIVATEINLAGE, BelegKategorie.KASSE_AUSGABE);
    }

    @Test
    @DisplayName("lohnZahlung ohne Sachkonto -> IllegalArgumentException")
    void lohnZahlung_ohneSachkonto_wirftFehler() {
        assertThatThrownBy(() -> service.lohnZahlung(
                new BigDecimal("200.00"), LocalDate.of(2026, 5, 13),
                "Diana Mustermann", null, null, mitarbeiter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sachkonto");
        verify(belegRepository, never()).save(any());
    }

    // ===================== Festschreibung =====================
    //
    // Die Schnellbuchungen waren der bequemste Weg an der Festschreibung
    // vorbei: Sie legen sofort validierte Bar-Belege mit einem frei
    // waehlbaren Datum an. Ohne die beiden folgenden Zusagen liesse sich in
    // einen abgeschlossenen Monat nachbuchen, und es entstuenden
    // Kassenbuch-Zeilen ganz ohne Protokollspur -- waehrend die Pruefung
    // weiterhin "Protokoll unversehrt" meldete.

    @Test
    @DisplayName("Bank-Abhebung prüft den Monat und schreibt ins Protokoll")
    void bankAbhebung_pruefungUndProtokoll() {
        LocalDate datum = LocalDate.of(2026, 5, 13);

        service.bankAbhebung(new BigDecimal("500.00"), datum, "AB-1", "Abhebung", mitarbeiter());

        verify(schreibschutz).assertMonatOffen(datum);
        verify(auditService).protokolliereErfassung(any(Beleg.class), any(), any());
    }

    @Test
    @DisplayName("Privateinlage prüft den Monat und schreibt ins Protokoll")
    void privatEinlage_pruefungUndProtokoll() {
        LocalDate datum = LocalDate.of(2026, 5, 13);

        service.privatEinlage(new BigDecimal("100.00"), datum, "Einlage", mitarbeiter());

        verify(schreibschutz).assertMonatOffen(datum);
        verify(auditService).protokolliereErfassung(any(Beleg.class), any(), any());
    }

    @Test
    @DisplayName("Privatentnahme prüft den Monat und schreibt ins Protokoll")
    void privatEntnahme_pruefungUndProtokoll() {
        LocalDate datum = LocalDate.of(2026, 5, 13);
        given(kasseSaldoService.projiziereSaldo(any(), any(), any(), any()))
                .willReturn(new BigDecimal("900.00"));

        service.privatEntnahme(new BigDecimal("100.00"), datum, "Entnahme", mitarbeiter());

        verify(schreibschutz).assertMonatOffen(datum);
        verify(auditService).protokolliereErfassung(any(Beleg.class), any(), any());
    }

    @Test
    @DisplayName("Ein abgeschlossener Monat blockt die Schnellbuchung, bevor etwas gespeichert wird")
    void abgeschlossenerMonatBlocktSchnellbuchung() {
        LocalDate datum = LocalDate.of(2026, 1, 15);
        org.mockito.Mockito.doThrow(new KassenbuchGesperrtException("Monat ist abgeschlossen."))
                .when(schreibschutz).assertMonatOffen(datum);

        assertThatThrownBy(() -> service.bankAbhebung(
                new BigDecimal("500.00"), datum, "AB-1", "Abhebung", mitarbeiter()))
                .isInstanceOf(KassenbuchGesperrtException.class);

        verify(belegRepository, never()).save(any());
        verify(auditService, never()).protokolliereErfassung(any(), any(), any());
    }

    private static Mitarbeiter mitarbeiter() {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(1L);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        return m;
    }
}
