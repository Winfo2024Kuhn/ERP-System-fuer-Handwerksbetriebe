package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.service.telefon.AnlagenAnruf;
import org.example.kalkulationsprogramm.service.telefon.AnlagenInfo;
import org.example.kalkulationsprogramm.service.telefon.AnlagenSprachnachricht;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spricht über echtes HTTP mit einer simulierten FRITZ!Box (JDK-HttpServer):
 * Digest-Anmeldung, SOAP-Aktionen, Listen-Downloads und Audiodateien.
 */
class FritzBoxTelefonAnlageIntegrationTest {

    private static final String BENUTZER = "erp";
    private static final String PASSWORT = "geheim-test";
    private static final String REALM = "F!Box SOAP-Auth";
    private static final String NONCE = "ABCDEF0123456789";

    private HttpServer server;
    private FritzBoxTelefonAnlage anlage;
    private TelefonZugang zugang;
    private final List<String> aufgerufenePfade = new CopyOnWriteArrayList<>();
    private volatile boolean tamVerboten;
    private volatile boolean waehlhilfeAus;
    private volatile boolean waehlenVerboten;
    private volatile List<String> telefone = List.of("FON1: Werkstatt", "DECT: Mobilteil Büro", "LAN: PC Büro");
    private volatile long umstellenVerzoegerungMs;
    /** 0 = normale Box; sonst meldet GetPhonePort schon bei Index 1 diesen SOAP-Fehler. */
    private volatile int telefonlisteFehler;
    /** Wählhilfe-Aufrufe in Ankunftsreihenfolge, z.B. "SET:LAN: PC Büro", "DIAL:09311234567". */
    private final List<String> waehlhilfe = new CopyOnWriteArrayList<>();

