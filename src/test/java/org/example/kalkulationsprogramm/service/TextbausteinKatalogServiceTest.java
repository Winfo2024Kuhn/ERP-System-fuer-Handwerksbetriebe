package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.dto.Textbaustein.DokumenttypGruppeDto;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinPlatzhalterDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextbausteinKatalogServiceTest {

    private final TextbausteinKatalogService katalog = new TextbausteinKatalogService();

    @Test
    @DisplayName("Jede Dokumentart steht in genau einer Gruppe")
    void jedeDokumentartGenauEinmal() {
        List<String> gruppiert = katalog.dokumenttypGruppen().stream()
                .flatMap(g -> g.dokumenttypen().stream())
                .toList();
        List<String> alle = Arrays.stream(Dokumenttyp.values()).map(Dokumenttyp::getLabel).toList();

        assertThat(gruppiert).containsExactlyInAnyOrderElementsOf(alle);
        assertThat(gruppiert).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Gruppen stehen in der abgestimmten Reihenfolge")
    void gruppenReihenfolge() {
        assertThat(katalog.dokumenttypGruppen()).extracting(DokumenttypGruppeDto::gruppe)
                .containsExactly("Angebote & Aufträge", "Rechnungen", "Mahnungen", "Korrekturen");
        assertThat(katalog.dokumenttypGruppen().get(0).dokumenttypen())
                .containsExactly("Angebot", "Nachtragsangebot", "Auftragsbestätigung");
    }

    @Test
    @DisplayName("Jeder Platzhalter hat Bezeichnung, Beschreibung, Beispiel und Gruppe")
    void platzhalterVollstaendig() {
        List<TextbausteinPlatzhalterDto> liste = katalog.platzhalter();

        assertThat(liste).hasSize(16);
        assertThat(liste).allSatisfy(p -> {
            assertThat(p.token()).matches("\\{\\{[A-Za-z_]++}}");
            assertThat(p.label()).isNotBlank();
            assertThat(p.beschreibung()).isNotBlank();
            assertThat(p.beispiel()).isNotBlank();
            assertThat(p.gruppe()).isNotBlank();
        });
        assertThat(liste).extracting(p -> p.token().toUpperCase()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Platzhalter sind nach Gruppen zusammenhängend sortiert")
    void platzhalterGruppenZusammenhaengend() {
        List<String> gruppenFolge = katalog.platzhalter().stream()
                .map(TextbausteinPlatzhalterDto::gruppe)
                .distinct()
                .toList();

        assertThat(gruppenFolge).containsExactly("Kunde", "Dokument", "Projekt", "Zahlung", "Vorgänger-Dokument");
        assertThat(katalog.platzhalter().get(1))
                .isEqualTo(new TextbausteinPlatzhalterDto("{{KUNDENNAME}}", "Kundenname",
                        "Name bzw. Firma des Kunden", "Max Mustermann", "Kunde"));
    }
}
