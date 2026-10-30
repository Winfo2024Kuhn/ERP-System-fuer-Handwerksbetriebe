package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.example.kalkulationsprogramm.dto.KostenstelleDto;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Nur Dummy-Daten. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KostenstelleServiceTest {

    @Mock
    private KostenstelleRepository repository;

    @InjectMocks
    private KostenstelleService service;

    @BeforeEach
    void speichernGibtZurueck() {
        when(repository.save(any(Kostenstelle.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    }

    private static Kostenstelle kostenstelle(Long id, String name, boolean aktiv) {
        Kostenstelle k = new Kostenstelle();
        k.setId(id);
        k.setBezeichnung(name);
        k.setTyp(KostenstellenTyp.GEMEINKOSTEN);
        k.setAktiv(aktiv);
        k.setSortierung(4);
        return k;
    }

    private static KostenstelleDto eingabe(Long id, String name) {
        KostenstelleDto dto = new KostenstelleDto();
        dto.setId(id);
        dto.setBezeichnung(name);
        dto.setTyp(KostenstellenTyp.LAGER);
        dto.setBeschreibung("Musterbeschreibung");
        dto.setIstInvestition(true);
        return dto;
    }

    private static HttpStatus status(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    @DisplayName("Neue Kostenstelle ist aktiv, Name wird getrimmt")
    void neueKostenstelleIstAktiv() {
        when(repository.findAllByBezeichnungIgnoreCase("Musterlager")).thenReturn(List.of());

        KostenstelleDto ergebnis = service.speichern(eingabe(null, "  Musterlager "));

        assertThat(ergebnis.getBezeichnung()).isEqualTo("Musterlager");
        assertThat(ergebnis.isAktiv()).isTrue();
        assertThat(ergebnis.getTyp()).isEqualTo(KostenstellenTyp.LAGER);
        assertThat(ergebnis.isIstInvestition()).isTrue();
        assertThat(ergebnis.getSortierung()).isZero();
    }

    @Test
    @DisplayName("Bearbeiten schaltet die Kostenstelle nicht ab, auch wenn 'aktiv' fehlt; Sortierung bleibt ohne Angabe")
    void bearbeitenLaesstAktivUndSortierung() {
        Kostenstelle lager = kostenstelle(1L, "Musterlager", true);
        when(repository.findById(1L)).thenReturn(Optional.of(lager));
        when(repository.findAllByBezeichnungIgnoreCase("Musterlager")).thenReturn(List.of(lager));

        KostenstelleDto dto = eingabe(1L, "Musterlager");
        dto.setAktiv(false);
        KostenstelleDto ergebnis = service.speichern(dto);

        assertThat(ergebnis.isAktiv()).isTrue();
        assertThat(ergebnis.getSortierung()).isEqualTo(4);
        assertThat(ergebnis.getBeschreibung()).isEqualTo("Musterbeschreibung");
    }

    @Test
    @DisplayName("Anlegen mit dem Namen einer gelöschten Kostenstelle holt diese zurück")
    void anlegenHoltGeloeschteZurueck() {
        Kostenstelle alt = kostenstelle(7L, "Musterlager", false);
        when(repository.findAllByBezeichnungIgnoreCase("musterlager")).thenReturn(List.of(alt));

        KostenstelleDto dto = eingabe(null, "musterlager");
        dto.setSortierung(9);
        KostenstelleDto ergebnis = service.speichern(dto);

        assertThat(ergebnis.getId()).isEqualTo(7L);
        assertThat(ergebnis.isAktiv()).isTrue();
        assertThat(ergebnis.getBezeichnung()).isEqualTo("musterlager");
        assertThat(ergebnis.getSortierung()).isEqualTo(9);
    }

    @Test
    @DisplayName("Name einer aktiven Kostenstelle gibt 409")
    void aktiverNameGibtKonflikt() {
        when(repository.findAllByBezeichnungIgnoreCase("Musterlager"))
                .thenReturn(List.of(kostenstelle(2L, "MUSTERLAGER", true)));

        assertThatThrownBy(() -> service.speichern(eingabe(null, "Musterlager")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("gibt es schon");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Umbenennen auf den Namen einer gelöschten Kostenstelle gibt 409 mit Hinweis")
    void umbenennenAufGeloeschtenNamen() {
        when(repository.findById(1L)).thenReturn(Optional.of(kostenstelle(1L, "Werkstatt", true)));
        when(repository.findAllByBezeichnungIgnoreCase("Musterlager"))
                .thenReturn(List.of(kostenstelle(7L, "Musterlager", false)));

        assertThatThrownBy(() -> service.speichern(eingabe(1L, "Musterlager")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Gelöschte Kostenstellen");
    }

    @Test
    @DisplayName("Bearbeiten einer unbekannten Kostenstelle gibt 404")
    void bearbeitenUnbekanntGibt404() {
        when(repository.findAllByBezeichnungIgnoreCase("Musterlager")).thenReturn(List.of());
        when(repository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.speichern(eingabe(Long.MAX_VALUE, "Musterlager")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Leerer Name, fehlender Typ und zu lange Eingaben geben 400")
    void ungueltigeEingabenGeben400() {
        assertThatThrownBy(() -> service.speichern(eingabe(null, "   ")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.speichern(eingabe(null, null)))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.speichern(eingabe(null, "x".repeat(10_001))))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));

        KostenstelleDto ohneTyp = eingabe(null, "Musterlager");
        ohneTyp.setTyp(null);
        assertThatThrownBy(() -> service.speichern(ohneTyp))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));

        KostenstelleDto langeBeschreibung = eingabe(null, "Musterlager");
        langeBeschreibung.setBeschreibung("x".repeat(501));
        assertThatThrownBy(() -> service.speichern(langeBeschreibung))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Sonderzeichen im Namen werden unverändert als Text gespeichert")
    void sonderzeichenBleibenText() {
        String name = "<script>alert(1)</script>'; DROP TABLE x; --";
        when(repository.findAllByBezeichnungIgnoreCase(name)).thenReturn(List.of());

        assertThat(service.speichern(eingabe(null, name)).getBezeichnung()).isEqualTo(name);
    }

    @Test
    @DisplayName("Löschen blendet aus, Wiederherstellen holt zurück")
    void loeschenUndWiederherstellen() {
        Kostenstelle k = kostenstelle(3L, "Musterlager", true);
        when(repository.findById(3L)).thenReturn(Optional.of(k));

        service.loeschen(3L);
        assertThat(k.isAktiv()).isFalse();

        assertThat(service.wiederherstellen(3L).isAktiv()).isTrue();
        assertThat(k.isAktiv()).isTrue();
    }

    @Test
    @DisplayName("Wiederherstellen gibt 409, wenn eine aktive Kostenstelle schon so heißt (Altdaten)")
    void wiederherstellenBeiNamensgleichheit() {
        Kostenstelle geloescht = kostenstelle(3L, "Musterlager", false);
        when(repository.findById(3L)).thenReturn(Optional.of(geloescht));
        when(repository.findAllByBezeichnungIgnoreCase("Musterlager"))
                .thenReturn(List.of(geloescht, kostenstelle(4L, "MUSTERLAGER", true)));

        assertThatThrownBy(() -> service.wiederherstellen(3L))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
        assertThat(geloescht.isAktiv()).isFalse();
    }

    @Test
    @DisplayName("Löschen einer unbekannten Kostenstelle tut nichts, Wiederherstellen gibt 404")
    void unbekannteIds() {
        when(repository.findById(0L)).thenReturn(Optional.empty());

        service.loeschen(0L);
        verify(repository, never()).save(any());
        assertThatThrownBy(() -> service.wiederherstellen(0L))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Gelöschte Kostenstellen werden als Liste geliefert")
    void geloeschteListe() {
        when(repository.findByAktivFalseOrderByBezeichnungAsc())
                .thenReturn(List.of(kostenstelle(6L, "Musterauto", false)));

        assertThat(service.findGeloeschte()).extracting(KostenstelleDto::getBezeichnung).containsExactly("Musterauto");
    }
}
