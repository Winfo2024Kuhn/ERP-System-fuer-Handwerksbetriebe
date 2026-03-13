package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnfrageMapperTest {

    private final AnfrageMapper mapper = new AnfrageMapper();

    @Test
    void mapptVollstaendigesAnfrageZuDto() {
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Bauhaus GmbH");
        kunde.setKundennummer("K-001");
        kunde.setKundenEmails(List.of("info@bauhaus.de"));
        kunde.setStrasse("Baustraße 1");
        kunde.setPlz("10115");
        kunde.setOrt("Berlin");
        kunde.setTelefon("030 12345");
        kunde.setMobiltelefon("0170 12345");
        kunde.setAnsprechspartner("Herr Meier");
        kunde.setAnrede(Anrede.HERR);

        Anfrage anfrage = new Anfrage();
        anfrage.setId(42L);
        anfrage.setKunde(kunde);

        AnfrageResponseDto dto = mapper.toAnfrageResponseDto(anfrage);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getKundenId()).isEqualTo(1L);
        assertThat(dto.getKundenName()).isEqualTo("Bauhaus GmbH");
        assertThat(dto.getKundennummer()).isEqualTo("K-001");
        assertThat(dto.getKundenStrasse()).isEqualTo("Baustraße 1");
        assertThat(dto.getKundenPlz()).isEqualTo("10115");
        assertThat(dto.getKundenOrt()).isEqualTo("Berlin");
        assertThat(dto.getKundenTelefon()).isEqualTo("030 12345");
        assertThat(dto.getKundenMobiltelefon()).isEqualTo("0170 12345");
        assertThat(dto.getKundenAnsprechpartner()).isEqualTo("Herr Meier");
        assertThat(dto.getKundenAnrede()).isEqualTo("HERR");
    }

    @Test
    void gibtNullZurueckBeiNullAnfrage() {
        AnfrageResponseDto dto = mapper.toAnfrageResponseDto(null);

        assertThat(dto).isNull();
    }

    @Test
    void mapptAnfrageOhneKunde() {
        Anfrage anfrage = new Anfrage();
        anfrage.setId(10L);
        anfrage.setKunde(null);

        AnfrageResponseDto dto = mapper.toAnfrageResponseDto(anfrage);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getKundenId()).isNull();
        assertThat(dto.getKundenName()).isNull();
    }

    @Test
    void vereinigtKundenEmailsUndAnfrageEmailsOhneDuplikate() {
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Test");
        kunde.setKundenEmails(List.of("info@test.de", "doppelt@test.de"));

        Anfrage anfrage = new Anfrage();
        anfrage.setId(1L);
        anfrage.setKunde(kunde);
        anfrage.setKundenEmails(List.of("doppelt@test.de", "extra@test.de"));

        AnfrageResponseDto dto = mapper.toAnfrageResponseDto(anfrage);

        assertThat(dto.getKundenEmails())
                .containsExactlyInAnyOrder("info@test.de", "doppelt@test.de", "extra@test.de");
    }

    @Test
    void mapptKundeOhneAnrede() {
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Test");
        kunde.setAnrede(null);

        Anfrage anfrage = new Anfrage();
        anfrage.setId(1L);
        anfrage.setKunde(kunde);

        AnfrageResponseDto dto = mapper.toAnfrageResponseDto(anfrage);

        assertThat(dto.getKundenAnrede()).isNull();
    }
}
