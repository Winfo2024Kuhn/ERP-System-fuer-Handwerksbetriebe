package org.example.kalkulationsprogramm.service.spam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.example.kalkulationsprogramm.domain.AbzockeArt;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Warnhinweise zu Betrugsmaschen. Jede Masche braucht eine Kombination; Gegenproben mit
 * alltäglichen Handwerker-Mails. Nur Dummy-Daten (IBANs sind die bekannten Beispiel-IBANs).
 */
class AbzockeErkennungTest {

    /** Beispiel-IBAN aus der Norm, Prüfziffer gültig. */
    private static final String IBAN_DE = "DE89 3704 0044 0532 0130 00";
    private static final String IBAN_AT = "AT611904300234573201";

    private static Email mail(String von, String betreff, String text) {
        Email email = new Email();
        email.setFromAddress(von);
        email.setSubject(betreff);
        email.setBody(text);
        return email;
    }

    @Nested
    class Bankverbindung {

        @Test
        void neueBankverbindungVomBekanntenLieferantenWirdGewarnt() {
            Email email = mail("buchhaltung@stahlhandel-muster.de", "Rechnung 4711",
                    "Sehr geehrte Damen und Herren, bitte beachten Sie unsere neue Bankverbindung: IBAN "
                            + IBAN_DE + ". Bitte überweisen Sie künftig nur noch auf dieses Konto.");
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Stahlhandel Muster GmbH");
            email.assignToLieferant(lieferant);

            assertThat(AbzockeErkennung.erkenne(email)).isEqualTo(AbzockeArt.BANKVERBINDUNG_GEAENDERT);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Unsere Bankverbindung hat sich geändert. Neu: IBAN " + IBAN_DE,
                "Unsere Bankverbindung hat sich geaendert. Neu: IBAN " + IBAN_DE,
                "Wegen einer Kontoänderung zahlen Sie bitte an " + IBAN_AT,
                "Neue IBAN: " + IBAN_AT,
                "Please note our updated bank details: " + IBAN_DE
        })
        void aenderungMitGueltigerIbanWirdGewarnt(String text) {
            assertThat(AbzockeErkennung.erkenne(mail("info@lieferant.example", "Info", text)))
                    .isEqualTo(AbzockeArt.BANKVERBINDUNG_GEAENDERT);
        }

        @Test
        void htmlMailOhneTextteilWirdGeprueft() {
            Email email = mail("info@lieferant.example", "Info", null);
            email.setHtmlBody("<p>Neue <b>Bankverbindung</b>:</p><p>" + IBAN_DE + "</p>");

            assertThat(AbzockeErkennung.erkenne(email)).isEqualTo(AbzockeArt.BANKVERBINDUNG_GEAENDERT);
        }

        @Test
        void normaleRechnungMitIbanImFussIstKeineWarnung() {
            Email email = mail("buchhaltung@stahlhandel-muster.de", "Rechnung 4711",
                    "Anbei Ihre Rechnung. Zahlbar innerhalb von 14 Tagen. Bankverbindung: Musterbank, IBAN "
                            + IBAN_DE + ", BIC MUSTDEFFXXX.");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }

        @Test
        void aenderungOhneGueltigeIbanIstKeineWarnung() {
            Email email = mail("info@lieferant.example", "Info",
                    "Unsere neue Bankverbindung erhalten Sie per Post. IBAN DE00 3704 0044 0532 0130 00");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }

