package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService.MailKonto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Deckt die Kontoauswahl fuer Ausgangsgeschaeftsdokumente ab.
 *
 * <p>Der Kern ist der Rueckfall: Ein eingeschaltetes, aber unvollstaendig
 * ausgefuelltes Konto darf den Versand nicht abreissen lassen. Eine Rechnung,
 * die gar nicht rausgeht, waere schlimmer als eine, die ueber das bisherige
 * Postfach geht.</p>
 *
 * <p>Alle Adressen sind Dummy-Werte (DSGVO).</p>
 */
@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceDokumentMailTest {

    private static final String STANDARD_HOST = "securesmtp.beispiel-provider.de";
    private static final String STANDARD_USER = "mustermann@beispiel-provider.de";
    private static final String STANDARD_PASSWORT = "standard-geheim";

    private static final String DOKUMENT_HOST = "mail.musterfirma-beispiel.de";
    private static final String DOKUMENT_USER = "rechnungen@musterfirma-beispiel.de";
    private static final String DOKUMENT_PASSWORT = "dokument-geheim";

    @Mock
    private SystemSettingRepository repository;

    @Mock
    private Environment environment;

    private SystemSettingsService service;

    /** Stellt die DB-Werte dar; wird pro Test befuellt. */
    private Map<String, String> gespeicherteWerte;

    @BeforeEach
    void setUp() {
        gespeicherteWerte = new HashMap<>();
        lenient().when(repository.findById(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = gespeicherteWerte.get(key);
            return value == null
                    ? Optional.empty()
                    : Optional.of(new SystemSetting(key, value, null));
        });

        service = new SystemSettingsService(repository, environment);
        // Property-Defaults, die sonst Spring injizieren wuerde.
        ReflectionTestUtils.setField(service, "defaultSmtpHost", STANDARD_HOST);
        ReflectionTestUtils.setField(service, "defaultSmtpPort", 465);
        ReflectionTestUtils.setField(service, "defaultSmtpUsername", STANDARD_USER);
        ReflectionTestUtils.setField(service, "defaultSmtpPassword", STANDARD_PASSWORT);
    }

    /** Trägt ein vollständiges, eingeschaltetes Dokument-Konto ein. */
    private void hinterlegeVollstaendigesDokumentKonto() {
        gespeicherteWerte.put("smtp.dokumente.aktiv", "true");
        gespeicherteWerte.put("smtp.dokumente.host", DOKUMENT_HOST);
        gespeicherteWerte.put("smtp.dokumente.port", "465");
        gespeicherteWerte.put("smtp.dokumente.username", DOKUMENT_USER);
        gespeicherteWerte.put("smtp.dokumente.password", DOKUMENT_PASSWORT);
    }

    @Test
    @DisplayName("Ohne Einrichtung liefert das Dokument-Konto die Standard-Zugangsdaten")
    void ohneEinrichtungStandardKonto() {
        MailKonto konto = service.getDokumentMailKonto();

        assertThat(konto.host()).isEqualTo(STANDARD_HOST);
        assertThat(konto.username()).isEqualTo(STANDARD_USER);
        assertThat(konto.password()).isEqualTo(STANDARD_PASSWORT);
        assertThat(service.nutztDokumentMailKonto()).isFalse();
    }

    @Test
    @DisplayName("Vollständig ausgefüllt, aber ausgeschaltet: weiterhin Standard-Konto")
    void ausgeschaltetTrotzVollstaendigerDatenStandardKonto() {
        hinterlegeVollstaendigesDokumentKonto();
        gespeicherteWerte.put("smtp.dokumente.aktiv", "false");

        MailKonto konto = service.getDokumentMailKonto();

        assertThat(konto.host()).isEqualTo(STANDARD_HOST);
        assertThat(konto.username()).isEqualTo(STANDARD_USER);
        assertThat(service.nutztDokumentMailKonto()).isFalse();
    }

    @Test
    @DisplayName("Eingeschaltet, aber ohne Passwort: Rückfall aufs Standard-Konto statt Versandabbruch")
    void unvollstaendigesKontoFaelltZurueck() {
        hinterlegeVollstaendigesDokumentKonto();
        gespeicherteWerte.remove("smtp.dokumente.password");

        MailKonto konto = service.getDokumentMailKonto();

        assertThat(konto.host()).isEqualTo(STANDARD_HOST);
        assertThat(konto.username()).isEqualTo(STANDARD_USER);
        assertThat(konto.password()).isEqualTo(STANDARD_PASSWORT);
        assertThat(service.nutztDokumentMailKonto()).isFalse();
    }

    @Test
    @DisplayName("Eingeschaltet und vollständig: Versand über das eigene Postfach")
    void vollstaendigesKontoWirdVerwendet() {
        hinterlegeVollstaendigesDokumentKonto();

        MailKonto konto = service.getDokumentMailKonto();

        assertThat(konto.host()).isEqualTo(DOKUMENT_HOST);
        assertThat(konto.port()).isEqualTo(465);
        assertThat(konto.username()).isEqualTo(DOKUMENT_USER);
        assertThat(konto.password()).isEqualTo(DOKUMENT_PASSWORT);
        assertThat(service.nutztDokumentMailKonto()).isTrue();
    }

    @Test
    @DisplayName("Ohne eigene Absender-Adresse gilt die Adresse des Postfachs")
    void absenderFaelltAufPostfachAdresseZurueck() {
        hinterlegeVollstaendigesDokumentKonto();

        assertThat(service.getDokumentMailKonto().fromAddress()).isEqualTo(DOKUMENT_USER);
    }

    @Test
    @DisplayName("Hinterlegte Absender-Adresse gewinnt gegenüber der Postfach-Adresse")
    void eigeneAbsenderAdresseGewinnt() {
        hinterlegeVollstaendigesDokumentKonto();
        gespeicherteWerte.put("mail.dokumente.from-address", "buchhaltung@musterfirma-beispiel.de");

        assertThat(service.getDokumentMailKonto().fromAddress())
                .isEqualTo("buchhaltung@musterfirma-beispiel.de");
    }

    @Test
    @DisplayName("Unbrauchbare Absender-Adresse ohne @ fällt auf die Postfach-Adresse zurück")
    void kaputteAbsenderAdresseFaelltZurueck() {
        hinterlegeVollstaendigesDokumentKonto();
        gespeicherteWerte.put("mail.dokumente.from-address", "kein-at-zeichen");

        assertThat(service.getDokumentMailKonto().fromAddress()).isEqualTo(DOKUMENT_USER);
    }

    @Test
    @DisplayName("Unlesbarer Port fällt auf 465 zurück statt den Versand mit Port 0 zu versuchen")
    void kaputterPortFaelltAufStandardZurueck() {
        hinterlegeVollstaendigesDokumentKonto();
        gespeicherteWerte.put("smtp.dokumente.port", "keine-zahl");

        assertThat(service.getDokumentMailKonto().port()).isEqualTo(465);
    }

    @Test
    @DisplayName("Ohne Anzeigename bleibt das Feld leer — Versand dann wie bisher nur mit Adresse")
    void ohneAnzeigenameLeer() {
        hinterlegeVollstaendigesDokumentKonto();

        assertThat(service.getDokumentMailKonto().fromName()).isEmpty();
    }

    @Test
    @DisplayName("Hinterlegter Anzeigename wird ins Konto uebernommen")
    void anzeigenameWirdUebernommen() {
        hinterlegeVollstaendigesDokumentKonto();
        gespeicherteWerte.put("mail.dokumente.absender-name", "Musterfirma Metallbau");

        assertThat(service.getDokumentMailKonto().fromName()).isEqualTo("Musterfirma Metallbau");
    }

    @Test
    @DisplayName("Die Kopie landet im Postfach, das auch verschickt hat")
    void sentKopieNutztDasVersendendePostfach() {
        hinterlegeVollstaendigesDokumentKonto();
        gespeicherteWerte.put("imap.username", "mustermann@beispiel-provider.de");
        gespeicherteWerte.put("imap.password", "standard-imap");

        var zugang = service.getDokumentImapZugang();

        // Ohne eigenen Posteingangs-Server gilt der Versand-Server.
        assertThat(zugang.host()).isEqualTo(DOKUMENT_HOST);
        assertThat(zugang.port()).isEqualTo(993);
        assertThat(zugang.username()).isEqualTo(DOKUMENT_USER);
        assertThat(zugang.password()).isEqualTo(DOKUMENT_PASSWORT);
    }

    @Test
    @DisplayName("Abweichender Posteingangs-Server schlaegt den Versand-Server")
    void eigenerImapHostGewinnt() {
        hinterlegeVollstaendigesDokumentKonto();
        gespeicherteWerte.put("imap.dokumente.host", "imap.musterfirma-beispiel.de");

        assertThat(service.getDokumentImapZugang().host()).isEqualTo("imap.musterfirma-beispiel.de");
    }

    @Test
    @DisplayName("Ohne eigenes Postfach landet die Kopie weiter im Standard-Posteingang")
    void ohneDokumentKontoStandardPosteingang() {
        gespeicherteWerte.put("imap.host", "imap.beispiel-provider.de");
        gespeicherteWerte.put("imap.username", "mustermann@beispiel-provider.de");
        gespeicherteWerte.put("imap.password", "standard-imap");

        var zugang = service.getDokumentImapZugang();

        assertThat(zugang.host()).isEqualTo("imap.beispiel-provider.de");
        assertThat(zugang.username()).isEqualTo("mustermann@beispiel-provider.de");
    }

    @Test
    @DisplayName("Das Standard-Konto bleibt vom Dokument-Konto unberührt")
    void standardKontoBleibtUnveraendert() {
        hinterlegeVollstaendigesDokumentKonto();

        MailKonto standard = service.getStandardMailKonto();

        assertThat(standard.host()).isEqualTo(STANDARD_HOST);
        assertThat(standard.username()).isEqualTo(STANDARD_USER);
        assertThat(standard.password()).isEqualTo(STANDARD_PASSWORT);
    }
}
