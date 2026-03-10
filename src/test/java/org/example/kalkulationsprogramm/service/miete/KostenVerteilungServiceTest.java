package org.example.kalkulationsprogramm.service.miete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.miete.Kostenposition;
import org.example.kalkulationsprogramm.domain.miete.KostenpositionBerechnung;
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle;
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel;
import org.example.kalkulationsprogramm.exception.MietabrechnungValidationException;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.miete.KostenpositionRepository;
import org.example.kalkulationsprogramm.repository.miete.MieteKostenstelleRepository;
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository;
import org.example.kalkulationsprogramm.repository.miete.MietparteiRepository;
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository;
import org.example.kalkulationsprogramm.repository.miete.VerteilungsschluesselEintragRepository;
import org.example.kalkulationsprogramm.repository.miete.VerteilungsschluesselRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KostenVerteilungServiceTest {

    @Mock private MietobjektRepository mietobjektRepository;
    @Mock private MietparteiRepository mietparteiRepository;
    @Mock private VerbrauchsgegenstandRepository verbrauchsgegenstandRepository;
    @Mock private MieteKostenstelleRepository kostenstelleRepository;
    @Mock private KostenpositionRepository kostenpositionRepository;
    @Mock private VerteilungsschluesselRepository verteilungsschluesselRepository;
    @Mock private VerteilungsschluesselEintragRepository verteilungsschluesselEintragRepository;

    private KostenVerteilungService service;

    @BeforeEach
    void setUp() {
        service = new KostenVerteilungService(
                mietobjektRepository, mietparteiRepository, verbrauchsgegenstandRepository,
                kostenstelleRepository, kostenpositionRepository,
                verteilungsschluesselRepository, verteilungsschluesselEintragRepository);
    }

    @Nested
    class KostenstelleTests {

        @Test
        void saveKostenstelleNeuErfolgreich() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);
            when(mietobjektRepository.findById(1L)).thenReturn(Optional.of(mietobjekt));
            when(kostenstelleRepository.save(any())).thenAnswer(inv -> {
                Kostenstelle ks = inv.getArgument(0);
                ks.setId(10L);
                return ks;
            });

            Kostenstelle input = new Kostenstelle();
            input.setName("Heizung");
            input.setBeschreibung("Heizkosten");
            input.setUmlagefaehig(true);

            Kostenstelle result = service.saveKostenstelle(1L, input);

            assertThat(result.getName()).isEqualTo("Heizung");
            assertThat(result.getMietobjekt()).isEqualTo(mietobjekt);
            assertThat(result.isUmlagefaehig()).isTrue();
        }

        @Test
        void saveKostenstelleWirftNotFoundBeiUnbekanntemMietobjekt() {
            when(mietobjektRepository.findById(999L)).thenReturn(Optional.empty());

            Kostenstelle input = new Kostenstelle();
            input.setName("Test");

            assertThatThrownBy(() -> service.saveKostenstelle(999L, input))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void deleteKostenstelleLoeschtErfolgreich() {
            Kostenstelle ks = new Kostenstelle();
            ks.setId(1L);
            when(kostenstelleRepository.findById(1L)).thenReturn(Optional.of(ks));

            service.deleteKostenstelle(1L);

            verify(kostenstelleRepository).delete(ks);
        }

        @Test
        void deleteKostenstelleWirftNotFoundBeiUnbekannter() {
            when(kostenstelleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteKostenstelle(999L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class KostenpositionTests {

        @Test
        void saveKostenpositionMitBetragErfolgreich() {
            Kostenstelle ks = new Kostenstelle();
            ks.setId(1L);
            when(kostenstelleRepository.findById(1L)).thenReturn(Optional.of(ks));
            when(kostenpositionRepository.save(any())).thenAnswer(inv -> {
                Kostenposition kp = inv.getArgument(0);
                kp.setId(10L);
                return kp;
            });

            Kostenposition input = new Kostenposition();
            input.setAbrechnungsJahr(2025);
            input.setBetrag(new BigDecimal("1234.567"));
            input.setBerechnung(KostenpositionBerechnung.BETRAG);

            Kostenposition result = service.saveKostenposition(1L, input);

            assertThat(result.getKostenstelle()).isEqualTo(ks);
            assertThat(result.getAbrechnungsJahr()).isEqualTo(2025);
            // Betrag wird auf 2 Dezimalstellen gerundet
            assertThat(result.getBetrag()).isEqualByComparingTo(new BigDecimal("1234.57"));
        }

        @Test
        void saveKostenpositionOhneBetragWirftException() {
            Kostenstelle ks = new Kostenstelle();
            ks.setId(1L);
            when(kostenstelleRepository.findById(1L)).thenReturn(Optional.of(ks));

            Kostenposition input = new Kostenposition();
            input.setBerechnung(KostenpositionBerechnung.BETRAG);
            // Kein Betrag gesetzt

            assertThatThrownBy(() -> service.saveKostenposition(1L, input))
                    .isInstanceOf(MietabrechnungValidationException.class);
        }

        @Test
        void saveKostenpositionMitVerbrauchsfaktor() {
            Kostenstelle ks = new Kostenstelle();
            ks.setId(1L);
            when(kostenstelleRepository.findById(1L)).thenReturn(Optional.of(ks));
            when(kostenpositionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Kostenposition input = new Kostenposition();
            input.setAbrechnungsJahr(2025);
            input.setBerechnung(KostenpositionBerechnung.VERBRAUCHSFAKTOR);
            input.setVerbrauchsfaktor(new BigDecimal("1.23456789"));

            Kostenposition result = service.saveKostenposition(1L, input);

            // Verbrauchsfaktor wird auf 5 Dezimalstellen gerundet
            assertThat(result.getVerbrauchsfaktor()).isEqualByComparingTo(new BigDecimal("1.23457"));
            // Betrag wird auf 0.00 gesetzt wenn null (weil VERBRAUCHSFAKTOR Modus)
            assertThat(result.getBetrag()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void deleteKostenpositionWirftNotFoundBeiUnbekannter() {
            when(kostenpositionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteKostenposition(999L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class CopyKostenpositionenVonVorjahr {

        @Test
        void kopiertPositionenVomVorjahr() {
            Kostenstelle ks = new Kostenstelle();
            ks.setId(1L);
            when(kostenstelleRepository.findByMietobjektIdOrderByNameAsc(10L)).thenReturn(List.of(ks));

            Kostenposition vorjahr1 = new Kostenposition();
            vorjahr1.setBeschreibung("Heizung");
            vorjahr1.setBetrag(new BigDecimal("500.00"));
            vorjahr1.setBerechnung(KostenpositionBerechnung.BETRAG);

            Kostenposition vorjahr2 = new Kostenposition();
            vorjahr2.setBeschreibung("Wasser");
            vorjahr2.setBetrag(new BigDecimal("200.00"));
            vorjahr2.setBerechnung(KostenpositionBerechnung.BETRAG);

            when(kostenpositionRepository.findByKostenstelleAndAbrechnungsJahr(ks, 2024))
                    .thenReturn(List.of(vorjahr1, vorjahr2));
            when(kostenpositionRepository.findByKostenstelleAndAbrechnungsJahr(ks, 2025))
                    .thenReturn(Collections.emptyList());
            when(kostenpositionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int kopiert = service.copyKostenpositionenVonVorjahr(10L, 2025);

            assertThat(kopiert).isEqualTo(2);

            ArgumentCaptor<Kostenposition> captor = ArgumentCaptor.forClass(Kostenposition.class);
            verify(kostenpositionRepository, times(2)).save(captor.capture());

            List<Kostenposition> gespeicherte = captor.getAllValues();
            assertThat(gespeicherte).extracting(Kostenposition::getAbrechnungsJahr).containsOnly(2025);
            assertThat(gespeicherte).extracting(Kostenposition::getBeschreibung)
                    .containsExactlyInAnyOrder("Heizung", "Wasser");
        }

        @Test
        void ueberschreibtNichtWennZieljahrBereitsPositionenHat() {
            Kostenstelle ks = new Kostenstelle();
            ks.setId(1L);
            when(kostenstelleRepository.findByMietobjektIdOrderByNameAsc(10L)).thenReturn(List.of(ks));

            Kostenposition existierend = new Kostenposition();
            existierend.setBeschreibung("Bereits vorhanden");

            when(kostenpositionRepository.findByKostenstelleAndAbrechnungsJahr(ks, 2024))
                    .thenReturn(List.of(new Kostenposition()));
            when(kostenpositionRepository.findByKostenstelleAndAbrechnungsJahr(ks, 2025))
                    .thenReturn(List.of(existierend));

            int kopiert = service.copyKostenpositionenVonVorjahr(10L, 2025);

            assertThat(kopiert).isZero();
            verify(kostenpositionRepository, never()).save(any());
        }

        @Test
        void gibt0ZurueckWennKeineKostenstellen() {
            when(kostenstelleRepository.findByMietobjektIdOrderByNameAsc(10L)).thenReturn(Collections.emptyList());

            int kopiert = service.copyKostenpositionenVonVorjahr(10L, 2025);

            assertThat(kopiert).isZero();
        }
    }

    @Nested
    class VerteilungsschluesselTests {

        @Test
        void getVerteilungsschluesselWirftNotFoundBeiUnbekanntemMietobjekt() {
            when(mietobjektRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getVerteilungsschluessel(999L))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void deleteVerteilungsschluesselErfolgreich() {
            Verteilungsschluessel vs = new Verteilungsschluessel();
            vs.setId(1L);
            when(verteilungsschluesselRepository.findById(1L)).thenReturn(Optional.of(vs));

            service.deleteVerteilungsschluessel(1L);

            verify(verteilungsschluesselRepository).delete(vs);
        }

        @Test
        void deleteVerteilungsschluesselWirftNotFound() {
            when(verteilungsschluesselRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteVerteilungsschluessel(999L))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
