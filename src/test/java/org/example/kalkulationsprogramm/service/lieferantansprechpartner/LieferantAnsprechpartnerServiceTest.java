package org.example.kalkulationsprogramm.service.lieferantansprechpartner;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.LieferantAnsprechpartner;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerRequestDto;
import org.example.kalkulationsprogramm.repository.LieferantAnsprechpartnerRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.telefon.RufnummernZuordnungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LieferantAnsprechpartnerServiceTest {

    @Mock LieferantAnsprechpartnerRepository repository;
    @Mock LieferantenRepository lieferantenRepository;
    @Mock RufnummernZuordnungService zuordnung;

    private LieferantAnsprechpartnerService service;
    private Lieferanten lieferant;

    @BeforeEach
    void setUp() {
        service = new LieferantAnsprechpartnerService(repository, lieferantenRepository, zuordnung);
        lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Muster Stahl KG");
    }

    private static LieferantAnsprechpartnerRequestDto anfrage() {
        LieferantAnsprechpartnerRequestDto r = new LieferantAnsprechpartnerRequestDto();
        r.setAnrede("HERR");
        r.setVorname("Max");
        r.setNachname("Mustermann");
        r.setAufgabe("Außendienst");
        r.setMobiltelefon("0171 1234567");
        r.setEmail("max.mustermann@example.com");
        return r;
    }

    private LieferantAnsprechpartner person(long id, Lieferanten l) {
        LieferantAnsprechpartner a = new LieferantAnsprechpartner();
        a.setId(id);
        a.setLieferant(l);
        a.setNachname("Musterfrau");
        a.setVorname("Erika");
        return a;
    }

    private static void erwarteStatus(Runnable aufruf, HttpStatus status, String meldung) {
        assertThatThrownBy(aufruf::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).isEqualTo(meldung);
        });
    }

    @Test
    @DisplayName("Liste: höchstens 200, sortiert aus der Abfrage, als DTO")
    void liste() {
        when(lieferantenRepository.existsById(7L)).thenReturn(true);
        ArgumentCaptor<Pageable> seite = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findeFuerLieferant(org.mockito.ArgumentMatchers.eq(7L), seite.capture()))
                .thenReturn(List.of(person(3L, lieferant)));

        List<LieferantAnsprechpartnerDto> liste = service.liste(7L);

        assertThat(liste).extracting(LieferantAnsprechpartnerDto::getId).containsExactly(3L);
        assertThat(liste.getFirst().getNachname()).isEqualTo("Musterfrau");
        assertThat(seite.getValue().getPageSize()).isEqualTo(LieferantAnsprechpartnerService.MAX_JE_LIEFERANT);
        assertThat(seite.getValue().getPageNumber()).isZero();
    }

    @Test
    @DisplayName("Liste eines unbekannten Lieferanten → 404")
    void listeUnbekannt() {
        when(lieferantenRepository.existsById(99L)).thenReturn(false);
        erwarteStatus(() -> service.liste(99L), HttpStatus.NOT_FOUND, "Lieferant nicht gefunden.");
    }

    @Test
    @DisplayName("Anlegen speichert die bereinigten Felder und verwirft das Rufnummern-Verzeichnis")
    void anlegen() {
        when(lieferantenRepository.findById(7L)).thenReturn(Optional.of(lieferant));
        when(repository.countByLieferantId(7L)).thenReturn(0L);
        when(repository.save(any())).thenAnswer(a -> {
            LieferantAnsprechpartner p = a.getArgument(0);
            p.setId(11L);
            return p;
        });

        LieferantAnsprechpartnerDto dto = service.anlegen(7L, anfrage());

        assertThat(dto.getId()).isEqualTo(11L);
        assertThat(dto.getAnrede()).isEqualTo("HERR");
        assertThat(dto.getAufgabe()).isEqualTo("Außendienst");
        ArgumentCaptor<LieferantAnsprechpartner> gespeichert = ArgumentCaptor.forClass(LieferantAnsprechpartner.class);
        verify(repository).save(gespeichert.capture());
        assertThat(gespeichert.getValue().getLieferant()).isSameAs(lieferant);
        assertThat(gespeichert.getValue().getAnrede()).isEqualTo(Anrede.HERR);
        verify(zuordnung).verwerfeCache();
    }

    @Test
    @DisplayName("Der 201. Ansprechpartner wird abgelehnt")
    void obergrenze() {
        when(lieferantenRepository.findById(7L)).thenReturn(Optional.of(lieferant));
        when(repository.countByLieferantId(7L)).thenReturn(200L);

        erwarteStatus(() -> service.anlegen(7L, anfrage()), HttpStatus.BAD_REQUEST,
                "Höchstens 200 Ansprechpartner je Lieferant.");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Anlegen bei unbekanntem Lieferanten → 404, Eingabe ungültig → 400 vor dem Speichern")
    void anlegenFehler() {
        when(lieferantenRepository.findById(99L)).thenReturn(Optional.empty());
        erwarteStatus(() -> service.anlegen(99L, anfrage()), HttpStatus.NOT_FOUND, "Lieferant nicht gefunden.");

        LieferantAnsprechpartnerRequestDto ohneNachname = anfrage();
        ohneNachname.setNachname(" ");
        erwarteStatus(() -> service.anlegen(7L, ohneNachname), HttpStatus.BAD_REQUEST,
                "Bitte einen Nachnamen eintragen.");
        verify(repository, never()).save(any());
        verifyNoInteractions(zuordnung);
    }

    @Test
    @DisplayName("Ändern überschreibt alle Felder (leere werden null)")
    void aendern() {
        LieferantAnsprechpartner vorhanden = person(3L, lieferant);
        vorhanden.setTelefon("0931 1");
        when(repository.findByIdAndLieferantId(3L, 7L)).thenReturn(Optional.of(vorhanden));

        LieferantAnsprechpartnerDto dto = service.aendern(7L, 3L, anfrage());

        assertThat(dto.getNachname()).isEqualTo("Mustermann");
        assertThat(vorhanden.getTelefon()).isNull();
        assertThat(vorhanden.getMobiltelefon()).isEqualTo("0171 1234567");
        verify(zuordnung).verwerfeCache();
    }

    @Test
    @DisplayName("Ändern/Löschen eines Ansprechpartners eines anderen Lieferanten → 404")
    void fremderAnsprechpartner() {
        when(repository.findByIdAndLieferantId(3L, 8L)).thenReturn(Optional.empty());
        when(lieferantenRepository.existsById(8L)).thenReturn(true);

        erwarteStatus(() -> service.aendern(8L, 3L, anfrage()), HttpStatus.NOT_FOUND, "Ansprechpartner nicht gefunden.");
        erwarteStatus(() -> service.loeschen(8L, 3L), HttpStatus.NOT_FOUND, "Ansprechpartner nicht gefunden.");
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("Ändern/Löschen bei unbekanntem Lieferanten → „Lieferant nicht gefunden.“")
    void unbekannterLieferant() {
        when(repository.findByIdAndLieferantId(3L, 99L)).thenReturn(Optional.empty());
        when(lieferantenRepository.existsById(99L)).thenReturn(false);

        erwarteStatus(() -> service.aendern(99L, 3L, anfrage()), HttpStatus.NOT_FOUND, "Lieferant nicht gefunden.");
        erwarteStatus(() -> service.loeschen(99L, 3L), HttpStatus.NOT_FOUND, "Lieferant nicht gefunden.");
    }

    @Test
    @DisplayName("Löschen entfernt den Ansprechpartner und verwirft das Rufnummern-Verzeichnis")
    void loeschen() {
        LieferantAnsprechpartner vorhanden = person(3L, lieferant);
        when(repository.findByIdAndLieferantId(3L, 7L)).thenReturn(Optional.of(vorhanden));

        service.loeschen(7L, 3L);

        verify(repository).delete(vorhanden);
        verify(zuordnung).verwerfeCache();
    }
}
