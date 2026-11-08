package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Erkennung, ob ein Absender ein Steuerberater ist: Haupt-E-Mail, weitere E-Mail, Domain.
 */
@ExtendWith(MockitoExtension.class)
class SteuerberaterAbsenderErkennungTest {

    @Mock
    private SteuerberaterKontaktRepository steuerberaterRepository;

    @InjectMocks
    private SteuerberaterAbsenderErkennung erkennung;

    private SteuerberaterKontakt kanzlei;

    @BeforeEach
    void setUp() {
        kanzlei = new SteuerberaterKontakt();
        kanzlei.setName("Steuerkanzlei Test");
        kanzlei.setEmail("kanzlei@steuerkanzlei-test.example");
        kanzlei.setAktiv(true);
        kanzlei.setAutoProcessEmails(true);
    }

    @Test
    void leererAbsenderIstKeinSteuerberater() {
        assertThat(erkennung.erkenne(null)).isEmpty();
        assertThat(erkennung.erkenne("  ")).isEmpty();
        verifyNoInteractions(steuerberaterRepository);
    }

    @Test
    void hauptEmailWirdExaktUndOhneGrossschreibungErkannt() {
        when(steuerberaterRepository.findByEmailIgnoreCase("kanzlei@steuerkanzlei-test.example"))
                .thenReturn(Optional.of(kanzlei));

        assertThat(erkennung.erkenne(" Kanzlei@Steuerkanzlei-Test.example ")).containsSame(kanzlei);
        verify(steuerberaterRepository, never()).findByAktivTrueAndAutoProcessEmailsTrue();
    }

    @Test
    void inaktiverSteuerberaterZaehltNichtAlsExakterTreffer() {
        kanzlei.setAktiv(false);
        when(steuerberaterRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(kanzlei));
        when(steuerberaterRepository.findeAktiveMitWeitererEmail(anyString())).thenReturn(List.of());
        when(steuerberaterRepository.findByAktivTrueAndAutoProcessEmailsTrue()).thenReturn(List.of());

        assertThat(erkennung.erkenne("kanzlei@steuerkanzlei-test.example")).isEmpty();
    }

    /**
     * E-Mails fremder Absender werden nicht als Steuerberater erkannt.
     */
    @Test
    void fremdeAbsenderWerdenIgnoriert() {
        when(steuerberaterRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(steuerberaterRepository.findeAktiveMitWeitererEmail(anyString())).thenReturn(List.of());
        when(steuerberaterRepository.findByAktivTrueAndAutoProcessEmailsTrue()).thenReturn(List.of(kanzlei));

        assertThat(erkennung.erkenne("max.mustermann@example.com")).isEmpty();
    }

    @Test
    void absenderOhneAtZeichenHatKeineDomain() {
        when(steuerberaterRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(steuerberaterRepository.findeAktiveMitWeitererEmail(anyString())).thenReturn(List.of());

        assertThat(erkennung.erkenne("kein-absender")).isEmpty();
        verify(steuerberaterRepository, never()).findByAktivTrueAndAutoProcessEmailsTrue();
    }

    /**
     * Domain-Match: Auch andere Absender derselben Kanzlei-Domain werden erkannt.
     */
    @Test
    void andereAbsenderDerSteuerberaterDomainWerdenErkannt() {
        SteuerberaterKontakt ohneEmail = new SteuerberaterKontakt();
        ohneEmail.setAktiv(true);
        when(steuerberaterRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(steuerberaterRepository.findeAktiveMitWeitererEmail(anyString())).thenReturn(List.of());
        when(steuerberaterRepository.findByAktivTrueAndAutoProcessEmailsTrue())
                .thenReturn(List.of(ohneEmail, kanzlei));

        assertThat(erkennung.erkenne("sekretariat@steuerkanzlei-test.example")).containsSame(kanzlei);
    }

    /**
     * Freemail-Domain: Hat der Steuerberater eine gmx.de-Adresse, ist nicht jeder
     * gmx.de-Absender die Kanzlei. Kein Domain-Match.
     */
    @Test
    void fremdeAbsenderDerselbenFreemailDomainWerdenIgnoriert() {
        SteuerberaterKontakt freemailKanzlei = freemailSteuerberater();
        when(steuerberaterRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(steuerberaterRepository.findeAktiveMitWeitererEmail(anyString())).thenReturn(List.of());
        // lenient: bei Freemail wird die Liste gar nicht abgefragt
        lenient().when(steuerberaterRepository.findByAktivTrueAndAutoProcessEmailsTrue())
                .thenReturn(List.of(freemailKanzlei));

        assertThat(erkennung.erkenne("Max.Mustermann@GMX.de")).isEmpty();
        verify(steuerberaterRepository, never()).findByAktivTrueAndAutoProcessEmailsTrue();
    }

    /**
     * Freemail-Domain: Die Haupt-E-Mail selbst wird weiterhin erkannt (exakter Treffer).
     */
    @Test
    void hauptEmailAufFreemailDomainWirdWeiterhinErkannt() {
        SteuerberaterKontakt freemailKanzlei = freemailSteuerberater();
        when(steuerberaterRepository.findByEmailIgnoreCase("steuerbuero.mustermann@gmx.de"))
                .thenReturn(Optional.of(freemailKanzlei));

        assertThat(erkennung.erkenne("steuerbuero.mustermann@gmx.de")).containsSame(freemailKanzlei);
        verify(steuerberaterRepository, never()).findByAktivTrueAndAutoProcessEmailsTrue();
    }

    /**
     * Ausdrücklich eingetragene weitere E-Mail einer Freemail-Kanzlei wird exakt erkannt –
     * auch ohne Haken „andere Adressen der Kanzlei erkennen“.
     */
    @Test
    void weitereEmailEinerFreemailKanzleiWirdErkannt() {
        SteuerberaterKontakt freemailKanzlei = freemailSteuerberater();
        freemailKanzlei.setAutoProcessEmails(false);
        freemailKanzlei.getWeitereEmails().add("lohn.mustermann@gmx.de");
        when(steuerberaterRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(steuerberaterRepository.findeAktiveMitWeitererEmail("lohn.mustermann@gmx.de"))
                .thenReturn(List.of(freemailKanzlei));

        assertThat(erkennung.erkenne("Lohn.Mustermann@gmx.de")).containsSame(freemailKanzlei);
        verify(steuerberaterRepository, never()).findByAktivTrueAndAutoProcessEmailsTrue();
    }

    private SteuerberaterKontakt freemailSteuerberater() {
        SteuerberaterKontakt freemail = new SteuerberaterKontakt();
        freemail.setName("Steuerbüro Mustermann");
        freemail.setEmail("steuerbuero.mustermann@gmx.de");
        freemail.setAktiv(true);
        freemail.setAutoProcessEmails(true);
        return freemail;
    }
}
