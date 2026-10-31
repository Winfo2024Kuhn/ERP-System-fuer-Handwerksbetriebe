package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.domain.TextbausteinTyp;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinDto;
import org.example.kalkulationsprogramm.repository.TextbausteinRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TextbausteinServiceTest {

    @Mock
    private TextbausteinRepository repository;

    @InjectMocks
    private TextbausteinService service;

    private static TextbausteinDto dto(String name, String... dokumenttypen) {
        TextbausteinDto dto = new TextbausteinDto();
        dto.setName(name);
        dto.setTyp("VORTEXT");
        dto.setBeschreibung("Anschreiben für Max Mustermann");
        dto.setHtml("<p>{{Anrede}} {{KUNDENNAME}}</p>");
        dto.setDokumenttypen(new java.util.ArrayList<>(List.of(dokumenttypen)));
        return dto;
    }

    private static Textbaustein baustein(Long id, String name, Integer sortOrder) {
        Textbaustein t = new Textbaustein();
        t.setId(id);
        t.setName(name);
        t.setSortOrder(sortOrder);
        return t;
    }

    @Test
    @DisplayName("Neuanlage übernimmt Typ, Beschreibung und Dokumentarten (Label und Enum-Name)")
    void createUebernimmtFelder() {
        when(repository.save(any(Textbaustein.class))).thenAnswer(a -> a.getArgument(0));

        Textbaustein saved = service.create(dto("  Angebot Einleitung  ", "Angebot", " 1. Mahnung ", "RECHNUNG", " "));

        assertThat(saved.getName()).isEqualTo("Angebot Einleitung");
        assertThat(saved.getTyp()).isEqualTo(TextbausteinTyp.VORTEXT);
        assertThat(saved.getBeschreibung()).isEqualTo("Anschreiben für Max Mustermann");
        assertThat(saved.getDokumenttypen())
                .containsExactly(Dokumenttyp.ANGEBOT, Dokumenttyp.ERSTE_MAHNUNG, Dokumenttyp.RECHNUNG);
    }

    @Test
    @DisplayName("Unbekannte Dokumentart ist ein Eingabefehler (400) und speichert nichts")
    void unbekannteDokumentartIst400() {
        assertThatThrownBy(() -> service.create(dto("Test", "'; DROP TABLE textbaustein; --")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).startsWith("Unbekannte Dokumentart: '; DROP TABLE");
                });
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Sehr lange unbekannte Dokumentart wird in der Meldung gekürzt")
    void langeDokumentartGekuerzt() {
        String lang = "x".repeat(200);
        assertThatThrownBy(() -> service.create(dto("Test", lang)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo("Unbekannte Dokumentart: " + "x".repeat(40) + "…"));
    }

    @Test
    @DisplayName("Ohne Dokumentarten-Liste bleibt der Baustein ohne Zuordnung")
    void ohneDokumentartenListe() {
        when(repository.save(any(Textbaustein.class))).thenAnswer(a -> a.getArgument(0));
        TextbausteinDto dto = dto("Test");
        dto.setDokumenttypen(null);

        assertThat(service.create(dto).getDokumenttypen()).isEmpty();
    }

    @Test
    @DisplayName("Bearbeiten ersetzt die Dokumentarten vollständig")
    void updateErsetztDokumentarten() {
        Textbaustein vorhanden = baustein(7L, "Alt", null);
        vorhanden.getDokumenttypen().add(Dokumenttyp.GUTSCHRIFT);
        when(repository.findById(7L)).thenReturn(Optional.of(vorhanden));
        when(repository.save(any(Textbaustein.class))).thenAnswer(a -> a.getArgument(0));

        Textbaustein saved = service.update(7L, dto("Neu", "Schlussrechnung"));

        assertThat(saved.getName()).isEqualTo("Neu");
        assertThat(saved.getDokumenttypen()).containsExactly(Dokumenttyp.SCHLUSSRECHNUNG);
    }

    @Test
    @DisplayName("Bearbeiten eines unbekannten Bausteins meldet 404 mit verständlicher Meldung")
    void unbekannteIdIst404() {
        when(repository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(Long.MAX_VALUE, dto("Neu")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("Diesen Textbaustein gibt es nicht mehr.");
                });
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Liste sortiert nach Reihenfolge, dann Name ohne Groß-/Kleinschreibung")
    void listeSortiert() {
        when(repository.findAllWithDokumenttypen()).thenReturn(List.of(
                baustein(1L, "zahlung", null), baustein(2L, "Angebot", null), baustein(3L, "Mahnung", 1)));

        assertThat(service.list()).extracting(Textbaustein::getId).containsExactly(3L, 2L, 1L);
    }

    @Test
    @DisplayName("Löschen ohne id tut nichts, mit id löscht es")
    void loeschen() {
        service.delete(null);
        verify(repository, never()).deleteById(any());

        service.delete(5L);
        verify(repository).deleteById(5L);
    }
}
