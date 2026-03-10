package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Kunde.KundeListItemDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeResponseDto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KundeMapperTest {

    private final KundeMapper mapper = new KundeMapper();

    private Kunde erstelleKunde() {
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setKundennummer("K-001");
        kunde.setName("Müller Bau GmbH");
        kunde.setAnrede(Anrede.FIRMA);
        kunde.setAnsprechspartner("Frau Schmidt");
        kunde.setStrasse("Hauptstraße 10");
        kunde.setPlz("80331");
        kunde.setOrt("München");
        kunde.setTelefon("089 12345");
        kunde.setMobiltelefon("0171 12345");
        kunde.setKundenEmails(List.of("info@mueller-bau.de", "buchhaltung@mueller-bau.de"));
        return kunde;
    }

    @Nested
    class ToListItem {

        @Test
        void mapptAlleFelder() {
            Kunde kunde = erstelleKunde();

            KundeListItemDto dto = mapper.toListItem(kunde);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getKundennummer()).isEqualTo("K-001");
            assertThat(dto.getName()).isEqualTo("Müller Bau GmbH");
            assertThat(dto.getAnrede()).isEqualTo("FIRMA");
            assertThat(dto.getAnsprechspartner()).isEqualTo("Frau Schmidt");
            assertThat(dto.getStrasse()).isEqualTo("Hauptstraße 10");
            assertThat(dto.getPlz()).isEqualTo("80331");
            assertThat(dto.getOrt()).isEqualTo("München");
            assertThat(dto.getTelefon()).isEqualTo("089 12345");
            assertThat(dto.getMobiltelefon()).isEqualTo("0171 12345");
            assertThat(dto.getKundenEmails()).containsExactly("info@mueller-bau.de", "buchhaltung@mueller-bau.de");
        }

        @Test
        void gibtNullZurueckBeiNullKunde() {
            assertThat(mapper.toListItem(null)).isNull();
        }

        @Test
        void setztHatProjekteAufTrueWennProjekteVorhanden() {
            Kunde kunde = erstelleKunde();
            Projekt projekt = new Projekt();
            projekt.setId(1L);
            kunde.setProjekts(List.of(projekt));

            KundeListItemDto dto = mapper.toListItem(kunde);

            assertThat(dto.isHatProjekte()).isTrue();
        }

        @Test
        void setztHatProjekteAufFalseBeiLeerenProjekten() {
            Kunde kunde = erstelleKunde();
            kunde.setProjekts(List.of());

            KundeListItemDto dto = mapper.toListItem(kunde);

            assertThat(dto.isHatProjekte()).isFalse();
        }

        @Test
        void setztHatProjekteAufFalseBeiNullProjekten() {
            Kunde kunde = erstelleKunde();
            kunde.setProjekts(null);

            KundeListItemDto dto = mapper.toListItem(kunde);

            assertThat(dto.isHatProjekte()).isFalse();
        }

        @Test
        void mapptKundeOhneAnrede() {
            Kunde kunde = erstelleKunde();
            kunde.setAnrede(null);

            KundeListItemDto dto = mapper.toListItem(kunde);

            assertThat(dto.getAnrede()).isNull();
        }
    }

    @Nested
    class ToResponseDto {

        @Test
        void mapptAlleFelder() {
            Kunde kunde = erstelleKunde();

            KundeResponseDto dto = mapper.toResponseDto(kunde);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getKundennummer()).isEqualTo("K-001");
            assertThat(dto.getName()).isEqualTo("Müller Bau GmbH");
            assertThat(dto.getAnrede()).isEqualTo("FIRMA");
            assertThat(dto.getAnsprechspartner()).isEqualTo("Frau Schmidt");
            assertThat(dto.getStrasse()).isEqualTo("Hauptstraße 10");
            assertThat(dto.getPlz()).isEqualTo("80331");
            assertThat(dto.getOrt()).isEqualTo("München");
            assertThat(dto.getTelefon()).isEqualTo("089 12345");
            assertThat(dto.getMobiltelefon()).isEqualTo("0171 12345");
            assertThat(dto.getKundenEmails()).containsExactly("info@mueller-bau.de", "buchhaltung@mueller-bau.de");
        }

        @Test
        void gibtNullZurueckBeiNullKunde() {
            assertThat(mapper.toResponseDto(null)).isNull();
        }

        @Test
        void mapptKundeMitNullWerten() {
            Kunde kunde = new Kunde();
            kunde.setId(5L);
            kunde.setKundennummer("K-005");
            kunde.setName("Minimal");

            KundeResponseDto dto = mapper.toResponseDto(kunde);

            assertThat(dto.getId()).isEqualTo(5L);
            assertThat(dto.getStrasse()).isNull();
            assertThat(dto.getPlz()).isNull();
            assertThat(dto.getOrt()).isNull();
            assertThat(dto.getTelefon()).isNull();
            assertThat(dto.getKundenEmails()).isEmpty();
        }
    }
}
