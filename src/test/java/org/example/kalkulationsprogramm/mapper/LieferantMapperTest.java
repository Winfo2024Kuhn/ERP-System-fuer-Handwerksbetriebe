package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantListItemDto;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LieferantMapperTest {

    @Mock
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;

    @InjectMocks
    private LieferantMapper mapper;

    private Lieferanten erstelleLieferant() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        lieferant.setLieferantenname("Stahlwerk AG");
        lieferant.setLieferantenTyp("Werkstoff");
        lieferant.setVertreter("Herr Schulz");
        lieferant.setStrasse("Industriestraße 5");
        lieferant.setPlz("45127");
        lieferant.setOrt("Essen");
        lieferant.setTelefon("0201 12345");
        lieferant.setMobiltelefon("0171 99999");
        lieferant.setIstAktiv(true);
        lieferant.setKundenEmails(List.of("vertrieb@stahlwerk.de"));
        return lieferant;
    }

    @Nested
    class ToListItem {

        @Test
        void mapptAlleFelder() {
            Lieferanten lieferant = erstelleLieferant();

            LieferantListItemDto dto = mapper.toListItem(lieferant);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getLieferantenname()).isEqualTo("Stahlwerk AG");
            assertThat(dto.getLieferantenTyp()).isEqualTo("Werkstoff");
            assertThat(dto.getVertreter()).isEqualTo("Herr Schulz");
            assertThat(dto.getStrasse()).isEqualTo("Industriestraße 5");
            assertThat(dto.getPlz()).isEqualTo("45127");
            assertThat(dto.getOrt()).isEqualTo("Essen");
            assertThat(dto.getTelefon()).isEqualTo("0201 12345");
            assertThat(dto.getMobiltelefon()).isEqualTo("0171 99999");
            assertThat(dto.getIstAktiv()).isTrue();
            assertThat(dto.getKundenEmails()).containsExactly("vertrieb@stahlwerk.de");
        }

        @Test
        void gibtNullZurueckBeiNullLieferant() {
            assertThat(mapper.toListItem(null)).isNull();
        }

        @Test
        void mapptLieferantMitNullFeldern() {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setId(2L);
            lieferant.setLieferantenname("Minimal");

            LieferantListItemDto dto = mapper.toListItem(lieferant);

            assertThat(dto.getId()).isEqualTo(2L);
            assertThat(dto.getLieferantenname()).isEqualTo("Minimal");
            assertThat(dto.getVertreter()).isNull();
            assertThat(dto.getTelefon()).isNull();
        }
    }

    @Nested
    class ToDetailItem {

        @Test
        void mapptDetailMitBerechneterLieferzeitUndBestellungen() {
            Lieferanten lieferant = erstelleLieferant();

            when(geschaeftsdokumentRepository.calculateAverageLieferzeitByLieferantId(1L))
                    .thenReturn(5.7);
            when(geschaeftsdokumentRepository.countBestellungenByLieferantId(1L))
                    .thenReturn(12L);

            LieferantListItemDto dto = mapper.toDetailItem(lieferant);

            assertThat(dto.getLieferantenname()).isEqualTo("Stahlwerk AG");
            assertThat(dto.getLieferzeit()).isEqualTo(5);
            assertThat(dto.getBestellungen()).isEqualTo(12);
        }

        @Test
        void gibtNullZurueckBeiNullLieferant() {
            assertThat(mapper.toDetailItem(null)).isNull();
        }

        @Test
        void setzLieferzeitNichtBeiNullErgebnis() {
            Lieferanten lieferant = erstelleLieferant();

            when(geschaeftsdokumentRepository.calculateAverageLieferzeitByLieferantId(1L))
                    .thenReturn(null);
            when(geschaeftsdokumentRepository.countBestellungenByLieferantId(1L))
                    .thenReturn(0L);

            LieferantListItemDto dto = mapper.toDetailItem(lieferant);

            assertThat(dto.getLieferzeit()).isNull();
            assertThat(dto.getBestellungen()).isEqualTo(0);
        }

        @Test
        void behandeltExceptionBeiLieferzeitBerechnungGraceful() {
            Lieferanten lieferant = erstelleLieferant();

            when(geschaeftsdokumentRepository.calculateAverageLieferzeitByLieferantId(1L))
                    .thenThrow(new RuntimeException("DB-Fehler"));
            when(geschaeftsdokumentRepository.countBestellungenByLieferantId(1L))
                    .thenReturn(5L);

            LieferantListItemDto dto = mapper.toDetailItem(lieferant);

            assertThat(dto.getLieferantenname()).isEqualTo("Stahlwerk AG");
            assertThat(dto.getLieferzeit()).isNull();
            assertThat(dto.getBestellungen()).isEqualTo(5);
        }

        @Test
        void behandeltExceptionBeiBestellungenZaehlungGraceful() {
            Lieferanten lieferant = erstelleLieferant();

            when(geschaeftsdokumentRepository.calculateAverageLieferzeitByLieferantId(1L))
                    .thenReturn(3.0);
            when(geschaeftsdokumentRepository.countBestellungenByLieferantId(1L))
                    .thenThrow(new RuntimeException("DB-Fehler"));

            LieferantListItemDto dto = mapper.toDetailItem(lieferant);

            assertThat(dto.getLieferzeit()).isEqualTo(3);
            assertThat(dto.getBestellungen()).isNull();
        }
    }
}
