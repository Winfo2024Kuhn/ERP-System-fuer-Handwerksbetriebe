package org.example.kalkulationsprogramm.service.openfile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.dto.OpenFileLauncherBefehlDto;
import org.example.kalkulationsprogramm.dto.OpenFileLauncherPaketDto;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Liefert den OpenFile Launcher (öffnet Excel-, HiCAD- und Tenado-Dateien aus dem ERP direkt
 * aus dem gemeinsamen Ordner) als Einrichtungs-Datei für Windows, als Terminal-Befehl für den Mac
 * sowie als unterschriebenes Update.
 *
 * <p>Die Einstellungen des Launchers (erlaubte Ordner, Ausweich-Rechnername für unterwegs,
 * Prüfschlüssel) entstehen hier aus der Netzwerk-Adresse des Datei-Ordners. Ändert sich die
 * Adresse oder das Skript, ändert sich die Version, und die PCs holen sich beim nächsten Öffnen
 * die neue Fassung – nur über https und nur mit gültiger Unterschrift ({@link LauncherSignatur}).</p>
 */
@Service
@RequiredArgsConstructor
public class OpenFileLauncherService {

    public enum Betriebssystem {
        WINDOWS("launcher.ps1"),
        MAC("launcher-mac.sh");

        private final String skriptDatei;

        Betriebssystem(String skriptDatei) {
            this.skriptDatei = skriptDatei;
        }

