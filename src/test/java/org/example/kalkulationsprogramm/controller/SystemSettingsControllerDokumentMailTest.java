package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.example.kalkulationsprogramm.controller.SystemSettingsController.DokumentMailRequest;
import org.example.kalkulationsprogramm.service.DateiOrdnerService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Prueft die Eingabevalidierung des Postfachs fuer Ausgangsgeschaeftsdokumente.
 *
 * <p>Wichtigster Fall: Absender-Adresse und Postfach muessen zur selben Domain
 * gehoeren. Andernfalls verschickt das Postfach Mails im Namen einer fremden
 * Domain, SPF und DKIM schlagen beim Empfaenger fehl und die Rechnung landet
 * zuverlaessiger im Spam als vorher — also genau das Gegenteil dessen, wofuer
 * das Feature gebaut wurde.</p>
 *
 * <p>Bewusst kein {@code @WebMvcTest}: Der Controller haengt nur an zwei
 * Services, ein Spring-Kontext waere teurer als noetig. Alle Adressen sind
 * Dummy-Werte (DSGVO).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemSettingsControllerDokumentMailTest {

    private static final String HOST = "mail.musterfirma-beispiel.de";
    private static final String POSTFACH = "rechnungen@musterfirma-beispiel.de";
    private static final String PASSWORT = "dummy-geheim";

    @Mock private SystemSettingsService settingsService;
    @Mock private DateiOrdnerService dateiOrdnerService;

    @InjectMocks private SystemSettingsController controller;

    private static DokumentMailRequest request(boolean aktiv, String host, String username,
            String password, String fromAddress) {
        return new DokumentMailRequest(aktiv, host, 465, username, password, fromAddress);
    }

    private static String meldung(ResponseEntity<Map<String, String>> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody().get("message");
    }

    @Test
    @DisplayName("Absender auf fremder Domain wird abgelehnt und nichts gespeichert")
    void fremdeAbsenderDomainWirdAbgelehnt() {
        var response = controller.saveDokumentMail(
                request(true, HOST, POSTFACH, PASSWORT, "info@ganz-andere-domain.de"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(meldung(response)).contains("selben Domain");
        verify(settingsService, never())
                .saveDokumentMailSettings(anyBoolean(), anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Absender auf derselben Domain wird gespeichert")
    void gleicheAbsenderDomainWirdGespeichert() {
        var response = controller.saveDokumentMail(
                request(true, HOST, POSTFACH, PASSWORT, "buchhaltung@musterfirma-beispiel.de"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(settingsService).saveDokumentMailSettings(true, HOST, 465, POSTFACH,
                PASSWORT, "buchhaltung@musterfirma-beispiel.de");
    }

    @Test
    @DisplayName("Leerer Absender ist erlaubt — dann gilt die Postfach-Adresse")
    void leererAbsenderIstErlaubt() {
        var response = controller.saveDokumentMail(request(true, HOST, POSTFACH, PASSWORT, ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(settingsService).saveDokumentMailSettings(true, HOST, 465, POSTFACH, PASSWORT, "");
    }

    @Test
    @DisplayName("Einschalten ohne Mail-Server wird abgelehnt")
    void ohneHostAbgelehnt() {
        var response = controller.saveDokumentMail(request(true, "  ", POSTFACH, PASSWORT, ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(meldung(response)).contains("Mail-Server");
    }

    @Test
    @DisplayName("Einschalten ohne Passwort wird abgelehnt, wenn auch keins gespeichert ist")
    void ohnePasswortAbgelehnt() {
        given(settingsService.getDokumentSmtpPassword()).willReturn("");

        var response = controller.saveDokumentMail(request(true, HOST, POSTFACH, null, ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(meldung(response)).contains("Passwort");
    }

    @Test
    @DisplayName("Leeres Passwort behaelt das bereits gespeicherte bei")
    void leeresPasswortBehaeltGespeichertes() {
        given(settingsService.getDokumentSmtpPassword()).willReturn(PASSWORT);

        var response = controller.saveDokumentMail(request(true, HOST, POSTFACH, "", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(settingsService).saveDokumentMailSettings(true, HOST, 465, POSTFACH, PASSWORT, "");
    }

    @Test
    @DisplayName("Benutzername ohne gueltige Mailadresse wird abgelehnt")
    void ungueltigerBenutzernameAbgelehnt() {
        var response = controller.saveDokumentMail(request(true, HOST, "kein-at-zeichen", PASSWORT, ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(meldung(response)).contains("E-Mail-Adresse");
    }

    @Test
    @DisplayName("Script-Tag als Absender wird abgelehnt statt gespeichert")
    void skriptAlsAbsenderAbgelehnt() {
        var response = controller.saveDokumentMail(
                request(true, HOST, POSTFACH, PASSWORT, "<script>alert(1)</script>"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(settingsService, never())
                .saveDokumentMailSettings(anyBoolean(), anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Ausgeschaltet darf ein halb ausgefuellter Entwurf gespeichert werden")
    void ausgeschaltetErlaubtUnvollstaendigenEntwurf() {
        // Der echte Service liefert nie null, sondern einen leeren String.
        given(settingsService.getDokumentSmtpPassword()).willReturn("");

        var response = controller.saveDokumentMail(request(false, "", "", "", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(settingsService).saveDokumentMailSettings(false, "", 465, "", "", "");
    }

    @Test
    @DisplayName("Port 0 oder negativ faellt auf 465 zurueck")
    void ungueltigerPortFaelltAufStandard() {
        controller.saveDokumentMail(
                new DokumentMailRequest(true, HOST, -1, POSTFACH, PASSWORT, ""));

        verify(settingsService).saveDokumentMailSettings(true, HOST, 465, POSTFACH, PASSWORT, "");
    }

    @Test
    @DisplayName("Ueberlanger Mail-Server wird abgewiesen statt gespeichert")
    void ueberlangeEingabeAbgelehnt() {
        String zuLang = "a".repeat(10_001);

        var response = controller.saveDokumentMail(request(true, zuLang, POSTFACH, PASSWORT, ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(meldung(response)).contains("zu lang");
        verify(settingsService, never())
                .saveDokumentMailSettings(anyBoolean(), anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Ueberlange Eingabe wird auch im ausgeschalteten Entwurf abgewiesen")
    void ueberlangeEingabeAuchAusgeschaltetAbgelehnt() {
        var response = controller.saveDokumentMail(
                request(false, "a".repeat(10_001), "", "", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(settingsService, never())
                .saveDokumentMailSettings(anyBoolean(), anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("SQL-Injection im Mail-Server wird nicht ausgefuehrt, sondern als Wert behandelt")
    void sqlInjectionWirdAlsWertBehandelt() {
        String boesartig = "'; DROP TABLE system_setting; --";
        given(settingsService.getDokumentSmtpPassword()).willReturn("");

        controller.saveDokumentMail(request(false, boesartig, "", "", ""));

        // JPA speichert parametrisiert; der Wert geht unveraendert als Text durch.
        verify(settingsService).saveDokumentMailSettings(false, boesartig, 465, "", "", "");
    }

    @Test
    @DisplayName("Lesen liefert die Einstellungen, aber niemals das Passwort selbst")
    void lesenLiefertKeinPasswort() {
        given(settingsService.isDokumentMailKontoAktiv()).willReturn(true);
        given(settingsService.getDokumentSmtpHost()).willReturn(HOST);
        given(settingsService.getDokumentSmtpPort()).willReturn(465);
        given(settingsService.getDokumentSmtpUsername()).willReturn(POSTFACH);
        given(settingsService.getDokumentSmtpPassword()).willReturn(PASSWORT);
        given(settingsService.getDokumentMailFromAddress()).willReturn(POSTFACH);

        var response = controller.getDokumentMail();
        var body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(body.host()).isEqualTo(HOST);
        assertThat(body.username()).isEqualTo(POSTFACH);
        assertThat(body.aktiv()).isTrue();
        // Nur die Information "ist gesetzt" geht raus, nie der Wert selbst.
        assertThat(body.passwordSet()).isTrue();
        assertThat(body.toString()).doesNotContain(PASSWORT);
    }

    @Test
    @DisplayName("Lesen meldet ein leeres Passwort als nicht gesetzt")
    void lesenMeldetFehlendesPasswort() {
        given(settingsService.getDokumentSmtpPassword()).willReturn("");

        assertThat(controller.getDokumentMail().getBody()).isNotNull();
        assertThat(controller.getDokumentMail().getBody().passwordSet()).isFalse();
    }

    @Test
    @DisplayName("Verbindungstest nutzt die uebergebenen Daten")
    void verbindungstestNutztUebergebeneDaten() {
        given(settingsService.testSmtp(HOST, 465, POSTFACH, PASSWORT, null))
                .willReturn(SystemSettingsService.TestResult.success("Verbindung steht."));

        var response = controller.testDokumentMail(
                new SystemSettingsController.DokumentMailTestRequest(HOST, 465, POSTFACH, PASSWORT, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
    }

    @Test
    @DisplayName("Verbindungstest ohne Eingaben greift auf die gespeicherten Werte zurueck")
    void verbindungstestNutztGespeicherteDaten() {
        given(settingsService.getDokumentSmtpHost()).willReturn(HOST);
        given(settingsService.getDokumentSmtpPort()).willReturn(465);
        given(settingsService.getDokumentSmtpUsername()).willReturn(POSTFACH);
        given(settingsService.getDokumentSmtpPassword()).willReturn(PASSWORT);
        given(settingsService.testSmtp(HOST, 465, POSTFACH, PASSWORT, null))
                .willReturn(SystemSettingsService.TestResult.failure("Anmeldung fehlgeschlagen."));

        var response = controller.testDokumentMail(
                new SystemSettingsController.DokumentMailTestRequest(null, 0, null, null, null));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        verify(settingsService).testSmtp(HOST, 465, POSTFACH, PASSWORT, null);
    }
}
