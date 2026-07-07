package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Schmaler Test für die Logik rund um den konfigurierbaren Standard-
 * Absender für automatische System-Mails (Auftragsbestätigung, Mahnung).
 *
 * <p>Wird ohne Spring-Context geführt — der Service hängt nur am
 * Repository und liest @Value-Defaults via Reflection-Felder, die
 * im Test direkt nicht relevant sind (sie bleiben null/0, was im
 * Test-Pfad als "leerer Default" interpretiert wird, genau wie bei
 * einer frischen Installation).</p>
 */
class SystemSettingsServiceMailFromTest {

    private SystemSettingRepository repository;
    private SystemSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(SystemSettingRepository.class);
        service = new SystemSettingsService(repository, mock(org.springframework.core.env.Environment.class));
    }

    @Test
    void mailFromAddress_leerInDb_faelltAufSmtpUserZurueck() {
        stubSetting("mail.from-address", null);
        stubSetting("smtp.username", "info-firma@t-online.de");

        assertThat(service.getMailFromAddress()).isEqualTo("info-firma@t-online.de");
    }

    @Test
    void mailFromAddress_konfiguriert_wirdZurueckgegeben() {
        stubSetting("mail.from-address", "kontakt@firma.de");
        stubSetting("smtp.username", "info-firma@t-online.de");

        assertThat(service.getMailFromAddress()).isEqualTo("kontakt@firma.de");
    }

    @Test
    void mailFromAddress_ohneAtZeichen_faelltAufSmtpUserZurueck() {
        // Defense-in-Depth: ein direkt in die DB geschriebener Müll-Wert
        // (kein "@") darf nicht ungeprüft als From-Adresse rausgehen.
        stubSetting("mail.from-address", "kein-at-zeichen");
        stubSetting("smtp.username", "info-firma@t-online.de");

        assertThat(service.getMailFromAddress()).isEqualTo("info-firma@t-online.de");
    }

    @Test
    void mailFromAddress_mitWhitespace_wirdGetrimmt() {
        stubSetting("mail.from-address", "  kontakt@firma.de  ");
        stubSetting("smtp.username", "info-firma@t-online.de");

        assertThat(service.getMailFromAddress()).isEqualTo("kontakt@firma.de");
    }

    @Test
    void mailFromAddress_platzhalter_faelltAufSmtpUserZurueck() {
        // sanitizeValue filtert OVERRIDE_IN_LOCAL und smtp.example.com aus.
        stubSetting("mail.from-address", "OVERRIDE_IN_LOCAL");
        stubSetting("smtp.username", "info-firma@t-online.de");

        assertThat(service.getMailFromAddress()).isEqualTo("info-firma@t-online.de");
    }

    private void stubSetting(String key, String value) {
        if (value == null) {
            when(repository.findById(eq(key))).thenReturn(Optional.empty());
        } else {
            when(repository.findById(eq(key))).thenReturn(Optional.of(new SystemSetting(key, value, null)));
        }
        // Einige Tests rufen save() in saveMailFromAddress() auf — brauchen wir
        // hier nicht, dieser Test deckt nur den Lese-Pfad ab. Der Stub bleibt
        // tolerant gegenüber unerwarteten Aufrufen anderer Keys.
        when(repository.save(any(SystemSetting.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void saveMailFromAddress_trimmt_undSpeichertGetrimmtenWert() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        service.saveMailFromAddress("  kontakt@firma.de  ");

        org.mockito.ArgumentCaptor<SystemSetting> captor =
                org.mockito.ArgumentCaptor.forClass(SystemSetting.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("mail.from-address");
        assertThat(captor.getValue().getValue()).isEqualTo("kontakt@firma.de");
    }

    @Test
    void saveMailFromAddress_leer_speichertLeerstring() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        service.saveMailFromAddress("");

        org.mockito.ArgumentCaptor<SystemSetting> captor =
                org.mockito.ArgumentCaptor.forClass(SystemSetting.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValue()).isEmpty();
    }
}
