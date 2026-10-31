package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class SicheresXmlTest {

    @Test
    void liestBytesUndText() {
        String xml = "<a xmlns:n=\"urn:x\"><n:b><c> 1 </c><c>2</c><d/></n:b><b/></a>";
        Document ausBytes = SicheresXml.parse(xml.getBytes(StandardCharsets.UTF_8));
        Document ausText = SicheresXml.parse(xml);

        assertThat(SicheresXml.elemente(ausBytes, "b")).hasSize(2);
        Element b = SicheresXml.elemente(ausText, "b").get(0);
        assertThat(SicheresXml.elemente(b, "c")).hasSize(2);
        assertThat(SicheresXml.kinder(b)).containsEntry("c", "2").containsEntry("d", "");
    }

    @Test
    void einfacheDoctypeWirdEntferntDefinitionenNicht() {
        String einfach = "  <!doctype html PUBLIC \"a\" \"b\">\n<a><b>1</b></a>";
        assertThat(SicheresXml.elemente(SicheresXml.parseMitEinfacherDoctype(einfach), "b")).hasSize(1);
        assertThat(SicheresXml.elemente(SicheresXml.parseMitEinfacherDoctype(einfach.getBytes(StandardCharsets.UTF_8)), "b")).hasSize(1);
        String mitDeklaration = "<?xml version=\"1.0\"?><!DOCTYPE a><a/>";
        assertThat(SicheresXml.parseMitEinfacherDoctype(mitDeklaration).getDocumentElement().getNodeName()).isEqualTo("a");
        assertThat(SicheresXml.parseMitEinfacherDoctype("\n<a/>").getDocumentElement().getNodeName()).isEqualTo("a");
        assertThat(SicheresXml.parseMitEinfacherDoctype("<a/>").getDocumentElement().getNodeName()).isEqualTo("a");

        byte[] mitBom = ("\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE html PUBLIC \"a\" \"b\"><a/>")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(SicheresXml.parseMitEinfacherDoctype(mitBom).getDocumentElement().getNodeName()).isEqualTo("a");

        // Nur am Anfang entfernen, nie im Inhalt
        String cdata = "<a><![CDATA[<!DOCTYPE x>]]></a>";
        assertThat(SicheresXml.parseMitEinfacherDoctype(cdata).getDocumentElement().getTextContent()).isEqualTo("<!DOCTYPE x>");
        assertThatThrownBy(() -> SicheresXml.parseMitEinfacherDoctype("<?xml version=\"1.0\"")).isInstanceOf(SicheresXml.NichtLesbar.class);

        String xxe = "<!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>";
        assertThatThrownBy(() -> SicheresXml.parseMitEinfacherDoctype(xxe)).isInstanceOf(SicheresXml.NichtLesbar.class);
        assertThatThrownBy(() -> SicheresXml.parseMitEinfacherDoctype("<!DOCTYPE a")).isInstanceOf(SicheresXml.NichtLesbar.class);
    }

    @Test
    void lehntDoctypeUndUnsinnAb() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>";
        assertThatThrownBy(() -> SicheresXml.parse(xxe)).isInstanceOf(SicheresXml.NichtLesbar.class);
        assertThatThrownBy(() -> SicheresXml.parse(xxe.getBytes(StandardCharsets.UTF_8))).isInstanceOf(SicheresXml.NichtLesbar.class);
        assertThatThrownBy(() -> SicheresXml.parse("<a>")).isInstanceOf(SicheresXml.NichtLesbar.class);
    }
}
