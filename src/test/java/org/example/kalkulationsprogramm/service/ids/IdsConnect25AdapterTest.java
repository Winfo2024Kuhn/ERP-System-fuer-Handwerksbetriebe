package org.example.kalkulationsprogramm.service.ids;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdsConnect25AdapterTest {

    private final IdsConnect25Adapter adapter = new IdsConnect25Adapter();

    private static String warenkorb(String rueckgabeKz, String... items) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Warenkorb>
                  <WarenkorbInfo><Date>2026-10-31</Date><Time>14:05:00</Time>
                    <RueckgabeKZ>%s</RueckgabeKZ><Version>2.5</Version></WarenkorbInfo>
                  <Order><OrderInfo><Cur>EUR</Cur></OrderInfo>%s</Order>
                </Warenkorb>
                """.formatted(rueckgabeKz, String.join("", items));
    }

    private IdsWarenkorb lies(String xml) {
        return adapter.leseWarenkorb(new IdsRueckgabe(xml.getBytes(StandardCharsets.UTF_8), null));
    }

    @Test
    @DisplayName("Anmeldeformular nach IDS-Connect 2.5: action=WKE, version, Zugangsdaten, Rücksprung, multipart")
    void anmeldeFormular() {
        ShopFormular f = adapter.anmeldeFormular(
                new IdsZugangsdaten("https://shop.example.com/ids", "4711", "max.mustermann", "geheim"),
                "https://erp.example.com/api/ids/rueckgabe/abc");

        assertThat(adapter.protokoll()).isEqualTo(IdsProtokoll.IDS_CONNECT_2_5);
        assertThat(f.action()).isEqualTo("https://shop.example.com/ids");
        assertThat(f.enctype()).isEqualTo("multipart/form-data");
        assertThat(f.felder()).containsEntry("action", "WKE").containsEntry("version", "2.5")
                .containsEntry("kndnr", "4711").containsEntry("name_kunde", "max.mustermann")
                .containsEntry("pw_kunde", "geheim")
                .containsEntry("hookurl", "https://erp.example.com/api/ids/rueckgabe/abc");
        assertThat(f.toString()).doesNotContain("geheim");
    }

    @Test
    @DisplayName("Variante „Nur Zugangsfelder“ (Würth-Zugangsblatt): ohne action und version, Rückweg gleich")
    void nurZugangsfelder() {
        IdsNurZugangsfelderAdapter wuerth = new IdsNurZugangsfelderAdapter();
        ShopFormular f = wuerth.anmeldeFormular(
                new IdsZugangsdaten("https://shop.example.com/ids", "4711", "max.mustermann", "geheim"), "https://erp/hook");

        assertThat(wuerth.protokoll()).isEqualTo(IdsProtokoll.IDS_NUR_ZUGANGSFELDER);
        assertThat(f.felder()).containsOnlyKeys("kndnr", "name_kunde", "pw_kunde", "hookurl");
        assertThat(f.enctype()).isEqualTo("multipart/form-data");
        assertThat(wuerth.leseWarenkorb(new IdsRueckgabe(null,
                warenkorb("Warenkorbrückgabe", "<OrderItem><ArtNo>X</ArtNo><Qty>1</Qty><QU>PCE</QU></OrderItem>"))).zeilen()).hasSize(1);
    }

    @Test
    @DisplayName("Leere Zugangsfelder werden gar nicht erst mitgeschickt")
    void anmeldeFormularOhneLeereFelder() {
        ShopFormular f = adapter.anmeldeFormular(new IdsZugangsdaten("https://shop.example.com", null, " ", null), "https://erp");
        assertThat(f.felder()).containsOnlyKeys("action", "version", "hookurl");
    }

    @Test
    @DisplayName("Liest Artikel, rechnet den Preis je Einheit aus PriceBasis und übersetzt die Einheit")
    void liestWarenkorb() {
        IdsWarenkorb k = lies(warenkorb("Warenkorbrückgabe",
                "<OrderItem><ArtNo>0903 100 100</ArtNo><Qty>100.00</Qty><QU>PCE</QU><Kurztext>Schraube 6x100</Kurztext>"
                        + "<OfferPrice>15.00</OfferPrice><NetPrice>12.50</NetPrice><PriceBasis>100</PriceBasis><EAN>4012345678901</EAN></OrderItem>",
                "<OrderItem><ArtNo>0892 161</ArtNo><Qty>5</Qty><QU>KRT</QU><Langtext>Silikon transparent\nzweite Zeile</Langtext>"
                        + "<NetPrice>8,40</NetPrice></OrderItem>"));

        assertThat(k.mitBestellung()).isFalse();
        assertThat(k.zeilen()).hasSize(2);
        IdsWarenkorb.Zeile schraube = k.zeilen().get(0);
        assertThat(schraube.artikelnummer()).isEqualTo("0903 100 100");
        assertThat(schraube.bezeichnung()).isEqualTo("Schraube 6x100");
        assertThat(schraube.menge()).isEqualByComparingTo("100");
        assertThat(schraube.einheit()).isEqualTo("Stk");
        assertThat(schraube.einkaufspreis()).isEqualByComparingTo("0.125");
        assertThat(schraube.ean()).isEqualTo("4012345678901");
        assertThat(schraube.hinweis()).isNull();

        IdsWarenkorb.Zeile silikon = k.zeilen().get(1);
        assertThat(silikon.bezeichnung()).isEqualTo("Silikon transparent");
        assertThat(silikon.langtext()).contains("zweite Zeile");
        assertThat(silikon.einheit()).isEqualTo("Kartusche");
        assertThat(silikon.einkaufspreis()).isEqualByComparingTo("1.68"); // 8,40 für 5 Stück
    }

    @Test
    @DisplayName("Fehlender Preis bleibt leer, Hinweis und Fehlertext des Shops werden übernommen, ungültige Menge wird 1 mit Hinweis")
    void fehlendeAngaben() {
        IdsWarenkorb k = lies(warenkorb("Warenkorbrückgabe",
                "<OrderItem><ArtNo>A1</ArtNo><Qty>abc</Qty><QU></QU><Hinweis>Nicht lieferbar</Hinweis>"
                        + "<Fehlertext>Preis auf Anfrage</Fehlertext></OrderItem>"));

        IdsWarenkorb.Zeile z = k.zeilen().get(0);
        assertThat(z.einkaufspreis()).isNull();
        assertThat(z.menge()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(z.einheit()).isEqualTo("Stk");
        assertThat(z.bezeichnung()).isEqualTo("Artikel A1");
        assertThat(z.hinweis()).contains("keine gültige Menge").contains("Nicht lieferbar").contains("Preis auf Anfrage");
    }

    @Test
    @DisplayName("Riesige oder unlesbare Werte sprengen nichts: Menge 1 mit Hinweis, Preis leer mit Hinweis")
    void riesigeWerte() {
        IdsWarenkorb k = lies(warenkorb("Warenkorbrückgabe",
                "<OrderItem><ArtNo>G1</ArtNo><Qty>1E999999999</Qty><QU>PCE</QU><NetPrice>99999999999999</NetPrice></OrderItem>"));
        IdsWarenkorb.Zeile z = k.zeilen().get(0);
        assertThat(z.menge()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(z.einkaufspreis()).isNull();
        assertThat(z.hinweis()).contains("keine gültige Menge").contains("unlesbaren Preis");
    }

    @Test
    @DisplayName("Echte Würth-Zeile: Nummer mit Füll-Leerzeichen, Preis je 100 Stück")
    void wuerthZeile() {
        IdsWarenkorb k = lies(warenkorb("Warenkorbrückgabe",
                "<OrderItem><ArtNo>032225      500</ArtNo><Qty>500.00</Qty><QU>PCE</QU><Kurztext>MU-6KT-DIN934-A2-SW5-M2,5</Kurztext>"
                        + "<NetPrice>9.05</NetPrice><PriceBasis>100</PriceBasis><EAN>4012345678901</EAN></OrderItem>"));
        IdsWarenkorb.Zeile z = k.zeilen().get(0);
        assertThat(z.artikelnummer()).isEqualTo("032225");
        assertThat(z.einkaufspreis()).isEqualByComparingTo("0.0905");
        assertThat(z.menge()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("Würth-Silikon: VE an der Nummer fällt weg, Zeilenpreis 115,20 für 24 Stück = 4,80 je Stück")
    void wuerthSilikon() {
        IdsWarenkorb.Zeile z = lies(warenkorb("Warenkorbrückgabe",
                "<OrderItem><ArtNo>08925304     24</ArtNo><Qty>24</Qty><QU>PCE</QU><Kurztext>SILDI-NEUT-BETONGRAU-310ML</Kurztext>"
                        + "<NetPrice>115.20</NetPrice><PriceBasis>1</PriceBasis></OrderItem>")).zeilen().get(0);
        assertThat(z.artikelnummer()).isEqualTo("08925304");
        assertThat(z.einkaufspreis()).isEqualByComparingTo("4.80");
        assertThat(IdsConnect25Adapter.ohneVerpackungseinheit("0903 100 100")).isEqualTo("0903 100 100");
        assertThat(IdsConnect25Adapter.ohneVerpackungseinheit(" A1 ")).isEqualTo("A1");
    }

    @Test
    @DisplayName("Formularfelder in fester Reihenfolge")
    void feldReihenfolge() {
        ShopFormular f = adapter.anmeldeFormular(new IdsZugangsdaten("https://shop", "1", "max", "pw"), "https://erp/h");
        assertThat(f.felder().keySet()).containsExactly("action", "version", "kndnr", "name_kunde", "pw_kunde", "hookurl");
    }

    @Test
    @DisplayName("„Warenkorbrückgabe mit Bestellung“ wird erkannt; Zeilen ohne Artikelnummer werden übergangen")
    void mitBestellung() {
        IdsWarenkorb k = lies(warenkorb("Warenkorbrückgabe mit Bestellung",
                "<OrderItem><Qty>1</Qty></OrderItem>",
                "<OrderItem><ArtNo>B2</ArtNo><Qty>2</Qty><QU>MTR</QU><NetPrice>3.00</NetPrice><PriceBasis>0</PriceBasis></OrderItem>"));
        assertThat(k.mitBestellung()).isTrue();
        assertThat(k.zeilen()).singleElement().satisfies(z -> {
            assertThat(z.artikelnummer()).isEqualTo("B2");
            assertThat(z.einheit()).isEqualTo("m");
            assertThat(z.einkaufspreis()).isEqualByComparingTo("1.50"); // 3,00 für 2 m
        });
    }

    @Test
    @DisplayName("HTML im Kurztext wird entfernt, überlange Texte werden gekürzt")
    void bereinigtTexte() {
        String lang = "x".repeat(300);
        IdsWarenkorb k = lies(warenkorb("Warenkorbrückgabe",
                "<OrderItem><ArtNo>C3</ArtNo><Qty>1</Qty><QU>PCE</QU><Kurztext>&lt;script&gt;alert(1)&lt;/script&gt;Dübel</Kurztext></OrderItem>",
                "<OrderItem><ArtNo>C4</ArtNo><Qty>1</Qty><QU>PCE</QU><Kurztext>" + lang + "</Kurztext></OrderItem>"));
        assertThat(k.zeilen().get(0).bezeichnung()).isEqualTo("alert(1)Dübel");
        assertThat(k.zeilen().get(1).bezeichnung()).hasSize(255);
    }

    @Test
    @DisplayName("Kodierung aus der XML-Deklaration (ISO-8859-1) wird beachtet; Text aus url-kodiertem Formular geht auch")
    void kodierung() {
        String xml = warenkorb("Warenkorbrückgabe", "<OrderItem><ArtNo>D5</ArtNo><Qty>1</Qty><QU>PCE</QU><Kurztext>Dübel Ø 8</Kurztext></OrderItem>")
                .replace("UTF-8", "ISO-8859-1");
        IdsWarenkorb k = adapter.leseWarenkorb(new IdsRueckgabe(xml.getBytes(StandardCharsets.ISO_8859_1), null));
        assertThat(k.zeilen().get(0).bezeichnung()).isEqualTo("Dübel Ø 8");

        IdsWarenkorb ausText = adapter.leseWarenkorb(new IdsRueckgabe(null, xml));
        assertThat(ausText.zeilen().get(0).bezeichnung()).isEqualTo("Dübel Ø 8");
    }

    @Test
    @DisplayName("Würth/Intershop: Leerzeilen und XHTML-DOCTYPE vor dem Warenkorb werden hingenommen")
    void wuerthDoctype() {
        // So beginnt die Antwort von Würth auf action=SV; der Warenkorb kommt aus derselben Vorlage.
        String xml = "\n\n<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" "
                + "\"https://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n\n"
                + "<Warenkorb><Order><OrderItem><ArtNo>0892 161 1</ArtNo><Qty>5</Qty><QU>PCE</QU></OrderItem></Order></Warenkorb>";
        assertThat(lies(xml).zeilen()).singleElement().satisfies(z -> assertThat(z.artikelnummer()).isEqualTo("0892 161 1"));
        assertThat(adapter.leseWarenkorb(new IdsRueckgabe(null, xml)).zeilen()).hasSize(1);
    }

    @Test
    @DisplayName("XXE: ein DOCTYPE wird abgelehnt, statt Dateien vom Server einzulesen")
    void xxe() {
        String boese = """
                <?xml version="1.0"?>
                <!DOCTYPE Warenkorb [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <Warenkorb><Order><OrderItem><ArtNo>&x;</ArtNo><Qty>1</Qty><QU>PCE</QU></OrderItem></Order></Warenkorb>
                """;
        assertThatThrownBy(() -> lies(boese)).isInstanceOf(IdsWarenkorbUngueltig.class).hasMessageContaining("kein lesbares");
    }

    @Test
    @DisplayName("Leer, kein XML, falsche Wurzel, ohne Artikel oder zu viele Artikel: verständlicher Fehler")
    void ungueltig() {
        assertThatThrownBy(() -> adapter.leseWarenkorb(new IdsRueckgabe(null, " "))).hasMessageContaining("leeren Warenkorb");
        assertThatThrownBy(() -> adapter.leseWarenkorb(null)).hasMessageContaining("leeren Warenkorb");
        assertThatThrownBy(() -> lies("kein xml")).hasMessageContaining("kein lesbares");
        assertThatThrownBy(() -> lies("<Bestellung/>")).hasMessageContaining("keinen IDS-Warenkorb");
        assertThatThrownBy(() -> lies(warenkorb("Warenkorbrückgabe"))).hasMessageContaining("keine Artikel");
        String item = "<OrderItem><ArtNo>E</ArtNo><Qty>1</Qty><QU>PCE</QU></OrderItem>";
        assertThatThrownBy(() -> lies(warenkorb("Warenkorbrückgabe", item.repeat(IdsConnect25Adapter.MAX_ZEILEN + 1))))
                .hasMessageContaining("mehr als 500");
    }

    @Test
    @DisplayName("Zahlen: Punkt, Komma, Tausenderkomma, Unsinn")
    void zahlen() {
        assertThat(IdsConnect25Adapter.zahl("1234.50")).isEqualByComparingTo("1234.50");
        assertThat(IdsConnect25Adapter.zahl("12,5")).isEqualByComparingTo("12.5");
        assertThat(IdsConnect25Adapter.zahl("1,234.50")).isEqualByComparingTo("1234.50");
        assertThat(IdsConnect25Adapter.zahl("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(IdsConnect25Adapter.zahl("1E999999999")).isNull();
        assertThat(IdsConnect25Adapter.zahl("-5")).isNull();
        assertThat(IdsConnect25Adapter.zahl("1".repeat(21))).isNull();
        assertThat(IdsConnect25Adapter.einzelpreis(new BigDecimal("1000000000"), null, BigDecimal.ONE)).isNull();
        assertThat(IdsConnect25Adapter.einzelpreis(new BigDecimal("10"), new BigDecimal("10000000"), new BigDecimal("2"))).isEqualByComparingTo("5");
        assertThat(IdsConnect25Adapter.zahl("'; DROP TABLE x; --")).isNull();
        assertThat(IdsConnect25Adapter.zahl(null)).isNull();
        assertThat(IdsConnect25Adapter.einzelpreis(new BigDecimal("-1"), null, BigDecimal.ONE)).isNull();
        assertThat(IdsConnect25Adapter.einzelpreis(new BigDecimal("10"), new BigDecimal("3"), BigDecimal.ONE)).isEqualByComparingTo("3.3333");
        // Ohne Preisbasis ist NetPrice der Preis der ganzen Zeile (Spezifikation, so schickt es Würth)
        assertThat(IdsConnect25Adapter.einzelpreis(new BigDecimal("691.20"), null, new BigDecimal("144"))).isEqualByComparingTo("4.80");
        assertThat(IdsConnect25Adapter.einzelpreis(new BigDecimal("691.20"), BigDecimal.ONE, new BigDecimal("144"))).isEqualByComparingTo("4.80");
    }
}
