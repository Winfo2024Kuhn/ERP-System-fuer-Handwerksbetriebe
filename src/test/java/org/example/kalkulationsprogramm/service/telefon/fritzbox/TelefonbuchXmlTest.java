package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchEintrag;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchEintrag.NummerArt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelefonbuchXmlTest {

    private static TelefonbuchEintrag eintrag(String name) {
        return new TelefonbuchEintrag(name, List.of(
                new TelefonbuchEintrag.Nummer("0123456789", NummerArt.GESCHAEFTLICH),
                new TelefonbuchEintrag.Nummer("01701234567", NummerArt.MOBIL),
                new TelefonbuchEintrag.Nummer("+431234567", NummerArt.PRIVAT)));
    }

    @Test
    @DisplayName("Neuer Eintrag: Name, Nummern mit Typ und Hauptnummer, ohne uniqueid")
    void neuerEintrag() {
        String xml = TelefonbuchXml.eintrag(eintrag("Max Mustermann (Kunde)"), null);

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?><contact><category>0</category>")
                .contains("<realName>Max Mustermann (Kunde)</realName>")
                .contains("<telephony nid=\"3\">")
                .contains("<number type=\"work\" prio=\"1\" id=\"0\">0123456789</number>")
                .contains("<number type=\"mobile\" prio=\"0\" id=\"1\">01701234567</number>")
                .contains("<number type=\"home\" prio=\"0\" id=\"2\">+431234567</number>")
                .doesNotContain("uniqueid");
        Document doc = SicheresXml.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(SicheresXml.elemente(doc, "number")).hasSize(3);
    }

    @Test
    @DisplayName("Update: uniqueid wird mitgesendet")
    void update() {
        assertThat(TelefonbuchXml.eintrag(eintrag("Max Mustermann (Kunde)"), "42"))
                .endsWith("</telephony><uniqueid>42</uniqueid></contact>");
    }

    @Test
    @DisplayName("Sonderzeichen im Namen werden genau einmal escaped und kommen beim Parsen unverändert heraus")
    void escaping() {
        String name = "Müller & Söhne <GmbH> \"Bau\" 'Nord' (Kunde)";
        String xml = TelefonbuchXml.eintrag(eintrag(name), null);

        assertThat(xml).contains("Müller &amp; Söhne &lt;GmbH&gt; &quot;Bau&quot; &apos;Nord&apos; (Kunde)");
        Document doc = SicheresXml.parse(xml.getBytes(StandardCharsets.UTF_8));
        Element realName = SicheresXml.elemente(doc, "realName").getFirst();
        assertThat(realName.getTextContent()).isEqualTo(name);
    }

    @Test
    @DisplayName("Steuerzeichen werden ausgelassen – sonst wäre das XML ungültig")
    void steuerzeichen() {
        String xml = TelefonbuchXml.eintrag(eintrag("Max\u0000Muster\u0007mann"), null);
        assertThat(xml).contains("<realName>MaxMustermann</realName>");
        SicheresXml.parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Eintrags-ID nur aus Ziffern – sonst kein XML")
    void ungueltigeId() {
        assertThatThrownBy(() -> TelefonbuchXml.eintrag(eintrag("Max"), "1</uniqueid><x>"))
                .extracting(e -> ((TelefonAnlageException) e).getGrund()).isEqualTo(Grund.UNERWARTETE_ANTWORT);
        assertThatThrownBy(() -> TelefonbuchXml.pruefeId(null)).isInstanceOf(TelefonAnlageException.class);
        assertThatThrownBy(() -> TelefonbuchXml.pruefeId("123456789012345678901")).isInstanceOf(TelefonAnlageException.class);
        assertThat(TelefonbuchXml.pruefeId(" 17 ")).isEqualTo("17");
    }

    @Test
    @DisplayName("Export der Box: uniqueids der Kontakte, ungültige werden übersprungen")
    void exportLesen() {
        String export = "<?xml version=\"1.0\" encoding=\"utf-8\"?><phonebooks><phonebook name=\"ERP-Kontakte\">"
                + "<contact><category>0</category><person><realName>Max Mustermann (Kunde)</realName></person>"
                + "<telephony nid=\"1\"><number type=\"work\" prio=\"1\" id=\"0\">0123456789</number></telephony>"
                + "<uniqueid>3</uniqueid></contact>"
                + "<contact><person><realName>Erika Musterfrau (Lieferant)</realName></person><uniqueid>7</uniqueid></contact>"
                + "<contact><person><realName>kaputt</realName></person><uniqueid>abc</uniqueid></contact>"
                + "<contact><person><realName>ohne</realName></person></contact>"
                + "</phonebook></phonebooks>";
        assertThat(TelefonbuchXml.eintragsIds(export.getBytes(StandardCharsets.UTF_8))).containsExactly("3", "7");
    }

    @Test
    @DisplayName("Export mit DOCTYPE (XXE) wird abgelehnt")
    void xxe() {
        String boese = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<phonebooks><phonebook><contact><uniqueid>&e;</uniqueid></contact></phonebook></phonebooks>";
        assertThatThrownBy(() -> TelefonbuchXml.eintragsIds(boese.getBytes(StandardCharsets.UTF_8)))
                .extracting(e -> ((TelefonAnlageException) e).getGrund()).isEqualTo(Grund.UNERWARTETE_ANTWORT);
    }

    @Test
    @DisplayName("Nummernarten → Typen der FRITZ!Box")
    void typen() {
        assertThat(TelefonbuchXml.typ(NummerArt.GESCHAEFTLICH)).isEqualTo("work");
        assertThat(TelefonbuchXml.typ(NummerArt.MOBIL)).isEqualTo("mobile");
        assertThat(TelefonbuchXml.typ(NummerArt.PRIVAT)).isEqualTo("home");
        assertThat(TelefonbuchXml.escape(null)).isEmpty();
    }
}
