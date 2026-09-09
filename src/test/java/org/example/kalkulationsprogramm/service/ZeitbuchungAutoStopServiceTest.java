package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import java.util.Optional;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZeitbuchungAutoStopServiceTest {

    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private ZeitkontoVersionRepository zeitkontoVersionRepository;
    @Mock private ZeitbuchungAuditService auditService;
    @Mock private MonatsSaldoService monatsSaldoService;

    private ZeitbuchungAutoStopService service;

    @BeforeEach
    void setUp() {
        service = new ZeitbuchungAutoStopService(zeitbuchungRepository, zeitkontoVersionRepository, auditService, monatsSaldoService);
    }

    private Mitarbeiter erstelleMitarbeiter(Long id) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        return m;
    }

    private ZeitkontoVersion erstelleZeitkonto(Long id, Mitarbeiter mitarbeiter, LocalTime buchungEndeZeit) {
        ZeitkontoVersion konto = new ZeitkontoVersion();
        konto.setId(id);
        konto.setMitarbeiter(mitarbeiter);
        konto.setBuchungEndeZeit(buchungEndeZeit);
        return konto;
    }

    @Nested
    class AutoStoppeWennNoetig {

        @Test
        void stopptBuchungUeberMitternacht() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L);
            ZeitkontoVersion konto = erstelleZeitkonto(1L, mitarbeiter, null);

            Zeitbuchung buchung = new Zeitbuchung();
            buchung.setId(100L);
            buchung.setMitarbeiter(mitarbeiter);
            // Buchung startete gestern um 20:00
            buchung.setStartZeit(LocalDate.now().minusDays(1).atTime(20, 0));
            buchung.setVersion(1);

            service.autoStoppeWennNoetig(buchung, konto);

            assertThat(buchung.getEndeZeit()).isNotNull();
            // Sollte bei 23:59 des Start-Tages gestoppt werden
            assertThat(buchung.getEndeZeit().toLocalTime()).isEqualTo(LocalTime.of(23, 59, 0));
            assertThat(buchung.getEndeZeit().toLocalDate()).isEqualTo(LocalDate.now().minusDays(1));
            assertThat(buchung.getAnzahlInStunden()).isNotNull();
            verify(zeitbuchungRepository).save(buchung);
            verify(auditService).protokolliereAenderung(eq(buchung), eq(mitarbeiter), any(), any());
        }

        @Test
        void berechnetStundenKorrektBeiMitternachtStop() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L);
            ZeitkontoVersion konto = erstelleZeitkonto(1L, mitarbeiter, null);

            Zeitbuchung buchung = new Zeitbuchung();
            buchung.setId(100L);
            buchung.setMitarbeiter(mitarbeiter);
            // Startete gestern um 22:00 -> 1h 59min bis 23:59
            buchung.setStartZeit(LocalDate.now().minusDays(1).atTime(22, 0));
            buchung.setVersion(1);

            service.autoStoppeWennNoetig(buchung, konto);

            // 22:00 bis 23:59 = 119 Minuten = 1.98 Stunden
            assertThat(buchung.getAnzahlInStunden()).isEqualByComparingTo(new BigDecimal("1.98"));
        }

        // Regression: ErfassungsQuelle.SYSTEM fehlte im MySQL-ENUM der zeitbuchung_audit-Tabelle,
        // was zu "Data truncated for column 'geaendert_via'" beim Auto-Stop führte (V314 fix).
        @Test
        void uebergibt_ErfassungsQuelle_SYSTEM_an_AuditService() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L);
            ZeitkontoVersion konto = erstelleZeitkonto(1L, mitarbeiter, null);

            Zeitbuchung buchung = new Zeitbuchung();
            buchung.setId(100L);
            buchung.setMitarbeiter(mitarbeiter);
            buchung.setStartZeit(LocalDate.now().minusDays(1).atTime(20, 0));
            buchung.setVersion(1);

            service.autoStoppeWennNoetig(buchung, konto);

            ArgumentCaptor<ErfassungsQuelle> quelleCaptor = ArgumentCaptor.forClass(ErfassungsQuelle.class);
            verify(auditService).protokolliereAenderung(
                    eq(buchung), eq(mitarbeiter), quelleCaptor.capture(), any());
            assertThat(quelleCaptor.getValue()).isEqualTo(ErfassungsQuelle.SYSTEM);
        }

        // Regression 30.07.2026: Der Auto-Stop hat drei Mitarbeitern eine geschätzte
        // Endezeit von 20:00 in die Zeitkonten geschrieben, ohne dass das irgendwo
        // erkennbar war. Die erfundenen Stunden liefen ungeprüft in den Monatssaldo.
        @Test
        void markiertAutomatischBeendeteBuchungAlsPruefFall() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L);
            ZeitkontoVersion konto = erstelleZeitkonto(1L, mitarbeiter, null);

            Zeitbuchung buchung = new Zeitbuchung();
            buchung.setId(100L);
            buchung.setMitarbeiter(mitarbeiter);
            buchung.setStartZeit(LocalDate.now().minusDays(1).atTime(20, 0));
            buchung.setVersion(1);
            assertThat(buchung.isAutomatischBeendet()).isFalse();

            service.autoStoppeWennNoetig(buchung, konto);

            assertThat(buchung.isAutomatischBeendet()).isTrue();
        }

        @Test
        void stopptNichtWennBuchungHeute() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L);
            // Kein buchungEndeZeit gesetzt
            ZeitkontoVersion konto = erstelleZeitkonto(1L, mitarbeiter, null);

            Zeitbuchung buchung = new Zeitbuchung();
            buchung.setId(100L);
            buchung.setMitarbeiter(mitarbeiter);
            // Buchung startete heute morgen um 08:00
            buchung.setStartZeit(LocalDate.now().atTime(8, 0));

            service.autoStoppeWennNoetig(buchung, konto);

            // Sollte nicht gestoppt werden (kein Mitternacht-Überlauf, kein buchungEndeZeit)
            assertThat(buchung.getEndeZeit()).isNull();
            verify(zeitbuchungRepository, never()).save(any());
        }
    }

    @Nested
    class PruefUndStoppeOffeneBuchungen {

        @Test
        void verarbeitetAlleOffenenBuchungenAllerZeitkonten() {
            Mitarbeiter m1 = erstelleMitarbeiter(1L);
            ZeitkontoVersion konto1 = erstelleZeitkonto(1L, m1, null);

            // Offene Buchung von gestern
            Zeitbuchung offeneBuchung = new Zeitbuchung();
            offeneBuchung.setId(100L);
            offeneBuchung.setMitarbeiter(m1);
            offeneBuchung.setStartZeit(LocalDate.now().minusDays(1).atTime(20, 0));
            offeneBuchung.setVersion(1);

            when(zeitkontoVersionRepository.findAm(1L, offeneBuchung.getStartZeit().toLocalDate()))
                    .thenReturn(Optional.of(konto1));
            when(zeitbuchungRepository.findByEndeZeitIsNull())
                    .thenReturn(List.of(offeneBuchung));

            service.pruefUndStoppeOffeneBuchungen();

            verify(zeitbuchungRepository).save(offeneBuchung);
            assertThat(offeneBuchung.getEndeZeit()).isNotNull();
        }

        @Test
        void behandeltLeereListeOhneError() {
            when(zeitbuchungRepository.findByEndeZeitIsNull()).thenReturn(Collections.emptyList());

            service.pruefUndStoppeOffeneBuchungen();

            verifyNoInteractions(zeitkontoVersionRepository);
        }
    }

    @Test
    void verwendetBuchungstagTrotzAusgeschaltetemKontoUndSpaeteremLauf() {
        Mitarbeiter m = erstelleMitarbeiter(1L);
        m.setFuehrtZeitkonto(false);
        m.setAktiv(false);
        LocalDate gestern = LocalDate.now().minusDays(1);
        Zeitbuchung b = offeneBuchung(m, gestern.atTime(8, 0));
        ZeitkontoVersion alt = erstelleZeitkonto(1L, m, LocalTime.of(17, 0));
        alt.setGueltigVon(gestern.minusYears(1));
        alt.setGueltigBis(gestern);
        when(zeitbuchungRepository.findByEndeZeitIsNull()).thenReturn(List.of(b));
        when(zeitkontoVersionRepository.findAm(1L, gestern)).thenReturn(Optional.of(alt));

        service.pruefUndStoppeOffeneBuchungen();

        assertThat(b.getEndeZeit()).isEqualTo(gestern.atTime(17, 0));
        assertThat(b.getAnzahlInStunden()).isEqualByComparingTo("9.00");
        assertThat(b.isAutomatischBeendet()).isTrue();
        verify(zeitkontoVersionRepository).findAm(1L, gestern);
        verify(zeitkontoVersionRepository, never()).findAm(1L, LocalDate.now());
        verify(monatsSaldoService).invalidiereFuerDateTime(1L, b.getStartZeit());
        verify(auditService).protokolliereAenderung(eq(b), eq(m), eq(ErfassungsQuelle.SYSTEM), any());
    }

    @Test
    void ohneVersionWerdenOffeneBuchungenWeiterBehandeltUndLookupGeteilt() {
        Mitarbeiter m = erstelleMitarbeiter(1L);
        m.setFuehrtZeitkonto(false);
        LocalDate gestern = LocalDate.now().minusDays(1);
        Zeitbuchung b1 = offeneBuchung(m, gestern.atTime(20, 0));
        Zeitbuchung b2 = offeneBuchung(m, gestern.atTime(22, 0));
        when(zeitbuchungRepository.findByEndeZeitIsNull()).thenReturn(List.of(b1, b2));
        when(zeitkontoVersionRepository.findAm(1L, gestern)).thenReturn(Optional.empty());
        service.pruefUndStoppeOffeneBuchungen();
        assertThat(b1.getEndeZeit()).isEqualTo(gestern.atTime(23, 59));
        assertThat(b2.getEndeZeit()).isEqualTo(gestern.atTime(23, 59));
        verify(zeitkontoVersionRepository, times(1)).findAm(1L, gestern);
        verify(zeitbuchungRepository).save(b1);
        verify(zeitbuchungRepository).save(b2);
    }

    @Test
    void systemUndUnvollstaendigeBuchungenWerdenUebersprungen() {
        Mitarbeiter system = erstelleMitarbeiter(2L);
        system.setArt(MitarbeiterArt.SYSTEM);
        LocalDateTime gestern = LocalDate.now().minusDays(1).atTime(20, 0);
        when(zeitbuchungRepository.findByEndeZeitIsNull()).thenReturn(List.of(
                offeneBuchung(system, gestern), offeneBuchung(null, gestern),
                offeneBuchung(erstelleMitarbeiter(1L), null)));
        service.pruefUndStoppeOffeneBuchungen();
        verifyNoInteractions(zeitkontoVersionRepository, auditService, monatsSaldoService);
        verify(zeitbuchungRepository, never()).save(any());
    }

    @Test
    void startNachZeitfensterWirdNichtRueckwaertsBeendet() {
        Mitarbeiter m = erstelleMitarbeiter(1L);
        LocalDate gestern = LocalDate.now().minusDays(1);
        Zeitbuchung b = offeneBuchung(m, gestern.atTime(23, 59, 30));
        service.autoStoppeWennNoetig(b, erstelleZeitkonto(1L, m, LocalTime.of(17, 0)));
        assertThat(b.getEndeZeit()).isEqualTo(b.getStartZeit());
        assertThat(b.getAnzahlInStunden()).isEqualByComparingTo("0.00");
    }

    @Test
    void ohneVersionHeuteBleibtBuchungOffen() {
        Zeitbuchung b = offeneBuchung(erstelleMitarbeiter(1L), LocalDate.now().atStartOfDay());
        service.autoStoppeWennNoetig(b, null);
        assertThat(b.getEndeZeit()).isNull();
        verify(zeitbuchungRepository, never()).save(any());
    }

    private Zeitbuchung offeneBuchung(Mitarbeiter m, LocalDateTime start) {
        Zeitbuchung b = new Zeitbuchung();
        b.setMitarbeiter(m);
        b.setStartZeit(start);
        return b;
    }

    @Nested
    class MonatsSaldoInvalidierung {

        @Test
        void invalidertMonatsSaldoNachAutoStop() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L);
            ZeitkontoVersion konto = erstelleZeitkonto(1L, mitarbeiter, null);

            Zeitbuchung buchung = new Zeitbuchung();
            buchung.setId(100L);
            buchung.setMitarbeiter(mitarbeiter);
            buchung.setStartZeit(LocalDate.now().minusDays(1).atTime(20, 0));
            buchung.setVersion(1);

            service.autoStoppeWennNoetig(buchung, konto);

            verify(monatsSaldoService).invalidiereFuerDateTime(eq(1L), eq(buchung.getStartZeit()));
        }
    }
}
