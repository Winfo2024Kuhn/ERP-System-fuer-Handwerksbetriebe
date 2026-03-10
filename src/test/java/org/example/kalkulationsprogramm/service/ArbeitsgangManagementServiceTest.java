package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangErstellenDto;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangStundensatzDto;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArbeitsgangManagementServiceTest {

    @Mock
    private ArbeitsgangRepository arbeitsgangRepository;

    @Mock
    private ZeitbuchungRepository zeitbuchungRepository;

    @Mock
    private ArbeitsgangStundensatzRepository stundensatzRepository;

    @Mock
    private AbteilungRepository abteilungRepository;

    @InjectMocks
    private ArbeitsgangManagementService service;

    @Nested
    class ErstelleArbeitsgang {

        @Test
        void erstelltArbeitsgangMitAllenPflichtfeldern() {
            Abteilung abteilung = new Abteilung();
            abteilung.setId(1L);
            abteilung.setName("Produktion");

            when(abteilungRepository.findById(1L)).thenReturn(Optional.of(abteilung));
            when(arbeitsgangRepository.save(any(Arbeitsgang.class))).thenAnswer(inv -> {
                Arbeitsgang a = inv.getArgument(0);
                a.setId(42L);
                return a;
            });

            ArbeitsgangErstellenDto dto = new ArbeitsgangErstellenDto();
            dto.setBeschreibung("Schweißen");
            dto.setAbteilungId(1L);

            Arbeitsgang result = service.erstelleArbeitsgang(dto);

            assertThat(result.getId()).isEqualTo(42L);
            assertThat(result.getBeschreibung()).isEqualTo("Schweißen");
            assertThat(result.getAbteilung()).isEqualTo(abteilung);
            verify(arbeitsgangRepository).save(any(Arbeitsgang.class));
        }

        @Test
        void wirftExceptionBeiUnbekannterAbteilung() {
            when(abteilungRepository.findById(99L)).thenReturn(Optional.empty());

            ArbeitsgangErstellenDto dto = new ArbeitsgangErstellenDto();
            dto.setBeschreibung("Test");
            dto.setAbteilungId(99L);

            assertThatThrownBy(() -> service.erstelleArbeitsgang(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Abteilung nicht gefunden");
        }
    }

    @Nested
    class LoescheArbeitsgang {

        @Test
        void loeschtArbeitsgangOhneReferenzen() {
            Arbeitsgang arbeitsgang = new Arbeitsgang();
            arbeitsgang.setId(5L);

            when(arbeitsgangRepository.findById(5L)).thenReturn(Optional.of(arbeitsgang));
            when(zeitbuchungRepository.countByArbeitsgangId(5L)).thenReturn(0L);

            service.loescheArbeitsgang(5L);

            verify(arbeitsgangRepository).delete(arbeitsgang);
        }

        @Test
        void wirftExceptionBeiReferenziertenBuchungen() {
            Arbeitsgang arbeitsgang = new Arbeitsgang();
            arbeitsgang.setId(5L);

            when(arbeitsgangRepository.findById(5L)).thenReturn(Optional.of(arbeitsgang));
            when(zeitbuchungRepository.countByArbeitsgangId(5L)).thenReturn(3L);

            assertThatThrownBy(() -> service.loescheArbeitsgang(5L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("referenziert");
        }

        @Test
        void wirftExceptionBeiUnbekanntemArbeitsgang() {
            when(arbeitsgangRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.loescheArbeitsgang(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nicht gefunden");
        }
    }

    @Nested
    class FindeAlle {

        @Test
        void gibtAlleArbeitsgaengeZurueck() {
            Arbeitsgang a1 = new Arbeitsgang();
            a1.setId(1L);
            Arbeitsgang a2 = new Arbeitsgang();
            a2.setId(2L);

            when(arbeitsgangRepository.findAll()).thenReturn(List.of(a1, a2));

            List<Arbeitsgang> result = service.findeAlle();

            assertThat(result).hasSize(2);
            verify(arbeitsgangRepository).findAll();
        }

        @Test
        void gibtLeereListeZurueckWennKeineVorhanden() {
            when(arbeitsgangRepository.findAll()).thenReturn(List.of());

            List<Arbeitsgang> result = service.findeAlle();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class AktualisiereStundensaetze {

        @Test
        void aktualisiertStundensatzFuerExistierendesJahr() {
            Arbeitsgang arbeitsgang = new Arbeitsgang();
            arbeitsgang.setId(10L);

            ArbeitsgangStundensatz existierend = new ArbeitsgangStundensatz();
            existierend.setId(1L);
            existierend.setArbeitsgang(arbeitsgang);
            existierend.setJahr(java.time.LocalDate.now().getYear());
            existierend.setSatz(new BigDecimal("50.00"));

            when(arbeitsgangRepository.findById(10L)).thenReturn(Optional.of(arbeitsgang));
            when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(eq(10L), anyInt()))
                    .thenReturn(Optional.of(existierend));

            ArbeitsgangStundensatzDto dto = new ArbeitsgangStundensatzDto();
            dto.setArbeitsgangId(10L);
            dto.setStundensatz(new BigDecimal("65.00"));

            service.aktualisiereStundensaetze(List.of(dto));

            ArgumentCaptor<ArbeitsgangStundensatz> captor = ArgumentCaptor.forClass(ArbeitsgangStundensatz.class);
            verify(stundensatzRepository).save(captor.capture());
            assertThat(captor.getValue().getSatz()).isEqualByComparingTo(new BigDecimal("65.00"));
        }

        @Test
        void erstelltNeuenStundensatzWennJahrNichtVorhanden() {
            Arbeitsgang arbeitsgang = new Arbeitsgang();
            arbeitsgang.setId(10L);

            when(arbeitsgangRepository.findById(10L)).thenReturn(Optional.of(arbeitsgang));
            when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(eq(10L), anyInt()))
                    .thenReturn(Optional.empty());

            service.aktualisiereEinzelnenStundensatz(10L, new BigDecimal("75.00"));

            ArgumentCaptor<ArbeitsgangStundensatz> captor = ArgumentCaptor.forClass(ArbeitsgangStundensatz.class);
            verify(stundensatzRepository).save(captor.capture());
            ArbeitsgangStundensatz saved = captor.getValue();
            assertThat(saved.getSatz()).isEqualByComparingTo(new BigDecimal("75.00"));
            assertThat(saved.getArbeitsgang()).isEqualTo(arbeitsgang);
            assertThat(saved.getJahr()).isEqualTo(java.time.LocalDate.now().getYear());
        }

        @Test
        void wirftExceptionBeiUnbekanntemArbeitsgangBeimUpdate() {
            when(arbeitsgangRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.aktualisiereEinzelnenStundensatz(99L, new BigDecimal("50")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nicht gefunden");
        }
    }
}
