package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests für den gemeinsamen Datei-Ordner (HiCAD/Tenado/Excel) in den
 * System-Einstellungen: DB-Wert schlägt Property-Fallback, und die
 * Pflicht in der Ersteinrichtung gilt nur im Standalone-Modus (h2).
 */
class SystemSettingsServiceDateiOrdnerTest {

    private SystemSettingRepository repository;
    private Environment environment;
    private SystemSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(SystemSettingRepository.class);
        environment = mock(Environment.class);
        service = new SystemSettingsService(repository, environment);
        // Property-Fallbacks wie in application.properties
        ReflectionTestUtils.setField(service, "defaultDateiOrdnerPfad", "C:\\Test\\CADdrawings");
        ReflectionTestUtils.setField(service, "defaultDateiOrdnerNetworkUrl", "OVERRIDE_IN_LOCAL");
    }

    private void stubSetting(String key, String value) {
        when(repository.findById(key)).thenReturn(
                value == null ? Optional.empty() : Optional.of(new SystemSetting(key, value, null)));
    }

    private void stubProfilH2(boolean istH2) {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(istH2);
    }

    @Test
    void dateiOrdnerPfad_dbWertSchlaegtProperty() {
        stubSetting("datei.ordner-pfad", "Z:\\Zeichnungen");
        assertThat(service.getDateiOrdnerPfad()).isEqualTo("Z:\\Zeichnungen");
    }

    @Test
    void dateiOrdnerPfad_ohneDbWertKommtPropertyFallback() {
        stubSetting("datei.ordner-pfad", null);
        assertThat(service.getDateiOrdnerPfad()).isEqualTo("C:\\Test\\CADdrawings");
    }

    @Test
    void networkUrl_platzhalterWirdZuLeer() {
        stubSetting("datei.ordner-network-url", null);
        assertThat(service.getDateiOrdnerNetworkUrl()).isEmpty();
    }

    @Test
    void networkUrl_dbWertWirdGeliefert() {
        stubSetting("datei.ordner-network-url", "\\\\server\\zeichnungen");
        assertThat(service.getDateiOrdnerNetworkUrl()).isEqualTo("\\\\server\\zeichnungen");
    }

    @Test
    void konfiguriert_imH2ProfilErstMitDbWert() {
        stubProfilH2(true);
        stubSetting("datei.ordner-pfad", null);
        assertThat(service.isDateiOrdnerConfigured()).isFalse();

        stubSetting("datei.ordner-pfad", "C:\\Zeichnungen");
        assertThat(service.isDateiOrdnerConfigured()).isTrue();
    }

    @Test
    void konfiguriert_produktivProfilZaehltPropertyAlsKonfiguriert() {
        // Produktiv-Server (MySQL) wird über application-local.properties
        // konfiguriert – der Assistent darf dort NICHT erneut aufpoppen.
        stubProfilH2(false);
        stubSetting("datei.ordner-pfad", null);
        assertThat(service.isDateiOrdnerConfigured()).isTrue();
    }
}
