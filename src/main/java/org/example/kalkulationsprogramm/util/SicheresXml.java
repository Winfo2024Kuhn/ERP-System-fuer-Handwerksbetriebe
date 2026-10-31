package org.example.kalkulationsprogramm.util;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * XXE-sicheres Lesen von XML aus fremden Quellen (FRITZ!Box, Lieferanten-Shops):
 * DOCTYPE verboten, keine externen Entities oder DTDs, kein XInclude.
 */
public final class SicheresXml {

    private SicheresXml() {
    }

    /** XML ist nicht lesbar oder enthält einen verbotenen DOCTYPE. */
    public static class NichtLesbar extends IllegalArgumentException {
        public NichtLesbar(Throwable ursache) {
            super("XML ist nicht lesbar.", ursache);
        }
    }

    /** Liest Rohbytes; die Kodierung kommt aus der XML-Deklaration (ohne Angabe UTF-8). */
    public static Document parse(byte[] xml) {
        try {
            return builder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new NichtLesbar(e);
        }
    }

    /** Liest schon dekodierten Text; eine Kodierungsangabe in der Deklaration wird dabei übergangen. */
    public static Document parse(String xml) {
        try {
            return builder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new NichtLesbar(e);
        }
    }

    /**
     * Wie {@link #parse(byte[])}, nimmt aber eine vorangestellte <b>einfache</b> DOCTYPE-Zeile hin, wie sie
     * Shop-Systeme (z. B. Intershop bei Würth) aus ihrer XHTML-Vorlage vor das XML setzen:
     * {@code <!DOCTYPE html PUBLIC "…" "…">}. Sie wird vor dem Lesen entfernt. Eine DOCTYPE mit eigenen
     * Definitionen ({@code [ … ]}, der Weg für XXE) bleibt verboten. Auch Leerraum vor dem Inhalt wird entfernt.
     */
    public static Document parseMitEinfacherDoctype(byte[] xml) {
        return parse(ohneEinfacheDoctype(xml));
    }

    /** Text-Variante von {@link #parseMitEinfacherDoctype(byte[])}. */
    public static Document parseMitEinfacherDoctype(String xml) {
        return parse(new String(ohneEinfacheDoctype(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Entfernt eine einfache DOCTYPE nur dort, wo sie stehen darf: am Anfang, höchstens nach Leerraum und der
     * XML-Deklaration – nie mitten im Inhalt (z. B. in CDATA). ASCII-kompatible Kodierungen (UTF-8, ISO-8859-x).
     */
    static byte[] ohneEinfacheDoctype(byte[] xml) {
        int start = ueberspringeLeerraum(xml, 0);
        int pos = start;
        if (beginntMit(xml, pos, "<?xml")) {
            int ende = indexOf(xml, "?>", pos, Math.min(xml.length, pos + 512));
            if (ende < 0) {
                return xml;
            }
            pos = ueberspringeLeerraum(xml, ende + 2);
        }
        if (!beginntMit(xml, pos, "<!DOCTYPE")) {
            return start == 0 ? xml : java.util.Arrays.copyOfRange(xml, start, xml.length);
        }
        int ende = -1;
        for (int i = pos; i < Math.min(xml.length, pos + 1024); i++) {
            if (xml[i] == '[') {
                // Interne Definitionen: nicht entfernen, der Parser lehnt die DOCTYPE dann ab.
                return xml;
            }
            if (xml[i] == '>') {
                ende = i;
                break;
            }
        }
        if (ende < 0) {
            return xml;
        }
        byte[] ergebnis = new byte[(pos - start) + (xml.length - ende - 1)];
        System.arraycopy(xml, start, ergebnis, 0, pos - start);
        System.arraycopy(xml, ende + 1, ergebnis, pos - start, xml.length - ende - 1);
        return ergebnis;
    }

    private static int ueberspringeLeerraum(byte[] daten, int von) {
        int i = von;
        // UTF-8-BOM am Anfang zählt wie Leerraum.
        if (i == 0 && daten.length >= 3 && (daten[0] & 0xFF) == 0xEF && (daten[1] & 0xFF) == 0xBB && (daten[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < daten.length && Character.isWhitespace(daten[i])) {
            i++;
        }
        return i;
    }

    private static boolean beginntMit(byte[] daten, int pos, String text) {
        return indexOf(daten, text, pos, Math.min(daten.length, pos + text.length())) == pos;
    }

    private static int indexOf(byte[] daten, String suche, int von, int bis) {
        // Ohne Rücksicht auf Groß-/Kleinschreibung: <!doctype und <!DOCTYPE, <?xml und <?XML.
        byte[] s = suche.toUpperCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        aussen:
        for (int i = von; i <= bis - s.length; i++) {
            for (int j = 0; j < s.length; j++) {
                if (Character.toUpperCase(daten[i + j]) != s[j]) {
                    continue aussen;
                }
            }
            return i;
        }
        return -1;
    }

    private static DocumentBuilder builder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(null);
        return b;
    }

    /** Alle Elemente mit diesem lokalen Namen, egal in welchem Namespace. */
    public static List<Element> elemente(Document doc, String lokalerName) {
        return elemente(doc.getElementsByTagNameNS("*", lokalerName));
    }

    /** Alle Nachfahren-Elemente mit diesem lokalen Namen, egal in welchem Namespace. */
    public static List<Element> elemente(Element eltern, String lokalerName) {
        return elemente(eltern.getElementsByTagNameNS("*", lokalerName));
    }

    private static List<Element> elemente(NodeList nodes) {
        List<Element> result = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    /** Text der direkten Kind-Elemente als Map (lokaler Name → getrimmter Text); bei Wiederholung zählt das letzte. */
    public static Map<String, String> kinder(Element parent) {
        Map<String, String> werte = new LinkedHashMap<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                String name = n.getLocalName() != null ? n.getLocalName() : n.getNodeName();
                werte.put(name, n.getTextContent() == null ? "" : n.getTextContent().trim());
            }
        }
        return werte;
    }
}
