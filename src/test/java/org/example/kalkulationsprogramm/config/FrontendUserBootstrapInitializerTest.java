package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Der Bootstrap-Admin wird NUR noch angelegt, wenn Benutzername UND Passwort
 * explizit konfiguriert sind (Produktiv-Server via application-local.properties).
 * Ohne diese Konfiguration (Release-.exe) bleibt die Einrichtungsphase offen:
 * Der erste Nutzer registriert sich selbst im Browser und wird Admin.
 */
@ExtendWith(MockitoExtension.class)
class FrontendUserBootstrapInitializerTest {

    @Mock
    private FrontendUserProfileRepository repository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private FrontendUserBootstrapInitializer initializer;

    @Test
    void ohneKonfigurationWirdKeinAdminAngelegt() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "");
        ReflectionTestUtils.setField(initializer, "adminPassword", "");

        initializer.run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void nurUsernameOhnePasswortLegtKeinenAdminAn() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "max.mustermann");
        ReflectionTestUtils.setField(initializer, "adminPassword", "");

        initializer.run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void mitVollstaendigerKonfigurationWirdAdminAngelegt() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "max.mustermann");
        ReflectionTestUtils.setField(initializer, "adminPassword", "sicheres-passwort");
        when(repository.countByUsernameIsNotNull()).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        initializer.run(null);

        ArgumentCaptor<FrontendUserProfile> captor = ArgumentCaptor.forClass(FrontendUserProfile.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("max.mustermann");
    }

    @Test
    void mitBestehendenNutzernPassiertNichts() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "max.mustermann");
        ReflectionTestUtils.setField(initializer, "adminPassword", "sicheres-passwort");
        lenient().when(repository.countByUsernameIsNotNull()).thenReturn(1L);

        initializer.run(null);

        verify(repository, never()).save(any());
    }
}
