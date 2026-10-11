package org.example.kalkulationsprogramm.service.benachrichtigung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.BenachrichtigungErledigt;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.BenachrichtigungErledigtRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BenachrichtigungErledigtServiceTest {

    @Mock private BenachrichtigungErledigtRepository repository;
    @Mock private FrontendUserProfileRepository benutzerRepository;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @InjectMocks private BenachrichtigungErledigtService service;

    private static BenachrichtigungErledigt eintrag(String schluessel, LocalDateTime am) {
        BenachrichtigungErledigt e = new BenachrichtigungErledigt();
        e.setSchluessel(schluessel);
        e.setErledigtAm(am);
        return e;
    }

    @Test
    @DisplayName("Ereignisse bleiben weg, Dauer-Hinweise erinnern nach einer Woche erneut")
    void dauerHinweiseKommenNachSiebenTagenWieder() {
        LocalDateTime jetzt = LocalDateTime.now();
        given(repository.findByBenutzerId(7L)).willReturn(List.of(
                eintrag("LIEFERSCHEIN:1", jetzt.minusDays(20)),
                eintrag("EINGANG_FAELLIG:2", jetzt.minusDays(3)),
                eintrag("EINGANG_FAELLIG:3", jetzt.minusDays(8)),
                eintrag("MONATSABSCHLUSS:2026-09", jetzt.minusDays(10))));

        assertThat(service.erledigteSchluessel(7L)).containsExactlyInAnyOrder("LIEFERSCHEIN:1", "EINGANG_FAELLIG:2");
    }

    @Test
    void ohneBenutzerNichtsErledigt() {
        assertThat(service.erledigteSchluessel(null)).isEmpty();
        service.merken(null, List.of("TERMIN:1"));
        service.merken(7L, List.of());
        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Merken legt neue Einträge an und setzt bei vorhandenen den Zeitpunkt neu")
    void merkenAktualisiertVorhandene() {
        LocalDateTime alt = LocalDateTime.now().minusDays(9);
        BenachrichtigungErledigt vorhanden = eintrag("URLAUBSANTRAG:4", alt);
        given(repository.findByBenutzerIdAndSchluesselIn(eq(7L), anyCollection())).willReturn(List.of(vorhanden));
        FrontendUserProfile max = new FrontendUserProfile();
        given(benutzerRepository.getReferenceById(7L)).willReturn(max);

        service.merken(7L, List.of("URLAUBSANTRAG:4", "TERMIN:5", "TERMIN:5"));

        ArgumentCaptor<List<BenachrichtigungErledigt>> gespeichert = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAllAndFlush(gespeichert.capture());
        List<BenachrichtigungErledigt> liste = new ArrayList<>(gespeichert.getValue());
        assertThat(liste).extracting(BenachrichtigungErledigt::getSchluessel)
                .containsExactly("URLAUBSANTRAG:4", "TERMIN:5");
        assertThat(liste).allSatisfy(e -> assertThat(e.getErledigtAm()).isAfter(alt));
        assertThat(liste.get(1).getBenutzer()).isSameAs(max);
    }

    @Test
    @DisplayName("Legt eine zweite Anfrage denselben Eintrag gleichzeitig an, klappt es im zweiten Versuch")
    void gleichzeitigAngelegtZweiterVersuch() {
        given(benutzerRepository.getReferenceById(7L)).willReturn(new FrontendUserProfile());
        given(repository.findByBenutzerIdAndSchluesselIn(eq(7L), anyCollection()))
                .willReturn(List.of())
                .willReturn(List.of(eintrag("TERMIN:5", LocalDateTime.now().minusMinutes(1))));
        given(repository.saveAllAndFlush(any()))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("uk_benachrichtigung_erledigt"))
                .willReturn(List.of());

        service.merken(7L, List.of("TERMIN:5"));

        verify(repository, org.mockito.Mockito.times(2)).saveAllAndFlush(any());
    }

    @Test
    void aufraeumenLoeschtNachAufbewahrungsfrist() {
        given(repository.deleteAelterAls(any())).willReturn(3);
        service.aufraeumen();
        ArgumentCaptor<LocalDateTime> grenze = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteAelterAls(grenze.capture());
        assertThat(grenze.getValue()).isBefore(LocalDateTime.now().minusDays(44));
        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    void dauerHinweisErkennung() {
        assertThat(BenachrichtigungErledigtService.istDauerHinweis("VERPASSTER_ANRUF:7")).isTrue();
        assertThat(BenachrichtigungErledigtService.istDauerHinweis("TERMIN:7")).isFalse();
        assertThat(BenachrichtigungErledigtService.istDauerHinweis("REKLAMATION")).isFalse();
        assertThat(BenachrichtigungErledigtService.istDauerHinweis(null)).isFalse();
    }
}
