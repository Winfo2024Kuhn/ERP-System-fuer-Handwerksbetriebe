package org.example.kalkulationsprogramm.service.lieferantansprechpartner;

import org.example.kalkulationsprogramm.domain.LieferantAnsprechpartner;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerKurzDto;
import org.example.kalkulationsprogramm.repository.LieferantAnsprechpartnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LieferantAnsprechpartnerUebersichtTest {

    @Mock LieferantAnsprechpartnerRepository repository;

    private LieferantAnsprechpartnerUebersicht uebersicht;

    @BeforeEach
    void setUp() {
        uebersicht = new LieferantAnsprechpartnerUebersicht(repository);
    }

    private static LieferantAnsprechpartner person(long lieferantId, long id, String vorname, String nachname) {
        Lieferanten l = new Lieferanten();
        l.setId(lieferantId);
        LieferantAnsprechpartner a = new LieferantAnsprechpartner();
        a.setLieferant(l);
        a.setId(id);
        a.setVorname(vorname);
        a.setNachname(nachname);
        return a;
    }

    @Test
    @DisplayName("Hinweis: erster Ansprechpartner, bei mehreren mit „ +N“; ohne Vorname nur Nachname; ohne Ansprechpartner fehlt der Eintrag")
    void hinweise() {
        when(repository.findeFuerLieferanten(List.of(1L, 2L, 3L))).thenReturn(List.of(
                person(1L, 10L, "Max", "Mustermann"),
                person(1L, 11L, "Erika", "Musterfrau"),
                person(1L, 12L, null, "Zentrale"),
                person(2L, 20L, null, "Musterfrau")));

        Map<Long, String> hinweise = uebersicht.hinweise(List.of(1L, 2L, 3L));

        assertThat(hinweise).containsEntry(1L, "Max Mustermann +2")
                .containsEntry(2L, "Musterfrau")
                .doesNotContainKey(3L);
        assertThat(uebersicht.hinweisText(List.of())).isNull();
    }

    @Test
    @DisplayName("Einzelhinweis für das Anruf-Fenster")
    void einzelhinweis() {
        when(repository.findeFuerLieferanten(List.of(1L))).thenReturn(List.of(person(1L, 10L, "Max", "Mustermann")));
        assertThat(uebersicht.hinweis(1L)).isEqualTo("Max Mustermann");
        assertThat(uebersicht.hinweis(null)).isNull();
    }

    @Test
    @DisplayName("Leere Seite fragt die Datenbank gar nicht erst")
    void leer() {
        assertThat(uebersicht.hinweise(List.of())).isEmpty();
        assertThat(uebersicht.fuerHandy(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Viele Lieferanten werden in Blöcken zu je 1000 geladen (keine Abfrage je Lieferant)")
    void bloecke() {
        when(repository.findeFuerLieferanten(anyCollection())).thenReturn(List.of());
        List<Long> ids = LongStream.rangeClosed(1, 2500).boxed().toList();

        uebersicht.hinweise(ids);

        verify(repository, times(3)).findeFuerLieferanten(anyCollection());
    }

    @Test
    @DisplayName("Handy-App: Kurzform je Lieferant in Abfrage-Reihenfolge")
    void handy() {
        LieferantAnsprechpartner max = person(1L, 10L, "Max", "Mustermann");
        max.setAufgabe("Außendienst");
        max.setMobiltelefon("0171 1234567");
        max.setEmail("max.mustermann@example.com");
        LieferantAnsprechpartner erika = person(1L, 11L, null, "Musterfrau");
        erika.setTelefon("0931 4444-12");
        when(repository.findeFuerLieferanten(List.of(1L, 2L))).thenReturn(new ArrayList<>(List.of(max, erika)));

        Map<Long, List<LieferantAnsprechpartnerKurzDto>> handy = uebersicht.fuerHandy(List.of(1L, 2L));

        assertThat(handy.get(1L)).containsExactly(
                new LieferantAnsprechpartnerKurzDto(10L, "Max Mustermann", "Außendienst", null, "0171 1234567",
                        "max.mustermann@example.com"),
                new LieferantAnsprechpartnerKurzDto(11L, "Musterfrau", null, "0931 4444-12", null, null));
        assertThat(handy.getOrDefault(2L, List.of())).isEmpty();
    }

    @Test
    @DisplayName("Anzahl für den Reiter aus einer Zählabfrage")
    void anzahl() {
        when(repository.countByLieferantId(5L)).thenReturn(3L);
        assertThat(uebersicht.anzahl(5L)).isEqualTo(3L);
    }
}