        static Betriebssystem aus(String wert) {
            if (wert != null) {
                for (Betriebssystem b : values()) {
                    if (b.name().equalsIgnoreCase(wert.strip())) {
                        return b;
                    }
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekanntes Betriebssystem.");
        }
    }

    static final String ORDNER = "openfile-launcher/";
    static final String OHNE_NETZWERK_ADRESSE =
            "Bitte zuerst die Netzwerk-Adresse des Datei-Ordners eintragen und speichern (z. B. \\\\server\\zeichnungen).";
    static final String FALSCHE_ADRESSE = "Die Adresse des ERP passt nicht zu dieser Anfrage.";
    static final String NUR_HTTPS_FUER_MAC =
            "Den Befehl für den Mac gibt es nur über die https-Adresse (z. B. die Tailscale-Adresse). Bitte das Programm darüber öffnen.";

    private static final int MAX_ADRESSE = 255;
    /** Rechnername bzw. Freigabe-Teil eines UNC-Pfads: keine Steuerzeichen, Anführungszeichen oder Leerraum am Rand. */
    private static final Pattern RECHNERNAME = Pattern.compile("[A-Za-z0-9._-]{1,253}+");
    private static final Pattern PFADTEIL = Pattern.compile("[^\\\\/:*?\"<>|\\p{Cntrl}]{1,255}+");
    /** Einmal-Link im Terminal-Befehl: https, Hostname/IPv4 oder IPv6 in Klammern, Port, Schlüssel. */
    private static final Pattern SICHERER_MAC_LINK = Pattern.compile(
            "https://(?:[a-z0-9.-]{1,253}+|\\[[0-9a-f:.]{2,45}+\\])(?::[0-9]{1,5}+)?/openfile-launcher/mac/[A-Za-z0-9_-]{43}+");

    /** Fertig unterschriebene Pakete – unterschrieben wird nur, wenn sich etwas geändert hat. */
    private record PaketSchluessel(Betriebssystem bs, String erpAdresse, String wurzel) {
    }

    private static final int MAX_ZWISCHENSPEICHER = 32;
    private static final Map<String, String> SKRIPTE = new ConcurrentHashMap<>();

    private final SystemSettingsService settings;
    private final LauncherSignatur signatur;
    private final ObjectMapper objectMapper;
    private final EinmalLinks einmalLinks;
    private final ContainerFreigabe containerFreigabe;

    private final Map<PaketSchluessel, OpenFileLauncherPaketDto> zwischenspeicher = new ConcurrentHashMap<>();
    /** Uhr für den fortlaufenden Stand; in Tests ersetzbar. */
    LongSupplier jetzt = System::currentTimeMillis;

    /** Die {@code .bat}-Datei für Windows: Doppelklick richtet den Launcher für den Benutzer ein. */
    public byte[] einrichtungWindows(String adresse, String serverName) {
        String erp = pruefeErpAdresse(adresse, serverName);
        OpenFileLauncherPaketDto paket = paket(Betriebssystem.WINDOWS, erp);
        String vorlage = ressource("einrichten-windows.bat");
        String bat = vorlage
                .replace("@@LAUNCHER_BASE64@@", base64(paket.skript()))
                .replace("@@CONFIG_BASE64@@", base64(paket.configJson()))
                .replace("@@ORDNER_ZUGANG@@", zugangFuer(wurzel()).map(z ->
                        "  # Zugang zum Datei-Ordner (Ordner-Freigabe des ERP): Windows meldet sich damit selbst an\n"
                        + "  cmdkey /add:" + z.rechner() + " /user:" + z.benutzer() + " /pass:" + z.passwort()
                        + " | Out-Null\n"
                        + "  Write-Host 'Zugang zum Datei-Ordner gespeichert.'\n").orElse(""));
        // cmd.exe braucht CRLF
        return bat.replace("\r\n", "\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Terminal-Befehl für den Mac. Eine heruntergeladene Einrichtungs-Datei sperrt macOS (keine
     * Apple-Unterschrift); was {@code curl} lädt, bekommt keine Quarantäne-Markierung. Der Befehl
     * holt das Skript über einen Einmal-Link ({@link EinmalLinks}) und führt es erst aus, wenn es
     * vollständig da ist ({@code bash -c "$(…)"} statt {@code curl | bash}).
     *
     * <p>Nur über https: Das Skript läuft ungesehen im Terminal. Über http könnte es im Netz
     * unterwegs ausgetauscht werden.</p>
     */
    public OpenFileLauncherBefehlDto einrichtungMacBefehl(String adresse, String serverName) {
        String erp = pruefeErpAdresse(adresse, serverName);
        if (!erp.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NUR_HTTPS_FUER_MAC);
        }
        String basis = erp + "/openfile-launcher/mac/";
        // Der Link steht in '…' in einem Shell-Befehl: ausdrücklich nur sichere Zeichen, auch wenn
        // pruefeErpAdresse später einmal mehr zulassen sollte. Geprüft wird vor dem Anlegen, damit
        // kein Link übrig bleibt, den niemand bekommt.
        if (!SICHERER_MAC_LINK.matcher(basis + "A".repeat(43)).matches()) {
            throw new IllegalStateException("Einmal-Link enthält unerwartete Zeichen");
        }
        String link = basis + einmalLinks.neu(einrichtungMacSkript(erp), URI.create(erp).getHost());
        return new OpenFileLauncherBefehlDto("/bin/bash -c \"$(curl -fSsL '" + link + "')\"",
                (int) EinmalLinks.GUELTIG.toMinutes());
    }

    /** Das Einrichtungs-Skript zum Einmal-Link – leer, wenn unbekannt, abgelaufen oder schon abgeholt. */
    public Optional<String> einrichtungMac(String schluessel, String serverName) {
        return einmalLinks.einloesen(schluessel, serverName);
    }

    String einrichtungMacSkript(String erp) {
        OpenFileLauncherPaketDto paket = paket(Betriebssystem.MAC, erp);
        Base64.Encoder zeilen = Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.US_ASCII));
        return ressource("einrichten-mac.command")
                .replace("@@LAUNCHER_BASE64@@", zeilen.encodeToString(paket.skript().getBytes(StandardCharsets.UTF_8)))
                .replace("@@CONFIG_BASE64@@", zeilen.encodeToString(paket.configJson().getBytes(StandardCharsets.UTF_8)))
                .replace("@@ORDNER_ZUGANG@@", zugangFuer(wurzel()).map(z ->
                        "# Zugang zum Datei-Ordner (Ordner-Freigabe des ERP) im Schluesselbund: Der Mac meldet sich\n"
                        + "# damit ohne Anmeldedialog selbst an (launcher.sh liest ihn ueber security).\n"
                        + "security add-internet-password -U -a '" + z.benutzer() + "' -s '" + z.rechner()
                        + "' -r 'smb ' -l '" + z.rechner() + "' -w '" + z.passwort() + "' \\\n"
                        + "  -T /usr/bin/security >/dev/null\n"
                        + "echo \"Zugang zum Datei-Ordner im Schluesselbund gespeichert.\"\n")
                        .orElse("echo \"Beim ersten Verbinden fragt der Mac nach Benutzer und Passwort des Ordners"
                                + " (im Schluesselbund sichern).\"\n"));
    }

