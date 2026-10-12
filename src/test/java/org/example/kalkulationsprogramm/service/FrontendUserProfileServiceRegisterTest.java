package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailGelesenRepository;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Der erste registrierte Nutzer (Einrichtungsphase der Release-.exe) wird
 * automatisch Admin — es gibt keinen vorab angelegten Bootstrap-Admin mehr.
 */
@ExtendWith(MockitoExtension.class)
class FrontendUserProfileServiceRegisterTest {

    @Mock
    private FrontendUserProfileRepository repository;
    @Mock
    private EmailSignatureRepository emailSignatureRepository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;
    @Mock
    private EmailAbsenderRepository emailAbsenderRepository;
    @Mock
    private EmailGelesenRepository emailGelesenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private FrontendUserProfileService service;

    private void stubSaveUndEncode() {
        stubEncodeUndFreienNamen();
        when(repository.save(any(FrontendUserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubEncodeUndFreienNamen() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(repository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
    }

    /** Speichern vergibt die ID wie die Datenbank. */
    private void stubSpeichernMitId(long id) {
        when(repository.save(any(FrontendUserProfile.class))).thenAnswer(inv -> {
            FrontendUserProfile p = inv.getArgument(0);
            p.setId(id);
            return p;
        });
    }

    @Test
    void ersterRegistrierterNutzerWirdAdmin() {
        stubSaveUndEncode();
        when(repository.countByUsernameIsNotNull()).thenReturn(0L);

        FrontendUserProfile created = service.register("Max Mustermann", "max.mustermann", "sicheres-passwort");

        assertThat(created.hasRole(FrontendUserRole.ADMIN)).isTrue();
        assertThat(created.hasRole(FrontendUserRole.USER)).isTrue();
    }

    @Test
    void weitereNutzerBleibenNormaleUser() {
        stubSaveUndEncode();
        when(repository.countByUsernameIsNotNull()).thenReturn(1L);

        FrontendUserProfile created = service.register("Erika Mustermann", "erika.mustermann", "sicheres-passwort");

        assertThat(created.hasRole(FrontendUserRole.ADMIN)).isFalse();
        assertThat(created.hasRole(FrontendUserRole.USER)).isTrue();
    }

    @Test
    void registrierterNutzerStartetMitAllenBisherigenMailsGelesen() {
        stubEncodeUndFreienNamen();
        stubSpeichernMitId(42L);
        when(repository.countByUsernameIsNotNull()).thenReturn(1L);

        service.register("Erika Mustermann", "erika.mustermann", "sicheres-passwort");

        verify(emailGelesenRepository).alleMailsAlsGelesen(eq(42L), eq(EmailDirection.IN), any());
    }

    @Test
    void neuerBenutzerVomAdminStartetMitAllenBisherigenMailsGelesen() {
        stubEncodeUndFreienNamen();
        stubSpeichernMitId(43L);
        FrontendUserProfile neu = new FrontendUserProfile();
        neu.setDisplayName("Max Mustermann");

        service.saveOrUpdate(neu, null, "max.mustermann", "sicheres-passwort", null, true, null);

        verify(emailGelesenRepository).alleMailsAlsGelesen(eq(43L), eq(EmailDirection.IN), any());
    }

    @Test
    void geaenderterBenutzerBehaeltSeinenLesestand() {
        FrontendUserProfile vorhanden = new FrontendUserProfile();
        vorhanden.setId(44L);
        vorhanden.setDisplayName("Max Mustermann");
        vorhanden.setShortCode("MM");
        when(repository.findById(44L)).thenReturn(Optional.of(vorhanden));
        when(repository.save(any(FrontendUserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        FrontendUserProfile aenderung = new FrontendUserProfile();
        aenderung.setId(44L);
        aenderung.setDisplayName("Max Mustermann");
        aenderung.setShortCode("MM");

        service.saveOrUpdate(aenderung, null, null, null, null, true, null);

        verify(emailGelesenRepository, never()).alleMailsAlsGelesen(any(), any(), any());
    }
}
