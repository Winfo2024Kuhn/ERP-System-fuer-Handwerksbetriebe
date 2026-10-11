package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Welche Host-Header die Netzgrenze aus dem eigenen Netz annimmt. Alle Namen sind Dummy-Werte. */
class ErlaubteAdressenTest {

    private static final ErlaubteAdressen OHNE_EINSTELLUNG = new ErlaubteAdressen("", "", Set::of);

    @Test
    void adressenOhneFremdesDnsSindImmerErlaubt() {
        String[] immer = {
                "192.168.178.20", "100.101.102.103", "127.0.0.1", "[fd7a:115c:a1e0::1]", "fd7a:115c:a1e0::1", "[::1]",
                "localhost", "LOCALHOST", "erp-pc", "app", "erp-pc.",
                "erp-rechner.tail-xxxx.ts.net", "erp-pc.fritz.box", "fritz.box", "erp-pc.local", "erp-pc.lan",
                "erp-pc.home.arpa", "erp.internal", "erp.localhost", "ERP-PC.FRITZ.BOX.", "host.docker.internal",
                "erp-pc.home", "erp-pc.firma.corp", "erp-pc.localdomain"};
        for (String host : immer) {
            assertThat(OHNE_EINSTELLUNG.erlaubt(host)).as(host).isTrue();
        }
    }

    @Test
    void fremdeNamenWerdenAbgewiesen() {
        String[] fremd = {
                "angreifer.example", "angreifer.example.", "localhost.angreifer.example", "192.168.178.20.angreifer.example",
                "angreifer-ts.net", "fritz.box.angreifer.example", "erp.example.test", "256.1.1.1", "1.2.3",
                "erp-pc.fritz.box.angreifer.example", "ts.net"};
        for (String host : fremd) {
            assertThat(OHNE_EINSTELLUNG.erlaubt(host)).as(host).isFalse();
        }
    }

    @Test
    void ohneHostNamenGibtEsNichtsZuPruefen() {
        // HTTP/1.0 ohne Host-Header: DNS-Rebinding geht nur mit Namen.
        assertThat(OHNE_EINSTELLUNG.erlaubt(null)).isTrue();
        assertThat(OHNE_EINSTELLUNG.erlaubt("")).isTrue();
    }

    @Test
    void eingetrageneAdressenGeltenAuchMitSchemaPortUndPfad() {
        var adressen = new ErlaubteAdressen(" erp.example.test , https://zeit.example.test:8443/zeiterfassung/,"
                + "lager.example.test:8080, ,[fd00::7]:8080", "", Set::of);
        assertThat(adressen.erlaubt("erp.example.test")).isTrue();
        assertThat(adressen.erlaubt("ERP.example.test.")).isTrue();
        assertThat(adressen.erlaubt("zeit.example.test")).isTrue();
        assertThat(adressen.erlaubt("lager.example.test")).isTrue();
        assertThat(adressen.erlaubt("buero.example.test")).isFalse();
        assertThat(adressen.erlaubt("angreifer.example")).isFalse();
    }

    @Test
    void sternPunktGiltFuerUnteradressenNichtFuerAehnlicheNamen() {
        var adressen = new ErlaubteAdressen("*.example.test", "", Set::of);
        assertThat(adressen.erlaubt("erp.example.test")).isTrue();
        assertThat(adressen.erlaubt("buero.erp.example.test")).isTrue();
        assertThat(adressen.erlaubt("example.test")).isFalse();
        assertThat(adressen.erlaubt("angreiferexample.test")).isFalse();
        assertThat(adressen.erlaubt("example.test.angreifer.example")).isFalse();
    }

    @Test
    void sternAlleinSchaltetDiePruefungAb() {
        var adressen = new ErlaubteAdressen("erp.example.test, *", "", Set::of);
        assertThat(adressen.erlaubt("angreifer.example")).isTrue();
    }

    @Test
    void adresseDerZeiterfassungIstErlaubt() {
        assertThat(new ErlaubteAdressen("", "https://zeit.example.test", Set::of).erlaubt("zeit.example.test")).isTrue();
        assertThat(new ErlaubteAdressen("", "https://zeit.example.test:8443/", Set::of).erlaubt("zeit.example.test")).isTrue();
        // Kaputte Einträge sperren niemanden zusätzlich aus und öffnen nichts.
        assertThat(new ErlaubteAdressen("", "https://zeit example test", Set::of).erlaubt("angreifer.example")).isFalse();
        assertThat(new ErlaubteAdressen(null, null, Set::of).erlaubt("angreifer.example")).isFalse();
    }