    @BeforeEach
    void starteBox() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::behandle);
        server.start();
        int port = server.getAddress().getPort();
        anlage = new FritzBoxTelefonAnlage(new Tr064Client(port, Duration.ofSeconds(2), Duration.ofSeconds(5)));
        zugang = new TelefonZugang("127.0.0.1", BENUTZER, PASSWORT);
    }

    @AfterEach
    void stoppeBox() {
        server.stop(0);
    }

    @Test
    @DisplayName("Verbindungstest liefert eigene Nummern, Anrufbeantworter und Vorwahlen")
    void verbindungstest() {
        AnlagenInfo info = anlage.pruefeVerbindung(zugang);

        assertThat(info.eigeneNummern()).containsExactly("2323", "555000");
        assertThat(info.anrufbeantworter()).extracting(AnlagenInfo.Anrufbeantworter::name).containsExactly("AB Nacht", "AB Tag");
        assertThat(info.landesvorwahl()).isEqualTo("49");
        assertThat(info.ortsvorwahl()).isEqualTo("931");
    }

    @Test
    @DisplayName("Anrufliste: URL der Box wird auf eigenen Host umgebogen, days wird angehängt")
    void anrufliste() {
        List<AnlagenAnruf> anrufe = anlage.ladeAnrufe(zugang, 5);

        assertThat(anrufe).hasSize(1);
        assertThat(anrufe.getFirst().art()).isEqualTo(TelefonAnrufArt.VERPASST);
        assertThat(aufgerufenePfade).contains("/calllist.lua?sid=0123abcd&days=5");
    }

    @Test
    @DisplayName("Sprachnachrichten und Audio mit Session-ID")
    void sprachnachrichtenUndAudio() {
        List<AnlagenSprachnachricht> liste = anlage.ladeSprachnachrichten(zugang, 1);

        assertThat(liste).hasSize(1);
        AnlagenSprachnachricht n = liste.getFirst();
        assertThat(n.zeitpunkt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 8, 14));
        assertThat(n.sitzung()).isEqualTo("0123abcd");
        assertThat(anlage.ladeAudio(zugang, n)).containsExactly(1, 2, 3, 4);
        assertThat(aufgerufenePfade).contains("/download.lua?path=/data/tam/rec/rec.1.000&sid=0123abcd");
    }

    @Test
    @DisplayName("Falsches Passwort → ANMELDUNG_FEHLGESCHLAGEN")
    void falschesPasswort() {
        TelefonZugang falsch = new TelefonZugang("127.0.0.1", BENUTZER, "falsch");
        assertThatThrownBy(() -> anlage.ladeAnrufe(falsch, 1))
                .isInstanceOf(TelefonAnlageException.class)
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.ANMELDUNG_FEHLGESCHLAGEN);
    }

    @Test
    @DisplayName("SOAP-Fehler 606 → KEINE_RECHTE")
    void keineRechte() {
        tamVerboten = true;
        assertThatThrownBy(() -> anlage.ladeSprachnachrichten(zugang, 0))
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.KEINE_RECHTE);
    }

    @Test
    @DisplayName("Box nicht erreichbar → NICHT_ERREICHBAR")
    void nichtErreichbar() {
        int port = server.getAddress().getPort();
        server.stop(0);
        FritzBoxTelefonAnlage weg = new FritzBoxTelefonAnlage(new Tr064Client(port, Duration.ofMillis(500), Duration.ofSeconds(1)));
        assertThatThrownBy(() -> weg.ladeAnrufe(zugang, 1))
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.NICHT_ERREICHBAR);
    }

    @Test
    @DisplayName("Audio nur über /download.lua – fremde Pfade werden nicht aufgerufen")
    void audioPfadGeprueft() {
        for (String pfad : List.of("/etc/passwd", "http://evil.test/download.lua?path=x", "/download.lua?path=x#frag", "")) {
            AnlagenSprachnachricht n = new AnlagenSprachnachricht(0, LocalDateTime.now(), "", "", pfad, "s");
            assertThatThrownBy(() -> anlage.ladeAudio(zugang, n)).isInstanceOf(TelefonAnlageException.class);
        }
        AnlagenSprachnachricht ohneSid = new AnlagenSprachnachricht(0, LocalDateTime.now(), "", "",
                "/download.lua?path=/data/tam/rec/rec.1.000", "");
        assertThat(anlage.ladeAudio(zugang, ohneSid)).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Pfad und Query aus Box-URLs; Host der Box-URL wird nie übernommen")
    void pfadUndQuery() {
        assertThat(Tr064Client.pfadUndQuery("https://fritz.box:49443/calllist.lua?sid=1")).isEqualTo("/calllist.lua?sid=1");
        assertThat(Tr064Client.pfadUndQuery("http://evil.test")).isEqualTo("/");
        assertThat(Tr064Client.pfadUndQuery("/a?b=c")).isEqualTo("/a?b=c");
        assertThatThrownBy(() -> Tr064Client.pfadUndQuery("//evil.test/x")).isInstanceOf(TelefonAnlageException.class);
        assertThatThrownBy(() -> Tr064Client.pfadUndQuery(" ")).isInstanceOf(TelefonAnlageException.class);
        assertThatThrownBy(() -> Tr064Client.pfadUndQuery("http://[kaputt")).isInstanceOf(TelefonAnlageException.class);
    }

    @Test
    @DisplayName("Relative Box-URLs ohne führenden Schrägstrich können Host und Zugangsdaten nicht umlenken")
    void pfadUndQueryOhneSchraegstrichWirdAbgelehnt() {
        for (String boese : List.of("x@evil.test/a", "@evil.test/a", "evil.test/a", "\\\\evil/a", "calllist.lua?sid=1")) {
            assertThatThrownBy(() -> Tr064Client.pfadUndQuery(boese))
                    .as(boese)
                    .isInstanceOf(TelefonAnlageException.class);
        }
    }

    @Test
    @DisplayName("Zieladresse zeigt immer auf eingestellten Host und Port")
    void zielAdresseBleibtAufDerBox() {
        Tr064Client client = new Tr064Client(49000, Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertThat(client.zielAdresse("fritz.box", "/calllist.lua?sid=1").toString())
                .isEqualTo("http://fritz.box:49000/calllist.lua?sid=1");
        for (String boese : List.of("x@evil.test/a", "//evil.test/a", "@evil.test/a", "")) {
            assertThatThrownBy(() -> client.zielAdresse("fritz.box", boese))
                    .as(boese)
                    .isInstanceOf(TelefonAnlageException.class);
        }
    }

    @Test
    @DisplayName("Session-ID: nur Buchstaben und Ziffern")
    void sitzungAus() {
        assertThat(FritzBoxTelefonAnlage.sitzungAus("https://x/tam.lua?sid=ab12&tamindex=0")).isEqualTo("ab12");
        assertThat(FritzBoxTelefonAnlage.sitzungAus("https://x/tam.lua?sid=ab12%26x")).isEqualTo("ab12");
        assertThat(FritzBoxTelefonAnlage.sitzungAus("https://x/tam.lua")).isEmpty();
    }

    // ---------------------------------------------------------------- Wählhilfe (Zurückrufen)

    @Test
    @DisplayName("Telefone: Namen aus GetPhonePort ab Index 1, bis die Box einen Fehler meldet")
    void telefone() {
        assertThat(anlage.ladeTelefone(zugang)).containsExactly("FON1: Werkstatt", "DECT: Mobilteil Büro", "LAN: PC Büro");
    }

    @Test
    @DisplayName("Telefone: leere Namen werden übersprungen, keine Telefone ergibt leere Liste")
    void telefoneLeer() {
        telefone = List.of("FON1: Werkstatt", " ", "LAN: PC Büro");
        assertThat(anlage.ladeTelefone(zugang)).containsExactly("FON1: Werkstatt", "LAN: PC Büro");
        telefone = List.of();
        assertThat(anlage.ladeTelefone(zugang)).isEmpty();
    }

    @Test
    @DisplayName("Telefone: fehlende Rechte werden nicht als leere Liste verschluckt")
    void telefoneOhneRechte() {
        waehlenVerboten = true;
        assertThatThrownBy(() -> anlage.ladeTelefone(zugang))
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.KEINE_RECHTE);
    }

    @Test
    @DisplayName("Anrufen: erst Wählhilfe auf das Telefon stellen, dann die Nummer wählen")
    void anrufen() {
        anlage.anrufen(zugang, "LAN: PC Büro", "09311234567");

        assertThat(waehlhilfe).containsExactly("SET:LAN: PC Büro", "DIAL:09311234567");
    }

    @Test
    @DisplayName("Anrufen: Sonderzeichen im Telefonnamen werden als XML maskiert übertragen")
    void anrufenMaskiert() {
        anlage.anrufen(zugang, "LAN: <Büro & Co>", "0931");

        assertThat(waehlhilfe).containsExactly("SET:LAN: <Büro & Co>", "DIAL:0931");
    }

    @Test
    @DisplayName("Anrufen bei ausgeschalteter Wählhilfe → verständlicher Hinweis WAEHLHILFE_AUS")
    void waehlhilfeAusgeschaltet() {
        waehlhilfeAus = true;
        assertThatThrownBy(() -> anlage.anrufen(zugang, "LAN: PC Büro", "09311234567"))
                .isInstanceOf(TelefonAnlageException.class)
                .hasMessageContaining("Wählhilfe")
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.WAEHLHILFE_AUS);
    }

    @Test
    @DisplayName("Anrufen ohne Recht (SOAP 606) → KEINE_RECHTE statt Wählhilfe-Hinweis")
    void anrufenOhneRechte() {
        waehlenVerboten = true;
        assertThatThrownBy(() -> anlage.anrufen(zugang, "LAN: PC Büro", "09311234567"))
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.KEINE_RECHTE);
    }

    @Test
    @DisplayName("Zwei gleichzeitige Rückrufe: Umstellen und Wählen laufen nie verschränkt")
    void gleichzeitigeAnrufe() throws Exception {
        umstellenVerzoegerungMs = 150;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> a = pool.submit(() -> {
                start.await();
                anlage.anrufen(zugang, "LAN: PC Büro", "0931111");
                return null;
            });
            Future<?> b = pool.submit(() -> {
                start.await();
                anlage.anrufen(zugang, "LAN: PC Chef", "0931222");
                return null;
            });
            start.countDown();
            a.get();
            b.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(waehlhilfe).hasSize(4);
        for (int i = 0; i < 4; i += 2) {
            String telefon = waehlhilfe.get(i);
            String nummer = waehlhilfe.get(i + 1);
            assertThat(telefon.equals("SET:LAN: PC Büro") ? nummer.equals("DIAL:0931111") : nummer.equals("DIAL:0931222"))
                    .as("Paar %s / %s", telefon, nummer)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Telefone: anderer SOAP-Fehler als \"Index gibt es nicht\" ergibt Fehler statt leerer Liste")
    void telefoneAndererFehler() {
        telefonlisteFehler = 501;
        assertThatThrownBy(() -> anlage.ladeTelefone(zugang))
                .isInstanceOf(TelefonAnlageException.class)
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.UNERWARTETE_ANTWORT);
    }

    @Test
    @DisplayName("Wählhilfe belegt: zweiter Anruf wartet nur kurz und meldet dann BESCHAEFTIGT")
    void waehlhilfeBelegt() throws Exception {
        int port = server.getAddress().getPort();
        FritzBoxTelefonAnlage kurzeSperre = new FritzBoxTelefonAnlage(
                new Tr064Client(port, Duration.ofSeconds(2), Duration.ofSeconds(5)), Duration.ofMillis(100));
        umstellenVerzoegerungMs = 1000;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> erster = pool.submit(() -> kurzeSperre.anrufen(zugang, "LAN: PC Büro", "0931111"));
            long frist = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (waehlhilfe.isEmpty()) {
                assertThat(System.nanoTime()).as("erster Anruf stellt die Wählhilfe um").isLessThan(frist);
                Thread.sleep(10);
            }
            assertThatThrownBy(() -> kurzeSperre.anrufen(zugang, "LAN: PC Chef", "0931222"))
                    .extracting(e -> ((TelefonAnlageException) e).getGrund())
                    .isEqualTo(Grund.BESCHAEFTIGT);
            erster.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(waehlhilfe).containsExactly("SET:LAN: PC Büro", "DIAL:0931111");
    }

    // ---------------------------------------------------------------- simulierte Box

    private void behandle(HttpExchange ex) throws IOException {
        String pfad = ex.getRequestURI().getRawPath()
                + (ex.getRequestURI().getRawQuery() != null ? "?" + ex.getRequestURI().getRawQuery() : "");
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null) {
            ex.getResponseHeaders().add("WWW-Authenticate",
                    "Digest realm=\"" + REALM + "\", nonce=\"" + NONCE + "\", algorithm=MD5, qop=\"auth\"");
            antworte(ex, 401, "");
            return;
        }
        if (!digestKorrekt(auth, ex.getRequestMethod(), pfad)) {
            antworte(ex, 401, "");
            return;
        }
        aufgerufenePfade.add(pfad);
        if ("POST".equals(ex.getRequestMethod())) {
            String soap = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String action = ex.getRequestHeaders().getFirst("SOAPACTION");
            antworteSoap(ex, action, soap);
            return;
        }
        if (pfad.startsWith("/calllist.lua")) {
            antworte(ex, 200, "<root><Call><Type>2</Type><Caller>09311234567</Caller><CalledNumber>2323</CalledNumber>"
                    + "<Date>29.09.26 11:20</Date><Duration>0:00</Duration></Call></root>");
        } else if (pfad.startsWith("/tamcalllist.lua")) {
            antworte(ex, 200, "<Root><Message><Tam>1</Tam><Called>2323</Called><Date>29.09.26 08:14</Date>"
                    + "<Number>09311234567</Number><Path>/download.lua?path=/data/tam/rec/rec.1.000</Path></Message></Root>");
        } else if (pfad.startsWith("/download.lua")) {
            ex.sendResponseHeaders(200, 4);
            ex.getResponseBody().write(new byte[]{1, 2, 3, 4});
            ex.close();
        } else {
            antworte(ex, 404, "");
        }
    }

    private void antworteSoap(HttpExchange ex, String action, String body) throws IOException {
        String a = action == null ? "" : action.replace("\"", "");
        String aktion = a.substring(a.indexOf('#') + 1);
        Map<String, String> werte;
        switch (aktion) {
            case "GetCallList" -> werte = Map.of("NewCallListURL", "https://fritz.box:49443/calllist.lua?sid=0123abcd");
            case "GetMessageList" -> {
                if (tamVerboten) {
                    antworte(ex, 500, "<s:Envelope><s:Body><s:Fault><detail><UPnPError><errorCode>606</errorCode>"
                            + "</UPnPError></detail></s:Fault></s:Body></s:Envelope>");
                    return;
                }
                assertThat(body).contains("<NewIndex>1</NewIndex>");
                werte = Map.of("NewURL", "https://fritz.box:49443/tamcalllist.lua?sid=0123abcd&tamindex=1");
            }
            case "GetList" -> werte = Map.of("NewTAMList", "<List><Item><Index>0</Index><Display>1</Display><Enable>1</Enable>"
                    + "<Name>AB Nacht</Name></Item><Item><Index>1</Index><Display>1</Display><Enable>1</Enable>"
                    + "<Name>AB Tag</Name></Item></List>");
            case "X_AVM-DE_GetNumbers" -> werte = Map.of("NewNumberList",
                    "<List><Item><Number>2323</Number></Item><Item><Number>555000</Number></Item></List>");
            case "X_AVM-DE_GetVoIPCommonCountryCode" -> werte = Map.of("NewX_AVM-DE_LKZ", "49", "NewX_AVM-DE_LKZPrefix", "00");
            case "X_AVM-DE_GetVoIPCommonAreaCode" -> werte = Map.of("NewX_AVM-DE_OKZ", "931", "NewX_AVM-DE_OKZPrefix", "0");
            case "X_AVM-DE_GetPhonePort" -> {
                if (waehlenVerboten) {
                    soapFehler(ex, 606);
                    return;
                }
                if (telefonlisteFehler != 0) {
                    soapFehler(ex, telefonlisteFehler);
                    return;
                }
                int index = Integer.parseInt(argument(body, "NewIndex"));
                if (index < 1 || index > telefone.size()) {
                    soapFehler(ex, 713);
                    return;
                }
                werte = Map.of("NewX_AVM-DE_PhoneName", telefone.get(index - 1));
            }
            case "X_AVM-DE_DialSetConfig" -> {
                if (waehlenVerboten) {
                    soapFehler(ex, 606);
                    return;
                }
                waehlhilfe.add("SET:" + unescape(argument(body, "NewX_AVM-DE_PhoneName")));
                pause(umstellenVerzoegerungMs);
                werte = Map.of();
            }
            case "X_AVM-DE_DialNumber" -> {
                if (waehlhilfeAus) {
                    soapFehler(ex, 501);
                    return;
                }
                waehlhilfe.add("DIAL:" + argument(body, "NewX_AVM-DE_PhoneNumber"));
                werte = Map.of();
            }
            default -> {
                antworte(ex, 500, "");
                return;
            }
        }
        StringBuilder inhalt = new StringBuilder();
        werte.forEach((k, v) -> inhalt.append('<').append(k).append('>').append(escape(v)).append("</").append(k).append('>'));
        antworte(ex, 200, "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"
                + "<u:" + aktion + "Response xmlns:u=\"urn:x\">" + inhalt + "</u:" + aktion + "Response></s:Body></s:Envelope>");
    }

    private static void soapFehler(HttpExchange ex, int code) throws IOException {
        antworte(ex, 500, "<s:Envelope><s:Body><s:Fault><detail><UPnPError><errorCode>" + code
                + "</errorCode></UPnPError></detail></s:Fault></s:Body></s:Envelope>");
    }

    private static String argument(String soap, String name) {
        Matcher m = Pattern.compile("<" + name + ">([^<]*+)</" + name + ">").matcher(soap);
        assertThat(m.find()).as("Argument %s fehlt in %s", name, soap).isTrue();
        return m.group(1);
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static void pause(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean digestKorrekt(String header, String methode, String uri) {
        Map<String, String> p = DigestAnmeldung.parameter(header);
        if (!BENUTZER.equals(p.get("username")) || !uri.equals(p.get("uri"))) {
            return false;
        }
        String ha1 = DigestAnmeldung.md5(BENUTZER + ":" + REALM + ":" + PASSWORT);
        String ha2 = DigestAnmeldung.md5(methode + ":" + uri);
        String erwartet = DigestAnmeldung.md5(ha1 + ":" + NONCE + ":" + p.get("nc") + ":" + p.get("cnonce") + ":auth:" + ha2);
        return erwartet.equals(p.get("response"));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void antworte(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            ex.getResponseBody().write(b);
        }
        ex.close();
    }
}
