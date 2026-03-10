package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoKorrekturAuditRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoKorrekturRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZeitkontoKorrekturServiceTest {

    @Mock private ZeitkontoKorrekturRepository korrekturRepository;
    @Mock private ZeitkontoKorrekturAuditRepository auditRepository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;
    @Mock private MonatsSaldoService monatsSaldoService;

    private ZeitkontoKorrekturService service;

    @BeforeEach
    void setUp() {
        service = new ZeitkontoKorrekturService(korrekturRepository, auditRepository, mitarbeiterRepository, monatsSaldoService);
    }

    private Mitarbeiter erstelleMitarbeiter(Long id, String vorname, String nachname) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname(vorname);
        m.setNachname(nachname);
        return m;
    }

    @Nested
    class ErstelleKorrektur {

        @Test
        void erstelltKorrekturErfolgreich() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L, "Max", "Müller");
            Mitarbeiter bearbeiter = erstelleMitarbeiter(2L, "Chef", "Admin");
            when(mitarbeiterRepository.findById(1L)).thenReturn(Optional.of(mitarbeiter));
            when(mitarbeiterRepository.findById(2L)).thenReturn(Optional.of(bearbeiter));
            when(korrekturRepository.save(any())).thenAnswer(inv -> {
                ZeitkontoKorrektur k = inv.getArgument(0);
                k.setId(10L);
                return k;
            });
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ZeitkontoKorrektur result = service.erstelleKorrektur(
                    1L, new BigDecimal("2.50"), LocalDate.of(2025, 6, 15),
                    "Überstundenausgleich", 2L, ErfassungsQuelle.DESKTOP, KorrekturTyp.STUNDEN);

            assertThat(result.getMitarbeiter()).isEqualTo(mitarbeiter);
            assertThat(result.getStunden()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(result.getDatum()).isEqualTo(LocalDate.of(2025, 6, 15));
            assertThat(result.getGrund()).isEqualTo("Überstundenausgleich");
            assertThat(result.getVersion()).isEqualTo(1);
            assertThat(result.getTyp()).isEqualTo(KorrekturTyp.STUNDEN);
        }

        @Test
        void erstelltAuditEintragBeiErstellung() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L, "Max", "Müller");
            Mitarbeiter bearbeiter = erstelleMitarbeiter(2L, "Chef", "Admin");
            when(mitarbeiterRepository.findById(1L)).thenReturn(Optional.of(mitarbeiter));
            when(mitarbeiterRepository.findById(2L)).thenReturn(Optional.of(bearbeiter));
            when(korrekturRepository.save(any())).thenAnswer(inv -> {
                ZeitkontoKorrektur k = inv.getArgument(0);
                k.setId(10L);
                return k;
            });
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.erstelleKorrektur(1L, new BigDecimal("1.00"), LocalDate.now(),
                    "Test", 2L, ErfassungsQuelle.DESKTOP, KorrekturTyp.STUNDEN);

            ArgumentCaptor<ZeitkontoKorrekturAudit> auditCaptor = ArgumentCaptor.forClass(ZeitkontoKorrekturAudit.class);
            verify(auditRepository).save(auditCaptor.capture());
            assertThat(auditCaptor.getValue().getAktion()).isEqualTo(AuditAktion.ERSTELLT);
        }

        @Test
        void invalidertMonatsSaldoCache() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L, "Max", "Müller");
            Mitarbeiter bearbeiter = erstelleMitarbeiter(2L, "Chef", "Admin");
            when(mitarbeiterRepository.findById(1L)).thenReturn(Optional.of(mitarbeiter));
            when(mitarbeiterRepository.findById(2L)).thenReturn(Optional.of(bearbeiter));
            when(korrekturRepository.save(any())).thenAnswer(inv -> {
                ZeitkontoKorrektur k = inv.getArgument(0);
                k.setId(10L);
                return k;
            });
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LocalDate datum = LocalDate.of(2025, 3, 15);
            service.erstelleKorrektur(1L, new BigDecimal("1.00"), datum,
                    "Test", 2L, ErfassungsQuelle.DESKTOP, null);

            verify(monatsSaldoService).invalidiereFuerDatum(1L, datum);
        }

        @Test
        void wirftExceptionOhneGrund() {
            assertThatThrownBy(() -> service.erstelleKorrektur(
                    1L, new BigDecimal("1.00"), LocalDate.now(),
                    "", 2L, ErfassungsQuelle.DESKTOP, KorrekturTyp.STUNDEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Begründung");
        }

        @Test
        void wirftExceptionBeiNullStunden() {
            assertThatThrownBy(() -> service.erstelleKorrektur(
                    1L, null, LocalDate.now(),
                    "Grund", 2L, ErfassungsQuelle.DESKTOP, KorrekturTyp.STUNDEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Korrekturstunden");
        }

        @Test
        void wirftExceptionBeiNullStundenWert() {
            assertThatThrownBy(() -> service.erstelleKorrektur(
                    1L, BigDecimal.ZERO, LocalDate.now(),
                    "Grund", 2L, ErfassungsQuelle.DESKTOP, KorrekturTyp.STUNDEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Korrekturstunden");
        }

        @Test
        void wirftExceptionBeiUnbekanntemMitarbeiter() {
            when(mitarbeiterRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.erstelleKorrektur(
                    999L, new BigDecimal("1.00"), LocalDate.now(),
                    "Grund", 2L, ErfassungsQuelle.DESKTOP, KorrekturTyp.STUNDEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mitarbeiter nicht gefunden");
        }

        @Test
        void setztDefaultTypStundenWennNull() {
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L, "Max", "Müller");
            Mitarbeiter bearbeiter = erstelleMitarbeiter(2L, "Chef", "Admin");
            when(mitarbeiterRepository.findById(1L)).thenReturn(Optional.of(mitarbeiter));
            when(mitarbeiterRepository.findById(2L)).thenReturn(Optional.of(bearbeiter));
            when(korrekturRepository.save(any())).thenAnswer(inv -> {
                ZeitkontoKorrektur k = inv.getArgument(0);
                k.setId(10L);
                return k;
            });
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ZeitkontoKorrektur result = service.erstelleKorrektur(
                    1L, new BigDecimal("1.00"), LocalDate.now(),
                    "Grund", 2L, ErfassungsQuelle.DESKTOP, null);

            assertThat(result.getTyp()).isEqualTo(KorrekturTyp.STUNDEN);
        }
    }

    @Nested
    class AendereKorrektur {

        @Test
        void aendertKorrekturErfolgreich() {
            Mitarbeiter bearbeiter = erstelleMitarbeiter(2L, "Chef", "Admin");
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L, "Max", "Müller");
            ZeitkontoKorrektur korrektur = new ZeitkontoKorrektur();
            korrektur.setId(10L);
            korrektur.setMitarbeiter(mitarbeiter);
            korrektur.setStunden(new BigDecimal("2.00"));
            korrektur.setGrund("Alter Grund");
            korrektur.setDatum(LocalDate.of(2025, 3, 1));
            korrektur.setVersion(1);
            korrektur.setStorniert(false);

            when(korrekturRepository.findById(10L)).thenReturn(Optional.of(korrektur));
            when(mitarbeiterRepository.findById(2L)).thenReturn(Optional.of(bearbeiter));
            when(korrekturRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ZeitkontoKorrektur result = service.aendereKorrektur(
                    10L, new BigDecimal("3.00"), "Neuer Grund",
                    2L, "Korrektur der Stunden", ErfassungsQuelle.DESKTOP);

            assertThat(result.getStunden()).isEqualByComparingTo(new BigDecimal("3.00"));
            assertThat(result.getGrund()).isEqualTo("Neuer Grund");
            assertThat(result.getVersion()).isEqualTo(2);
        }

        @Test
        void wirftExceptionBeiStornierterKorrektur() {
            ZeitkontoKorrektur korrektur = new ZeitkontoKorrektur();
            korrektur.setId(10L);
            korrektur.setStorniert(true);
            when(korrekturRepository.findById(10L)).thenReturn(Optional.of(korrektur));

            assertThatThrownBy(() -> service.aendereKorrektur(
                    10L, new BigDecimal("3.00"), "Neuer Grund",
                    2L, "Grund", ErfassungsQuelle.DESKTOP))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Stornierte Korrekturen");
        }

        @Test
        void wirftExceptionOhneAenderungsgrund() {
            assertThatThrownBy(() -> service.aendereKorrektur(
                    10L, new BigDecimal("3.00"), "Neuer Grund",
                    2L, "", ErfassungsQuelle.DESKTOP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Änderungsgrund");
        }

        @Test
        void erstelltAuditEintragBeiAenderung() {
            Mitarbeiter bearbeiter = erstelleMitarbeiter(2L, "Chef", "Admin");
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L, "Max", "Müller");
            ZeitkontoKorrektur korrektur = new ZeitkontoKorrektur();
            korrektur.setId(10L);
            korrektur.setMitarbeiter(mitarbeiter);
            korrektur.setStunden(new BigDecimal("2.00"));
            korrektur.setGrund("Grund");
            korrektur.setDatum(LocalDate.of(2025, 3, 1));
            korrektur.setVersion(1);
            korrektur.setStorniert(false);

            when(korrekturRepository.findById(10L)).thenReturn(Optional.of(korrektur));
            when(mitarbeiterRepository.findById(2L)).thenReturn(Optional.of(bearbeiter));
            when(korrekturRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.aendereKorrektur(10L, new BigDecimal("3.00"), "Neuer Grund",
                    2L, "Korrektur", ErfassungsQuelle.DESKTOP);

            ArgumentCaptor<ZeitkontoKorrekturAudit> captor = ArgumentCaptor.forClass(ZeitkontoKorrekturAudit.class);
            verify(auditRepository).save(captor.capture());
            assertThat(captor.getValue().getAktion()).isEqualTo(AuditAktion.GEAENDERT);
        }
    }

    @Nested
    class StorniereKorrektur {

        @Test
        void storniertKorrekturErfolgreich() {
            Mitarbeiter bearbeiter = erstelleMitarbeiter(2L, "Chef", "Admin");
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L, "Max", "Müller");
            ZeitkontoKorrektur korrektur = new ZeitkontoKorrektur();
            korrektur.setId(10L);
            korrektur.setMitarbeiter(mitarbeiter);
            korrektur.setStunden(new BigDecimal("2.00"));
            korrektur.setGrund("Grund");
            korrektur.setDatum(LocalDate.of(2025, 3, 1));
            korrektur.setVersion(1);
            korrektur.setStorniert(false);

            when(korrekturRepository.findById(10L)).thenReturn(Optional.of(korrektur));
            when(mitarbeiterRepository.findById(2L)).thenReturn(Optional.of(bearbeiter));
            when(korrekturRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.storniereKorrektur(10L, 2L, "Fehlerhafte Buchung", ErfassungsQuelle.DESKTOP);

            assertThat(korrektur.getStorniert()).isTrue();
            assertThat(korrektur.getStorniertAm()).isNotNull();
            assertThat(korrektur.getStorniertVon()).isEqualTo(bearbeiter);
            assertThat(korrektur.getStornierungsgrund()).isEqualTo("Fehlerhafte Buchung");
            assertThat(korrektur.getVersion()).isEqualTo(2);
        }

        @Test
        void wirftExceptionBeiBereitsStornierterKorrektur() {
            ZeitkontoKorrektur korrektur = new ZeitkontoKorrektur();
            korrektur.setId(10L);
            korrektur.setStorniert(true);
            when(korrekturRepository.findById(10L)).thenReturn(Optional.of(korrektur));

            assertThatThrownBy(() -> service.storniereKorrektur(
                    10L, 2L, "Doppel-Storno", ErfassungsQuelle.DESKTOP))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bereits storniert");
        }

        @Test
        void wirftExceptionOhneStornierungsgrund() {
            assertThatThrownBy(() -> service.storniereKorrektur(
                    10L, 2L, "", ErfassungsQuelle.DESKTOP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Stornierungsgrund");
        }

        @Test
        void erstelltAuditEintragBeiStornierung() {
            Mitarbeiter bearbeiter = erstelleMitarbeiter(2L, "Chef", "Admin");
            Mitarbeiter mitarbeiter = erstelleMitarbeiter(1L, "Max", "Müller");
            ZeitkontoKorrektur korrektur = new ZeitkontoKorrektur();
            korrektur.setId(10L);
            korrektur.setMitarbeiter(mitarbeiter);
            korrektur.setStunden(new BigDecimal("2.00"));
            korrektur.setGrund("Grund");
            korrektur.setDatum(LocalDate.of(2025, 3, 1));
            korrektur.setVersion(1);
            korrektur.setStorniert(false);

            when(korrekturRepository.findById(10L)).thenReturn(Optional.of(korrektur));
            when(mitarbeiterRepository.findById(2L)).thenReturn(Optional.of(bearbeiter));
            when(korrekturRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.storniereKorrektur(10L, 2L, "Storno", ErfassungsQuelle.DESKTOP);

            ArgumentCaptor<ZeitkontoKorrekturAudit> captor = ArgumentCaptor.forClass(ZeitkontoKorrekturAudit.class);
            verify(auditRepository).save(captor.capture());
            assertThat(captor.getValue().getAktion()).isEqualTo(AuditAktion.STORNIERT);
        }
    }

    @Nested
    class SummiereKorrekturen {

        @Test
        void summiertNurAktiveStundenKorrekturen() {
            ZeitkontoKorrektur aktiv1 = new ZeitkontoKorrektur();
            aktiv1.setStunden(new BigDecimal("2.00"));
            aktiv1.setStorniert(false);
            aktiv1.setTyp(KorrekturTyp.STUNDEN);

            ZeitkontoKorrektur aktiv2 = new ZeitkontoKorrektur();
            aktiv2.setStunden(new BigDecimal("3.50"));
            aktiv2.setStorniert(false);
            aktiv2.setTyp(KorrekturTyp.STUNDEN);

            ZeitkontoKorrektur storniert = new ZeitkontoKorrektur();
            storniert.setStunden(new BigDecimal("10.00"));
            storniert.setStorniert(true);
            storniert.setTyp(KorrekturTyp.STUNDEN);

            ZeitkontoKorrektur urlaub = new ZeitkontoKorrektur();
            urlaub.setStunden(new BigDecimal("8.00"));
            urlaub.setStorniert(false);
            urlaub.setTyp(KorrekturTyp.URLAUB);

            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(eq(1L), any(), any()))
                    .thenReturn(List.of(aktiv1, aktiv2, storniert, urlaub));

            BigDecimal summe = service.summiereAktiveKorrekturen(1L, 2025);

            assertThat(summe).isEqualByComparingTo(new BigDecimal("5.50"));
        }

        @Test
        void summiertNurAktiveUrlaubsKorrekturen() {
            ZeitkontoKorrektur urlaub = new ZeitkontoKorrektur();
            urlaub.setStunden(new BigDecimal("8.00"));
            urlaub.setStorniert(false);
            urlaub.setTyp(KorrekturTyp.URLAUB);

            ZeitkontoKorrektur stunden = new ZeitkontoKorrektur();
            stunden.setStunden(new BigDecimal("5.00"));
            stunden.setStorniert(false);
            stunden.setTyp(KorrekturTyp.STUNDEN);

            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(eq(1L), any(), any()))
                    .thenReturn(List.of(urlaub, stunden));

            BigDecimal summe = service.summiereAktiveUrlaubsKorrekturen(1L, 2025);

            assertThat(summe).isEqualByComparingTo(new BigDecimal("8.00"));
        }

        @Test
        void gibtNullSummeBeiLeerenKorrekturen() {
            when(korrekturRepository.findByMitarbeiterIdAndDatumBetween(eq(1L), any(), any()))
                    .thenReturn(List.of());

            BigDecimal summe = service.summiereAktiveKorrekturen(1L, 2025);

            assertThat(summe).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class GetKorrekturen {

        @Test
        void filterstornierteBeiAktivenKorrekturen() {
            ZeitkontoKorrektur aktiv = new ZeitkontoKorrektur();
            aktiv.setStorniert(false);

            ZeitkontoKorrektur storniert = new ZeitkontoKorrektur();
            storniert.setStorniert(true);

            when(korrekturRepository.findByMitarbeiterIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(aktiv, storniert));

            List<ZeitkontoKorrektur> result = service.getAktiveKorrekturenByMitarbeiter(1L);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getStorniert()).isFalse();
        }

        @Test
        void gibtAlleKorrekturenInklusiveStornierterZurueck() {
            ZeitkontoKorrektur aktiv = new ZeitkontoKorrektur();
            aktiv.setStorniert(false);

            ZeitkontoKorrektur storniert = new ZeitkontoKorrektur();
            storniert.setStorniert(true);

            when(korrekturRepository.findByMitarbeiterIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(aktiv, storniert));

            List<ZeitkontoKorrektur> result = service.getAlleKorrekturenByMitarbeiter(1L);

            assertThat(result).hasSize(2);
        }
    }
}
