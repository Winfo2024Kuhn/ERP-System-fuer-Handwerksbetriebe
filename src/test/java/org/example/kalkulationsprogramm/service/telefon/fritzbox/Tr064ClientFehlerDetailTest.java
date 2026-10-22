package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Fehler-Detail aus Antworten der FRITZ!Box: nur harmlose, kurze Texte, nie die Sitzungs-ID. */
class Tr064ClientFehlerDetailTest {

    private static String fault(String code, String beschreibung) {
        return "<s:Envelope><s:Body><s:Fault><detail><UPnPError><errorCode>" + code + "</errorCode>"
                + (beschreibung == null ? "" : "<errorDescription>" + beschreibung + "</errorDescription>")
                + "</UPnPError></detail></s:Fault></s:Body></s:Envelope>";
    }

    @Test
    @DisplayName("Fehlercode mit Beschreibung der Box")
    void mitBeschreibung() {
        assertThat(Tr064Client.soapFehlerDetail("SetPhonebookEntryUID", 402, fault("402", "Invalid Args")))
                .isEqualTo("SetPhonebookEntryUID: Fehlercode 402 „Invalid Args“");
    }

    @Test
    @DisplayName("Fehlercode ohne Beschreibung")
    void ohneBeschreibung() {
        assertThat(Tr064Client.soapFehlerDetail("AddPhonebook", 820, fault("820", null)))
                .isEqualTo("AddPhonebook: Fehlercode 820");
    }

    @Test
    @DisplayName("HTTP 500 ganz ohne Fehlercode")
    void ohneFehlercode() {
        assertThat(Tr064Client.soapFehlerDetail("GetPhonebook", 0, "kaputt"))
                .isEqualTo("GetPhonebook: HTTP 500 ohne Fehlercode");
    }

    @Test
    @DisplayName("Beschreibung der Box: Sonderzeichen raus, auf 60 Zeichen gekürzt")
    void beschreibungBereinigt() {
        String detail = Tr064Client.soapFehlerDetail("AddPhonebook", 501,
                fault("501", "Action &quot;Failed&quot; {x}; " + "a".repeat(100)));

        assertThat(detail).startsWith("AddPhonebook: Fehlercode 501 „Action quotFailedquot x ");
        assertThat(detail).doesNotContain("&", "\"", "{", ";");
        String beschreibung = detail.substring(detail.indexOf('„') + 1, detail.indexOf('“'));
        assertThat(beschreibung).hasSizeLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("Keine Log-Injection: Zeilenumbrüche, Umlaute und Unicode-Steuerzeichen der Box fliegen raus")
    void keineLogInjection() {
        String detail = Tr064Client.soapFehlerDetail("AddPhonebook", 501,
                fault("501", "Action Failed\r\nWARN gefälscht\u202Eevil\tx"));

        assertThat(detail).doesNotContain("\r", "\n", "\t", "\u202E", "ä");
        assertThat(detail).isEqualTo("AddPhonebook: Fehlercode 501 „Action FailedWARN geflschtevilx“");
    }

    @Test
    @DisplayName("Dateiname aus Pfad: ohne Query (Sitzungs-ID) und ohne fremde Zeichen")
    void dateiname() {
        assertThat(Tr064Client.dateiname("/phonebook.lua?sid=0123abcd&pbid=1")).isEqualTo("phonebook.lua");
        assertThat(Tr064Client.dateiname("/a/b/calllist.lua")).isEqualTo("calllist.lua");
        assertThat(Tr064Client.dateiname("/<script>.lua")).isEqualTo("script.lua");
        assertThat(Tr064Client.dateiname("/")).isEqualTo("Datei");
        assertThat(Tr064Client.dateiname("/" + "x".repeat(100))).hasSize(60);
    }
}