    /**
     * Neue Fassung für einen Launcher, der {@code versionDesPcs} hat – leer, wenn er aktuell ist.
     */
    public Optional<OpenFileLauncherPaketDto> aktuellesPaket(String system, String versionDesPcs, String adresse,
            String serverName) {
        Betriebssystem bs = Betriebssystem.aus(system);
        String erp = pruefeErpAdresse(adresse, serverName);
        if (!erp.startsWith("https://")) {
            // Der Launcher fragt nur über https; über http gäbe es nichts zu prüfen.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Updates gibt es nur über https.");
        }
        if (!signatur.istEingerichtet()) {
            // Noch nie eine Einrichtung heruntergeladen: Es kann keinen Launcher geben, der fragt.
            // Den Schlüssel erzeugt erst der Admin-Download, nicht eine Anfrage ohne Anmeldung.
            return Optional.empty();
        }
        // Aktuelle PCs bekommen ihr "nichts Neues", ohne dass dafür unterschrieben wird
        String wurzel = wurzel();
        if (version(bs, wurzel, aliasHosts(erp, wurzel)).equals(versionDesPcs)) {
            return Optional.empty();
        }
        return Optional.of(paket(bs, erp));
    }

    /**
     * Zugang zur Ordner-Freigabe des Docker-Pakets – nur, wenn die eingestellte Netzwerk-Adresse
     * auch auf deren Rechner zeigt. Er steckt allein in der Einrichtung (die nur Admins
     * herunterladen bzw. per Einmal-Link bekommen), nie im Update, das jeder PC ohne Anmeldung holt.
     */
    private Optional<ContainerFreigabe.Zugang> zugangFuer(String wurzel) {
        String rechner = uncHost(wurzel);
        return containerFreigabe.zugang().filter(z -> z.rechner().equalsIgnoreCase(rechner));
    }

