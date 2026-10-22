package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchEintrag;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Baut das Eintrags-XML für {@code SetPhonebookEntryUID} und liest die
 * Eintrags-Kennungen aus dem Telefonbuch-Export der FRITZ!Box.
 * <p>
 * <b>Escaping:</b> Hier wird jeder Wert genau einmal für das Eintrags-XML
 * escaped. Das fertige XML geht als Text-Argument in die SOAP-Anfrage und wird
 * dort von {@code Tr064Client} ein zweites Mal escaped – die Box packt die
 * SOAP-Ebene aus und liest dann dieses XML. Zeichen, die in XML 1.0 gar nicht
 * vorkommen dürfen (Steuerzeichen), werden schon beim Bauen der Soll-Liste entfernt
 * und hier zur Sicherheit noch einmal ausgelassen.
 */
final class TelefonbuchXml {

    private static final int MAX_ID_LAENGE = 20;

    private TelefonbuchXml() {
    }

    static String eintrag(TelefonbuchEintrag eintrag, String eintragsId) {
        List<TelefonbuchEintrag.Nummer> nummern = eintrag.nummern();
        StringBuilder xml = new StringBuilder(256)
                .append("<?xml version=\"1.0\" encoding=\"utf-8\"?><contact><category>0</category><person><realName>")
                .append(escape(eintrag.name()))
                .append("</realName></person><telephony nid=\"").append(nummern.size()).append("\">");
        for (int i = 0; i < nummern.size(); i++) {
            TelefonbuchEintrag.Nummer n = nummern.get(i);
            xml.append("<number type=\"").append(typ(n.art())).append("\" prio=\"").append(i == 0 ? 1 : 0)
                    .append("\" id=\"").append(i).append("\">").append(escape(n.nummer())).append("</number>");
        }
        xml.append("</telephony>");
        if (eintragsId != null) {
            xml.append("<uniqueid>").append(pruefeId(eintragsId)).append("</uniqueid>");
        }
        return xml.append("</contact>").toString();
    }

    /** Typen laut FRITZ!Box-Telefonbuch: work = geschäftlich, mobile = Handy, home = privat. */
    static String typ(TelefonbuchEintrag.NummerArt art) {
        return switch (art) {
            case GESCHAEFTLICH -> "work";
            case MOBIL -> "mobile";
            case PRIVAT -> "home";
        };
    }

    /** Kennungen ({@code <uniqueid>}) aller Kontakte im Telefonbuch-Export. */
    static Set<String> eintragsIds(byte[] export) {
        Document doc = SicheresXml.parse(export);
        Set<String> ids = new LinkedHashSet<>();
        for (Element kontakt : SicheresXml.elemente(doc, "contact")) {
            String id = SicheresXml.kinder(kontakt).get("uniqueid");
            if (istGueltigeId(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Eine Eintrags-Kennung der Box: nur Ziffern, höchstens 20 Stellen. */
    static boolean istGueltigeId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LAENGE) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            if (id.charAt(i) < '0' || id.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    static String pruefeId(String id) {
        String s = id == null ? null : id.trim();
        if (!istGueltigeId(s)) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
        }
        return s;
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (c >= 0x20 || c == '\t') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