    @Test
    void nameDiesesRechnersWirdErstBeiBedarfErmittelt() {
        AtomicInteger abfragen = new AtomicInteger();
        var adressen = new ErlaubteAdressen("", "", () -> {
            abfragen.incrementAndGet();
            return Set.of("ERP-PC.example.test");
        });
        assertThat(adressen.erlaubt("192.168.178.20")).isTrue();
        assertThat(adressen.erlaubt("erp-pc")).isTrue();
        assertThat(abfragen).hasValue(0);

        assertThat(adressen.erlaubt("erp-pc.example.test")).isTrue();
        assertThat(adressen.erlaubt("angreifer.example")).isFalse();
        assertThat(abfragen).as("nur einmal je Konfiguration").hasValue(1);
    }

    @Test
    void abgewieseneAdresseStehtEinmalUndOhneSteuerzeichenImLog() {
        List<String> log = mitLog(() -> {
            var adressen = new ErlaubteAdressen("", "", Set::of);
            adressen.meldeAbgewiesen("angreifer.example");
            adressen.meldeAbgewiesen("ANGREIFER.example.");
            adressen.meldeAbgewiesen("zweiter.example\r\nGefaelschte Zeile");
            adressen.meldeAbgewiesen("a".repeat(150) + ".example");
        });
        assertThat(log).satisfiesExactly(
                erste -> assertThat(erste).contains("\"angreifer.example\"", "app.security.allowed-hosts ergänzen", "kommagetrennt"),
                zweite -> assertThat(zweite).contains("zweiter.example??gefaelschte?zeile").doesNotContain("\n"),
                lang -> assertThat(lang).contains("\"" + "a".repeat(100) + "…\""));
    }

    @Test
    void logMeldetJeStundeHoechstensHundertAdressen() {
        AtomicLong uhr = new AtomicLong();
        var adressen = new ErlaubteAdressen("", "", Set::of, uhr::get);
        List<String> log = mitLog(() -> {
            for (int i = 0; i <= ErlaubteAdressen.MAX_MELDUNGEN_JE_FENSTER; i++) {
                adressen.meldeAbgewiesen("scanner-" + i + ".example");
            }
        });
        assertThat(log).hasSize(ErlaubteAdressen.MAX_MELDUNGEN_JE_FENSTER);

        // Nach einer Stunde kommen auch neue (und alte) Adressen wieder ins Log.
        uhr.set(ErlaubteAdressen.MELDEFENSTER_MS);
        assertThat(mitLog(() -> {
            adressen.meldeAbgewiesen("erp.firma.example");
            adressen.meldeAbgewiesen("scanner-0.example");
        })).hasSize(2);
    }

    @Test
    void domainsMitUmlautUndUnterstrichLassenSichFreigeben() {
        var adressen = new ErlaubteAdressen("erp.schlosserei-müller.example, erp_pc.firma.example, *.bäckerei.example",
                "https://zeit.tischlerei-höfer.example:8443/", Set::of);
        // Der Browser schickt Umlaut-Namen als xn--…
        assertThat(adressen.erlaubt(IDN.toASCII("erp.schlosserei-müller.example"))).isTrue();
        assertThat(adressen.erlaubt(IDN.toASCII("zeit.tischlerei-höfer.example"))).isTrue();
        assertThat(adressen.erlaubt(IDN.toASCII("lager.bäckerei.example"))).isTrue();
        assertThat(adressen.erlaubt("erp_pc.firma.example")).isTrue();
        assertThat(adressen.erlaubt("erp.schlosserei-mueller.example")).isFalse();
    }

    @Test
    void eszettUndUnsichtbareVerbinderGebenKeineFremdeDomainFrei() {
        // java.net.IDN machte aus straße "strasse" – eine andere, frei registrierbare Domain.
        List<String> log = mitLog(() -> {
            var adressen = new ErlaubteAdressen("erp.straße.example, *.straße.example, er\u200Dp.firma.example",
                    "https://zeit.straße.example", Set::of);
            assertThat(adressen.erlaubt("erp.strasse.example")).isFalse();
            assertThat(adressen.erlaubt("lager.strasse.example")).isFalse();
            assertThat(adressen.erlaubt("zeit.strasse.example")).isFalse();
            assertThat(adressen.erlaubt("erp.firma.example")).isFalse();
        });
        assertThat(log).hasSize(4).allSatisfy(zeile -> assertThat(zeile).contains("xn--Form"));
        assertThat(log.get(3)).startsWith("zeiterfassung.base-url");
    }

