package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungVerwendungDto;
import org.example.kalkulationsprogramm.mapper.LeistungMapper;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeistungServiceTest {

    @Mock
    private LeistungRepository leistungRepository;
    @Mock
    private LeistungMapper leistungMapper;
    @Mock
    private ProduktkategorieRepository produktkategorieRepository;
    @Mock
    private AusgangsGeschaeftsDokumentRepository dokumentRepository;

    @InjectMocks
    private LeistungService leistungService;

    private static LeistungCreateDto dto(String name, Long folderId, Verrechnungseinheit unit) {
        LeistungCreateDto dto = new LeistungCreateDto();
        dto.setName(name);
        dto.setDescription("<p>Geländer aus Edelstahl montieren</p>");
        dto.setPrice(new BigDecimal("120.00"));
        dto.setFolderId(folderId);
        dto.setUnit(unit);
        return dto;
    }

    private static void erwarte400(Runnable aufruf, String meldung) {
        assertThatThrownBy(aufruf::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo(meldung);
                });
    }

    @Nested
    @DisplayName("createLeistung")
    class Create {

        @Test
        @DisplayName("Legt Leistung im Ordner der untersten Ebene an")
        void legtLeistungAn() {
            LeistungCreateDto eingabe = dto("Geländer montieren", 7L, null);
            Leistung entity = new Leistung();
            LeistungDto ergebnis = new LeistungDto();
            ergebnis.setId(42L);
            when(produktkategorieRepository.existsById(7L)).thenReturn(true);
            when(produktkategorieRepository.existsByUebergeordneteKategorieId(7L)).thenReturn(false);
            when(leistungMapper.toEntity(eingabe)).thenReturn(entity);
            when(leistungRepository.save(entity)).thenReturn(entity);
            when(leistungMapper.toDto(entity)).thenReturn(ergebnis);

            assertThat(leistungService.createLeistung(eingabe).getId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("Leerraum am Rand des Namens wird entfernt")
        void nameWirdGetrimmt() {
            LeistungCreateDto eingabe = dto("  Geländer montieren  ", 7L, null);
            Leistung entity = new Leistung();
            when(produktkategorieRepository.existsById(7L)).thenReturn(true);
            when(leistungMapper.toEntity(eingabe)).thenReturn(entity);
            when(leistungRepository.save(entity)).thenReturn(entity);
            when(leistungMapper.toDto(entity)).thenReturn(new LeistungDto());

            leistungService.createLeistung(eingabe);

            assertThat(eingabe.getName()).isEqualTo("Geländer montieren");
        }

        @Test
        @DisplayName("Ohne Ordner, aber mit Einheit ist erlaubt")
        void ohneOrdnerMitEinheit() {
            LeistungCreateDto eingabe = dto("Geländer montieren", null, Verrechnungseinheit.STUECK);
            Leistung entity = new Leistung();
            when(leistungMapper.toEntity(eingabe)).thenReturn(entity);
            when(leistungRepository.save(entity)).thenReturn(entity);
            when(leistungMapper.toDto(entity)).thenReturn(new LeistungDto());

            leistungService.createLeistung(eingabe);

            verify(leistungRepository).save(entity);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("Leerer Name gibt 400")
        void leererName(String name) {
            erwarte400(() -> leistungService.createLeistung(dto(name, 7L, null)),
                    "Bitte geben Sie der Leistung einen Namen.");
            verify(leistungRepository, never()).save(any());
        }

        @Test
        @DisplayName("Fehlender Name oder fehlende Angaben geben 400")
        void fehlenderName() {
            erwarte400(() -> leistungService.createLeistung(dto(null, 7L, null)),
                    "Bitte geben Sie der Leistung einen Namen.");
            erwarte400(() -> leistungService.createLeistung(null),
                    "Bitte geben Sie der Leistung einen Namen.");
        }

        @Test
        @DisplayName("Überlanger Name gibt 400")
        void ueberlangerName() {
            erwarte400(() -> leistungService.createLeistung(dto("A".repeat(10_001), 7L, null)),
                    "Der Name der Leistung ist zu lang (höchstens 255 Zeichen).");
        }

        @Test
        @DisplayName("Name mit genau 255 Zeichen ist erlaubt")
        void nameMitMaximallaenge() {
            LeistungCreateDto eingabe = dto("A".repeat(255), 7L, null);
            Leistung entity = new Leistung();
            when(produktkategorieRepository.existsById(7L)).thenReturn(true);
            when(leistungMapper.toEntity(eingabe)).thenReturn(entity);
            when(leistungRepository.save(entity)).thenReturn(entity);
            when(leistungMapper.toDto(entity)).thenReturn(new LeistungDto());

            leistungService.createLeistung(eingabe);

            verify(leistungRepository).save(entity);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("Ungültige Ordner-ID gibt 400 ohne Datenbankabfrage")
        void ungueltigeOrdnerId(long folderId) {
            erwarte400(() -> leistungService.createLeistung(dto("Geländer montieren", folderId, null)),
                    "Der gewählte Ordner existiert nicht mehr.");
            verify(produktkategorieRepository, never()).existsById(any());
        }

        @Test
        @DisplayName("Unbekannter Ordner gibt 400")
        void unbekannterOrdner() {
            when(produktkategorieRepository.existsById(Long.MAX_VALUE)).thenReturn(false);
            erwarte400(() -> leistungService.createLeistung(dto("Geländer montieren", Long.MAX_VALUE, null)),
                    "Der gewählte Ordner existiert nicht mehr.");
        }

        @Test
        @DisplayName("Ordner mit Unterordnern gibt 400")
        void ordnerMitUnterordnern() {
            when(produktkategorieRepository.existsById(3L)).thenReturn(true);
            when(produktkategorieRepository.existsByUebergeordneteKategorieId(3L)).thenReturn(true);
            erwarte400(() -> leistungService.createLeistung(dto("Geländer montieren", 3L, null)),
                    "Leistungen können nur in der untersten Ordnerebene gespeichert werden.");
            verify(leistungRepository, never()).save(any());
        }

        @Test
        @DisplayName("Weder Ordner noch Einheit gibt 400")
        void wederOrdnerNochEinheit() {
            erwarte400(() -> leistungService.createLeistung(dto("Geländer montieren", null, null)),
                    "Bitte wählen Sie einen Ordner oder eine Einheit für die Leistung.");
        }

        @Test
        @DisplayName("SQL- und HTML-Zeichen im Namen werden unverändert weitergegeben")
        void sonderzeichenImNamen() {
            LeistungCreateDto eingabe = dto("'; DROP TABLE leistung; -- <script>alert(1)</script>", 7L, null);
            Leistung entity = new Leistung();
            when(produktkategorieRepository.existsById(7L)).thenReturn(true);
            when(leistungMapper.toEntity(eingabe)).thenReturn(entity);
            when(leistungRepository.save(entity)).thenReturn(entity);
            when(leistungMapper.toDto(entity)).thenReturn(new LeistungDto());

            leistungService.createLeistung(eingabe);

            verify(leistungMapper).toEntity(eingabe);
        }
    }

    @Nested
    @DisplayName("updateLeistung")
    class Update {

        @Test
        @DisplayName("Unbekannte Leistung gibt 404")
        void unbekannteLeistung() {
            when(leistungRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leistungService.updateLeistung(99L, dto("Geländer montieren", 7L, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("Bestehende Leistung in Zwischenordner darf bearbeitet werden")
        void zwischenordnerBeimBearbeitenErlaubt() {
            Leistung entity = new Leistung();
            LeistungCreateDto eingabe = dto("Geländer montieren", 3L, null);
            when(leistungRepository.findById(5L)).thenReturn(Optional.of(entity));
            when(produktkategorieRepository.existsById(3L)).thenReturn(true);
            when(leistungRepository.save(entity)).thenReturn(entity);
            when(leistungMapper.toDto(entity)).thenReturn(new LeistungDto());

            leistungService.updateLeistung(5L, eingabe);

            verify(leistungMapper).updateEntity(entity, eingabe);
            verify(produktkategorieRepository, never()).existsByUebergeordneteKategorieId(any());
        }

        @Test
        @DisplayName("Leerer Name beim Bearbeiten gibt 400")
        void leererNameBeimBearbeiten() {
            when(leistungRepository.findById(5L)).thenReturn(Optional.of(new Leistung()));
            erwarte400(() -> leistungService.updateLeistung(5L, dto(" ", 7L, null)),
                    "Bitte geben Sie der Leistung einen Namen.");
        }
    }

    @Nested
    @DisplayName("Verwendung in Dokumenten")
    class Verwendung {

        private AusgangsGeschaeftsDokument dokument(long id, String positionen, Projekt projekt) {
            AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
            d.setId(id);
            d.setDokumentNummer("AN-2026-0" + id);
            d.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            d.setDatum(LocalDate.of(2026, 3, 2));
            d.setPositionenJson(positionen);
            d.setProjekt(projekt);
            return d;
        }

        @Test
        @DisplayName("Findet nur Dokumente mit genau dieser Leistung, nicht 120 statt 12")
        void filtertGenau() {
            Projekt neubau = new Projekt();
            neubau.setId(9L);
            neubau.setBauvorhaben("Neubau Mustermann");
            when(leistungRepository.existsById(12L)).thenReturn(true);
            when(dokumentRepository.findeMitLeistungInPositionen("%\"leistungId\":12%", "%\"leistungId\": 12%",
                    "%\"leistungId\":\"12\"%")).thenReturn(List.of(
                    dokument(1, "[{\"type\":\"SERVICE\",\"leistungId\":12,\"quantity\":1}]", neubau),
                    dokument(2, "[{\"type\":\"SERVICE\",\"leistungId\":120,\"quantity\":1}]", null)));

            LeistungVerwendungDto verwendung = leistungService.ermittleVerwendung(12L);

            assertThat(verwendung.dokumentAnzahl()).isEqualTo(1);
            assertThat(verwendung.dokumente()).singleElement().satisfies(d -> {
                assertThat(d.dokumentNummer()).isEqualTo("AN-2026-01");
                assertThat(d.typ()).isEqualTo(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
                assertThat(d.projektId()).isEqualTo(9L);
                assertThat(d.bauvorhaben()).isEqualTo("Neubau Mustermann");
            });
        }

        @Test
        @DisplayName("Kürzt die Liste, zählt aber alle")
        void kuerztListe() {
            when(leistungRepository.existsById(5L)).thenReturn(true);
            List<AusgangsGeschaeftsDokument> viele = IntStream.range(0, 25)
                    .mapToObj(i -> dokument(i, "[{\"type\":\"SERVICE\",\"leistungId\":5}]", null))
                    .toList();
            when(dokumentRepository.findeMitLeistungInPositionen(any(), any(), any())).thenReturn(viele);

            LeistungVerwendungDto verwendung = leistungService.ermittleVerwendung(5L);

            assertThat(verwendung.dokumentAnzahl()).isEqualTo(25);
            assertThat(verwendung.dokumente()).hasSize(LeistungService.MAX_DOKUMENTE);
            assertThat(verwendung.dokumente().get(0).projektId()).isNull();
        }

        @ParameterizedTest
        @ValueSource(longs = { -1L, 0L, Long.MAX_VALUE })
        @DisplayName("Unbekannte oder ungültige IDs ergeben 404")
        void unbekannt(long id) {
            assertThatThrownBy(() -> leistungService.ermittleVerwendung(id))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
            verify(dokumentRepository, never()).findeMitLeistungInPositionen(any(), any(), any());
        }
    }
}