        @Test
        void ibanPruefungKenntLaenderUndPruefziffer() {
            assertThat(AbzockeErkennung.enthaeltIban("iban de89370400440532013000 bic x")).isTrue();
            assertThat(AbzockeErkennung.enthaeltIban("at61 1904 3002 3457 3201 kein")).isTrue();
            assertThat(AbzockeErkennung.enthaeltIban("xx89 3704 0044 0532 0130 00")).isFalse();
            assertThat(AbzockeErkennung.enthaeltIban("de89 3704 0044")).isFalse();
        }
    }

    @Nested
    class BehoerdeGericht {

        @Test
        void absenderNameAlsGerichtMitZahlungWirdGewarnt() {
            Email email = mail("poststelle@gerichtskasse-online.example", "Wichtige Mitteilung",
                    "Bitte begleichen Sie die offene Forderung innerhalb von 3 Tagen.");
            email.setFromName("Amtsgericht Musterstadt");

            assertThat(AbzockeErkennung.erkenne(email)).isEqualTo(AbzockeArt.BEHOERDE_GERICHT);
        }

        @Test
        void drohungImBetreffMitBehoerdeImTextWirdGewarnt() {
            Email email = mail("info@vollstreckung-service.example", "Ankündigung Kontopfändung",
                    "Das Finanzamt hat die Pfändung beantragt. Zahlung bis Freitag.");

            assertThat(AbzockeErkennung.erkenne(email)).isEqualTo(AbzockeArt.BEHOERDE_GERICHT);
        }

        @Test
        void behoerdeUndForderungImBetreffWirdGewarnt() {
            Email email = mail("zoll@paket-zustellung.example", "Hauptzollamt: Zollgebühren offen",
                    "Ihre Sendung wird erst nach Zahlung freigegeben.");

            assertThat(AbzockeErkennung.erkenne(email)).isEqualTo(AbzockeArt.BEHOERDE_GERICHT);
        }

        @ParameterizedTest
        @ValueSource(strings = {"poststelle@ag-musterstadt.justiz.nrw.de", "info@zoll.de", "post@bzst.bund.de"})
        void amtlicheDomainWirdNichtGewarnt(String von) {
            Email email = mail(von, "Zahlungsaufforderung Amtsgericht", "Bitte zahlen Sie bis zur Frist.");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }

        @Test
        void kundeErwaehntFinanzamtIstKeineWarnung() {
            Email email = mail("max.mustermann@gmail.com", "Rechnung Treppengeländer",
                    "Für das Finanzamt brauche ich eine Rechnung mit Lohnanteil. Zahlung per Überweisung.");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }

        @Test
        void mahnungEinesLieferantenIstKeineWarnung() {
            Email email = mail("buchhaltung@stahlhandel-muster.de", "3. Mahnung Rechnung 4711",
                    "Ohne Zahlung beantragen wir einen Mahnbescheid beim Amtsgericht.");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }

        @Test
        void gerichtetIstKeinGericht() {
            Email email = mail("info@kunde.example", "Ihre an uns gerichtete Zahlung",
                    "Die an Sie gerichtete Zahlung ist eingegangen.");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }

        @Test
        void eigeneSteuerkanzleiWirdNichtGewarnt() {
            Email email = mail("kanzlei@steuerberatung-muster.de", "Finanzamt: Zahlung fällig",
                    "Das Finanzamt fordert die Zahlung bis zur Frist.");
            SteuerberaterKontakt kanzlei = new SteuerberaterKontakt();
            kanzlei.setName("Mustermann Steuerberatung");
            email.assignToSteuerberater(kanzlei);

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }
    }

    @Nested
    class GoogleProfil {

        @Test
        void fremdeDomainMitProfilUndDruckWirdGewarnt() {
            Email email = mail("service@business-center.example", "Ihr Google Unternehmensprofil",
                    "Ihr Google-Unternehmensprofil wird in 48 Stunden gelöscht. Jetzt verifizieren!");

            assertThat(AbzockeErkennung.erkenne(email)).isEqualTo(AbzockeArt.GOOGLE_PROFIL);
        }

        @Test
        void echteGoogleMailWirdNichtGewarnt() {
            Email email = mail("businessprofile-noreply@google.com", "Ihr Google Unternehmensprofil",
                    "Bitte bestätigen Sie Ihr Google-Unternehmensprofil.");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }

        @Test
        void profilOhneDruckIstKeineWarnung() {
            Email email = mail("info@agentur.example", "Bewertungen",
                    "Viele Kunden finden Sie über Ihr Google Unternehmensprofil.");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }
    }

    @Nested
    class Branchenbuch {

        @Test
        void korrekturabzugFuerBranchenverzeichnisWirdGewarnt() {
            Email email = mail("info@deutsches-firmenregister.example", "Korrekturabzug",
                    "Bitte prüfen Sie den Korrekturabzug Ihres Eintrags im Branchenverzeichnis. "
                            + "Jahresgebühr 890 EUR, Laufzeit 2 Jahre.");

            assertThat(AbzockeErkennung.erkenne(email)).isEqualTo(AbzockeArt.BRANCHENBUCH);
        }

        @Test
        void eintragMitKostenpflichtigWirdGewarnt() {
            Email email = mail("info@gewerbe-portal.example", "Ihr Eintrag",
                    "Ihr Eintrag wird mit Unterschrift kostenpflichtig aktiviert.");

            assertThat(AbzockeErkennung.erkenne(email)).isEqualTo(AbzockeArt.BRANCHENBUCH);
        }

        @Test
        void leistungsverzeichnisIstKeinBranchenbuch() {
            Email email = mail("planung@architekt-muster.de", "Leistungsverzeichnis Treppe",
                    "Anbei das Leistungsverzeichnis, Laufzeit der Ausschreibung 4 Wochen, Kosten bitte pro Position.");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }

        @Test
        void echtesVerzeichnisWirdNichtGewarnt() {
            Email email = mail("rechnung@gelbeseiten.de", "Ihr Branchenbuch-Eintrag",
                    "Ihr Eintrag im Branchenbuch, Jahresgebühr wie vereinbart.");

            assertThat(AbzockeErkennung.erkenne(email)).isNull();
        }
    }

    @Nested
    class Allgemein {

        @Test
        void gefaehrlichsteMascheGewinnt() {
            Email email = mail("info@deutsches-firmenregister.example", "Korrekturabzug Branchenverzeichnis",
                    "Jahresgebühr 890 EUR. Neue Bankverbindung: " + IBAN_DE);

            assertThat(AbzockeErkennung.erkenne(email)).isEqualTo(AbzockeArt.BANKVERBINDUNG_GEAENDERT);
        }

        @Test
        void leereMailIstKeineWarnung() {
            assertThat(AbzockeErkennung.erkenne(null)).isNull();
            assertThat(AbzockeErkennung.erkenne(mail(null, null, null))).isNull();
            assertThat(AbzockeErkennung.erkenne(mail("max.mustermann@gmail.com", "Anfrage Treppe",
                    "Guten Tag, können Sie mir ein Angebot für eine Innentreppe machen?"))).isNull();
        }

        @Test
        void zehntausendZeichenSindSchnellGeprueft() {
            String[] boeseTexte = {
                    "de12 ".repeat(2_000),
                    "a".repeat(10_000),
                    "neue bankverbindung iban ".repeat(400),
                    "de89 3704 0044 0532 0130 0".repeat(400),
                    "amtsgericht zahlung ".repeat(500),
                    "<a<a<a<a".repeat(1_250)
            };
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                for (String text : boeseTexte) {
                    Email email = mail("x@phish.example", text, text);
                    email.setFromName(text);
                    AbzockeErkennung.erkenne(email);
                    SpamSignale.erkenne(email);
                }
            });
        }
    }
}
