package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.WerkstoffService.Eingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WerkstoffServiceTest {

    private final WerkstoffRepository repo = mock(WerkstoffRepository.class);
    private WerkstoffService service;

    private static Werkstoff werkstoff(long id, String name, String anzeige) {
        Werkstoff w = new Werkstoff();
        w.setId(id);
        w.setName(name);
        w.setAnzeigename(anzeige);
        return w;
    }

    private static Eingabe eingabe(String name) {
        return new Eingabe(name, null, null, null, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        service = new WerkstoffService(repo);
        when(repo.saveAndFlush(any(Werkstoff.class))).thenAnswer(a -> a.getArgument(0));
    }

    @Test
    void listeIstNachAnzeigenameSortiertUndZaehltDieArtikel() {
        when(repo.findAll()).thenReturn(List.of(werkstoff(1, "S235JR", "Baustahl"), werkstoff(2, "1.4301", null), werkstoff(3, "EN AW-6060", "Aluminium")));
        when(repo.zaehleArtikelJeWerkstoff()).thenReturn(List.<Object[]>of(new Object[] { 1L, 12L }, new Object[] { 3L, 1L }));

        var zeilen = service.alle();

        assertThat(zeilen).extracting(WerkstoffService.Zeile::name).containsExactly("1.4301", "EN AW-6060", "S235JR");
        assertThat(zeilen).extracting(WerkstoffService.Zeile::artikelAnzahl).containsExactly(0L, 1L, 12L);
    }

    @Test
    void anlegenSpeichertAlleAngabenGetrimmtUndLeereAlsNull() {
        var z = service.anlegen(new Eingabe("  S355J2  ", " Baustahl hochfest ", "", new BigDecimal("7.85"), " EN 10025 ", true, null, "  geeignet  "));

        assertThat(z.name()).isEqualTo("S355J2");
        assertThat(z.anzeigename()).isEqualTo("Baustahl hochfest");
        assertThat(z.werkstattname()).isNull();
        assertThat(z.dichte()).isEqualByComparingTo("7.850");
        assertThat(z.werkstoffnorm()).isEqualTo("EN 10025");
        assertThat(z.verzinkungsgeeignet()).isTrue();
        assertThat(z.pulverbeschichtungsgeeignet()).isFalse();
        assertThat(z.beschichtungshinweis()).isEqualTo("geeignet");
        assertThat(z.artikelAnzahl()).isZero();
    }

    @Test
    void ohneNameDoppelterNameOderUnsinnigeDichteGibtKlartext() {
        assertThatThrownBy(() -> service.anlegen(eingabe("  "))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Namen");
        assertThatThrownBy(() -> service.anlegen(null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("fehlen");
        when(repo.findAll()).thenReturn(List.of(werkstoff(1, "S235JR", null)));
        assertThatThrownBy(() -> service.anlegen(eingabe("s235jr"))).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT)).hasMessageContaining("S235JR").hasMessageContaining("schon");
        assertThatThrownBy(() -> service.anlegen(new Eingabe("X", null, null, new BigDecimal("78.5"), null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Dichte");
        assertThatThrownBy(() -> service.anlegen(eingabe("x".repeat(256)))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("zu lang");
        assertThatThrownBy(() -> service.anlegen(new Eingabe("X", null, "w".repeat(256), null, null, null, null, null))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Werkstattname");
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void aendernDarfDenEigenenNamenBehaltenUndMeldetFremdeDoppelte() {
        Werkstoff vorhanden = werkstoff(1, "S235JR", "Baustahl");
        when(repo.findById(1L)).thenReturn(Optional.of(vorhanden));
        when(repo.findAll()).thenReturn(List.of(vorhanden));
        when(repo.zaehleArtikel(1L)).thenReturn(4L);

        var z = service.aendern(1L, new Eingabe("S235JR", "Baustahl neu", null, null, null, null, null, null));

        assertThat(z.anzeigename()).isEqualTo("Baustahl neu");
        assertThat(z.artikelAnzahl()).isEqualTo(4L);

        when(repo.findAll()).thenReturn(List.of(vorhanden, werkstoff(2, "1.4301", null)));
        assertThatThrownBy(() -> service.aendern(1L, eingabe("1.4301"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("schon");
        assertThatThrownBy(() -> service.aendern(99L, eingabe("Y"))).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void einBenutzterWerkstoffLaesstSichNichtLoeschenEinFreierSchon() {
        Werkstoff w = werkstoff(1, "S235JR", null);
        when(repo.findById(1L)).thenReturn(Optional.of(w));
        when(repo.zaehleArtikel(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.loeschen(1L)).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("3 Artikeln");
        verify(repo, never()).delete(any());

        when(repo.zaehleArtikel(1L)).thenReturn(0L);
        service.loeschen(1L);
        verify(repo).delete(w);
    }

    @Test
    void dichteGrenzenGeltenZwischen0Komma1Und25AuchLeerIstErlaubt() {
        assertThat(service.anlegen(new Eingabe("A", null, null, new BigDecimal("0.1"), null, null, null, null)).dichte()).isEqualByComparingTo("0.1");
        assertThat(service.anlegen(new Eingabe("B", null, null, new BigDecimal("25"), null, null, null, null)).dichte()).isEqualByComparingTo("25");
        assertThat(service.anlegen(new Eingabe("C", null, null, null, null, null, null, null)).dichte()).isNull();

        for (String unsinn : new String[] { "0", "0.09", "25.01", "-7.85" }) {
            assertThatThrownBy(() -> service.anlegen(new Eingabe("D", null, null, new BigDecimal(unsinn), null, null, null, null)))
                    .as("Dichte %s", unsinn)
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getReason()).contains("kg/dm³");
                    });
        }
    }

    @Test
    void sonderzeichenBleibenText() {
        var z = service.anlegen(new Eingabe("<script>alert(1)</script>", "'; DROP TABLE werkstoff; --", null, null, null, null, null, null));

        assertThat(z.name()).isEqualTo("<script>alert(1)</script>");
        assertThat(z.anzeigename()).isEqualTo("'; DROP TABLE werkstoff; --");
    }

    @Test
    void ueberlangeAngabenWerdenMitKlartextAbgelehnt() {
        assertThatThrownBy(() -> service.anlegen(new Eingabe("X", "a".repeat(256), null, null, null, null, null, null))).hasMessageContaining("Anzeigename");
        assertThatThrownBy(() -> service.anlegen(new Eingabe("X", null, null, null, "n".repeat(256), null, null, null))).hasMessageContaining("Norm");
        assertThatThrownBy(() -> service.anlegen(new Eingabe("X", null, null, null, null, null, null, "h".repeat(256)))).hasMessageContaining("Hinweis");
        assertThatThrownBy(() -> service.anlegen(eingabe("z".repeat(10_001)))).hasMessageContaining("zu lang");
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void ungueltigeIdsGeben404() {
        for (long id : new long[] { 0L, -1L, Long.MAX_VALUE }) {
            when(repo.findById(id)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.aendern(id, eingabe("Y"))).isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
            assertThatThrownBy(() -> service.loeschen(id)).isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        verify(repo, never()).delete(any());
    }

    @Test
    void einBenutzterWerkstoffMitNurEinemArtikelStehtImEinzahl() {
        when(repo.findById(1L)).thenReturn(Optional.of(werkstoff(1, "S235JR", null)));
        when(repo.zaehleArtikel(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.loeschen(1L)).hasMessageContaining("1 Artikel benutzt");
    }

    @Test
    void ohneEignungsangabeGiltNein() {
        var z = service.anlegen(eingabe("Q"));

        assertThat(z.verzinkungsgeeignet()).isFalse();
        assertThat(z.pulverbeschichtungsgeeignet()).isFalse();
    }

    @Test
    void angabenBisZurLaengeDerDatenbankspalteSindErlaubt() {
        var z = service.anlegen(new Eingabe("n".repeat(255), "a".repeat(255), "w".repeat(255), null, "e".repeat(255), null, null, "h".repeat(255)));

        assertThat(z.name()).hasSize(255);
        assertThat(z.beschichtungshinweis()).hasSize(255);
    }

    @Test
    void einGleichzeitigAngelegterNameKommtAlsKlartextMit409NichtAlsDatenbankfehler() {
        when(repo.findAll()).thenReturn(List.of());
        when(repo.saveAndFlush(any(Werkstoff.class))).thenThrow(new DataIntegrityViolationException("werkstoff_name_key"));

        assertThatThrownBy(() -> service.anlegen(eingabe("S235JR")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("Einen Werkstoff mit dem Namen „S235JR“ gibt es schon.");
                });
    }

    @Test
    void schonVorhandeneNamenInUnterschiedlicherSchreibweiseFuehrenNichtZumServerfehler() {
        // Alte Daten: zwei Werkstoffe, die sich nur in der Schreibweise unterscheiden. Eine Einzelabfrage mit zwei Treffern würde abbrechen.
        when(repo.findAll()).thenReturn(List.of(werkstoff(1, "S235JR", null), werkstoff(2, "s235jr", null)));

        assertThatThrownBy(() -> service.anlegen(eingabe("S235jr")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
