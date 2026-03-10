package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.miete.*;
import org.example.kalkulationsprogramm.dto.miete.*;
import org.example.kalkulationsprogramm.service.miete.KostenpositionBerechner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MieteMapperTest {

    @Mock
    private KostenpositionBerechner kostenpositionBerechner;

    @InjectMocks
    private MieteMapper mapper;

    @Nested
    class MietobjektMapping {

        @Test
        void mapptEntityZuDto() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);
            mietobjekt.setName("Wohnhaus Mitte");
            mietobjekt.setStrasse("Berliner Str. 5");
            mietobjekt.setPlz("10115");
            mietobjekt.setOrt("Berlin");

            MietobjektDto dto = mapper.toDto(mietobjekt);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Wohnhaus Mitte");
            assertThat(dto.getStrasse()).isEqualTo("Berliner Str. 5");
            assertThat(dto.getPlz()).isEqualTo("10115");
            assertThat(dto.getOrt()).isEqualTo("Berlin");
        }

        @Test
        void gibtNullZurueckBeiNullEntity() {
            assertThat(mapper.toDto((Mietobjekt) null)).isNull();
        }

        @Test
        void mapptDtoZuEntity() {
            MietobjektDto dto = new MietobjektDto();
            dto.setId(2L);
            dto.setName("Gewerbepark");
            dto.setStrasse("Industriestr. 10");
            dto.setPlz("45127");
            dto.setOrt("Essen");

            Mietobjekt entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isEqualTo(2L);
            assertThat(entity.getName()).isEqualTo("Gewerbepark");
            assertThat(entity.getStrasse()).isEqualTo("Industriestr. 10");
            assertThat(entity.getPlz()).isEqualTo("45127");
            assertThat(entity.getOrt()).isEqualTo("Essen");
        }
    }

    @Nested
    class MietparteiMapping {

        @Test
        void mapptEntityZuDto() {
            Mietpartei partei = new Mietpartei();
            partei.setId(1L);
            partei.setName("Müller");
            partei.setRolle(MietparteiRolle.MIETER);
            partei.setEmail("mueller@example.de");
            partei.setTelefon("030 12345");
            partei.setMonatlicherVorschuss(new BigDecimal("250.00"));

            MietparteiDto dto = mapper.toDto(partei);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Müller");
            assertThat(dto.getRolle()).isEqualTo(MietparteiRolle.MIETER);
            assertThat(dto.getEmail()).isEqualTo("mueller@example.de");
            assertThat(dto.getTelefon()).isEqualTo("030 12345");
            assertThat(dto.getMonatlicherVorschuss()).isEqualByComparingTo(new BigDecimal("250.00"));
        }

        @Test
        void mapptDtoZuEntity() {
            MietparteiDto dto = new MietparteiDto();
            dto.setId(2L);
            dto.setName("Schmidt");
            dto.setRolle(MietparteiRolle.EIGENTUEMER);
            dto.setEmail("schmidt@example.de");

            Mietpartei entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isEqualTo(2L);
            assertThat(entity.getName()).isEqualTo("Schmidt");
            assertThat(entity.getRolle()).isEqualTo(MietparteiRolle.EIGENTUEMER);
        }
    }

    @Nested
    class RaumMapping {

        @Test
        void mapptRaumEntityZuDto() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);

            Raum raum = new Raum();
            raum.setId(10L);
            raum.setMietobjekt(mietobjekt);
            raum.setName("Wohnzimmer");
            raum.setBeschreibung("EG links");
            raum.setFlaecheQuadratmeter(new BigDecimal("25.5"));

            RaumDto dto = mapper.toDto(raum);

            assertThat(dto.getId()).isEqualTo(10L);
            assertThat(dto.getMietobjektId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Wohnzimmer");
            assertThat(dto.getBeschreibung()).isEqualTo("EG links");
            assertThat(dto.getFlaecheQuadratmeter()).isEqualByComparingTo(new BigDecimal("25.5"));
        }

        @Test
        void mapptRaumDtoZuEntity() {
            RaumDto dto = new RaumDto();
            dto.setId(10L);
            dto.setName("Küche");
            dto.setBeschreibung("OG rechts");
            dto.setFlaecheQuadratmeter(new BigDecimal("12.0"));

            Raum entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isEqualTo(10L);
            assertThat(entity.getName()).isEqualTo("Küche");
            assertThat(entity.getBeschreibung()).isEqualTo("OG rechts");
            assertThat(entity.getFlaecheQuadratmeter()).isEqualByComparingTo(new BigDecimal("12.0"));
        }
    }

    @Nested
    class VerbrauchsgegenstandMapping {

        @Test
        void mapptEntityZuDto() {
            Raum raum = new Raum();
            raum.setId(5L);

            Verbrauchsgegenstand gegenstand = new Verbrauchsgegenstand();
            gegenstand.setId(1L);
            gegenstand.setRaum(raum);
            gegenstand.setName("Wasserzähler");
            gegenstand.setSeriennummer("SN-12345");
            gegenstand.setVerbrauchsart(Verbrauchsart.WASSER);
            gegenstand.setEinheit("m³");
            gegenstand.setAktiv(true);

            VerbrauchsgegenstandDto dto = mapper.toDto(gegenstand);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getRaumId()).isEqualTo(5L);
            assertThat(dto.getName()).isEqualTo("Wasserzähler");
            assertThat(dto.getSeriennummer()).isEqualTo("SN-12345");
            assertThat(dto.getVerbrauchsart()).isEqualTo(Verbrauchsart.WASSER);
            assertThat(dto.getEinheit()).isEqualTo("m³");
            assertThat(dto.isAktiv()).isTrue();
        }

        @Test
        void mapptDtoZuEntity() {
            VerbrauchsgegenstandDto dto = new VerbrauchsgegenstandDto();
            dto.setId(2L);
            dto.setName("Stromzähler");
            dto.setSeriennummer("SN-99999");
            dto.setVerbrauchsart(Verbrauchsart.STROM);
            dto.setEinheit("kWh");
            dto.setAktiv(false);

            Verbrauchsgegenstand entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isEqualTo(2L);
            assertThat(entity.getName()).isEqualTo("Stromzähler");
            assertThat(entity.getVerbrauchsart()).isEqualTo(Verbrauchsart.STROM);
            assertThat(entity.isAktiv()).isFalse();
        }
    }

    @Nested
    class ZaehlerstandMapping {

        @Test
        void mapptEntityZuDto() {
            Verbrauchsgegenstand gegenstand = new Verbrauchsgegenstand();
            gegenstand.setId(3L);

            Zaehlerstand zaehlerstand = new Zaehlerstand();
            zaehlerstand.setId(100L);
            zaehlerstand.setVerbrauchsgegenstand(gegenstand);
            zaehlerstand.setAbrechnungsJahr(2025);
            zaehlerstand.setStichtag(LocalDate.of(2025, 12, 31));
            zaehlerstand.setStand(new BigDecimal("1234.5678"));
            zaehlerstand.setVerbrauch(new BigDecimal("100.1234"));
            zaehlerstand.setKommentar("Jahresablesung");

            ZaehlerstandDto dto = mapper.toDto(zaehlerstand);

            assertThat(dto.getId()).isEqualTo(100L);
            assertThat(dto.getVerbrauchsgegenstandId()).isEqualTo(3L);
            assertThat(dto.getAbrechnungsJahr()).isEqualTo(2025);
            assertThat(dto.getStichtag()).isEqualTo(LocalDate.of(2025, 12, 31));
            assertThat(dto.getStand()).isEqualByComparingTo(new BigDecimal("1234.5678"));
            assertThat(dto.getVerbrauch()).isEqualByComparingTo(new BigDecimal("100.1234"));
            assertThat(dto.getKommentar()).isEqualTo("Jahresablesung");
        }

        @Test
        void mapptDtoZuEntityMitVerbrauchsgegenstandId() {
            ZaehlerstandDto dto = new ZaehlerstandDto();
            dto.setId(200L);
            dto.setVerbrauchsgegenstandId(7L);
            dto.setAbrechnungsJahr(2024);
            dto.setStichtag(LocalDate.of(2024, 6, 30));
            dto.setStand(new BigDecimal("500.0000"));

            Zaehlerstand entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isEqualTo(200L);
            assertThat(entity.getVerbrauchsgegenstand()).isNotNull();
            assertThat(entity.getVerbrauchsgegenstand().getId()).isEqualTo(7L);
            assertThat(entity.getAbrechnungsJahr()).isEqualTo(2024);
        }

        @Test
        void mapptDtoZuEntityOhneVerbrauchsgegenstandId() {
            ZaehlerstandDto dto = new ZaehlerstandDto();
            dto.setId(201L);
            dto.setVerbrauchsgegenstandId(null);

            Zaehlerstand entity = mapper.toEntity(dto);

            assertThat(entity.getVerbrauchsgegenstand()).isNull();
        }
    }

    @Nested
    class KostenstelleMapping {

        @Test
        void mapptEntityZuDto() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);

            Verteilungsschluessel schluessel = new Verteilungsschluessel();
            schluessel.setId(10L);

            Kostenstelle kostenstelle = new Kostenstelle();
            kostenstelle.setId(5L);
            kostenstelle.setMietobjekt(mietobjekt);
            kostenstelle.setName("Heizkosten");
            kostenstelle.setBeschreibung("Zentrale Gasheizung");
            kostenstelle.setUmlagefaehig(true);
            kostenstelle.setStandardSchluessel(schluessel);

            KostenstelleDto dto = mapper.toDto(kostenstelle);

            assertThat(dto.getId()).isEqualTo(5L);
            assertThat(dto.getMietobjektId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Heizkosten");
            assertThat(dto.getBeschreibung()).isEqualTo("Zentrale Gasheizung");
            assertThat(dto.isUmlagefaehig()).isTrue();
            assertThat(dto.getStandardSchluesselId()).isEqualTo(10L);
        }

        @Test
        void mapptEntityOhneStandardSchluessel() {
            Kostenstelle kostenstelle = new Kostenstelle();
            kostenstelle.setId(6L);
            kostenstelle.setName("Wasser");
            kostenstelle.setStandardSchluessel(null);

            KostenstelleDto dto = mapper.toDto(kostenstelle);

            assertThat(dto.getStandardSchluesselId()).isNull();
        }

        @Test
        void mapptDtoZuEntity() {
            KostenstelleDto dto = new KostenstelleDto();
            dto.setId(7L);
            dto.setName("Müllentsorgung");
            dto.setBeschreibung("Restmüll & Papier");
            dto.setUmlagefaehig(true);
            dto.setStandardSchluesselId(20L);

            Kostenstelle entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isEqualTo(7L);
            assertThat(entity.getName()).isEqualTo("Müllentsorgung");
            assertThat(entity.getStandardSchluessel()).isNotNull();
            assertThat(entity.getStandardSchluessel().getId()).isEqualTo(20L);
        }

        @Test
        void mapptDtoZuEntityOhneSchluessel() {
            KostenstelleDto dto = new KostenstelleDto();
            dto.setId(8L);
            dto.setName("Test");
            dto.setStandardSchluesselId(null);

            Kostenstelle entity = mapper.toEntity(dto);

            assertThat(entity.getStandardSchluessel()).isNull();
        }
    }

    @Nested
    class VerteilungsschluesselMapping {

        @Test
        void mapptEntityMitEintraegenZuDto() {
            Mietobjekt mietobjekt = new Mietobjekt();
            mietobjekt.setId(1L);

            Mietpartei partei = new Mietpartei();
            partei.setId(10L);

            Verbrauchsgegenstand gegenstand = new Verbrauchsgegenstand();
            gegenstand.setId(20L);

            VerteilungsschluesselEintrag eintrag = new VerteilungsschluesselEintrag();
            eintrag.setId(100L);
            eintrag.setAnteil(new BigDecimal("0.5000"));
            eintrag.setKommentar("50%");
            eintrag.setMietpartei(partei);
            eintrag.setVerbrauchsgegenstand(gegenstand);

            Verteilungsschluessel schluessel = new Verteilungsschluessel();
            schluessel.setId(50L);
            schluessel.setMietobjekt(mietobjekt);
            schluessel.setName("50/50");
            schluessel.setBeschreibung("Halbe-Halbe");
            schluessel.setTyp(VerteilungsschluesselTyp.PROZENTUAL);
            schluessel.setEintraege(List.of(eintrag));

            VerteilungsschluesselDto dto = mapper.toDto(schluessel);

            assertThat(dto.getId()).isEqualTo(50L);
            assertThat(dto.getMietobjektId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("50/50");
            assertThat(dto.getTyp()).isEqualTo(VerteilungsschluesselTyp.PROZENTUAL);
            assertThat(dto.getEintraege()).hasSize(1);

            VerteilungsschluesselEintragDto eintragDto = dto.getEintraege().get(0);
            assertThat(eintragDto.getId()).isEqualTo(100L);
            assertThat(eintragDto.getAnteil()).isEqualByComparingTo(new BigDecimal("0.5000"));
            assertThat(eintragDto.getMietparteiId()).isEqualTo(10L);
            assertThat(eintragDto.getVerbrauchsgegenstandId()).isEqualTo(20L);
        }

        @Test
        void mapptDtoMitEintraegenZuEntity() {
            VerteilungsschluesselEintragDto eintragDto = new VerteilungsschluesselEintragDto();
            eintragDto.setId(1L);
            eintragDto.setAnteil(new BigDecimal("1.0000"));
            eintragDto.setMietparteiId(10L);
            eintragDto.setVerbrauchsgegenstandId(20L);
            eintragDto.setKommentar("100%");

            VerteilungsschluesselDto dto = new VerteilungsschluesselDto();
            dto.setId(50L);
            dto.setName("Voll");
            dto.setTyp(VerteilungsschluesselTyp.VERBRAUCH);
            dto.setEintraege(List.of(eintragDto));

            Verteilungsschluessel entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isEqualTo(50L);
            assertThat(entity.getName()).isEqualTo("Voll");
            assertThat(entity.getTyp()).isEqualTo(VerteilungsschluesselTyp.VERBRAUCH);
            assertThat(entity.getEintraege()).hasSize(1);

            VerteilungsschluesselEintrag eintrag = entity.getEintraege().get(0);
            assertThat(eintrag.getMietpartei().getId()).isEqualTo(10L);
            assertThat(eintrag.getVerbrauchsgegenstand().getId()).isEqualTo(20L);
            assertThat(eintrag.getVerteilungsschluessel()).isEqualTo(entity);
        }

        @Test
        void mapptEntityOhneEintraege() {
            Verteilungsschluessel schluessel = new Verteilungsschluessel();
            schluessel.setId(51L);
            schluessel.setName("Leer");
            schluessel.setEintraege(null);

            VerteilungsschluesselDto dto = mapper.toDto(schluessel);

            assertThat(dto.getEintraege()).isEmpty();
        }
    }

    @Nested
    class KostenpositionMapping {

        @Test
        void mapptEntityZuDtoMitBerechneterBetrag() {
            Kostenstelle kostenstelle = new Kostenstelle();
            kostenstelle.setId(5L);

            Kostenposition kostenposition = new Kostenposition();
            kostenposition.setId(1L);
            kostenposition.setKostenstelle(kostenstelle);
            kostenposition.setAbrechnungsJahr(2025);
            kostenposition.setBetrag(new BigDecimal("1000.00"));
            kostenposition.setBerechnung(KostenpositionBerechnung.BETRAG);
            kostenposition.setBeschreibung("Heizöl");
            kostenposition.setBelegNummer("HZ-001");
            kostenposition.setBuchungsdatum(LocalDate.of(2025, 3, 15));

            when(kostenpositionBerechner.berechne(any(), eq(2025), any()))
                    .thenReturn(new KostenpositionBerechner.KostenpositionVerteilErgebnis(
                            new BigDecimal("1200.00"), null, null));

            KostenpositionDto dto = mapper.toDto(kostenposition);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getKostenstelleId()).isEqualTo(5L);
            assertThat(dto.getAbrechnungsJahr()).isEqualTo(2025);
            assertThat(dto.getBetrag()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(dto.getBerechneterBetrag()).isEqualByComparingTo(new BigDecimal("1200.00"));
            assertThat(dto.getBeschreibung()).isEqualTo("Heizöl");
            assertThat(dto.getBelegNummer()).isEqualTo("HZ-001");
        }

        @Test
        void fallbackAufBetragBeiBerechnerException() {
            Kostenposition kostenposition = new Kostenposition();
            kostenposition.setId(2L);
            kostenposition.setAbrechnungsJahr(2025);
            kostenposition.setBetrag(new BigDecimal("500.00"));
            kostenposition.setBerechnung(KostenpositionBerechnung.VERBRAUCHSFAKTOR);

            when(kostenpositionBerechner.berechne(any(), eq(2025), any()))
                    .thenThrow(new RuntimeException("Konfigurationsfehler"));

            KostenpositionDto dto = mapper.toDto(kostenposition);

            assertThat(dto.getBerechneterBetrag()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        void mapptVerteilungsschluesselOverride() {
            Verteilungsschluessel schluessel = new Verteilungsschluessel();
            schluessel.setId(30L);

            Kostenposition kostenposition = new Kostenposition();
            kostenposition.setId(3L);
            kostenposition.setAbrechnungsJahr(null);
            kostenposition.setBetrag(new BigDecimal("100.00"));
            kostenposition.setVerteilungsschluesselOverride(schluessel);

            KostenpositionDto dto = mapper.toDto(kostenposition);

            assertThat(dto.getVerteilungsschluesselId()).isEqualTo(30L);
        }

        @Test
        void mapptDtoZuEntity() {
            KostenpositionDto dto = new KostenpositionDto();
            dto.setId(10L);
            dto.setAbrechnungsJahr(2025);
            dto.setBetrag(new BigDecimal("800.00"));
            dto.setBerechnung(KostenpositionBerechnung.VERBRAUCHSFAKTOR);
            dto.setVerbrauchsfaktor(new BigDecimal("0.15"));
            dto.setBeschreibung("Strom");
            dto.setBelegNummer("ST-001");
            dto.setBuchungsdatum(LocalDate.of(2025, 6, 1));
            dto.setVerteilungsschluesselId(40L);

            Kostenposition entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isEqualTo(10L);
            assertThat(entity.getAbrechnungsJahr()).isEqualTo(2025);
            assertThat(entity.getBetrag()).isEqualByComparingTo(new BigDecimal("800.00"));
            assertThat(entity.getBerechnung()).isEqualTo(KostenpositionBerechnung.VERBRAUCHSFAKTOR);
            assertThat(entity.getVerbrauchsfaktor()).isEqualByComparingTo(new BigDecimal("0.15"));
            assertThat(entity.getVerteilungsschluesselOverride()).isNotNull();
            assertThat(entity.getVerteilungsschluesselOverride().getId()).isEqualTo(40L);
        }

        @Test
        void setztDefaultBerechnungAufBetragBeiNull() {
            KostenpositionDto dto = new KostenpositionDto();
            dto.setId(11L);
            dto.setAbrechnungsJahr(2025);
            dto.setBerechnung(null);

            Kostenposition entity = mapper.toEntity(dto);

            assertThat(entity.getBerechnung()).isEqualTo(KostenpositionBerechnung.BETRAG);
        }
    }
}
