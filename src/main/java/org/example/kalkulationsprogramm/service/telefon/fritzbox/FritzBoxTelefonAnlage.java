package org.example.kalkulationsprogramm.service.telefon.fritzbox;

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
import java.util.List;
import java.util.Map;

/**
 * FRITZ!Box-Umsetzung der {@link TelefonAnlage} über TR-064.
 * Liest nur – auf der Box wird nichts gelöscht oder als gehört markiert.
 */
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

    private final Tr064Client client;

    @Autowired
    public FritzBoxTelefonAnlage() {
        this(new Tr064Client());
    }

    FritzBoxTelefonAnlage(Tr064Client client) {
        this.client = client;
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
