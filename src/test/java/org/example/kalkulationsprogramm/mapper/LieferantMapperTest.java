package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantListItemDto;
import org.example.kalkulationsprogramm.service.lieferantansprechpartner.LieferantAnsprechpartnerUebersicht;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LieferantMapperTest {

    @Mock
    private LieferantAnsprechpartnerUebersicht ansprechpartnerUebersicht;

    @InjectMocks
    private LieferantMapper mapper;

    private Lieferanten erstelleLieferant() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        lieferant.setLieferantenname("Stahlwerk AG");
        lieferant.setLieferantenTyp("Werkstoff");
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
            assertThat(dto.getStrasse()).isEqualTo("Industriestraße 5");
            assertThat(dto.getPlz()).isEqualTo("45127");
            assertThat(dto.getOrt()).isEqualTo("Essen");
            assertThat(dto.getTelefon()).isEqualTo("0201 12345");
            assertThat(dto.getMobiltelefon()).isEqualTo("0171 99999");
            assertThat(dto.getIstAktiv()).isTrue();
            assertThat(dto.getKundenEmails()).containsExactly("vertrieb@stahlwerk.de");
            assertThat(dto.getAnsprechpartnerHinweis()).isNull();
            verifyNoInteractions(ansprechpartnerUebersicht);
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
            assertThat(dto.getTelefon()).isNull();
        }
    }

    @Nested
    class ToListItems {

        @Test
        void setztAnsprechpartnerHinweisFuerDieGanzeSeiteMitEinerAbfrage() {
            Lieferanten stahl = erstelleLieferant();
            Lieferanten ohne = new Lieferanten();
            ohne.setId(2L);
            ohne.setLieferantenname("Ohne Ansprechpartner");
            when(ansprechpartnerUebersicht.hinweise(List.of(1L, 2L))).thenReturn(Map.of(1L, "Max Mustermann +2"));

            List<LieferantListItemDto> liste = mapper.toListItems(List.of(stahl, ohne));

            assertThat(liste).extracting(LieferantListItemDto::getAnsprechpartnerHinweis)
                    .containsExactly("Max Mustermann +2", null);
            verify(ansprechpartnerUebersicht, times(1)).hinweise(List.of(1L, 2L));
        }

        @Test
        void leereSeiteFragtNichts() {
            assertThat(mapper.toListItems(List.of())).isEmpty();
            verifyNoInteractions(ansprechpartnerUebersicht);
        }
    }
}
