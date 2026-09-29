package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.service.telefon.AnlagenAnruf;
import org.example.kalkulationsprogramm.service.telefon.AnlagenInfo;
import org.example.kalkulationsprogramm.service.telefon.AnlagenSprachnachricht;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FritzXmlParserTest {

    private static byte[] xml(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static final String ANRUFLISTE = """
            <?xml version="1.0" encoding="utf-8"?>
            <root><timestamp>1</timestamp>
            <Call><Id>6</Id><Type>1</Type><Called>2323</Called><Caller>09311234567</Caller><CalledNumber>2323</CalledNumber>
              <Name>Max Mustermann</Name><Port>10</Port><Date>29.09.26 11:55</Date><Duration>0:05</Duration></Call>
            <Call><Id>5</Id><Type>2</Type><Called>2323</Called><Caller>01711234567</Caller><CalledNumber>2323</CalledNumber>
              <Date>29.09.26 11:20</Date><Duration>0:00</Duration></Call>
            <Call><Id>4</Id><Type>3</Type><Called>09317654321</Called><Caller>2323</Caller><CallerNumber>2323</CallerNumber>
              <Date>29.09.26 10:03</Date><Duration>1:12</Duration></Call>
            <Call><Id>3</Id><Type>1</Type><Called>SIP: 2323</Called><Caller></Caller><Port>41</Port><Device>AB Tag</Device>
              <Date>29.09.26 09:52</Date><Duration>0:01</Duration></Call>
            <Call><Id>2</Id><Type>9</Type><Caller>09311234567</Caller><CalledNumber>2323</CalledNumber><Date>29.09.26 09:50</Date></Call>
            <Call><Id>1</Id><Type>10</Type><Caller>0800123456</Caller><CalledNumber>555000</CalledNumber><Date>kaputt</Date></Call>
            <Call><Id>0</Id><Type>10</Type><Caller>0800123456</Caller><CalledNumber>555000</CalledNumber><Date>28.09.26 08:00</Date></Call>
            </root>
            """;

    @Test
    @DisplayName("Anrufliste: Typen, eigene Nummer, Gegenüber, Dauer; laufende und kaputte Einträge fallen weg")
    void anrufliste() {
        List<AnlagenAnruf> anrufe = FritzXmlParser.anrufliste(xml(ANRUFLISTE));

        assertThat(anrufe).hasSize(5);
        assertThat(anrufe.get(0)).isEqualTo(new AnlagenAnruf(LocalDateTime.of(2026, 9, 29, 11, 55),
                TelefonAnrufArt.ANGENOMMEN, "09311234567", "2323", 5, "Max Mustermann", null));
        assertThat(anrufe.get(1).art()).isEqualTo(TelefonAnrufArt.VERPASST);
        assertThat(anrufe.get(1).name()).isNull();
        assertThat(anrufe.get(2)).isEqualTo(new AnlagenAnruf(LocalDateTime.of(2026, 9, 29, 10, 3),
                TelefonAnrufArt.AUSGEHEND, "09317654321", "2323", 72, null, null));
        assertThat(anrufe.get(3).art()).isEqualTo(TelefonAnrufArt.ANRUFBEANTWORTER);
        assertThat(anrufe.get(3).anrufbeantworter()).isEqualTo(1);
        assertThat(anrufe.get(3).gegenNummer()).isEmpty();
        assertThat(anrufe.get(3).eigeneNummer()).isEqualTo("2323");
        assertThat(anrufe.get(4).art()).isEqualTo(TelefonAnrufArt.ABGEWIESEN);
        assertThat(anrufe.get(4).eigeneNummer()).isEqualTo("555000");
    }

    @Test
    @DisplayName("XXE: DOCTYPE wird abgelehnt statt aufgelöst")
    void xxeWirdAbgelehnt() {
        String boese = """
                <?xml version="1.0"?>
                <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <root><Call><Type>1</Type><Caller>&xxe;</Caller><Date>29.09.26 11:55</Date></Call></root>
                """;
        assertThatThrownBy(() -> FritzXmlParser.anrufliste(xml(boese)))
                .isInstanceOf(TelefonAnlageException.class)
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(TelefonAnlageException.Grund.UNERWARTETE_ANTWORT);
    }

    @Test
    @DisplayName("Kein XML → UNERWARTETE_ANTWORT")
    void keinXml() {
        assertThatThrownBy(() -> FritzXmlParser.anrufliste(xml("<html>Login</")))
                .isInstanceOf(TelefonAnlageException.class);
    }

    @Test
    @DisplayName("Sprachnachrichten mit Pfad, AB-Index und Sitzung; Einträge ohne Pfad fallen weg")
    void sprachnachrichten() {
        String liste = """
                <Root>
                <Message><Index>0</Index><Tam>1</Tam><Called>2323</Called><Date>29.09.26 08:14</Date><Duration>0:01</Duration>
                  <Name></Name><New>1</New><Number>09311234567</Number><Path>/download.lua?path=/data/tam/rec/rec.1.000</Path></Message>
                <Message><Index>1</Index><Called>2323</Called><Date>28.09.26 20:00</Date><Number></Number>
                  <Path>/download.lua?path=/data/tam/rec/rec.1.001</Path></Message>
                <Message><Index>2</Index><Date>28.09.26 19:00</Date><Number>0931</Number></Message>
                </Root>
                """;
        List<AnlagenSprachnachricht> n = FritzXmlParser.sprachnachrichten(xml(liste), 1, "abc");
        assertThat(n).hasSize(2);
        assertThat(n.get(0)).isEqualTo(new AnlagenSprachnachricht(1, LocalDateTime.of(2026, 9, 29, 8, 14),
                "09311234567", "2323", "/download.lua?path=/data/tam/rec/rec.1.000", "abc"));
        assertThat(n.get(1).anrufbeantworter()).isEqualTo(1);
        assertThat(n.get(1).gegenNummer()).isEmpty();
    }

    @Test
    @DisplayName("Anrufbeantworter-Liste: Namen, Aktiv-Status, versteckte fallen weg")
    void anrufbeantworter() {
        String liste = """
                <List><Item><Index>0</Index><Display>1</Display><Enable>1</Enable><Name>AB Nacht</Name></Item>
                <Item><Index>1</Index><Display>1</Display><Enable>0</Enable><Name>AB Tag</Name></Item>
                <Item><Index>2</Index><Display>0</Display><Enable>0</Enable><Name></Name></Item>
                <Item><Index>3</Index><Enable>1</Enable><Name></Name></Item>
                <Item><Name>ohne Index</Name></Item></List>
                """;
        assertThat(FritzXmlParser.anrufbeantworterListe(xml(liste))).containsExactly(
                new AnlagenInfo.Anrufbeantworter(0, "AB Nacht", true),
                new AnlagenInfo.Anrufbeantworter(1, "AB Tag", false),
                new AnlagenInfo.Anrufbeantworter(3, "Anrufbeantworter 4", true));
    }

    @Test
    @DisplayName("Eigene Rufnummern ohne Doppelte und ohne SIP-Präfix")
    void rufnummern() {
        String liste = """
                <List><Item><Number>2323</Number><Type>eVoIP</Type></Item>
                <Item><Number>SIP: 555000</Number></Item><Item><Number>2323</Number></Item><Item><Number></Number></Item></List>
                """;
        assertThat(FritzXmlParser.rufnummern(xml(liste))).containsExactly("2323", "555000");
    }

    @Test
    @DisplayName("Hilfsfunktionen: Dauer, Datum, Typ, SIP-Präfix")
    void hilfen() {
        assertThat(FritzXmlParser.dauerMinuten("1:05")).isEqualTo(65);
        assertThat(FritzXmlParser.dauerMinuten("x:05")).isZero();
        assertThat(FritzXmlParser.dauerMinuten("5")).isZero();
        assertThat(FritzXmlParser.dauerMinuten(null)).isZero();
        assertThat(FritzXmlParser.datum("31.02.26 10:00")).isNull();
        assertThat(FritzXmlParser.datum(" ")).isNull();
        assertThat(FritzXmlParser.art("11")).isNull();
        assertThat(FritzXmlParser.art(null)).isNull();
        assertThat(FritzXmlParser.ohneSipPraefix(null)).isEmpty();
        assertThat(FritzXmlParser.ohneSipPraefix("SIP0: 2323")).isEqualTo("2323");
        assertThat(FritzXmlParser.ohneSipPraefix("1".repeat(60))).hasSize(40);
    }
}