    private String wurzel() {
        return normalisiereUnc(settings.getDateiOrdnerNetworkUrl())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, OHNE_NETZWERK_ADRESSE));
    }

    /**
     * Ausweich-Rechnername für unterwegs: Über Tailscale kennt der PC den Büro-Namen nicht,
     * wohl aber den Namen des ERP.
     */
    private static List<String> aliasHosts(String erpAdresse, String wurzel) {
        String erpHost = URI.create(erpAdresse).getHost();
        String uncHost = uncHost(wurzel);
        // Liegt der Ordner schon im Tailscale-Netz (Ordner-Freigabe des Docker-Pakets), ist er
        // im Büro wie unterwegs unter demselben Namen erreichbar - ein Umweg führte ins Leere.
        if (uncHost.toLowerCase(Locale.ROOT).endsWith(".ts.net")) {
            return List.of();
        }
        if (erpHost != null && !erpHost.equalsIgnoreCase(uncHost) && istRechnername(erpHost)
                && !"localhost".equalsIgnoreCase(erpHost) && !erpHost.startsWith("127.")) {
            return List.of(erpHost);
        }
        return List.of();
    }

    private static String uncHost(String wurzel) {
        return wurzel.substring(2).split("\\\\", 2)[0];
    }

    OpenFileLauncherPaketDto paket(Betriebssystem bs, String erpAdresse) {
        String wurzel = wurzel();
        PaketSchluessel schluessel = new PaketSchluessel(bs, erpAdresse, wurzel);
        OpenFileLauncherPaketDto vorhanden = zwischenspeicher.get(schluessel);
        if (vorhanden != null) {
            return vorhanden;
        }
        if (zwischenspeicher.size() >= MAX_ZWISCHENSPEICHER) {
            zwischenspeicher.clear();
        }
        OpenFileLauncherPaketDto neu = baue(bs, erpAdresse, wurzel);
        OpenFileLauncherPaketDto gewonnen = zwischenspeicher.putIfAbsent(schluessel, neu);
        return gewonnen != null ? gewonnen : neu;
    }

    private OpenFileLauncherPaketDto baue(Betriebssystem bs, String erpAdresse, String wurzel) {
        String skript = ressource(bs.skriptDatei);
        List<String> aliasHosts = aliasHosts(erpAdresse, wurzel);
        Map<String, String> rootAliases = new LinkedHashMap<>();
        for (String alias : aliasHosts) {
            rootAliases.put(wurzel, "\\\\" + alias + wurzel.substring(2 + uncHost(wurzel).length()));
        }
        String pruefPem = signatur.oeffentlicherSchluesselPem();
        String version = version(bs, wurzel, aliasHosts);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("version", version);
        // Fortlaufend: Der Launcher nimmt nichts an, was älter ist als das, was er schon hat
        config.put("stand", jetzt.getAsLong());
        config.put("erpAdresse", erpAdresse);
        config.put("allowedRoots", List.of(wurzel));
        config.put("rootAliases", rootAliases);
        config.put("aliasHosts", aliasHosts);
        config.put("pruefSchluesselXml", signatur.oeffentlicherSchluesselXml());
        config.put("pruefSchluesselPem", pruefPem);
        String configJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        String unterschrift = signatur.unterschreibe(version + "\n" + configJson + "\n" + skript);
        return new OpenFileLauncherPaketDto(version, skript, configJson, unterschrift);
    }

    /**
     * Adresse des ERP aus Sicht des Browsers bzw. Launchers. Der Rechnername muss zu der Anfrage
     * passen, die gerade hier ankommt – ein untergeschobener Link kann so keine fremde
     * Update-Adresse in die Einrichtungs-Datei schreiben.
     */
    static String pruefeErpAdresse(String adresse, String serverName) {
        if (adresse == null || adresse.isBlank() || adresse.length() > MAX_ADRESSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, FALSCHE_ADRESSE);
        }
        URI uri;
        try {
            uri = new URI(adresse.strip());
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, FALSCHE_ADRESSE);
        }
        String schema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean nurUrsprung = (uri.getRawPath() == null || uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
        if (!("https".equals(schema) || "http".equals(schema)) || uri.getHost() == null || !nurUrsprung
                || serverName == null || !uri.getHost().equalsIgnoreCase(serverName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, FALSCHE_ADRESSE);
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return schema + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }

    /** {@code \\HOST\\Freigabe\Ordner\} → {@code \\HOST\Freigabe\Ordner}; leer, wenn kein gültiger UNC-Pfad. */
    static Optional<String> normalisiereUnc(String networkUrl) {
        if (networkUrl == null) {
            return Optional.empty();
        }
        String pfad = networkUrl.strip().replace('/', '\\');
        if (!pfad.startsWith("\\\\")) {
            return Optional.empty();
        }
        List<String> teile = new ArrayList<>();
        for (String teil : pfad.substring(2).split("\\\\")) {
            if (!teil.isEmpty()) {
                teile.add(teil);
            }
        }
        if (teile.size() < 2 || !istRechnername(teile.get(0))) {
            return Optional.empty();
        }
        for (String teil : teile.subList(1, teile.size())) {
            if (!PFADTEIL.matcher(teil).matches() || ".".equals(teil) || "..".equals(teil)
                    || !teil.equals(teil.strip())) {
                return Optional.empty();
            }
        }
        return Optional.of("\\\\" + String.join("\\", teile));
    }

    private static boolean istRechnername(String name) {
        return RECHNERNAME.matcher(name).matches();
    }

    private String version(Betriebssystem bs, String wurzel, List<String> aliasHosts) {
        String skript = ressource(bs.skriptDatei);
        String pruefXml = signatur.oeffentlicherSchluesselXml();
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String teil : List.of(bs.name(), skript, wurzel, String.join(",", aliasHosts), pruefXml)) {
                sha.update(teil.getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
            }
            return HexFormat.of().formatHex(sha.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static String ressource(String name) {
        return SKRIPTE.computeIfAbsent(name, OpenFileLauncherService::leseRessource);
    }

    private static String leseRessource(String name) {
        try (InputStream in = new ClassPathResource(ORDNER + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Launcher-Datei fehlt: " + name, e);
        }
    }
}
