package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.AbteilungDokumentBerechtigung;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbteilungServiceTest {

    @Mock AbteilungRepository abteilungRepository;
    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock ArbeitsgangRepository arbeitsgangRepository;
    @Mock AbteilungDokumentBerechtigungRepository berechtigungRepository;
    @InjectMocks AbteilungService service;

    private static Abteilung abteilung(long id, String name) {
        Abteilung a = new Abteilung();
        a.setId(id);
        a.setName(name);
        return a;
    }

    private static HttpStatus status(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    void findeAlleGibtDieListeDesRepositoriesZurueck() {
        List<Abteilung> alle = List.of(abteilung(1, "Montage"));
        when(abteilungRepository.findAll()).thenReturn(alle);
        assertThat(service.findeAlle()).isEqualTo(alle);
    }

    @Test
    void erstelleSpeichertGetrimmtenNamen() {
        when(abteilungRepository.existsByNameIgnoreCase("Montage")).thenReturn(false);
        when(abteilungRepository.save(any(Abteilung.class))).thenAnswer(a -> a.getArgument(0));

        Abteilung neu = service.erstelle("  Montage  ");

        assertThat(neu.getName()).isEqualTo("Montage");
    }

    @Test
    void erstelleSpeichertSonderzeichenUnveraendertAlsText() {
        // React zeigt den Namen als Text an; Skript-Tags und SQL-Schnipsel sind hier nur Zeichen.
        String xss = "<script>alert(1)</script>";
        String sql = "'; DROP TABLE abteilung; --";
        when(abteilungRepository.save(any(Abteilung.class))).thenAnswer(a -> a.getArgument(0));

        assertThat(service.erstelle(xss).getName()).isEqualTo(xss);
        assertThat(service.erstelle(sql).getName()).isEqualTo(sql);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void erstelleOhneNamenIst400(String name) {
        assertThatThrownBy(() -> service.erstelle(name))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Bitte einen Namen");
        verify(abteilungRepository, never()).save(any());
    }

    @Test
    void erstelleMitUeberlangemNamenIst400() {
        assertThatThrownBy(() -> service.erstelle("x".repeat(10_001)))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("zu lang");
        assertThatThrownBy(() -> service.erstelle("x".repeat(AbteilungService.MAX_NAME_LAENGE + 1)))
                .hasMessageContaining("zu lang");
        verify(abteilungRepository, never()).save(any());
    }

    @Test
    void erstelleMitHoechstlaengeIstErlaubt() {
        when(abteilungRepository.save(any(Abteilung.class))).thenAnswer(a -> a.getArgument(0));
        String name = "x".repeat(AbteilungService.MAX_NAME_LAENGE);
        assertThat(service.erstelle(name).getName()).isEqualTo(name);
    }

    @Test
    void erstelleDoppeltenNamenIst409() {
        when(abteilungRepository.existsByNameIgnoreCase("montage")).thenReturn(true);
        assertThatThrownBy(() -> service.erstelle("montage"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Eine Abteilung „montage“ gibt es schon.");
        verify(abteilungRepository, never()).save(any());
    }

    @Test
    void benenneUmAendertNurDenNamen() {
        Abteilung a = abteilung(3, "Werkstatt");
        a.setDarfMonatAbschliessen(true);
        when(abteilungRepository.findById(3L)).thenReturn(Optional.of(a));
        when(abteilungRepository.existsByNameIgnoreCaseAndIdNot("Schlosserei", 3L)).thenReturn(false);
        when(abteilungRepository.save(a)).thenReturn(a);

        Abteilung ergebnis = service.benenneUm(3L, " Schlosserei ");

        assertThat(ergebnis.getName()).isEqualTo("Schlosserei");
        assertThat(ergebnis.getDarfMonatAbschliessen()).isTrue();
    }

    @Test
    void benenneUmAufVorhandenenNamenIst409() {
        when(abteilungRepository.findById(3L)).thenReturn(Optional.of(abteilung(3, "Werkstatt")));
        when(abteilungRepository.existsByNameIgnoreCaseAndIdNot("Montage", 3L)).thenReturn(true);
        assertThatThrownBy(() -> service.benenneUm(3L, "Montage"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
        verify(abteilungRepository, never()).save(any());
    }

    @Test
    void benenneUmUnbekannteAbteilungIst404() {
        when(abteilungRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.benenneUm(Long.MAX_VALUE, "Montage"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("gibt es nicht mehr");
    }

    @Test
    void loescheEntferntRechteUndAbteilung() {
        Abteilung a = abteilung(4, "Büro");
        List<AbteilungDokumentBerechtigung> rechte = List.of(new AbteilungDokumentBerechtigung());
        when(abteilungRepository.findById(4L)).thenReturn(Optional.of(a));
        when(mitarbeiterRepository.countByAbteilungen_Id(4L)).thenReturn(0L);
        when(arbeitsgangRepository.countByAbteilung_Id(4L)).thenReturn(0L);
        when(berechtigungRepository.findByAbteilungId(4L)).thenReturn(rechte);

        service.loesche(4L);

        verify(berechtigungRepository).deleteAll(rechte);
        verify(abteilungRepository).delete(a);
    }

    @Test
    void loescheMitZugeordnetenMitarbeiternUndArbeitsgaengenIst409() {
        when(abteilungRepository.findById(4L)).thenReturn(Optional.of(abteilung(4, "Montage")));
        when(mitarbeiterRepository.countByAbteilungen_Id(4L)).thenReturn(3L);
        when(arbeitsgangRepository.countByAbteilung_Id(4L)).thenReturn(5L);

        assertThatThrownBy(() -> service.loesche(4L))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("„Montage“ kann nicht gelöscht werden: 3 Mitarbeiter und 5 Arbeitsgänge sind noch zugeordnet.");
        verify(abteilungRepository, never()).delete(any());
        verify(berechtigungRepository, never()).deleteAll(any());
    }

    @Test
    void loescheUnbekannteAbteilungIst404() {
        when(abteilungRepository.findById(0L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loesche(0L))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void meldungPasstSichDerAnzahlAn() {
        assertThat(AbteilungService.nochZugeordnetMeldung("Büro", 1, 0))
                .isEqualTo("„Büro“ kann nicht gelöscht werden: 1 Mitarbeiter ist noch zugeordnet. "
                        + "Bitte zuerst beim Mitarbeiter (auch bei ausgeschiedenen) eine andere Abteilung wählen.");
        assertThat(AbteilungService.nochZugeordnetMeldung("Büro", 2, 0))
                .contains("2 Mitarbeiter sind noch zugeordnet.");
        assertThat(AbteilungService.nochZugeordnetMeldung("Montage", 0, 1))
                .contains("1 Arbeitsgang ist noch zugeordnet.")
                .endsWith("Bitte zuerst die Arbeitsgänge unter Vorlagen & Stammdaten → Arbeitsgänge löschen.");
        assertThat(AbteilungService.nochZugeordnetMeldung("Montage", 1, 1))
                .contains("1 Mitarbeiter und 1 Arbeitsgang sind noch zugeordnet.")
                .endsWith("Bitte zuerst beim Mitarbeiter (auch bei ausgeschiedenen) eine andere Abteilung wählen und die Arbeitsgänge löschen.");
    }
}
