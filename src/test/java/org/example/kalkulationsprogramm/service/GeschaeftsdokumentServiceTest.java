package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.Geschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Zahlung;
import org.example.kalkulationsprogramm.dto.Geschaeftsdokument.GeschaeftsdokumentErstellenDto;
import org.example.kalkulationsprogramm.dto.Geschaeftsdokument.ZahlungErstellenDto;
import org.example.kalkulationsprogramm.repository.GeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZahlungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeschaeftsdokumentServiceTest {

    @Mock private GeschaeftsdokumentRepository dokumentRepository;
    @Mock private ZahlungRepository zahlungRepository;
    @Mock private KundeRepository kundeRepository;
    @Mock private ProjektRepository projektRepository;

    private GeschaeftsdokumentService service;

    @BeforeEach
    void setUp() {
        service = new GeschaeftsdokumentService(dokumentRepository, zahlungRepository, kundeRepository, projektRepository);
    }

    @Nested
    class Erstellen {

        @Test
        void erstelltDokumentMitAllenFeldern() {
            when(dokumentRepository.findMaxNummer(any())).thenReturn(Optional.of(0));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                Geschaeftsdokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

            GeschaeftsdokumentErstellenDto dto = new GeschaeftsdokumentErstellenDto();
            dto.setDokumenttyp("Angebot");
            dto.setBetreff("Testbetreff");
            dto.setBetragNetto(new BigDecimal("1000.00"));
            dto.setMwstSatz(new BigDecimal("0.19"));
            dto.setProjektId(5L);

            Geschaeftsdokument result = service.erstellen(dto);

            assertThat(result.getDokumenttyp()).isEqualTo(Dokumenttyp.ANGEBOT);
            assertThat(result.getBetreff()).isEqualTo("Testbetreff");
            assertThat(result.getBetragNetto()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(result.getBetragBrutto()).isEqualByComparingTo(new BigDecimal("1190.00"));
            assertThat(result.getDokumentNummer()).isNotNull();
            assertThat(result.getDokumentNummer()).contains("-A-");
        }

        @Test
        void setztDefaultMwstSatzWennNull() {
            when(dokumentRepository.findMaxNummer(any())).thenReturn(Optional.of(0));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                Geschaeftsdokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            GeschaeftsdokumentErstellenDto dto = new GeschaeftsdokumentErstellenDto();
            dto.setDokumenttyp("Rechnung");
            dto.setBetragNetto(new BigDecimal("500.00"));

            Geschaeftsdokument result = service.erstellen(dto);

            assertThat(result.getMwstSatz()).isEqualByComparingTo(new BigDecimal("0.19"));
        }

        @Test
        void generiertAbschlagsnummerKorrekt() {
            when(dokumentRepository.findMaxNummer(any())).thenReturn(Optional.of(0));
            Geschaeftsdokument vorgaenger = new Geschaeftsdokument();
            vorgaenger.setId(10L);
            vorgaenger.setDokumenttyp(Dokumenttyp.AUFTRAGSBESTAETIGUNG);
            when(dokumentRepository.findById(10L)).thenReturn(Optional.of(vorgaenger));

            Geschaeftsdokument bestehendeAbschlag = new Geschaeftsdokument();
            bestehendeAbschlag.setDokumenttyp(Dokumenttyp.ABSCHLAGSRECHNUNG);
            when(dokumentRepository.findByVorgaengerDokumentId(10L)).thenReturn(List.of(bestehendeAbschlag));

            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                Geschaeftsdokument d = inv.getArgument(0);
                d.setId(2L);
                return d;
            });

            GeschaeftsdokumentErstellenDto dto = new GeschaeftsdokumentErstellenDto();
            dto.setDokumenttyp("Abschlagsrechnung");
            dto.setVorgaengerDokumentId(10L);

            Geschaeftsdokument result = service.erstellen(dto);

            assertThat(result.getAbschlagsNummer()).isEqualTo(2);
        }

        @Test
        void setztDatumAufHeuteWennNull() {
            when(dokumentRepository.findMaxNummer(any())).thenReturn(Optional.of(0));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                Geschaeftsdokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            GeschaeftsdokumentErstellenDto dto = new GeschaeftsdokumentErstellenDto();
            dto.setDokumenttyp("Angebot");

            Geschaeftsdokument result = service.erstellen(dto);

            assertThat(result.getDatum()).isEqualTo(LocalDate.now());
        }
    }

    @Nested
    class Konvertieren {

        @Test
        void konvertiertDokumentMitVorgaengerReferenz() {
            Geschaeftsdokument vorgaenger = new Geschaeftsdokument();
            vorgaenger.setId(1L);
            vorgaenger.setDokumenttyp(Dokumenttyp.ANGEBOT);
            vorgaenger.setBetreff("Original");
            vorgaenger.setBetragNetto(new BigDecimal("5000.00"));
            vorgaenger.setBetragBrutto(new BigDecimal("5950.00"));
            vorgaenger.setMwstSatz(new BigDecimal("0.19"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(vorgaenger));
            when(dokumentRepository.findMaxNummer(any())).thenReturn(Optional.of(0));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                Geschaeftsdokument d = inv.getArgument(0);
                d.setId(2L);
                return d;
            });

            Geschaeftsdokument result = service.konvertieren(1L, "Auftragsbestätigung");

            assertThat(result.getDokumenttyp()).isEqualTo(Dokumenttyp.AUFTRAGSBESTAETIGUNG);
            assertThat(result.getVorgaengerDokument()).isEqualTo(vorgaenger);
            assertThat(result.getBetragNetto()).isEqualByComparingTo(new BigDecimal("5000.00"));
            assertThat(result.getBetreff()).isEqualTo("Original");
            assertThat(result.getDokumentNummer()).contains("-AB-");
        }

        @Test
        void wirftExceptionBeiUnbekanntemVorgaenger() {
            when(dokumentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.konvertieren(999L, "Rechnung"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nicht gefunden");
        }
    }

    @Nested
    class BerechneAbschluss {

        @Test
        void berechnetGrunddatenKorrekt() {
            Geschaeftsdokument dokument = new Geschaeftsdokument();
            dokument.setId(1L);
            dokument.setDokumenttyp(Dokumenttyp.RECHNUNG);
            dokument.setBetragNetto(new BigDecimal("1000.00"));
            dokument.setBetragBrutto(new BigDecimal("1190.00"));
            dokument.setMwstSatz(new BigDecimal("0.19"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            var info = service.berechneAbschluss(1L);

            assertThat(info.getNettosumme()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(info.getGesamtsumme()).isEqualByComparingTo(new BigDecimal("1190.00"));
            assertThat(info.getMwstBetrag()).isEqualByComparingTo(new BigDecimal("190.00"));
            assertThat(info.getMwstProzent()).isEqualByComparingTo(new BigDecimal("19"));
        }

        @Test
        void berechnetNochZuZahlenKorrekt() {
            Geschaeftsdokument dokument = new Geschaeftsdokument();
            dokument.setId(1L);
            dokument.setDokumenttyp(Dokumenttyp.RECHNUNG);
            dokument.setBetragNetto(new BigDecimal("1000.00"));
            dokument.setBetragBrutto(new BigDecimal("1190.00"));
            dokument.setMwstSatz(new BigDecimal("0.19"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            var info = service.berechneAbschluss(1L);

            assertThat(info.getNochZuZahlen()).isEqualByComparingTo(new BigDecimal("1190.00"));
        }
    }

    @Nested
    class ZahlungErfassen {

        @Test
        void erfasstZahlungKorrekt() {
            Geschaeftsdokument dokument = new Geschaeftsdokument();
            dokument.setId(1L);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));
            when(zahlungRepository.save(any())).thenAnswer(inv -> {
                Zahlung z = inv.getArgument(0);
                z.setId(10L);
                return z;
            });

            ZahlungErstellenDto dto = new ZahlungErstellenDto();
            dto.setBetrag(new BigDecimal("500.00"));
            dto.setZahlungsart("Überweisung");
            dto.setVerwendungszweck("Rechnung 123");
            dto.setZahlungsdatum(LocalDate.of(2026, 3, 10));

            Zahlung result = service.zahlungErfassen(1L, dto);

            assertThat(result.getBetrag()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(result.getZahlungsart()).isEqualTo("Überweisung");
            assertThat(result.getGeschaeftsdokument()).isEqualTo(dokument);
            assertThat(result.getZahlungsdatum()).isEqualTo(LocalDate.of(2026, 3, 10));
        }

        @Test
        void setztDefaultDatumWennNull() {
            Geschaeftsdokument dokument = new Geschaeftsdokument();
            dokument.setId(1L);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));
            when(zahlungRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ZahlungErstellenDto dto = new ZahlungErstellenDto();
            dto.setBetrag(new BigDecimal("100.00"));

            Zahlung result = service.zahlungErfassen(1L, dto);

            assertThat(result.getZahlungsdatum()).isEqualTo(LocalDate.now());
        }
    }

    @Nested
    class FindByProjekt {

        @Test
        void gibtLeereListeWennKeineDokumente() {
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(5L)).thenReturn(Collections.emptyList());

            var result = service.findByProjekt(5L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class FindById {

        @Test
        void gibtNullWennNichtGefunden() {
            when(dokumentRepository.findById(999L)).thenReturn(Optional.empty());

            var result = service.findById(999L);

            assertThat(result).isNull();
        }
    }
}
