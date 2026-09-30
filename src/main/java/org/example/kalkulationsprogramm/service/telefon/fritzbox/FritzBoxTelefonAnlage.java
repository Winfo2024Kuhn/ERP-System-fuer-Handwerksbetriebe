package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.telefon.AnlagenAnruf;
import org.example.kalkulationsprogramm.service.telefon.AnlagenInfo;
import org.example.kalkulationsprogramm.service.telefon.AnlagenSprachnachricht;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlage;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FRITZ!Box-Umsetzung der {@link TelefonAnlage} über TR-064.
 * Liest Anrufe und Nachrichten nur – auf der Box wird nichts gelöscht oder als
 * gehört markiert. Einzige Änderung: Zum Anrufen stellt die Wählhilfe auf das
 * gewünschte Telefon um.
 */
@Slf4j
@Component
public class FritzBoxTelefonAnlage implements TelefonAnlage {

    static final String ONTEL = "urn:dslforum-org:service:X_AVM-DE_OnTel:1";
    static final String ONTEL_URL = "/upnp/control/x_contact";
    static final String TAM = "urn:dslforum-org:service:X_AVM-DE_TAM:1";
    static final String TAM_URL = "/upnp/control/x_tam";
    static final String VOIP = "urn:dslforum-org:service:X_VoIP:1";
    static final String VOIP_URL = "/upnp/control/x_voip";

    private static final String DOWNLOAD_PRAEFIX = "/download.lua?path=";
    private static final int MAX_TAGE = 999;
    /** Obergrenze für die Telefonliste – mehr Anschlüsse hat keine FRITZ!Box. */
    private static final int MAX_TELEFONE = 50;
    /** UPnP-Fehler "Index gibt es nicht" – Ende der Telefonliste. */
    private static final int INDEX_UNGUELTIG = 713;
    private static final Duration STANDARD_SPERR_WARTEZEIT = Duration.ofSeconds(5);

    private final Tr064Client client;
    /**
     * Die Wählhilfe ist eine Einstellung für die ganze Box: Umstellen und Wählen
     * dürfen sich nicht überholen. Wer nicht rasch drankommt, bekommt
     * BESCHAEFTIGT – statt dass sich Anfragen hinter einer hängenden Box stauen.
     */
    private final ReentrantLock waehlhilfe = new ReentrantLock();
    private final Duration sperrWartezeit;

    @Autowired
    public FritzBoxTelefonAnlage() {
        this(new Tr064Client());
    }

    FritzBoxTelefonAnlage(Tr064Client client) {
        this(client, STANDARD_SPERR_WARTEZEIT);
    }

    FritzBoxTelefonAnlage(Tr064Client client, Duration sperrWartezeit) {
        this.client = client;
        this.sperrWartezeit = sperrWartezeit;
    }

    @Override
    public AnlagenInfo pruefeVerbindung(TelefonZugang zugang) {
        Map<String, String> nummern = client.aktion(zugang, VOIP_URL, VOIP, "X_AVM-DE_GetNumbers", Map.of());
        List<String> eigeneNummern = FritzXmlParser.rufnummern(xmlAusWert(nummern.get("NewNumberList")));

        Map<String, String> tams = client.aktion(zugang, TAM_URL, TAM, "GetList", Map.of());
        List<AnlagenInfo.Anrufbeantworter> anrufbeantworter =
                FritzXmlParser.anrufbeantworterListe(xmlAusWert(tams.get("NewTAMList")));

        String land = optionalerWert(zugang, "X_AVM-DE_GetVoIPCommonCountryCode", "LKZ");
        String ort = optionalerWert(zugang, "X_AVM-DE_GetVoIPCommonAreaCode", "OKZ");
        return new AnlagenInfo(eigeneNummern, anrufbeantworter, land, ort);
    }

    @Override
    public List<AnlagenAnruf> ladeAnrufe(TelefonZugang zugang, int tage) {
        int t = Math.clamp(tage, 1, MAX_TAGE);
        Map<String, String> antwort = client.aktion(zugang, ONTEL_URL, ONTEL, "GetCallList", Map.of());
        String url = pflichtWert(antwort, "NewCallListURL");
        String trenner = url.contains("?") ? "&" : "?";
        return FritzXmlParser.anrufliste(client.laden(zugang, url + trenner + "days=" + t));
    }

    @Override
    public List<AnlagenSprachnachricht> ladeSprachnachrichten(TelefonZugang zugang, int anrufbeantworter) {
        Map<String, String> antwort = client.aktion(zugang, TAM_URL, TAM, "GetMessageList",
                Map.of("NewIndex", String.valueOf(anrufbeantworter)));
        String url = pflichtWert(antwort, "NewURL");
        return FritzXmlParser.sprachnachrichten(client.laden(zugang, url), anrufbeantworter, sitzungAus(url));
    }

