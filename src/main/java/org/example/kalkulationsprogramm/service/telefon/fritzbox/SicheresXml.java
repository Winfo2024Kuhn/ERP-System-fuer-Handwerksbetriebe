package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;

/**
 * XXE-sicheres Lesen der XML-Antworten der FRITZ!Box über {@link org.example.kalkulationsprogramm.util.SicheresXml};
 * unlesbare Antworten werden zu {@link TelefonAnlageException}.
 */
final class SicheresXml {

    private SicheresXml() {
    }

    static Document parse(byte[] xml) {
        try {
            return org.example.kalkulationsprogramm.util.SicheresXml.parse(xml);
        } catch (org.example.kalkulationsprogramm.util.SicheresXml.NichtLesbar e) {
            throw new TelefonAnlageException(TelefonAnlageException.Grund.UNERWARTETE_ANTWORT, e.getCause());
        }
    }

    static List<Element> elemente(Document doc, String lokalerName) {
        return org.example.kalkulationsprogramm.util.SicheresXml.elemente(doc, lokalerName);
    }

    static Map<String, String> kinder(Element parent) {
        return org.example.kalkulationsprogramm.util.SicheresXml.kinder(parent);
    }
}
