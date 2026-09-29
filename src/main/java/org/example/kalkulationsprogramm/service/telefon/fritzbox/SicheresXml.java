package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XXE-sicheres Lesen der XML-Antworten der FRITZ!Box: DOCTYPE verboten,
 * keine externen Entities oder DTDs.
 */
final class SicheresXml {

    private SicheresXml() {
    }

    static Document parse(byte[] xml) {
        try {
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
            return b.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new TelefonAnlageException(TelefonAnlageException.Grund.UNERWARTETE_ANTWORT, e);
        }
    }

    /** Alle Elemente mit diesem lokalen Namen, egal in welchem Namespace. */
    static List<Element> elemente(Document doc, String lokalerName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", lokalerName);
        List<Element> result = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    /** Text der direkten Kind-Elemente als Map (lokaler Name → getrimmter Text). */
    static Map<String, String> kinder(Element parent) {
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
