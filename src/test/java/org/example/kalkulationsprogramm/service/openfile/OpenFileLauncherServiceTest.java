package org.example.kalkulationsprogramm.service.openfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.openfile.OpenFileLauncherService.Betriebssystem;
import org.example.kalkulationsprogramm.dto.OpenFileLauncherBefehlDto;
import org.example.kalkulationsprogramm.dto.OpenFileLauncherPaketDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenFileLauncherServiceTest {

    private static final String ERP = "https://erp.firma-mustermann.de";
    private static final String HOST = "erp.firma-mustermann.de";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Schlüssel einmal für alle Tests – RSA-3072 zu erzeugen dauert. */
    private static LauncherSignatur signatur;

    private SystemSettingsService settings;
    private ContainerFreigabe containerFreigabe;
    private OpenFileLauncherService service;

    @BeforeAll
    static void schluessel() {
        signatur = new LauncherSignatur(mock(SystemSettingsService.class));
        signatur.oeffentlicherSchluesselPem(); // Schlüssel wie nach einem Admin-Download
    }

    @BeforeEach
    void setUp() {
        settings = mock(SystemSettingsService.class);
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("\\\\BUERO_PC\\\\Zeichnungen\\");
        containerFreigabe = mock(ContainerFreigabe.class);
        service = new OpenFileLauncherService(settings, signatur, JSON, new EinmalLinks(), containerFreigabe);
    }

    private static boolean unterschriftGueltig(OpenFileLauncherPaketDto paket) throws Exception {
        JsonNode config = JSON.readTree(paket.configJson());
        String b64 = config.get("pruefSchluesselPem").asString()
                .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initVerify(key);
        s.update((paket.version() + "\n" + paket.configJson() + "\n" + paket.skript()).getBytes(StandardCharsets.UTF_8));
        return s.verify(Base64.getDecoder().decode(paket.signatur()));
    }

    // ---------- Adresse des ERP ----------

    @Test
    void erpAdresseWirdAufUrsprungGekuerzt() {
        assertThat(OpenFileLauncherService.pruefeErpAdresse("https://ERP.firma-mustermann.de/", HOST))
                .isEqualTo("https://erp.firma-mustermann.de");
        assertThat(OpenFileLauncherService.pruefeErpAdresse("http://buero-pc:8080", "BUERO-PC"))
                .isEqualTo("http://buero-pc:8080");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "https://boese.example.com",          // anderer Rechner als die Anfrage
            "https://erp.firma-mustermann.de/pfad",
            "https://erp.firma-mustermann.de/?x=1",
            "https://erp.firma-mustermann.de/#a",
            "https://max@erp.firma-mustermann.de",
            "ftp://erp.firma-mustermann.de",
            "javascript:alert(1)",
            "<script>alert(1)</script>",
            "'; DROP TABLE system_setting; --",
            "erp.firma-mustermann.de",
            "https://erp.firma mustermann.de"})
    void falscheErpAdresseWirdAbgelehnt(String adresse) {
        assertThatThrownBy(() -> OpenFileLauncherService.pruefeErpAdresse(adresse, HOST))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo(OpenFileLauncherService.FALSCHE_ADRESSE);
                });
    }

    @Test
    void ueberlangeAdresseUndFehlenderServerNameWerdenAbgelehnt() {
        assertThatThrownBy(() -> OpenFileLauncherService.pruefeErpAdresse("https://" + "a".repeat(10_000) + ".de", HOST))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> OpenFileLauncherService.pruefeErpAdresse(ERP, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ---------- Netzwerk-Adresse ----------

    @Test
    void uncPfadWirdBereinigt() {
        assertThat(OpenFileLauncherService.normalisiereUnc("\\\\BUERO_PC\\\\Zeichnungen\\")).contains("\\\\BUERO_PC\\Zeichnungen");
        assertThat(OpenFileLauncherService.normalisiereUnc(" //server/share/CAD Dateien ")).contains("\\\\server\\share\\CAD Dateien");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"C:\\Zeichnungen", "\\\\server", "\\\\server\\", "\\\\ser ver\\share",
            "\\\\server\\..\\geheim", "\\\\server\\share\\.", "\\\\server\\sha\"re", "\\\\server\\a:b",
            "\\\\server\\<script>", "\\\\server\\ share"})
    void ungueltigerUncPfadIstLeer(String pfad) {
        assertThat(OpenFileLauncherService.normalisiereUnc(pfad)).isEmpty();
    }

    // ---------- Paket ----------

    @Test
    void paketIstUnterschriebenUndEnthaeltAusweichNamenFuerUnterwegs() throws Exception {
        OpenFileLauncherPaketDto paket = service.paket(Betriebssystem.WINDOWS, ERP);

        assertThat(unterschriftGueltig(paket)).isTrue();
        assertThat(paket.skript()).startsWith("param(");
        assertThat(paket.version()).hasSize(16).matches("[0-9a-f]+");
        JsonNode config = JSON.readTree(paket.configJson());
        assertThat(config.get("version").asString()).isEqualTo(paket.version());
        assertThat(config.get("erpAdresse").asString()).isEqualTo(ERP);
        assertThat(config.get("allowedRoots").get(0).asString()).isEqualTo("\\\\BUERO_PC\\Zeichnungen");
        assertThat(config.get("rootAliases").get("\\\\BUERO_PC\\Zeichnungen").asString())
                .isEqualTo("\\\\erp.firma-mustermann.de\\Zeichnungen");
        assertThat(config.get("aliasHosts").get(0).asString()).isEqualTo(HOST);
        assertThat(config.get("pruefSchluesselXml").asString()).startsWith("<RSAKeyValue>");
    }

    @Test
    void keinAusweichNameWennErpUndFreigabeAufDemselbenRechner() throws Exception {
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("\\\\erp.firma-mustermann.de\\Zeichnungen");
        JsonNode config = JSON.readTree(service.paket(Betriebssystem.MAC, ERP).configJson());
        assertThat(config.get("aliasHosts")).isEmpty();
        assertThat(config.get("rootAliases")).isEmpty();

        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("\\\\BUERO_PC\\Zeichnungen");
        JsonNode lokal = JSON.readTree(service.paket(Betriebssystem.MAC, "http://localhost:8080").configJson());
        assertThat(lokal.get("aliasHosts")).isEmpty();
        JsonNode ip = JSON.readTree(service.paket(Betriebssystem.MAC, "http://127.0.0.1:8080").configJson());
        assertThat(ip.get("aliasHosts")).isEmpty();
    }

    @Test
    void keinAusweichNameWennDerOrdnerSchonImTailscaleNetzLiegt() throws Exception {
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("\\\\erp-dateien.tail1234.ts.net\\CADdrawings");
        JsonNode config = JSON.readTree(service.paket(Betriebssystem.WINDOWS, "https://buero-pc.tail1234.ts.net").configJson());

        assertThat(config.get("allowedRoots").get(0).asString()).isEqualTo("\\\\erp-dateien.tail1234.ts.net\\CADdrawings");
        assertThat(config.get("aliasHosts")).isEmpty();
        assertThat(config.get("rootAliases")).isEmpty();
    }

    @Test
    void versionAendertSichMitAdresseUndSystemNichtMitErpAdresse() {
        String windows = service.paket(Betriebssystem.WINDOWS, ERP).version();
        assertThat(service.paket(Betriebssystem.WINDOWS, ERP).version()).isEqualTo(windows);
        assertThat(service.paket(Betriebssystem.MAC, ERP).version()).isNotEqualTo(windows);

        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("\\\\BUERO_PC\\Neu");
        assertThat(service.paket(Betriebssystem.WINDOWS, ERP).version()).isNotEqualTo(windows);
    }

    @Test
    void ohneNetzwerkAdresseGibtEsKeinPaket() {
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("");
        assertThatThrownBy(() -> service.paket(Betriebssystem.WINDOWS, ERP))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getReason()).isEqualTo(OpenFileLauncherService.OHNE_NETZWERK_ADRESSE));
    }

    // ---------- Update-Abfrage ----------

    @Test
    void aktuellerPcBekommtNichtsVeralteterDasPaket() {
        String version = service.paket(Betriebssystem.MAC, ERP).version();

        assertThat(service.aktuellesPaket("mac", version, ERP, HOST)).isEmpty();
        Optional<OpenFileLauncherPaketDto> neu = service.aktuellesPaket("MAC", "alt", ERP, HOST);
        assertThat(neu).isPresent();
        assertThat(neu.get().skript()).startsWith("#!/bin/bash");
        assertThat(service.aktuellesPaket("windows", null, ERP, HOST)).isPresent();
    }

    @Test
    void updateNurUeberHttpsUndBekanntesSystem() {
        assertThatThrownBy(() -> service.aktuellesPaket("windows", "x", "http://erp.firma-mustermann.de", HOST))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.aktuellesPaket("linux", "x", ERP, HOST))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.aktuellesPaket(null, "x", ERP, HOST))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.aktuellesPaket("windows", "x", ERP, "boese.example.com"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void paketTraegtFortlaufendenStandUndWirdNurEinmalUnterschrieben() throws Exception {
        LauncherSignatur zaehlend = spy(signatur);
        OpenFileLauncherService svc = new OpenFileLauncherService(settings, zaehlend, JSON, new EinmalLinks(), containerFreigabe);
        svc.jetzt = () -> 1_760_000_000_000L;

        OpenFileLauncherPaketDto erstes = svc.paket(Betriebssystem.WINDOWS, ERP);
        assertThat(svc.paket(Betriebssystem.WINDOWS, ERP)).isSameAs(erstes);
        verify(zaehlend, times(1)).unterschreibe(anyString());
        assertThat(JSON.readTree(erstes.configJson()).get("stand").asLong()).isEqualTo(1_760_000_000_000L);

        // Neue Netzwerk-Adresse: neues Paket mit späterem Stand
        svc.jetzt = () -> 1_760_000_000_500L;
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("\\\\BUERO_PC\\Neu");
        OpenFileLauncherPaketDto zweites = svc.paket(Betriebssystem.WINDOWS, ERP);
        assertThat(zweites.version()).isNotEqualTo(erstes.version());
        assertThat(JSON.readTree(zweites.configJson()).get("stand").asLong()).isGreaterThan(1_760_000_000_000L);
        assertThat(unterschriftGueltig(zweites)).isTrue();
    }

    @Test
    void aktuellerPcKostetKeineUnterschrift() {
        String version = service.aktuellesPaket("windows", "alt", ERP, HOST).orElseThrow().version();
        LauncherSignatur zaehlend = spy(signatur);
        OpenFileLauncherService frisch = new OpenFileLauncherService(settings, zaehlend, JSON, new EinmalLinks(), containerFreigabe);

        assertThat(frisch.aktuellesPaket("windows", version, ERP, HOST)).isEmpty();
        verify(zaehlend, never()).unterschreibe(anyString());
    }

    @Test
    void zwischenspeicherBleibtBegrenzt() {
        for (int i = 0; i < 40; i++) {
            when(settings.getDateiOrdnerNetworkUrl()).thenReturn("\\\\BUERO_PC\\Ordner" + i);
            assertThat(service.paket(Betriebssystem.MAC, ERP).configJson()).contains("Ordner" + i);
        }
    }

    @Test
    void updateAbfrageErzeugtKeinenSchluessel() {
        LauncherSignatur ohneSchluessel = mock(LauncherSignatur.class);
        OpenFileLauncherService svc = new OpenFileLauncherService(settings, ohneSchluessel, JSON, new EinmalLinks(), containerFreigabe);

        assertThat(svc.aktuellesPaket("windows", "alt", ERP, HOST)).isEmpty();
        verify(ohneSchluessel).istEingerichtet();
        verifyNoMoreInteractions(ohneSchluessel);
    }

    @Test
    void umlauteImOrdnernamenKommenAlsUtf8AufDenPc() throws Exception {
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("\\\\BUERO_PC\\Aufträge");
        String bat = new String(service.einrichtungWindows(ERP, HOST), StandardCharsets.UTF_8);
        Matcher config = Pattern.compile("\\$configB64 = '([A-Za-z0-9+/=]+)'").matcher(bat);
        assertThat(config.find()).isTrue();
        String json = new String(Base64.getDecoder().decode(config.group(1)), StandardCharsets.UTF_8);
        assertThat(JSON.readTree(json).get("allowedRoots").get(0).asString()).isEqualTo("\\\\BUERO_PC\\Aufträge");
        // ... und der Windows-Launcher liest config.json als UTF-8, nicht als ANSI
        assertThat(OpenFileLauncherService.ressource("launcher.ps1"))
                .contains("[IO.File]::ReadAllText($configFile)")
                .doesNotContain("Get-Content $configFile");
    }

    @Test
    void launcherHaltenDieSicherheitsregelnEin() {
        String windows = OpenFileLauncherService.ressource("launcher.ps1");
        String mac = OpenFileLauncherService.ressource("launcher-mac.sh");
        // Pfadgrenze: \\server\Zeichnungen erlaubt nicht \\server\Zeichnungen-Lohn
        assertThat(windows).contains("StartsWith(($rn + '\\').ToLower())");
        assertThat(mac).contains("\"$(klein \"$wurzel\")\"/*");
        // Nur Tabellen und Zeichnungen, keine Programme
        assertThat(windows).contains("$ErlaubteEndungen -notcontains $ext");
        assertThat(mac).contains("*.xlsx|*.xls|*.xlsm|*.xlsb|*.xlt|*.xltx|*.xltm|*.csv|*.ods|*.sza|*.tcd) ;;");
        // Update: nur https, Unterschrift, gleiche ERP-Adresse, nur neuerer Stand
        assertThat(windows).contains("StartsWith('https://')", "Test-Unterschrift $paket",
                "-ne $ErpAdresse", "-le $bisherStand");
        assertThat(mac).contains("--proto '=https'", "openssl dgst -sha256 -verify",
                "!= \"$erp\"", "-le \"$bisher\"");
    }

    @Test
    void launcherNennenDieUrsacheWennDerOrdnerNichtErreichbarIst() {
        String windows = OpenFileLauncherService.ressource("launcher.ps1");
        String mac = OpenFileLauncherService.ressource("launcher-mac.sh");
        String freigabeFehlt = "Das ERP ist erreichbar, der gemeinsame Ordner aber nicht. Wahrscheinlich ist der Ordner"
                + " fuer unterwegs (Tailscale) noch nicht freigegeben. Das richtet der Administrator einmal ein"
                + " (Tailscale-Regeln bzw. Freigabe-Tailscale.ps1 am Rechner mit dem Ordner).";
        String nichtErreichbar = "Der gemeinsame Ordner ist gerade nicht erreichbar. Unterwegs bitte Tailscale"
                + " einschalten - sonst ist der Rechner mit dem Ordner vielleicht aus.";
        // Beide Systeme sagen dasselbe, abhängig davon, ob das ERP selbst antwortet
        assertThat(windows).contains(freigabeFehlt, nichtErreichbar, "if (Test-ErpErreichbar) {",
                "Zeige-Meldung $MeldungFreigabeFehlt", "Zeige-Meldung $MeldungNichtErreichbar");
        assertThat(mac).contains(freigabeFehlt, nichtErreichbar, "if erp_erreichbar; then",
                "meldung \"$MELDUNG_FREIGABE_FEHLT\"", "meldung \"$MELDUNG_NICHT_ERREICHBAR\"");
        assertThat(mac).doesNotContain("Unterwegs bitte Tailscale einschalten.\"");
    }

    // ---------- Einrichtungs-Dateien ----------

    @Test
    void windowsEinrichtungIstBatMitCrlfUndEingesetztemSkript() throws Exception {
        String bat = new String(service.einrichtungWindows(ERP, HOST), StandardCharsets.UTF_8);

        assertThat(bat).startsWith("@echo off\r\n").doesNotContain("@@").doesNotContainPattern("[^\r]\n");
        // Kann das Ordner-Passwort enthalten - loescht sich nach der Einrichtung selbst
        assertThat(bat).contains("pause\r\nrem Selbst loeschen: (goto) beendet die Stapeldatei, del laeuft danach noch\r\n"
                + "(goto) 2>nul & del \"%~f0\"\r\n");
        assertThat(bat).matches("\\p{ASCII}*");
        Matcher launcher = Pattern.compile("\\$launcherB64 = '([A-Za-z0-9+/=]+)'").matcher(bat);
        Matcher config = Pattern.compile("\\$configB64 = '([A-Za-z0-9+/=]+)'").matcher(bat);
        assertThat(launcher.find()).isTrue();
        assertThat(config.find()).isTrue();
        String skript = new String(Base64.getDecoder().decode(launcher.group(1)), StandardCharsets.UTF_8);
        assertThat(skript).isEqualTo(OpenFileLauncherService.ressource("launcher.ps1"));
        JsonNode cfg = JSON.readTree(Base64.getDecoder().decode(config.group(1)));
        assertThat(cfg.get("erpAdresse").asString()).isEqualTo(ERP);
    }

    // ---------- Zugang zur Ordner-Freigabe des Docker-Pakets ----------

    private static final String FREIGABE = "\\\\erp-dateien.tail1234.ts.net\\CADdrawings";
    private static final String DUMMY_PASSWORT = "MusterPasswort2345abcdefgh";

    private void freigabeEingeschaltet(String eingestellteAdresse) {
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn(eingestellteAdresse);
        when(containerFreigabe.zugang()).thenReturn(Optional.of(new ContainerFreigabe.Zugang(
                FREIGABE, "erp-dateien.tail1234.ts.net", "erp-dateien", DUMMY_PASSWORT)));
    }

    @Test
    void einrichtungBringtDenZugangZurFreigabeMitAberNieDasUpdate() {
        freigabeEingeschaltet(FREIGABE);

        String bat = new String(service.einrichtungWindows(ERP, HOST), StandardCharsets.UTF_8);
        assertThat(bat).contains("  cmdkey /add:erp-dateien.tail1234.ts.net /user:erp-dateien /pass:" + DUMMY_PASSWORT
                + " | Out-Null\r\n").doesNotContain("@@").doesNotContainPattern("[^\r]\n");

        String mac = service.einrichtungMacSkript(ERP);
        assertThat(mac).contains("security add-internet-password -U -a 'erp-dateien' -s 'erp-dateien.tail1234.ts.net'"
                + " -r 'smb ' -l 'erp-dateien.tail1234.ts.net' -w '" + DUMMY_PASSWORT + "' \\\n"
                + "  -T /usr/bin/security >/dev/null\n")
                .doesNotContain("@@").doesNotContain("fragt der Mac nach Benutzer und Passwort");

        // Das Update holt jeder PC ohne Anmeldung - darin darf das Passwort nie stehen
        for (Betriebssystem bs : Betriebssystem.values()) {
            OpenFileLauncherPaketDto paket = service.paket(bs, ERP);
            assertThat(paket.configJson() + paket.skript()).doesNotContain(DUMMY_PASSWORT);
            assertThat(service.aktuellesPaket(bs.name(), "alt", ERP, HOST).orElseThrow().configJson())
                    .doesNotContain(DUMMY_PASSWORT);
        }
    }

    @Test
    void keinZugangWennDieEingestellteAdresseAufEinenAnderenRechnerZeigt() {
        freigabeEingeschaltet("\\\\BUERO_PC\\CADdrawings");

        String bat = new String(service.einrichtungWindows(ERP, HOST), StandardCharsets.UTF_8);
        assertThat(bat).doesNotContain("cmdkey", DUMMY_PASSWORT, "@@");
        String mac = service.einrichtungMacSkript(ERP);
        assertThat(mac).doesNotContain("security add-internet-password", DUMMY_PASSWORT, "@@")
                .contains("echo \"Beim ersten Verbinden fragt der Mac nach Benutzer und Passwort des Ordners");
    }

    @Test
    void ohneFreigabeBleibtDieEinrichtungWieBisher() {
        String bat = new String(service.einrichtungWindows(ERP, HOST), StandardCharsets.UTF_8);
        assertThat(bat).doesNotContain("cmdkey", "@@");
        assertThat(service.einrichtungMacSkript(ERP)).doesNotContain("security add-internet-password", "@@");
    }

    @Test
    void launcherHolenBeiUnbekanntemOrdnerSofortDieNeueFassung() {
        String windows = OpenFileLauncherService.ressource("launcher.ps1");
        String mac = OpenFileLauncherService.ressource("launcher-mac.sh");
        // Nach dem Umstellen der Adresse im ERP scheitert der erste Klick nicht mehr
        assertThat(windows).contains("if (-not $match -and $ErpAdresse) {", "Update-Launcher\n    try {",
                "$match = Get-MatchingRoot $unc $AllowedRoots\n  }");
        assertThat(mac).contains("  aktualisieren\n  if im_erlaubten_ordner; then erlaubt=1; fi");
    }

    @Test
    void macLauncherVerbindetMitDemZugangAusDerEinrichtungOhneDialog() {
        String mac = OpenFileLauncherService.ressource("launcher-mac.sh");
        // Zugang aus dem Schluesselbund, Werte streng geprueft, Passwort nur ueber stdin
        assertThat(mac).contains("if mit_zugang_verbinden \"$k\" \"$freigabe_url\"; then",
                "case \"$1\" in ''|*[!A-Za-z0-9._-]*) return 1 ;; esac",
                "case \"$pw\" in ''|*[!A-Za-z0-9]*) return 1 ;; esac",
                "| osascript >/dev/null");
        // Ohne gespeicherten Zugang wie bisher ueber den Finder
        assertThat(mac).contains("open \"smb://$k/$freigabe_url\"");
    }

    private static final Pattern MAC_BEFEHL = Pattern.compile(
            "/bin/bash -c \"\\$\\(curl -fSsL '(https://erp\\.firma-mustermann\\.de/openfile-launcher/mac/([A-Za-z0-9_-]{43}))'\\)\"");

    @Test
    void macBefehlHoltDasSkriptUeberEinenEinmalLink() {
        OpenFileLauncherBefehlDto befehl = service.einrichtungMacBefehl(ERP + "/", HOST);

        assertThat(befehl.gueltigMinuten()).isEqualTo(10);
        Matcher teile = MAC_BEFEHL.matcher(befehl.befehl());
        assertThat(teile.matches()).as(befehl.befehl()).isTrue();

        String command = service.einrichtungMac(teile.group(2), HOST).orElseThrow();
        assertThat(command).startsWith("#!/bin/bash\n").doesNotContain("@@").doesNotContain("\r");
        Matcher launcher = Pattern.compile("<<'LAUNCHER'\n([A-Za-z0-9+/=\n]+)\nLAUNCHER").matcher(command);
        assertThat(launcher.find()).isTrue();
        String skript = new String(Base64.getMimeDecoder().decode(launcher.group(1)), StandardCharsets.UTF_8);
        assertThat(skript).isEqualTo(OpenFileLauncherService.ressource("launcher-mac.sh"));
        Matcher config = Pattern.compile("<<'CONFIG'\n([A-Za-z0-9+/=\n]+)\nCONFIG").matcher(command);
        assertThat(config.find()).isTrue();
        JsonNode cfg = JSON.readTree(Base64.getMimeDecoder().decode(config.group(1)));
        assertThat(cfg.get("erpAdresse").asString()).isEqualTo(ERP);

        // Nur einmal einlösbar
        assertThat(service.einrichtungMac(teile.group(2), HOST)).isEmpty();
    }

    @Test
    void jederMacBefehlHatEinenEigenenLink() {
        String erster = service.einrichtungMacBefehl(ERP, HOST).befehl();
        String zweiter = service.einrichtungMacBefehl(ERP, HOST).befehl();

        assertThat(erster).isNotEqualTo(zweiter);
    }

    @Test
    void macBefehlOhneNetzwerkAdresseIst400() {
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("");

        assertThatThrownBy(() -> service.einrichtungMacBefehl(ERP, HOST))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo(OpenFileLauncherService.OHNE_NETZWERK_ADRESSE);
                });
    }

    @Test
    void macBefehlNurUeberHttps() {
        assertThatThrownBy(() -> service.einrichtungMacBefehl("http://buero-pc:8080", "BUERO-PC"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo(OpenFileLauncherService.NUR_HTTPS_FUER_MAC);
                });
    }

    @Test
    void macBefehlMitPortUndIpv4() {
        assertThat(service.einrichtungMacBefehl("https://192.168.0.10:8443", "192.168.0.10").befehl())
                .matches("/bin/bash -c \"\\$\\(curl -fSsL 'https://192\\.168\\.0\\.10:8443/openfile-launcher/mac/[A-Za-z0-9_-]{43}'\\)\"");
    }

    @Test
    void einrichtungPrueftDieErpAdresse() {
        assertThatThrownBy(() -> service.einrichtungWindows("https://boese.example.com", HOST))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.einrichtungMacBefehl("https://boese.example.com", HOST))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.einrichtungMacBefehl("https://erp.firma-mustermann.de/'; rm -rf ~ #", HOST))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void launcherSkripteSindReinesAscii() {
        // Windows PowerShell 5 liest UTF-8 ohne BOM als ANSI
        assertThat(OpenFileLauncherService.ressource("launcher.ps1")).matches("\\p{ASCII}*");
        assertThat(OpenFileLauncherService.ressource("einrichten-windows.bat")).matches("\\p{ASCII}*");
    }

    @Test
    void deploymentKopieEntsprichtDerKanonischenFassung() throws IOException {
        // Die alte Admin-Installation (deployment/openfile-launcher) nutzt dasselbe Skript
        assertThat(Files.readString(Path.of("deployment", "openfile-launcher", "launcher.ps1")))
                .isEqualTo(OpenFileLauncherService.ressource("launcher.ps1"));
    }

    @Test
    void fehlendeRessourceIstEinKlarerFehler() {
        assertThatThrownBy(() -> OpenFileLauncherService.ressource("gibt-es-nicht.ps1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
