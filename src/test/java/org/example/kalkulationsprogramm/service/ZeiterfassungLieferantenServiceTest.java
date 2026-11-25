package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerKurzDto;
import org.example.kalkulationsprogramm.dto.Lieferant.ZeiterfassungLieferantDto;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.lieferantansprechpartner.LieferantAnsprechpartnerUebersicht;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZeiterfassungLieferantenServiceTest {

    @Mock LieferantenRepository lieferantenRepository;
    @Mock LieferantAnsprechpartnerUebersicht ansprechpartner;

    private ZeiterfassungLieferantenService service;

    @BeforeEach
    void setUp() {
        service = new ZeiterfassungLieferantenService(lieferantenRepository, ansprechpartner);
        when(lieferantenRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(lieferantenRepository.findeEmailsFuerLieferanten(anyCollection())).thenReturn(List.of());
    }

    private static Lieferanten lieferant(long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        l.setOrt("Würzburg");
        return l;
    }

    private Pageable seite() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<Lieferanten>> spec = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> seite = ArgumentCaptor.forClass(Pageable.class);
        verify(lieferantenRepository).findAll(spec.capture(), seite.capture());
        return seite.getValue();
    }

    @ParameterizedTest(name = "limit={0} → {1}")
    @CsvSource(value = {"NULL, 15", "100000, 50", "0, 15", "-3, 15", "7, 7"}, nullValues = "NULL")
    @DisplayName("Obergrenze serverseitig: Standard 15, höchstens 50, in der Datenbank begrenzt")
    void obergrenze(Integer limit, int erwartet) {
        service.getLieferanten(limit, null);
        Pageable seite = seite();
        assertThat(seite.getPageSize()).isEqualTo(erwartet);
        assertThat(seite.getPageNumber()).isZero();
        assertThat(seite.getSort().getOrderFor("lieferantenname")).isNotNull();
    }

    @Test
    @DisplayName("Einträge mit Ansprechpartnern und E-Mails, beides gesammelt geladen; JSON-Felder wie bisher")
    void listeMitAnsprechpartnernUndEmails() {
        when(lieferantenRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lieferant(1L, "Muster Stahl KG"), lieferant(2L, "Holz Beispiel GmbH"))));
        when(lieferantenRepository.findeEmailsFuerLieferanten(List.of(1L, 2L))).thenReturn(List.<Object[]>of(
                new Object[]{1L, "info@example.com"}, new Object[]{1L, "max.mustermann@example.com"}));
        LieferantAnsprechpartnerKurzDto max = new LieferantAnsprechpartnerKurzDto(10L, "Max Mustermann", "Außendienst",
                null, "0171 1234567", "max.mustermann@example.com");
        when(ansprechpartner.fuerHandy(List.of(1L, 2L))).thenReturn(Map.of(1L, List.of(max)));

        List<ZeiterfassungLieferantDto> liste = service.getLieferanten(null, null);

        assertThat(liste).hasSize(2);
        assertThat(liste.get(0).firmenname()).isEqualTo("Muster Stahl KG");
        assertThat(liste.get(0).kundenEmails()).containsExactly("info@example.com", "max.mustermann@example.com");
        assertThat(liste.get(0).ansprechpartner()).containsExactly(max);
        assertThat(liste.get(1).kundenEmails()).isEmpty();
        assertThat(liste.get(1).ansprechpartner()).isEmpty();
        verify(ansprechpartner, times(1)).fuerHandy(List.of(1L, 2L));
        verify(lieferantenRepository, times(1)).findeEmailsFuerLieferanten(List.of(1L, 2L));

        Map<String, Object> json = JsonMapper.builder().build().convertValue(liste.get(0),
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() { });
        assertThat(json.keySet()).containsExactlyInAnyOrder("id", "firmenname", "aliasName", "strasse", "plz", "ort",
                "telefon", "mobiltelefon", "kundenEmails", "lieferantenTyp", "eigeneKundennummer", "ansprechpartner");
    }

    @Test
    @DisplayName("Leere Seite fragt E-Mails und Ansprechpartner gar nicht erst")
    void leer() {
        assertThat(service.getLieferanten(null, "gibt es nicht")).isEmpty();
        verify(lieferantenRepository, never()).findeEmailsFuerLieferanten(anyCollection());
        verify(ansprechpartner, never()).fuerHandy(anyCollection());
    }
}
