package org.example.kalkulationsprogramm.service.dokumentstand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WiederherstellungsZaehlerPruefungTest {

    @Mock AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @InjectMocks WiederherstellungsZaehlerPruefung pruefung;

    private void gespeichert(int zaehler) {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setId(7L);
        d.setWiederherstellungsZaehler(zaehler);
        when(dokumentRepository.findById(7L)).thenReturn(Optional.of(d));
    }

    @Test
    @DisplayName("Ohne Zähler (alter Editor): keine Prüfung, keine Abfrage")
    void ohneZaehler() {
        assertThatCode(() -> pruefung.pruefe(7L, null)).doesNotThrowAnyException();
        verify(dokumentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Gleicher Zähler: speichern erlaubt")
    void gleich() {
        gespeichert(2);
        assertThatCode(() -> pruefung.pruefe(7L, 2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Abweichender Zähler (Editor kennt das Zurücksetzen nicht): 409 mit Meldung")
    void abweichend() {
        gespeichert(1);
        assertThatThrownBy(() -> pruefung.pruefe(7L, 0)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getReason()).isEqualTo("Dieses Dokument wurde inzwischen auf einen älteren Stand "
                    + "zurückgesetzt. Ihre letzte Änderung wurde nicht gespeichert. Bitte das Dokument neu öffnen.");
        });
    }

    @Test
    @DisplayName("Unbekanntes Dokument: keine eigene Meldung, das Speichern meldet es selbst")
    void unbekannt() {
        when(dokumentRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatCode(() -> pruefung.pruefe(9L, 0)).doesNotThrowAnyException();
    }
}
