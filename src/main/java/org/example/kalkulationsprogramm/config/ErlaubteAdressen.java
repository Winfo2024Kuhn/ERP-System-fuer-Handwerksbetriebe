package org.example.kalkulationsprogramm.config;

import java.net.IDN;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Schutz vor DNS-Rebinding: Unter welchen Adressen (Host-Header) das ERP Anfragen aus dem
 * eigenen Netz annimmt. Geprüft wird in {@link ZeiterfassungSecurityFilter}.
 *
 * <p>Beim DNS-Rebinding lässt eine fremde Webseite ihren eigenen Namen (etwa
 * {@code boese-seite.example}) nachträglich auf die Adresse des ERP zeigen. Der Browser eines
 * Mitarbeiters schickt die Anfragen ihres Skripts dann ins Büronetz, trägt im Host-Header aber
 * den fremden Namen ein. Angenommen werden deshalb nur Namen, die ein Fremder nicht auf eine
 * beliebige Adresse zeigen lassen kann:</p>
 * <ul>
 *   <li>IP-Adressen ({@code 192.168.178.20}, {@code 100.64.0.7}, {@code [fd7a::1]}): Dafür fragt
 *       der Browser kein DNS.</li>
 *   <li>{@code localhost} und Namen ohne Punkt ({@code erp-pc}, {@code app} im Docker-Netz):
 *       Die löst nur das eigene Netz auf.</li>
 *   <li>Namen, die es nur im eigenen Netz gibt oder die Tailscale vergibt: siehe
 *       {@link #LOKALE_ENDUNGEN}.</li>
 *   <li>Die Adresse aus {@code zeiterfassung.base-url} und der Name dieses Rechners.</li>
 *   <li>Alles aus {@code app.security.allowed-hosts}: {@code erp.firma.de}, {@code *.firma.de};
 *       {@code *} schaltet die Prüfung ab.</li>
 * </ul>
 * So sperrt das Update keine bestehende Installation aus, auch nicht im Docker-Container, der
 * die Adresse seines Rechners gar nicht kennt. Nur eine eigene Domain, die im Büronetz direkt
 * auf das ERP zeigt, muss einmal eingetragen werden. Namen mit Umlaut zählen in der
 * Schreibweise, die der Browser schickt ({@code xn--…}).
 */
@Slf4j
final class ErlaubteAdressen {

    /**
     * Endungen, unter denen ein Fremder keine Adresse bekommt, die auf das Büronetz zeigt.
     * Reserviert: {@code .local} (mDNS), {@code .localhost}, {@code .home.arpa}, {@code .internal}.
     * Von der ICANN dauerhaft nicht vergeben: {@code .home}, {@code .corp}. Von Tailscale vergeben:
     * {@code .ts.net}. {@code fritz.box} gehört AVM; {@code .lan} und {@code .localdomain}
     * (Router, Linux) sind nicht reserviert, aber nicht vergeben – Restrisiko bewusst in Kauf
     * genommen, siehe docs/MOBILE_TOKEN_SECURITY.md.
     */
    static final List<String> LOKALE_ENDUNGEN = List.of(
            ".ts.net", ".fritz.box", ".local", ".localhost", ".home.arpa", ".internal",
            ".home", ".corp", ".lan", ".localdomain");

    /** Nach einem Fehlschlag fragt das ERP den Namen dieses Rechners erst so viel später wieder. */
    static final long NEUER_VERSUCH_NACH_MS = 5 * 60_000L;

    /** Im Log steht jede abgewiesene Adresse höchstens einmal je Stunde, höchstens so viele. */
    static final long MELDEFENSTER_MS = 60 * 60_000L;
    static final int MAX_MELDUNGEN_JE_FENSTER = 100;

    /**
     * Zeichen, die {@link IDN} (IDNA2003) anders umwandelt als der Browser: Aus {@code straße} würde
     * {@code strasse} – eine fremde Domain. Solche Einträge nimmt das ERP nur in der xn--Form an.
     */
    private static final String ABWEICHENDE_ZEICHEN = "\u00DF\u03C2\u200C\u200D";

    private final boolean alleErlaubt;
    private final Set<String> namen;
    private final List<String> eigeneEndungen;
    private final Supplier<Set<String>> rechnernamenQuelle;
    private final LongSupplier uhr;
    private final Set<String> gemeldet = ConcurrentHashMap.newKeySet();
    private volatile long meldefensterEnde;
    private volatile Set<String> rechnernamen;
    private volatile long naechsterVersuch;

    /**
     * @param konfiguriert       kommagetrennte Einträge aus {@code app.security.allowed-hosts}
     * @param baseUrl            {@code zeiterfassung.base-url}, die Adresse für QR-Codes und Links
     * @param rechnernamenQuelle Name dieses Rechners, normalerweise {@link #eigeneRechnernamen()}
     */
    ErlaubteAdressen(String konfiguriert, String baseUrl, Supplier<Set<String>> rechnernamenQuelle) {
        this(konfiguriert, baseUrl, rechnernamenQuelle, System::currentTimeMillis);
    }

    ErlaubteAdressen(String konfiguriert, String baseUrl, Supplier<Set<String>> rechnernamenQuelle, LongSupplier uhr) {
        boolean alle = false;
        Set<String> exakt = new HashSet<>();
        List<String> endungen = new ArrayList<>();
        for (String eintrag : eintraege(konfiguriert)) {
            if (eintrag.equals("*")) {
                alle = true;
            } else if (eintrag.startsWith("*.")) {
                String endung = lesbarerName(eintrag.substring(2), "app.security.allowed-hosts");
                if (endung.isEmpty()) {
                    continue;
                }
                if (endung.indexOf('.') < 0) {
                    log.warn("app.security.allowed-hosts: \"{}\" ignoriert – nach \"*.\" gehört eine eigene Domain wie *.firma.de, "
                            + "keine ganze Endung.", fuerLog(eintrag));
                } else {
                    endungen.add("." + endung);
                }
            } else {
                String name = lesbarerName(eintrag, "app.security.allowed-hosts");
                if (!name.isEmpty()) {
                    exakt.add(name);
                }
            }
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            String name = lesbarerName(baseUrl, "zeiterfassung.base-url");
            if (!name.isEmpty()) {
                exakt.add(name);
            }
        }
        this.alleErlaubt = alle;
        this.namen = Set.copyOf(exakt);
        this.eigeneEndungen = List.copyOf(endungen);
        this.rechnernamenQuelle = rechnernamenQuelle;
        this.uhr = uhr;
    }

    /** {@code host} wie von {@code HttpServletRequest#getServerName()}: ohne Port, IPv6 in Klammern. */
    boolean erlaubt(String host) {
        String name = normalisiere(host);
        if (alleErlaubt || name.isEmpty() || istIpAdresse(name)) {
            return true;
        }
        if (name.equals("localhost") || name.indexOf('.') < 0 || name.equals("fritz.box")) {
            return true;
        }
        if (LOKALE_ENDUNGEN.stream().anyMatch(name::endsWith)) {
            return true;
        }
        if (namen.contains(name) || eigeneEndungen.stream().anyMatch(name::endsWith)) {
            return true;
        }
        return rechnernamen().contains(name);
    }

    /** Schreibt, wie der Administrator eine abgewiesene Adresse freigibt – je Adresse höchstens einmal im Meldefenster. */
    void meldeAbgewiesen(String host) {
        long jetzt = uhr.getAsLong();
        if (jetzt >= meldefensterEnde) {
            gemeldet.clear();
            meldefensterEnde = jetzt + MELDEFENSTER_MS;
        }
        String name = fuerLog(host);
        if (gemeldet.size() < MAX_MELDUNGEN_JE_FENSTER && gemeldet.add(name)) {
            log.warn("Anfrage an die nicht freigegebene Adresse \"{}\" abgewiesen (Schutz vor DNS-Rebinding). "
                    + "Ist das eine Adresse dieses ERP, sie in app.security.allowed-hosts ergänzen, mehrere kommagetrennt "
                    + "(Docker: APP_SECURITY_ALLOWEDHOSTS in app.env).", name);
        }
    }

    /**
     * Erst bei Bedarf: Der Rechnername kann eine DNS-Abfrage kosten, und die meisten Adressen
     * passen vorher. Ein Fehlschlag gilt nicht bis zum Neustart, sondern wird später wiederholt.
     */
    private Set<String> rechnernamen() {
        Set<String> bekannt = rechnernamen;
        if (bekannt != null) {
            return bekannt;
        }
        long jetzt = uhr.getAsLong();
        if (jetzt < naechsterVersuch) {
            return Set.of();
        }
        Set<String> ermittelt = rechnernamenQuelle.get().stream()
                .filter(Objects::nonNull)
                .map(ErlaubteAdressen::normalisiere)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (ermittelt.isEmpty()) {
            naechsterVersuch = jetzt + NEUER_VERSUCH_NACH_MS;
        } else {
            rechnernamen = ermittelt;
        }
        return ermittelt;
    }

    static Set<String> eigeneRechnernamen() {
        return rechnernamenVon(InetAddress::getLocalHost);
    }

    static Set<String> rechnernamenVon(Callable<InetAddress> rechner) {
        try {
            InetAddress adresse = rechner.call();
            return Set.copyOf(List.of(adresse.getHostName(), adresse.getCanonicalHostName()));
        } catch (Exception e) {
            log.debug("Name dieses Rechners nicht ermittelbar: {}", e.getMessage());
            return Set.of();
        }
    }

    private static List<String> eintraege(String konfiguriert) {
        if (konfiguriert == null) {
            return List.of();
        }
        return Arrays.stream(konfiguriert.split(","))
                .map(String::trim)
                .filter(eintrag -> !eintrag.isEmpty())
                .toList();
    }

    /** Normalisierter Name oder leer (mit Hinweis im Log), wenn der Eintrag keine sichere Adresse ist. */
    private static String lesbarerName(String eintrag, String einstellung) {
        String host = hostAus(eintrag);
        if (host.chars().anyMatch(c -> ABWEICHENDE_ZEICHEN.indexOf(c) >= 0)) {
            log.warn("{}: \"{}\" ignoriert – mit ß, ς oder unsichtbaren Verbindern bitte die xn--Form eintragen, "
                    + "die im Log bei der abgewiesenen Adresse steht.", einstellung, fuerLog(eintrag));
            return "";
        }
        String name = normalisiere(host);
        if (name.isEmpty() || !name.chars().allMatch(ErlaubteAdressen::erlaubtesZeichen)) {
            log.warn("{}: \"{}\" ist keine lesbare Adresse und wird ignoriert.", einstellung, fuerLog(eintrag));
            return "";
        }
        return name;
    }

    private static boolean erlaubtesZeichen(int c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == '_' || c == ':';
    }

    /**
     * Akzeptiert auch {@code https://erp.firma.de:8443/} – Hauptsache, der Name stimmt. Bewusst
     * ohne {@code java.net.URI}: Die kennt weder Umlaute noch Unterstriche im Namen.
     */
    static String hostAus(String eintrag) {
        if (eintrag == null) {
            return "";
        }
        String wert = eintrag.trim();
        int schema = wert.indexOf("://");
        if (schema >= 0) {
            wert = wert.substring(schema + 3);
        }
        for (char trenner : new char[]{'/', '?', '#'}) {
            int stelle = wert.indexOf(trenner);
            if (stelle >= 0) {
                wert = wert.substring(0, stelle);
            }
        }
        int zugang = wert.lastIndexOf('@');
        if (zugang >= 0) {
            wert = wert.substring(zugang + 1);
        }
        return ohnePort(wert);
    }

    /** {@code [fd00::7]:8080} → {@code [fd00::7]}, {@code erp.firma.de:8443} → {@code erp.firma.de}; IPv6 ohne Klammern bleibt ganz. */
    static String ohnePort(String hostUndPort) {
        if (hostUndPort.startsWith("[")) {
            int ende = hostUndPort.indexOf(']');
            return ende > 0 ? hostUndPort.substring(0, ende + 1) : hostUndPort;
        }
        int doppelpunkt = hostUndPort.indexOf(':');
        return doppelpunkt >= 0 && doppelpunkt == hostUndPort.lastIndexOf(':')
                ? hostUndPort.substring(0, doppelpunkt) : hostUndPort;
    }

    /** Klein, ohne IPv6-Klammern und abschließenden Punkt, Umlaute wie im Browser ({@code xn--…}). */
    static String normalisiere(String host) {
        String name = ohneZusaetze(host);
        if (name.chars().anyMatch(c -> c > 127)) {
            try {
                name = IDN.toASCII(name, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException e) {
                // Bleibt, wie er ist: passt dann auf keine Adresse.
            }
        }
        return name;
    }

    private static String ohneZusaetze(String host) {
        if (host == null) {
            return "";
        }
        String name = host.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("[") && name.endsWith("]")) {
            name = name.substring(1, name.length() - 1);
        }
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        return name;
    }

    /** Ohne DNS-Abfrage: IPv6 erkennt man am Doppelpunkt, IPv4 an vier Zahlen bis 255. */
    static boolean istIpAdresse(String name) {
        if (name.indexOf(':') >= 0) {
            return true;
        }
        String[] teile = name.split("\\.", -1);
        if (teile.length != 4) {
            return false;
        }
        for (String teil : teile) {
            if (teil.isEmpty() || teil.length() > 3 || !teil.chars().allMatch(c -> c >= '0' && c <= '9')
                    || Integer.parseInt(teil) > 255) {
                return false;
            }
        }
        return true;
    }

    /** Nur Zeichen, die in einem Host-Namen vorkommen, und nicht beliebig lang. Ohne IDN-Umwandlung: So steht da, was eingetragen ist. */
    static String fuerLog(String host) {
        String name = ohneZusaetze(host).replaceAll("[^a-z0-9.:_-]", "?");
        return name.length() > 100 ? name.substring(0, 100) + "…" : name;
    }
}
