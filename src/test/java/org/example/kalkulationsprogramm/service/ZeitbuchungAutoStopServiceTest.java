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
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZeitbuchungAutoStopServiceTest {

    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private ZeitkontoRepository zeitkontoRepository;
    @Mock private ZeitbuchungAuditService auditService;
    @Mock private MonatsSaldoService monatsSaldoService;

    private ZeitbuchungAutoStopService service;

    @BeforeEach
    void setUp() {
        service = new ZeitbuchungAutoStopService(zeitbuchungRepository, zeitkontoRepository, auditService, monatsSaldoService);
    }

    private Mitarbeiter erstelleMitarbeiter(Long id) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        return m;
    }

    private Zeitkonto erstelleZeitkonto(Long id, Mitarbeiter mitarbeiter, LocalTime buchungEndeZeit) {
        Zeitkonto konto = new Zeitkonto();
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
            Zeitkonto konto = erstelleZeitkonto(1L, mitarbeiter, null);

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
            Zeitkonto konto = erstelleZeitkonto(1L, mitarbeiter, null);

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

        @Test
        void stopptNichtWennBuchungHeute() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L);
            // Kein buchungEndeZeit gesetzt
            Zeitkonto konto = erstelleZeitkonto(1L, mitarbeiter, null);

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
            Zeitkonto konto1 = erstelleZeitkonto(1L, m1, null);

            // Offene Buchung von gestern
            Zeitbuchung offeneBuchung = new Zeitbuchung();
            offeneBuchung.setId(100L);
            offeneBuchung.setMitarbeiter(m1);
            offeneBuchung.setStartZeit(LocalDate.now().minusDays(1).atTime(20, 0));
            offeneBuchung.setVersion(1);

            when(zeitkontoRepository.findAll()).thenReturn(List.of(konto1));
            when(zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(1L))
                    .thenReturn(List.of(offeneBuchung));

            service.pruefUndStoppeOffeneBuchungen();

            verify(zeitbuchungRepository).save(offeneBuchung);
            assertThat(offeneBuchung.getEndeZeit()).isNotNull();
        }

        @Test
        void behandeltLeereListeOhneError() {
            when(zeitkontoRepository.findAll()).thenReturn(Collections.emptyList());

            service.pruefUndStoppeOffeneBuchungen();

            verify(zeitbuchungRepository, never()).findByMitarbeiterIdAndEndeZeitIsNull(any());
        }
    }

    @Nested
    class MonatsSaldoInvalidierung {

        @Test
        void invalidertMonatsSaldoNachAutoStop() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L);
            Zeitkonto konto = erstelleZeitkonto(1L, mitarbeiter, null);

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