    @Test
    void unlesbareUndZuBreiteEintraegeWerdenGemeldetUndIgnoriert() {
        List<String> log = mitLog(() -> {
            var adressen = new ErlaubteAdressen("[fd00::7, *.de, *., erp\u0000.example, *fritz, gut.example.test", "http://[::1", Set::of);
            assertThat(adressen.erlaubt("angreifer.de")).isFalse();
            assertThat(adressen.erlaubt("gut.example.test")).isTrue();
        });
        assertThat(log).hasSize(6);
        assertThat(log.get(0)).contains("?fd00::7", "keine lesbare Adresse");
        assertThat(log.get(1)).contains("\"?.de\"", "keine ganze Endung");
        assertThat(log.get(2)).contains("keine lesbare Adresse");
        assertThat(log.get(3)).contains("erp?.example").doesNotContain("\u0000");
        assertThat(log.get(4)).contains("?fritz", "keine lesbare Adresse");
        assertThat(log.get(5)).contains("zeiterfassung.base-url");
    }

    @Test
    void fehlgeschlagenerRechnernameWirdSpaeterNeuErmittelt() {
        AtomicLong uhr = new AtomicLong();
        AtomicInteger abfragen = new AtomicInteger();
        Supplier<Set<String>> quelle = () -> abfragen.incrementAndGet() == 1 ? Set.of() : Set.of("erp-pc.firma.example");
        var adressen = new ErlaubteAdressen("", "", quelle, uhr::get);

        assertThat(adressen.erlaubt("erp-pc.firma.example")).isFalse();
        uhr.set(ErlaubteAdressen.NEUER_VERSUCH_NACH_MS - 1);
        assertThat(adressen.erlaubt("erp-pc.firma.example")).isFalse();
        assertThat(abfragen).as("vor Ablauf kein neuer Versuch").hasValue(1);

        uhr.set(ErlaubteAdressen.NEUER_VERSUCH_NACH_MS);
        assertThat(adressen.erlaubt("erp-pc.firma.example")).isTrue();
        assertThat(adressen.erlaubt("angreifer.example")).isFalse();
        assertThat(abfragen).as("Erfolg wird behalten").hasValue(2);
    }

    @Test
    void rechnernamenOhneAntwortSindLeer() {
        assertThat(ErlaubteAdressen.rechnernamenVon(() -> { throw new UnknownHostException("erp-pc"); })).isEmpty();
        assertThat(ErlaubteAdressen.rechnernamenVon(InetAddress::getLoopbackAddress)).isNotEmpty();
    }

    @Test
    void hostAusUndIpErkennungBrauchenKeinDns() {
        assertThat(ErlaubteAdressen.hostAus("https://erp.example.test:8443/x")).isEqualTo("erp.example.test");
        assertThat(ErlaubteAdressen.hostAus("erp.example.test:8080/x")).isEqualTo("erp.example.test");
        assertThat(ErlaubteAdressen.hostAus("[fd00::7]:8080")).isEqualTo("[fd00::7]");
        assertThat(ErlaubteAdressen.hostAus("fd00::7")).isEqualTo("fd00::7");
        assertThat(ErlaubteAdressen.hostAus("http://[fd00::7]:8080")).isEqualTo("[fd00::7]");
        assertThat(ErlaubteAdressen.hostAus("https://max@erp.example.test?x#y")).isEqualTo("erp.example.test");
        assertThat(ErlaubteAdressen.hostAus("[fd00::7")).as("Klammer nicht geschlossen").isEqualTo("[fd00::7");
        assertThat(ErlaubteAdressen.hostAus(null)).isEmpty();
        assertThat(ErlaubteAdressen.normalisiere(null)).isEmpty();

        assertThat(ErlaubteAdressen.istIpAdresse("10.0.0.1")).isTrue();
        assertThat(ErlaubteAdressen.istIpAdresse("255.255.255.255")).isTrue();
        assertThat(ErlaubteAdressen.istIpAdresse("fd00::7")).isTrue();
        assertThat(ErlaubteAdressen.istIpAdresse("10.0.0")).isFalse();
        assertThat(ErlaubteAdressen.istIpAdresse("10.0.0.1.angreifer.example")).isFalse();
        assertThat(ErlaubteAdressen.istIpAdresse("10.0..1")).isFalse();
        assertThat(ErlaubteAdressen.istIpAdresse("10.0.0.1234")).isFalse();
        assertThat(ErlaubteAdressen.istIpAdresse("١٠.0.0.1")).as("nur ASCII-Ziffern").isFalse();
    }

    /** Was {@link ErlaubteAdressen} beim Ausführen ins Log schreibt. */
    private static List<String> mitLog(Runnable aktion) {
        Logger logger = (Logger) LoggerFactory.getLogger(ErlaubteAdressen.class);
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        logger.addAppender(log);
        try {
            aktion.run();
        } finally {
            logger.detachAppender(log);
        }
        return log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
