package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Titel aus GAEB-Kurz- und Langtexten: Tags weg, Sonderzeichen genau einmal entpackt. */
class GaebImportServiceTest {

    private final GaebImportService service = new GaebImportService();

    private List<Map<String, Object>> importiere(String items) {
        String xml = "<GAEB><Award><BoQ><BoQBody><Itemlist>" + items + "</Itemlist></BoQBody></BoQ></Award></GAEB>";
        return service.parseGaebXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String position(String rno, String kurztext, String langtext) {
        String outline = kurztext == null ? ""
                : "<OutlineText><OutlTxt><TextOutlTxt>" + kurztext + "</TextOutlTxt></OutlTxt></OutlineText>";
        return "<Item RNoPart=\"" + rno + "\"><Qty>2</Qty><QU>m</QU><Description><CompleteText>"
                + "<DetailTxt><Text>" + langtext + "</Text></DetailTxt>" + outline
                + "</CompleteText></Description></Item>";
    }

    @Test
    void kurztextAlsTitelBehaeltMaskierteSonderzeichenAlsText() {
        // "&lt;b&gt;" im XML ist Text und kommt als "<b>" an, "&amp;lt;" als "&lt;" – beides kein HTML.
        List<Map<String, Object>> bloecke = importiere(position("0001",
                "<p>Handlauf &lt;b&gt; &amp;lt;Edelstahl&amp;gt;</p>", "<p>Langtext</p>"));

        // Früher machte das doppelte Entpacken aus &lt;Edelstahl&gt; ein <Edelstahl>.
        assertThat(bloecke.get(0)).containsEntry("title", "Handlauf <b> &lt;Edelstahl&gt;");
    }

    @Test
    void ohneKurztextWirdDieErsteZeileDesLangtextsZumTitel() {
        List<Map<String, Object>> bloecke = importiere(position("0002", null,
                "<p>Treppe &amp; Geländer</p><p>aus Stahl</p>"));

        assertThat(bloecke.get(0)).containsEntry("title", "Treppe & Geländer aus Stahl");
    }

    @Test
    void kuerztLangeTitel() {
        String lang = "Stahlträger ".repeat(20);
        List<Map<String, Object>> bloecke = importiere(
                position("0003", "<p>" + lang + "</p>", "<p>x</p>")
                        + position("0004", null, "<p>" + lang + "</p>"));

        assertThat((String) bloecke.get(0).get("title")).hasSize(153).endsWith("...");
        assertThat((String) bloecke.get(1).get("title")).hasSize(122).endsWith("...");
    }
}