    @Override
    public byte[] ladeAudio(TelefonZugang zugang, AnlagenSprachnachricht nachricht) {
        String pfad = nachricht.downloadPfad();
        if (pfad == null || !pfad.startsWith(DOWNLOAD_PRAEFIX) || pfad.contains("://") || pfad.contains("#")) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
        }
        String sid = nachricht.sitzung();
        String url = sid == null || sid.isEmpty() ? pfad : pfad + "&sid=" + sid;
        return client.laden(zugang, url);
    }

    /** Wählhilfe-Telefone über GetPhonePort, ab Index 1, bis die Box "gibt es nicht" meldet. */
    @Override
    public List<String> ladeTelefone(TelefonZugang zugang) {
        List<String> telefone = new ArrayList<>();
        for (int index = 1; index <= MAX_TELEFONE; index++) {
            Map<String, String> antwort;
            try {
                antwort = client.aktion(zugang, VOIP_URL, VOIP, "X_AVM-DE_GetPhonePort",
                        Map.of("NewIndex", String.valueOf(index)));
            } catch (SoapFehler e) {
                if (e.code() == INDEX_UNGUELTIG) {
                    break;
                }
                throw e;
            }
            String name = antwort.get("NewX_AVM-DE_PhoneName");
            if (name != null && !name.isBlank()) {
                telefone.add(name);
            }
        }
        return telefone;
    }

    @Override
    public void anrufen(TelefonZugang zugang, String telefon, String nummer) {
        sperren();
        try {
            waehlhilfeAktion(zugang, "X_AVM-DE_DialSetConfig", Map.of("NewX_AVM-DE_PhoneName", telefon));
            waehlhilfeAktion(zugang, "X_AVM-DE_DialNumber", Map.of("NewX_AVM-DE_PhoneNumber", nummer));
        } finally {
            waehlhilfe.unlock();
        }
    }

    private void sperren() {
        try {
            if (!waehlhilfe.tryLock(sperrWartezeit.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new TelefonAnlageException(Grund.BESCHAEFTIGT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelefonAnlageException(Grund.BESCHAEFTIGT, e);
        }
    }

    /**
     * Schlägt Umstellen oder Wählen fehl, ist fast immer die Wählhilfe aus – das
     * Telefon selbst stammt aus der Liste der Box. Protokolliert wird nur Aktion
     * und Fehlercode, nie Nummer oder Telefonname.
     */
    private void waehlhilfeAktion(TelefonZugang zugang, String aktion, Map<String, String> argumente) {
        try {
            client.aktion(zugang, VOIP_URL, VOIP, aktion, argumente);
        } catch (SoapFehler e) {
            log.warn("Telefon: FRITZ!Box-Wählhilfe lehnt {} ab (UPnP-Fehler {})", aktion, e.code());
            throw new TelefonAnlageException(Grund.WAEHLHILFE_AUS, e);
        }
    }

    /** Session-ID aus "…?sid=abc123&tamindex=0"; nur Buchstaben und Ziffern werden übernommen. */
    static String sitzungAus(String url) {
        int start = url.indexOf("sid=");
        if (start < 0) {
            return "";
        }
        StringBuilder sid = new StringBuilder();
        for (int i = start + 4; i < url.length() && sid.length() < 64; i++) {
            char c = url.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                break;
            }
            sid.append(c);
        }
        return sid.toString();
    }

    /** Vorwahlen sind nett zu haben – ältere FRITZ!OS-Versionen kennen die Aktion nicht. */
    private String optionalerWert(TelefonZugang zugang, String aktion, String schluesselTeil) {
        try {
            Map<String, String> w = client.aktion(zugang, VOIP_URL, VOIP, aktion, Map.of());
            return w.entrySet().stream()
                    .filter(e -> e.getKey().contains(schluesselTeil) && !e.getKey().contains("Prefix"))
                    .map(Map.Entry::getValue)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst()
                    .orElse(null);
        } catch (TelefonAnlageException e) {
            if (e.getGrund() == Grund.NICHT_ERREICHBAR || e.getGrund() == Grund.ANMELDUNG_FEHLGESCHLAGEN) {
                throw e;
            }
            return null;
        }
    }

    private static String pflichtWert(Map<String, String> werte, String schluessel) {
        String wert = werte.get(schluessel);
        if (wert == null || wert.isBlank()) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
        }
        return wert;
    }

    private static byte[] xmlAusWert(String wert) {
        if (wert == null || wert.isBlank()) {
            return "<List/>".getBytes(StandardCharsets.UTF_8);
        }
        return wert.getBytes(StandardCharsets.UTF_8);
    }
}
