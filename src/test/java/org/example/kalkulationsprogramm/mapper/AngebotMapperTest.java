package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Angebot;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Angebot.AngebotResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AngebotMapperTest {

    private final AngebotMapper mapper = new AngebotMapper();

    @Test
    void mapptVollstaendigesAngebotZuDto() {
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

        Angebot angebot = new Angebot();
        angebot.setId(42L);
        angebot.setKunde(kunde);

        AngebotResponseDto dto = mapper.toAngebotResponseDto(angebot);

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
    void gibtNullZurueckBeiNullAngebot() {
        AngebotResponseDto dto = mapper.toAngebotResponseDto(null);

        assertThat(dto).isNull();
    }

    @Test
    void mapptAngebotOhneKunde() {
        Angebot angebot = new Angebot();
        angebot.setId(10L);
        angebot.setKunde(null);

        AngebotResponseDto dto = mapper.toAngebotResponseDto(angebot);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getKundenId()).isNull();
        assertThat(dto.getKundenName()).isNull();
    }

    @Test
    void vereinigtKundenEmailsUndAngebotEmailsOhneDuplikate() {
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Test");
        kunde.setKundenEmails(List.of("info@test.de", "doppelt@test.de"));

        Angebot angebot = new Angebot();
        angebot.setId(1L);
        angebot.setKunde(kunde);
        angebot.setKundenEmails(List.of("doppelt@test.de", "extra@test.de"));

        AngebotResponseDto dto = mapper.toAngebotResponseDto(angebot);

        assertThat(dto.getKundenEmails())
                .containsExactlyInAnyOrder("info@test.de", "doppelt@test.de", "extra@test.de");
    }

    @Test
    void mapptKundeOhneAnrede() {
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Test");
        kunde.setAnrede(null);

        Angebot angebot = new Angebot();
        angebot.setId(1L);
        angebot.setKunde(kunde);

        AngebotResponseDto dto = mapper.toAngebotResponseDto(angebot);

        assertThat(dto.getKundenAnrede()).isNull();
    }
}
