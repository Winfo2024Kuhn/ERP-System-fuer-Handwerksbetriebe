package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.LieferantReklamation;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.CreateReklamationRequest;
import org.example.kalkulationsprogramm.repository.LieferantBildRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.AngemeldeterMitarbeiterService;
import org.example.kalkulationsprogramm.service.LieferantDateiablage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** „Erstellt von“ einer Reklamation: Handy-App über das Token, PC über die Anmeldung (Issue #266). */
@ExtendWith(MockitoExtension.class)
class LieferantReklamationErstellerTest {

    @Mock private LieferantReklamationRepository reklamationRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private LieferantDokumentRepository dokumentRepository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;
    @Mock private LieferantBildRepository bildRepository;
    @Mock private LieferantDateiablage dateiablage;
    @Mock private AngemeldeterMitarbeiterService angemeldeterMitarbeiter;
    @InjectMocks private LieferantReklamationController controller;

    private final Authentication anmeldung = new UsernamePasswordAuthenticationToken("max", null);

    private static Mitarbeiter mitarbeiter(String vorname) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vorname);
        m.setNachname("Mustermann");
        return m;
    }

    @BeforeEach
    void lieferant() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(3L);
        lieferant.setLieferantenname("Muster Stahlhandel GmbH");
        given(lieferantenRepository.findById(3L)).willReturn(Optional.of(lieferant));
        given(reklamationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private Mitarbeiter gespeicherterErsteller() {
        ArgumentCaptor<LieferantReklamation> gespeichert = ArgumentCaptor.forClass(LieferantReklamation.class);
        verify(reklamationRepository).save(gespeichert.capture());
        return gespeichert.getValue().getErstelltVon();
    }

    private static CreateReklamationRequest anfrage() {
        CreateReklamationRequest request = new CreateReklamationRequest();
        request.setBeschreibung("Träger verbogen");
        return request;
    }

    @Test
    void amPcKommtDerErstellerAusDerAnmeldung() {
        Mitarbeiter max = mitarbeiter("Max");
        given(angemeldeterMitarbeiter.handelnderMitarbeiter(anmeldung)).willReturn(Optional.of(max));

        controller.create(3L, anfrage(), null, anmeldung);

        assertThat(gespeicherterErsteller()).isSameAs(max);
        verify(mitarbeiterRepository, never()).findByLoginTokenAndAktivTrue(any());
    }

    @Test
    void handyAppNutztWeiterDasToken() {
        Mitarbeiter erika = mitarbeiter("Erika");
        given(mitarbeiterRepository.findByLoginTokenAndAktivTrue("token-123")).willReturn(Optional.of(erika));

        controller.create(3L, anfrage(), "token-123", anmeldung);

        assertThat(gespeicherterErsteller()).isSameAs(erika);
        verify(angemeldeterMitarbeiter, never()).handelnderMitarbeiter(any());
    }

    @Test
    void benutzerOhneMitarbeiterBleibtLeer() {
        given(angemeldeterMitarbeiter.handelnderMitarbeiter(anmeldung)).willReturn(Optional.empty());

        controller.create(3L, anfrage(), "  ", anmeldung);

        assertThat(gespeicherterErsteller()).isNull();
    }
}
