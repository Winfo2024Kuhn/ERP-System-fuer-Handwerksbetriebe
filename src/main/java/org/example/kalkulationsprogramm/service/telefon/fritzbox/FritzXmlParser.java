package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.service.telefon.AnlagenAnruf;
import org.example.kalkulationsprogramm.service.telefon.AnlagenInfo;
import org.example.kalkulationsprogramm.service.telefon.AnlagenSprachnachricht;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Liest die XML-Listen der FRITZ!Box (Anrufliste, Anrufbeantworter,
 * Rufnummern). Einträge, die sich nicht lesen lassen, werden übersprungen
 * statt die ganze Liste zu verwerfen.
 */
final class FritzXmlParser {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.uu HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int TAM_PORT_START = 40;
    private static final int TAM_PORT_ENDE = 49;

    private FritzXmlParser() {
    }

    /**
     * Typen laut AVM: 1 = angenommen, 2 = verpasst, 3 = ausgehend, 10 = abgewiesen;
     * 9/11 = laufendes Gespräch (wird übersprungen). Port 40–49 = Anrufbeantworter.
     */
    static List<AnlagenAnruf> anrufliste(byte[] xml) {
        Document doc = SicheresXml.parse(xml);
        List<AnlagenAnruf> anrufe = new ArrayList<>();
        for (Element call : SicheresXml.elemente(doc, "Call")) {
            Map<String, String> w = SicheresXml.kinder(call);
            LocalDateTime zeitpunkt = datum(w.get("Date"));
            TelefonAnrufArt art = art(w.get("Type"));
            if (zeitpunkt == null || art == null) {
                continue;
            }
            boolean ausgehend = art == TelefonAnrufArt.AUSGEHEND;
            String gegen = ausgehend ? w.get("Called") : w.get("Caller");
            String eigene = ausgehend
                    ? ersteBelegte(w.get("CallerNumber"), w.get("Caller"))
                    : ersteBelegte(w.get("CalledNumber"), w.get("Called"));
            Integer ab = null;
            Integer port = zahl(w.get("Port"));
            if (art == TelefonAnrufArt.ANGENOMMEN && port != null
                    && port >= TAM_PORT_START && port <= TAM_PORT_ENDE) {
                art = TelefonAnrufArt.ANRUFBEANTWORTER;
                ab = port - TAM_PORT_START;
            }
            anrufe.add(new AnlagenAnruf(zeitpunkt, art, ohneSipPraefix(gegen), ohneSipPraefix(eigene),
                    dauerMinuten(w.get("Duration")), leerZuNull(w.get("Name")), ab));
        }
        return anrufe;
    }

    static List<AnlagenSprachnachricht> sprachnachrichten(byte[] xml, int anrufbeantworter, String sitzung) {
        Document doc = SicheresXml.parse(xml);
        List<AnlagenSprachnachricht> liste = new ArrayList<>();
        for (Element msg : SicheresXml.elemente(doc, "Message")) {
            Map<String, String> w = SicheresXml.kinder(msg);
            LocalDateTime zeitpunkt = datum(w.get("Date"));
            String pfad = w.get("Path");
            if (zeitpunkt == null || pfad == null || pfad.isBlank()) {
                continue;
            }
            Integer tam = zahl(w.get("Tam"));
            liste.add(new AnlagenSprachnachricht(tam != null ? tam : anrufbeantworter, zeitpunkt,
                    ohneSipPraefix(w.get("Number")), ohneSipPraefix(w.get("Called")), pfad, sitzung));
        }
        return liste;
    }

    static List<AnlagenInfo.Anrufbeantworter> anrufbeantworterListe(byte[] xml) {
        Document doc = SicheresXml.parse(xml);
        List<AnlagenInfo.Anrufbeantworter> liste = new ArrayList<>();
        for (Element item : SicheresXml.elemente(doc, "Item")) {
            Map<String, String> w = SicheresXml.kinder(item);
            Integer index = zahl(w.get("Index"));
            if (index == null) {
                continue;
            }
            // Nicht angezeigte ABs (Display=0) gibt es auf der Box nicht wirklich
            if ("0".equals(w.get("Display"))) {
                continue;
            }
            String name = leerZuNull(w.get("Name"));
            liste.add(new AnlagenInfo.Anrufbeantworter(index,
                    name != null ? name : "Anrufbeantworter " + (index + 1),
                    "1".equals(w.get("Enable"))));
        }
        return liste;
    }

    static List<String> rufnummern(byte[] xml) {
        Document doc = SicheresXml.parse(xml);
        List<String> nummern = new ArrayList<>();
        for (Element item : SicheresXml.elemente(doc, "Item")) {
            String nummer = ohneSipPraefix(SicheresXml.kinder(item).get("Number"));
            if (!nummer.isEmpty() && !nummern.contains(nummer)) {
                nummern.add(nummer);
            }
        }
        return nummern;
    }

    static TelefonAnrufArt art(String typ) {
        if (typ == null) {
            return null;
        }
        return switch (typ.trim()) {
            case "1" -> TelefonAnrufArt.ANGENOMMEN;
            case "2" -> TelefonAnrufArt.VERPASST;
            case "3" -> TelefonAnrufArt.AUSGEHEND;
            case "10" -> TelefonAnrufArt.ABGEWIESEN;
            default -> null;
        };
    }

    static LocalDateTime datum(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATUM);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "1:05" (h:mm) → 65 Minuten. */
    static int dauerMinuten(String text) {
        if (text == null) {
            return 0;
        }
        String[] teile = text.trim().split(":", -1);
        if (teile.length != 2) {
            return 0;
        }
        Integer h = zahl(teile[0]);
        Integer m = zahl(teile[1]);
        return h == null || m == null ? 0 : h * 60 + m;
    }

    /** "SIP: 2323" → "2323"; null → "". */
    static String ohneSipPraefix(String nummer) {
        if (nummer == null) {
            return "";
        }
        String n = nummer.trim();
        int doppelpunkt = n.indexOf(':');
        if (doppelpunkt >= 0 && doppelpunkt < 10) {
            n = n.substring(doppelpunkt + 1).trim();
        }
        return n.length() > 40 ? n.substring(0, 40) : n;
    }

    private static String ersteBelegte(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String leerZuNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    private static Integer zahl(String s) {
        if (s == null || s.isBlank() || s.trim().length() > 9) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
