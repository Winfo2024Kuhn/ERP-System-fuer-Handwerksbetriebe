package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchAnlage.Telefonbuch;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchEintrag;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchEintrag.NummerArt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Telefonbuch-Zugriff über echtes HTTP gegen eine simulierte FRITZ!Box
 * (JDK-HttpServer, Digest-Anmeldung, SOAP wie TR-064 X_AVM-DE_OnTel).
 * Nur Dummy-Daten.
 */
class FritzBoxTelefonbuchIntegrationTest {

    private static final String BENUTZER = "erp";
    private static final String PASSWORT = "geheim-test";
    private static final String REALM = "F!Box SOAP-Auth";
    private static final String NONCE = "ABCDEF0123456789";

    /** Telefonbuch-ID → Name; ID → (uniqueid → Eintrags-XML, wie es angekommen ist). */
    private final Map<String, String> buecher = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> eintraege = new ConcurrentHashMap<>();
    private final AtomicInteger naechsteUid = new AtomicInteger(100);
    private final List<String> aktionen = new CopyOnWriteArrayList<>();
    private volatile int schreibFehler;
    private volatile int loeschFehler;

    private HttpServer server;
    private FritzBoxTelefonbuch telefonbuch;
    private TelefonZugang zugang;

    @BeforeEach
    void starteBox() throws IOException {
        buecher.put("0", "Telefonbuch");
        eintraege.put("0", new ConcurrentHashMap<>(Map.of("1", "<contact><uniqueid>1</uniqueid></contact>")));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::behandle);
        server.start();
        telefonbuch = new FritzBoxTelefonbuch(new Tr064Client(server.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(5)));
        zugang = new TelefonZugang("127.0.0.1", BENUTZER, PASSWORT);
    }

    @AfterEach
    void stoppeBox() {
        server.stop(0);
    }

    private static TelefonbuchEintrag eintrag(String name) {
        return new TelefonbuchEintrag(name, List.of(new TelefonbuchEintrag.Nummer("0123456789", NummerArt.GESCHAEFTLICH),
                new TelefonbuchEintrag.Nummer("01701234567", NummerArt.MOBIL)));
    }

    @Test
    @DisplayName("Fehlt das Telefonbuch, wird es angelegt – das bestehende bleibt unberührt")
    void legtTelefonbuchAn() {
        Telefonbuch buch = telefonbuch.stelleTelefonbuchBereit(zugang, "ERP-Kontakte", null);

        assertThat(buch.neu()).isTrue();
        assertThat(buecher).containsEntry(buch.id(), "ERP-Kontakte").containsEntry("0", "Telefonbuch");
        assertThat(eintraege.get("0")).containsOnlyKeys("1");
        assertThat(aktionen).contains("AddPhonebook");
    }

    @Test
    @DisplayName("Bekanntes Telefonbuch unter seiner ID: nichts anlegen")
    void bekanntesTelefonbuch() {
        buecher.put("2", "ERP-Kontakte");
        eintraege.put("2", new ConcurrentHashMap<>());

        Telefonbuch buch = telefonbuch.stelleTelefonbuchBereit(zugang, "ERP-Kontakte", "2");

        assertThat(buch).isEqualTo(new Telefonbuch("2", false));
        assertThat(aktionen).doesNotContain("AddPhonebook");
    }

    @Test
    @DisplayName("Unter der gemerkten ID steht ein fremdes Telefonbuch: wird nicht benutzt, ERP-Kontakte über den Namen gefunden")
    void fremdesTelefonbuchUnterGemerkterId() {
        buecher.put("1", "Privat");
        buecher.put("3", "ERP-Kontakte");
        eintraege.put("3", new ConcurrentHashMap<>());

        Telefonbuch buch = telefonbuch.stelleTelefonbuchBereit(zugang, "ERP-Kontakte", "1");

        assertThat(buch).isEqualTo(new Telefonbuch("3", true));
        assertThat(aktionen).doesNotContain("AddPhonebook");
    }

    @Test
    @DisplayName("Eintrag anlegen, ändern, auslesen und löschen – Sonderzeichen kommen unverändert an")
    void eintragAnlegenAendernLoeschen() {
        String id = telefonbuch.stelleTelefonbuchBereit(zugang, "ERP-Kontakte", null).id();
        String name = "Müller & Söhne <GmbH> \"Nord\" 'Süd' (Kunde)";

        String uid = telefonbuch.speichereEintrag(zugang, id, eintrag(name), null);
        Document angekommen = SicheresXml.parse(eintraege.get(id).get(uid).getBytes(StandardCharsets.UTF_8));
        assertThat(SicheresXml.elemente(angekommen, "realName").getFirst().getTextContent()).isEqualTo(name);
        assertThat(SicheresXml.elemente(angekommen, "number")).extracting(Element::getTextContent)
                .containsExactly("0123456789", "01701234567");
        assertThat(SicheresXml.elemente(angekommen, "uniqueid")).extracting(Element::getTextContent).containsExactly(uid);

        String gleicheUid = telefonbuch.speichereEintrag(zugang, id, eintrag("Erika Musterfrau (Kunde)"), uid);
        assertThat(gleicheUid).isEqualTo(uid);
        assertThat(eintraege.get(id)).hasSize(1);
        assertThat(eintraege.get(id).get(uid)).contains("Erika Musterfrau (Kunde)");

        assertThat(telefonbuch.ladeEintragsIds(zugang, id)).containsExactly(uid);

        telefonbuch.loescheEintrag(zugang, id, uid);
        assertThat(eintraege.get(id)).isEmpty();
        // schon weg: kein Fehler
        telefonbuch.loescheEintrag(zugang, id, uid);
        assertThat(eintraege.get("0")).containsOnlyKeys("1");
    }

    @Test
    @DisplayName("Ändern eines in der Box gelöschten Eintrags legt ihn neu an")
    void aendernFehlenderEintrag() {
        String id = telefonbuch.stelleTelefonbuchBereit(zugang, "ERP-Kontakte", null).id();

        String uid = telefonbuch.speichereEintrag(zugang, id, eintrag("Max Mustermann (Kunde)"), "55");

        assertThat(uid).isNotEqualTo("55");
        assertThat(eintraege.get(id)).containsOnlyKeys(uid);
    }

    @Test
    @DisplayName("Fehlendes Schreibrecht → verständliche Meldung, was in der FRITZ!Box einzustellen ist")
    void keineSchreibrechte() {
        String id = telefonbuch.stelleTelefonbuchBereit(zugang, "ERP-Kontakte", null).id();
        schreibFehler = 606;

        assertThatThrownBy(() -> telefonbuch.speichereEintrag(zugang, id, eintrag("Max Mustermann (Kunde)"), null))
                .isInstanceOf(TelefonAnlageException.class)
                .hasMessageContaining("darf das Telefonbuch nicht ändern")
                .hasMessageContaining("System › FRITZ!Box-Benutzer")
                .extracting(e -> ((TelefonAnlageException) e).getGrund()).isEqualTo(Grund.TELEFONBUCH_KEINE_RECHTE);
    }

    @Test
    @DisplayName("Box verlangt zweiten Faktor → eigene Meldung")
    void zweiterFaktor() {
        String id = telefonbuch.stelleTelefonbuchBereit(zugang, "ERP-Kontakte", null).id();
        schreibFehler = 866;

        assertThatThrownBy(() -> telefonbuch.loescheEintrag(zugang, id, "1"))
                .extracting(e -> ((TelefonAnlageException) e).getGrund()).isEqualTo(Grund.TELEFONBUCH_BESTAETIGUNG);
    }

    @Test
    @DisplayName("Sonstiger SOAP-Fehler beim Schreiben → UNERWARTETE_ANTWORT")
    void sonstigerFehler() {
        String id = telefonbuch.stelleTelefonbuchBereit(zugang, "ERP-Kontakte", null).id();
        schreibFehler = 820;

        assertThatThrownBy(() -> telefonbuch.speichereEintrag(zugang, id, eintrag("Max Mustermann (Kunde)"), null))
                .extracting(e -> ((TelefonAnlageException) e).getGrund()).isEqualTo(Grund.UNERWARTETE_ANTWORT);
        assertThatThrownBy(() -> telefonbuch.loescheEintrag(zugang, id, "1"))
                .extracting(e -> ((TelefonAnlageException) e).getGrund()).isEqualTo(Grund.UNERWARTETE_ANTWORT);
    }

    @Test
    @DisplayName("Falsches Passwort → ANMELDUNG_FEHLGESCHLAGEN")
    void falschesPasswort() {
        TelefonZugang falsch = new TelefonZugang("127.0.0.1", BENUTZER, "falsch");
        assertThatThrownBy(() -> telefonbuch.stelleTelefonbuchBereit(falsch, "ERP-Kontakte", null))
                .extracting(e -> ((TelefonAnlageException) e).getGrund()).isEqualTo(Grund.ANMELDUNG_FEHLGESCHLAGEN);
    }

    @Test
    @DisplayName("Ungültige Telefonbuch-ID wird gar nicht erst gesendet")
    void ungueltigeTelefonbuchId() {
        assertThatThrownBy(() -> telefonbuch.loescheEintrag(zugang, "0</NewPhonebookID>", "1"))
                .isInstanceOf(TelefonAnlageException.class);
        assertThat(aktionen).isEmpty();
    }

    @Test
    @DisplayName("Telefonbuch löschen: das eigene unter der gemerkten ID, andere bleiben unberührt")
    void telefonbuchLoeschen() {
        buecher.put("2", "ERP-Kontakte");
        eintraege.put("2", new ConcurrentHashMap<>(Map.of("7", "<contact><uniqueid>7</uniqueid></contact>")));
        buecher.put("3", "Privat");
        eintraege.put("3", new ConcurrentHashMap<>());

        assertThat(telefonbuch.loescheTelefonbuch(zugang, "ERP-Kontakte", "2")).isTrue();

        assertThat(buecher).containsOnlyKeys("0", "3");
        assertThat(eintraege.get("0")).containsOnlyKeys("1");
        assertThat(aktionen).containsOnlyOnce("DeletePhonebook");
    }

    @Test
    @DisplayName("Gemerkte ID zeigt auf ein fremdes Telefonbuch: das wird nie gelöscht, nur „ERP-Kontakte“ über den Namen")
    void telefonbuchLoeschenNurUeberNamen() {
        buecher.put("3", "Privat");
        eintraege.put("3", new ConcurrentHashMap<>());
        buecher.put("5", "ERP-Kontakte");
        eintraege.put("5", new ConcurrentHashMap<>());

        assertThat(telefonbuch.loescheTelefonbuch(zugang, "ERP-Kontakte", "3")).isTrue();

        assertThat(buecher).containsOnlyKeys("0", "3");
    }

    @Test
    @DisplayName("Kein Telefonbuch „ERP-Kontakte“ da oder nur als Haupttelefonbuch: nichts wird gelöscht")
    void telefonbuchLoeschenNichtsDa() {
        assertThat(telefonbuch.loescheTelefonbuch(zugang, "ERP-Kontakte", "0")).isFalse();
        buecher.put("0", "ERP-Kontakte");
        assertThat(telefonbuch.loescheTelefonbuch(zugang, "ERP-Kontakte", "0")).isFalse();

        assertThat(buecher).containsOnlyKeys("0");
        assertThat(aktionen).doesNotContain("DeletePhonebook");
    }

    @Test
    @DisplayName("Telefonbuch löschen ohne Recht → verständliche Meldung")
    void telefonbuchLoeschenOhneRecht() {
        buecher.put("2", "ERP-Kontakte");
        eintraege.put("2", new ConcurrentHashMap<>());
        loeschFehler = 606;

        assertThatThrownBy(() -> telefonbuch.loescheTelefonbuch(zugang, "ERP-Kontakte", "2"))
                .extracting(e -> ((TelefonAnlageException) e).getGrund()).isEqualTo(Grund.TELEFONBUCH_KEINE_RECHTE);
        assertThat(buecher).containsKey("2");
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
        if ("POST".equals(ex.getRequestMethod())) {
            String action = ex.getRequestHeaders().getFirst("SOAPACTION").replace("\"", "");
            antworteSoap(ex, action.substring(action.indexOf('#') + 1), ex.getRequestBody().readAllBytes());
        } else if (pfad.startsWith("/phonebook.lua")) {
            String id = pfad.substring(pfad.indexOf("pbid=") + 5);
            StringBuilder export = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><phonebooks><phonebook>");
            eintraege.getOrDefault(id, Map.of()).values().forEach(export::append);
            antworte(ex, 200, export.append("</phonebook></phonebooks>").toString());
        } else {
            antworte(ex, 404, "");
        }
    }

    private void antworteSoap(HttpExchange ex, String aktion, byte[] body) throws IOException {
        aktionen.add(aktion);
        Map<String, String> a = SicheresXml.kinder(SicheresXml.elemente(SicheresXml.parse(body), aktion).getFirst());
        Map<String, String> werte;
        switch (aktion) {
            case "GetPhonebookList" -> werte = Map.of("NewPhonebookList", String.join(",", buecher.keySet().stream().sorted().toList()));
            case "GetPhonebook" -> {
                String id = a.get("NewPhonebookID");
                if (!buecher.containsKey(id)) {
                    soapFehler(ex, 713);
                    return;
                }
                werte = Map.of("NewPhonebookName", buecher.get(id), "NewPhonebookExtraID", "",
                        "NewPhonebookURL", "https://fritz.box:49443/phonebook.lua?sid=0123abcd&pbid=" + id);
            }
            case "AddPhonebook" -> {
                assertThat(a).containsKeys("NewPhonebookExtraID", "NewPhonebookName");
                String id = String.valueOf(buecher.size() + 5);
                buecher.put(id, a.get("NewPhonebookName"));
                eintraege.put(id, new ConcurrentHashMap<>());
                werte = Map.of();
            }
            case "DeletePhonebook" -> {
                if (loeschFehler != 0) {
                    soapFehler(ex, loeschFehler);
                    return;
                }
                assertThat(a).containsKeys("NewPhonebookID", "NewPhonebookExtraID");
                String id = a.get("NewPhonebookID");
                if (buecher.remove(id) == null) {
                    soapFehler(ex, 713);
                    return;
                }
                eintraege.remove(id);
                werte = Map.of();
            }
            case "SetPhonebookEntryUID" -> {
                if (schreibFehler != 0) {
                    soapFehler(ex, schreibFehler);
                    return;
                }
                Map<String, String> buch = eintraege.get(a.get("NewPhonebookID"));
                String xml = a.get("NewPhonebookEntryData");
                assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?><contact>");
                Document kontakt = SicheresXml.parse(xml.getBytes(StandardCharsets.UTF_8));
                List<Element> uidElemente = SicheresXml.elemente(kontakt, "uniqueid");
                String uid;
                if (uidElemente.isEmpty()) {
                    uid = String.valueOf(naechsteUid.incrementAndGet());
                } else {
                    uid = uidElemente.getFirst().getTextContent();
                    if (!buch.containsKey(uid)) {
                        soapFehler(ex, 714);
                        return;
                    }
                }
                xml = xml.substring(xml.indexOf("<contact>"));
                buch.put(uid, uidElemente.isEmpty() ? xml.replace("</contact>", "<uniqueid>" + uid + "</uniqueid></contact>") : xml);
                werte = Map.of("NewPhonebookEntryUniqueID", uid);
            }
            case "DeletePhonebookEntryUID" -> {
                if (schreibFehler != 0) {
                    soapFehler(ex, schreibFehler);
                    return;
                }
                Map<String, String> buch = eintraege.get(a.get("NewPhonebookID"));
                if (buch == null || buch.remove(a.get("NewPhonebookEntryUniqueID")) == null) {
                    soapFehler(ex, 714);
                    return;
                }
                werte = Map.of();
            }
            default -> {
                antworte(ex, 500, "");
                return;
            }
        }
        StringBuilder inhalt = new StringBuilder();
        werte.forEach((k, v) -> inhalt.append('<').append(k).append('>').append(TelefonbuchXml.escape(v)).append("</").append(k).append('>'));
        antworte(ex, 200, "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"
                + "<u:" + aktion + "Response xmlns:u=\"urn:x\">" + inhalt + "</u:" + aktion + "Response></s:Body></s:Envelope>");
    }

    private static void soapFehler(HttpExchange ex, int code) throws IOException {
        antworte(ex, 500, "<s:Envelope><s:Body><s:Fault><detail><UPnPError><errorCode>" + code
                + "</errorCode></UPnPError></detail></s:Fault></s:Body></s:Envelope>");
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

    private static void antworte(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            ex.getResponseBody().write(b);
        }
        ex.close();
    }
}
