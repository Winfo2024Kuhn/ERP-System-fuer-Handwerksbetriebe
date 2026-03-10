package org.example.kalkulationsprogramm.service.miete;

import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository;
import org.example.kalkulationsprogramm.repository.miete.MietparteiRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MietobjektServiceTest {

    @Mock
    private MietobjektRepository mietobjektRepository;

    @Mock
    private MietparteiRepository mietparteiRepository;

    @InjectMocks
    private MietobjektService service;

    @Nested
    class FindAll {

        @Test
        void gibtAlleMietobjekteZurueck() {
            Mietobjekt m1 = new Mietobjekt();
            m1.setId(1L);
            m1.setName("Haus A");
            Mietobjekt m2 = new Mietobjekt();
            m2.setId(2L);
            m2.setName("Haus B");

            when(mietobjektRepository.findAll()).thenReturn(List.of(m1, m2));

            List<Mietobjekt> result = service.findAll();

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class GetById {

        @Test
        void findetMietobjektPerId() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);
            mietobjekt.setName("Haus A");

            when(mietobjektRepository.findById(1L)).thenReturn(Optional.of(mietobjekt));

            Mietobjekt result = service.getById(1L);

            assertThat(result.getName()).isEqualTo("Haus A");
        }

        @Test
        void wirftNotFoundExceptionBeiUnbekannterID() {
            when(mietobjektRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("nicht gefunden");
        }
    }

    @Nested
    class Save {

        @Test
        void speichertMietobjekt() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setName("Neues Haus");
            mietobjekt.setStrasse("Hauptstraße 1");
            mietobjekt.setPlz("12345");
            mietobjekt.setOrt("Berlin");

            when(mietobjektRepository.save(any(Mietobjekt.class))).thenAnswer(inv -> {
                Mietobjekt m = inv.getArgument(0);
                m.setId(10L);
                return m;
            });

            Mietobjekt result = service.save(mietobjekt);

            assertThat(result.getId()).isEqualTo(10L);
            verify(mietobjektRepository).save(mietobjekt);
        }
    }

    @Nested
    class Delete {

        @Test
        void loeschtMietobjekt() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(5L);

            when(mietobjektRepository.findById(5L)).thenReturn(Optional.of(mietobjekt));

            service.delete(5L);

            verify(mietobjektRepository).delete(mietobjekt);
        }

        @Test
        void wirftNotFoundExceptionBeiLoeschenUnbekannterID() {
            when(mietobjektRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class SavePartei {

        @Test
        void speichertMieterMitVorauszahlung() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);
            when(mietobjektRepository.findById(1L)).thenReturn(Optional.of(mietobjekt));

            Mietpartei partei = new Mietpartei();
            partei.setName("Müller");
            partei.setRolle(MietparteiRolle.MIETER);
            partei.setMonatlicherVorschuss(new BigDecimal("250.555"));

            when(mietparteiRepository.save(any(Mietpartei.class))).thenAnswer(inv -> inv.getArgument(0));

            Mietpartei result = service.savePartei(1L, partei);

            assertThat(result.getMietobjekt()).isEqualTo(mietobjekt);
            assertThat(result.getMonatlicherVorschuss()).isEqualByComparingTo(new BigDecimal("250.56"));
        }

        @Test
        void setztVorauszahlungAufNullBeiNichtMieter() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);
            when(mietobjektRepository.findById(1L)).thenReturn(Optional.of(mietobjekt));

            Mietpartei partei = new Mietpartei();
            partei.setName("Eigentümer");
            partei.setRolle(MietparteiRolle.EIGENTUEMER);
            partei.setMonatlicherVorschuss(new BigDecimal("500.00"));

            when(mietparteiRepository.save(any(Mietpartei.class))).thenAnswer(inv -> inv.getArgument(0));

            Mietpartei result = service.savePartei(1L, partei);

            assertThat(result.getMonatlicherVorschuss()).isNull();
        }

        @Test
        void setztNegativenVorschussAufNull() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);
            when(mietobjektRepository.findById(1L)).thenReturn(Optional.of(mietobjekt));

            Mietpartei partei = new Mietpartei();
            partei.setName("Mieter");
            partei.setRolle(MietparteiRolle.MIETER);
            partei.setMonatlicherVorschuss(new BigDecimal("-100.00"));

            when(mietparteiRepository.save(any(Mietpartei.class))).thenAnswer(inv -> inv.getArgument(0));

            Mietpartei result = service.savePartei(1L, partei);

            assertThat(result.getMonatlicherVorschuss()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void behandeltNullVorauszahlungBeiMieter() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);
            when(mietobjektRepository.findById(1L)).thenReturn(Optional.of(mietobjekt));

            Mietpartei partei = new Mietpartei();
            partei.setName("Mieter");
            partei.setRolle(MietparteiRolle.MIETER);
            partei.setMonatlicherVorschuss(null);

            when(mietparteiRepository.save(any(Mietpartei.class))).thenAnswer(inv -> inv.getArgument(0));

            Mietpartei result = service.savePartei(1L, partei);

            assertThat(result.getMonatlicherVorschuss()).isNull();
        }
    }

    @Nested
    class DeletePartei {

        @Test
        void loeschtMietpartei() {
            Mietpartei partei = new Mietpartei();
            partei.setId(10L);

            when(mietparteiRepository.findById(10L)).thenReturn(Optional.of(partei));

            service.deletePartei(10L);

            verify(mietparteiRepository).delete(partei);
        }

        @Test
        void wirftNotFoundExceptionBeiUnbekannterPartei() {
            when(mietparteiRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deletePartei(99L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class GetParteien {

        @Test
        void gibtParteienDesMietobjektsZurueck() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);

            Mietpartei p1 = new Mietpartei();
            p1.setName("Müller");
            Mietpartei p2 = new Mietpartei();
            p2.setName("Schmidt");

            when(mietobjektRepository.findById(1L)).thenReturn(Optional.of(mietobjekt));
            when(mietparteiRepository.findByMietobjektOrderByNameAsc(mietobjekt))
                    .thenReturn(List.of(p1, p2));

            List<Mietpartei> result = service.getParteien(1L);

            assertThat(result).hasSize(2);
        }
    }
}
