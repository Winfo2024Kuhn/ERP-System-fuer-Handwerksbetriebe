package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
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
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private FrontendUserProfileService service;

    private void stubSaveUndEncode() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(repository.save(any(FrontendUserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
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
}
