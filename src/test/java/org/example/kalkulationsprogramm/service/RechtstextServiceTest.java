package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.RechtstextArt;
import org.example.kalkulationsprogramm.domain.RechtstextFassung;
import org.example.kalkulationsprogramm.repository.RechtstextFassungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RechtstextServiceTest {

    private static final RechtstextArt W = RechtstextArt.WIDERRUFSBELEHRUNG;
    private static final Clock UHR = Clock.fixed(Instant.parse("2026-11-02T08:15:00Z"), ZoneId.of("Europe/Berlin"));

    @Mock RechtstextFassungRepository repository;

    private RechtstextService service;

    @BeforeEach
    void setUp() {
        service = new RechtstextService(repository, UHR);
    }

    private static RechtstextFassung fassung(int nr, String html) {
        RechtstextFassung f = new RechtstextFassung();
        f.setArt(W);
        f.setFassung(nr);
        f.setStand(LocalDate.of(2026, 10, 20));
        f.setGeaendertAm(LocalDateTime.of(2026, 10, 20, 10, 0));
        f.setInhaltHtml(html);
        return f;
    }

    @Test
    @DisplayName("Ohne gespeicherte Fassung gilt der Standardtext als Fassung 0")
    void aktuell_ohneFassung_standardtext() {
        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.empty());

        RechtstextService.Rechtstext t = service.aktuell(W);

        assertThat(t.fassung()).isZero();
        assertThat(t.standardtext()).isTrue();
        assertThat(t.stand()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(t.geaendertAm()).isNull();
        assertThat(t.html()).contains("<h3>Widerrufsrecht</h3>");
    }

    @Test
    @DisplayName("Standardtext folgt dem gesetzlichen Muster ab 19.06.2026")
    void standardtext_folgtMuster() {
        String html = service.standardHtml(W);

        assertThat(html)
                .contains("ab dem Tag des Vertragsabschlusses.")
                .contains("(z. B. ein mit der Post versandter Brief oder eine E-Mail)")
                .contains("müssen Sie uns ({{FIRMA_KONTAKT}})")
                .contains("während der Widerrufsfrist beginnen soll, so haben Sie")
                .contains("§ 356 Abs. 5 Nr. 2 BGB")
                .doesNotContain("Telefax")
                .doesNotContain("Vertragsschlusses")
                .doesNotContain("§ 357a")
                .doesNotContain("Muster-Widerrufsformular</h3>");
        // einmal geladen, danach aus dem Speicher
        assertThat(service.standardHtml(W)).isSameAs(html);
    }

    @Test
    @DisplayName("Jüngste Fassung ist der gültige Text")
    void aktuell_mitFassung() {
        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.of(fassung(4, "<p>Max</p>")));

        RechtstextService.Rechtstext t = service.aktuell(W);

        assertThat(t.fassung()).isEqualTo(4);
        assertThat(t.standardtext()).isFalse();
        assertThat(t.html()).isEqualTo("<p>Max</p>");
    }

    @Test
    @DisplayName("Speichern legt eine neue Fassung mit heutigem Stand an und bereinigt das HTML")
    void speichere_neueFassung() {
        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.of(fassung(2, "<p>alt</p>")));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RechtstextService.Rechtstext t = service.speichere(W,
                "<p onclick=\"x()\">Neu <strong>fett</strong></p><script>alert(1)</script><img src=x onerror=alert(1)>");

        ArgumentCaptor<RechtstextFassung> captor = ArgumentCaptor.forClass(RechtstextFassung.class);
        verify(repository).saveAndFlush(captor.capture());
        RechtstextFassung neu = captor.getValue();
        assertThat(neu.getFassung()).isEqualTo(3);
        assertThat(neu.getStand()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(neu.getGeaendertAm()).isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 15));
        assertThat(neu.getInhaltHtml()).isEqualTo("<p>Neu <strong>fett</strong></p>");
        assertThat(t.fassung()).isEqualTo(3);
        assertThat(t.standardtext()).isFalse();
    }

    @Test
    @DisplayName("Erste eigene Fassung bekommt die Nummer 1")
    void speichere_ersteFassung() {
        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.speichere(W, "<p>Max Mustermann</p>").fassung()).isEqualTo(1);
    }

    @Test
    @DisplayName("Unveränderter Text legt keine neue Fassung an")
    void speichere_unveraendert() {
        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.of(fassung(2, "<p>gleich</p>")));

        RechtstextService.Rechtstext t = service.speichere(W, "  <p>gleich</p> ");

        assertThat(t.fassung()).isEqualTo(2);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Zurücksetzen speichert den Standardtext – gilt er schon, entsteht nichts Neues")
    void setzeStandard() {
        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.empty());

        RechtstextService.Rechtstext t = service.setzeStandard(W);

        assertThat(t.standardtext()).isTrue();
        verify(repository, never()).save(any());

        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.of(fassung(1, "<p>eigen</p>")));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        RechtstextService.Rechtstext zurueck = service.setzeStandard(W);
        assertThat(zurueck.fassung()).isEqualTo(2);
        assertThat(zurueck.html()).isEqualTo(service.standardHtml(W));
    }

    @Test
    @DisplayName("Gleichzeitiges Speichern: der Zweite bekommt 409 statt eines Serverfehlers")
    void speichere_gleichzeitig_409() {
        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.of(fassung(2, "<p>alt</p>")));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_rechtstext_fassung_art_fassung"));

        assertThatThrownBy(() -> service.speichere(W, "<p>Max Mustermann</p>"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("neu laden");
                });
    }

    @Test
    @DisplayName("Alle Arten mit ihrem gültigen Text")
    void alle() {
        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.empty());

        assertThat(service.alle()).singleElement().satisfies(t -> {
            assertThat(t.art()).isEqualTo(W);
            assertThat(t.standardtext()).isTrue();
        });
    }

    @Test
    @DisplayName("Leerer Text, nur Markup oder SQL/XSS-Reste ohne Text: 400")
    void speichere_leer() {
        for (String leer : new String[] { null, "", "   ", "<p></p><p> </p>", "<script>alert(1)</script>" }) {
            assertThatThrownBy(() -> service.speichere(W, leer))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getReason()).contains("leer");
                    });
        }
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("SQL-Zeichen bleiben reiner Text")
    void speichere_sqlBleibtText() {
        when(repository.findFirstByArtOrderByFassungDesc(W)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.speichere(W, "'; DROP TABLE x; --").html()).isEqualTo("'; DROP TABLE x; --");
    }

    @Test
    @DisplayName("Mehr als 50.000 Zeichen: 400")
    void speichere_zuLang() {
        String lang = "<p>" + "x".repeat(RechtstextService.MAX_LAENGE) + "</p>";

        assertThatThrownBy(() -> service.speichere(W, lang))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("zu lang");
                });
    }

    @Test
    @DisplayName("Fassungen: Liste und einzelne Fassung, unbekannt 404")
    void fassungen() {
        when(repository.findByArtOrderByFassungDesc(W)).thenReturn(List.of(fassung(2, "<p>b</p>"), fassung(1, "<p>a</p>")));
        when(repository.findByArtAndFassung(W, 1)).thenReturn(Optional.of(fassung(1, "<p>a</p>")));
        when(repository.findByArtAndFassung(W, 9)).thenReturn(Optional.empty());

        assertThat(service.fassungen(W)).extracting(RechtstextFassung::getFassung).containsExactly(2, 1);
        assertThat(service.fassung(W, 1).getInhaltHtml()).isEqualTo("<p>a</p>");
        assertThatThrownBy(() -> service.fassung(W, 9))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Art aus dem Pfad: Groß-/Kleinschreibung egal, Unbekanntes 404")
    void artAus() {
        assertThat(RechtstextService.artAus("widerrufsbelehrung")).isEqualTo(W);
        assertThat(RechtstextService.artAus(" WIDERRUFSBELEHRUNG ")).isEqualTo(W);
        for (String falsch : new String[] { null, "", "agb", "'; DROP TABLE x; --", "<script>" }) {
            assertThatThrownBy(() -> RechtstextService.artAus(falsch))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}
