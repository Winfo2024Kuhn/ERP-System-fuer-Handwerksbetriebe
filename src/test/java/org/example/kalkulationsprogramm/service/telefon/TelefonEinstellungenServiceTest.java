package org.example.kalkulationsprogramm.service.telefon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufbeantworterDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonEinstellungenSpeichernDto;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelefonEinstellungenServiceTest {

    @Mock SystemSettingsService settings;
    private final Map<String, String> werte = new HashMap<>();
    private final MailSecretService secrets = new MailSecretService("0123456789abcdef0123456789abcdef");
    private TelefonEinstellungenService service;

    @BeforeEach
    void setUp() {
        when(settings.get(anyString(), any())).thenAnswer(i -> werte.getOrDefault(i.getArgument(0), i.getArgument(1)));
        doAnswer(i -> werte.put(i.getArgument(0), i.getArgument(1))).when(settings).save(anyString(), any(), any());
        service = new TelefonEinstellungenService(settings, secrets, new ObjectMapper());
    }

    private static TelefonEinstellungenSpeichernDto dto(String host, String benutzer, String passwort) {
        return new TelefonEinstellungenSpeichernDto(true, host, benutzer, passwort, List.of("2323", " ", "2323"),
                List.of(new AnrufbeantworterDto(0, "AB Nacht"), new AnrufbeantworterDto(1, " "), new AnrufbeantworterDto(1, "doppelt")),
                24, 6);
    }

    @Test
    @DisplayName("Speichern: Passwort verschlüsselt, Nummern/ABs bereinigt, Zugang entschlüsselt")
    void speichern() {
        service.speichere(dto("fritz.box", "erp", "geheim"));

        assertThat(werte.get(TelefonEinstellungenService.PASSWORT)).startsWith("v1:").doesNotContain("geheim");
        assertThat(service.geschaeftsnummern()).containsExactly("2323");
        assertThat(service.anrufbeantworter()).containsExactly(new AnrufbeantworterDto(0, "AB Nacht"), new AnrufbeantworterDto(1, "AB 2"));
        assertThat(service.aufbewahrungAnrufeMonate()).isEqualTo(24);
        assertThat(service.aufbewahrungSprachnachrichtenMonate()).isEqualTo(6);
        assertThat(service.istAktiv()).isTrue();
        assertThat(service.zugang()).contains(new TelefonZugang("fritz.box", "erp", "geheim"));

        TelefonEinstellungenDto geladen = service.lade(true);
        assertThat(geladen.passwortGesetzt()).isTrue();
        assertThat(geladen.verschluesselungEingerichtet()).isTrue();
        assertThat(geladen.anrufmonitorVerbunden()).isTrue();
        assertThat(geladen.toString()).doesNotContain("geheim");
    }

    @Test
    @DisplayName("Leeres Passwort lässt das gespeicherte unverändert")
    void passwortUnveraendert() {
        service.speichere(dto("fritz.box", "erp", "geheim"));
        String vorher = werte.get(TelefonEinstellungenService.PASSWORT);
        service.speichere(dto(null, null, ""));
        service.speichere(dto(null, null, null));
        assertThat(werte.get(TelefonEinstellungenService.PASSWORT)).isEqualTo(vorher);
    }

    @Test
    @DisplayName("Ungültige Eingaben werden abgelehnt")
    void ungueltig() {
        assertThatThrownBy(() -> service.speichere(dto("http://evil.test", null, null))).hasMessageContaining("Adresse");
        assertThatThrownBy(() -> service.speichere(new TelefonEinstellungenSpeichernDto(null, null, null, null, null, null, 0, null)))
                .hasMessageContaining("120");
        assertThatThrownBy(() -> service.speichere(new TelefonEinstellungenSpeichernDto(null, null, null, null, null, null, null, 121)))
                .hasMessageContaining("120");
        assertThatThrownBy(() -> service.speichere(new TelefonEinstellungenSpeichernDto(null, null, "x".repeat(10_001), null, null, null, null, null)))
                .hasMessageContaining("zu lang");
        List<String> viele = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            viele.add("10" + i);
        }
        assertThatThrownBy(() -> service.speichere(new TelefonEinstellungenSpeichernDto(null, null, null, null, viele, null, null, null)))
                .hasMessageContaining("Zu viele");
    }

    @Test
    @DisplayName("Ohne Verschlüsselungsschlüssel lässt sich kein Passwort speichern, Zugang bleibt leer")
    void ohneSchluessel() {
        TelefonEinstellungenService ohne = new TelefonEinstellungenService(settings, new MailSecretService(""), new ObjectMapper());
        assertThatThrownBy(() -> ohne.speichere(dto("fritz.box", "erp", "geheim"))).hasMessageContaining("encryption-key");
        werte.put(TelefonEinstellungenService.PASSWORT, "v1:irgendwas");
        werte.put(TelefonEinstellungenService.BENUTZER, "erp");
        assertThat(ohne.zugang()).isEmpty();
        assertThat(ohne.lade(false).verschluesselungEingerichtet()).isFalse();
    }

    @Test
    @DisplayName("Kaputtes gespeichertes Passwort → kein Zugang statt Absturz")
    void kaputtesPasswort() {
        werte.put(TelefonEinstellungenService.PASSWORT, "v1:kaputt");
        werte.put(TelefonEinstellungenService.BENUTZER, "erp");
        assertThat(service.zugang()).isEmpty();
    }

    @Test
    @DisplayName("Test-Zugang: Eingaben haben Vorrang, fehlende kommen aus den Einstellungen")
    void testZugang() {
        assertThat(service.zugangFuerTest(null, null, null)).isEmpty();
        assertThat(service.zugangFuerTest("192.168.178.1", "erp", "pw")).contains(new TelefonZugang("192.168.178.1", "erp", "pw"));
        service.speichere(dto("fritz.box", "erp", "geheim"));
        assertThat(service.zugangFuerTest(" ", "", "")).contains(new TelefonZugang("fritz.box", "erp", "geheim"));
        assertThat(service.zugangFuerTest("evil.test/x", "erp", "pw")).isEmpty();
    }

    @Test
    @DisplayName("Standardwerte, Vorwahlen, Status")
    void standardUndStatus() {
        assertThat(service.host()).isEqualTo("fritz.box");
        werte.put(TelefonEinstellungenService.HOST, " ");
        assertThat(service.host()).isEqualTo("fritz.box");
        assertThat(service.aufbewahrungAnrufeMonate()).isEqualTo(12);
        werte.put(TelefonEinstellungenService.AUFBEWAHRUNG_ANRUFE, "abc");
        assertThat(service.aufbewahrungAnrufeMonate()).isEqualTo(12);
        werte.put(TelefonEinstellungenService.AUFBEWAHRUNG_ANRUFE, "999");
        assertThat(service.aufbewahrungAnrufeMonate()).isEqualTo(120);
        werte.put(TelefonEinstellungenService.ANRUFBEANTWORTER, "kein json");
        assertThat(service.anrufbeantworter()).isEmpty();

        service.speichereVorwahlen("49", "0931");
        service.speichereVorwahlen(" ", null);
        assertThat(service.landesvorwahl()).isEqualTo("49");
        assertThat(service.ortsvorwahl()).isEqualTo("0931");

        assertThat(service.letzteAbholung()).isNull();
        service.merkeFehler("FRITZ!Box nicht erreichbar");
        assertThat(service.letzterFehler()).isEqualTo("FRITZ!Box nicht erreichbar");
        LocalDateTime t = LocalDateTime.of(2026, 9, 29, 12, 0);
        service.merkeErfolg(t);
        assertThat(service.letzteAbholung()).isEqualTo(t);
        assertThat(service.letzterFehler()).isNull();
        werte.put(TelefonEinstellungenService.LETZTE_ABHOLUNG, "kaputt");
        assertThat(service.letzteAbholung()).isNull();
    }
}
