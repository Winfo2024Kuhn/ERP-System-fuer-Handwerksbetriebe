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
 * aus dem gemeinsamen Ordner) als Einrichtungs-Datei für Windows und Mac sowie als
 * unterschriebenes Update.
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

    private static final int MAX_ADRESSE = 255;
    /** Rechnername bzw. Freigabe-Teil eines UNC-Pfads: keine Steuerzeichen, Anführungszeichen oder Leerraum am Rand. */
    private static final Pattern RECHNERNAME = Pattern.compile("[A-Za-z0-9._-]{1,253}+");
    private static final Pattern PFADTEIL = Pattern.compile("[^\\\\/:*?\"<>|\\p{Cntrl}]{1,255}+");

    /** Fertig unterschriebene Pakete – unterschrieben wird nur, wenn sich etwas geändert hat. */
    private record PaketSchluessel(Betriebssystem bs, String erpAdresse, String wurzel) {
    }

    private static final int MAX_ZWISCHENSPEICHER = 32;
    private static final Map<String, String> SKRIPTE = new ConcurrentHashMap<>();

    private final SystemSettingsService settings;
    private final LauncherSignatur signatur;
    private final ObjectMapper objectMapper;

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
                .replace("@@CONFIG_BASE64@@", base64(paket.configJson()));
        // cmd.exe braucht CRLF
        return bat.replace("\r\n", "\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    /** Zip mit der ausführbaren {@code .command}-Datei für den Mac. */
    public byte[] einrichtungMac(String adresse, String serverName) {
        String erp = pruefeErpAdresse(adresse, serverName);
        OpenFileLauncherPaketDto paket = paket(Betriebssystem.MAC, erp);
        Base64.Encoder zeilen = Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.US_ASCII));
        String command = ressource("einrichten-mac.command")
                .replace("@@LAUNCHER_BASE64@@", zeilen.encodeToString(paket.skript().getBytes(StandardCharsets.UTF_8)))
                .replace("@@CONFIG_BASE64@@", zeilen.encodeToString(paket.configJson().getBytes(StandardCharsets.UTF_8)));
        return new UnixZip()
                .datei("ERP-Dateien-oeffnen.command", command.getBytes(StandardCharsets.UTF_8), UnixZip.AUSFUEHRBAR)
                .fertig();
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
