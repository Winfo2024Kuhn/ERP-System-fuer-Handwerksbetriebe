package org.example.email;

import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EingebetteteBilderTest {

    private static final byte[] BILD = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    private static final String B64 = Base64.getEncoder().encodeToString(BILD);

    @Test
    @DisplayName("HTML ohne eingebettete Bilder bleibt unverändert")
    void ohneBilder() {
        assertThat(EingebetteteBilder.herausloesen(null).html()).isNull();
        var e = EingebetteteBilder.herausloesen("<p>Max Mustermann</p><img src=\"https://example.com/a.png\">");
        assertThat(e.html()).isEqualTo("<p>Max Mustermann</p><img src=\"https://example.com/a.png\">");
        assertThat(e.bilder()).isEmpty();
    }

    @Test
    @DisplayName("data:-Bilder werden zu cid:-Verweisen mit passenden Bilddaten")
    void bilderWerdenHerausgeloest() {
        String html = "<img src=\"data:image/png;base64," + B64 + "\" width=\"100\"><img SRC='data:image/jpg;base64," + B64 + "'>";

        var e = EingebetteteBilder.herausloesen(html);

        assertThat(e.bilder()).hasSize(2);
        assertThat(e.bilder().get(0).contentType()).isEqualTo("image/png");
        assertThat(e.bilder().get(1).contentType()).isEqualTo("image/jpeg");
        assertThat(e.bilder().get(0).daten()).isEqualTo(BILD);
        assertThat(e.html()).doesNotContain("base64")
                .contains("src=\"cid:" + e.bilder().get(0).cid() + "\" width=\"100\"")
                .contains("SRC='cid:" + e.bilder().get(1).cid() + "'");
    }

    @Test
    @DisplayName("Base64 mit Zeilenumbrüchen wird verstanden, kaputtes bleibt stehen")
    void zeilenumbruecheUndKaputtes() {
        var mitUmbruch = EingebetteteBilder.herausloesen("<img src=\"data:image/gif;base64," + B64.substring(0, 4) + "\n" + B64.substring(4) + "\">");
        assertThat(mitUmbruch.bilder()).singleElement().satisfies(b -> assertThat(b.daten()).isEqualTo(BILD));

        String kaputt = "<img src=\"data:image/png;base64,A\">";
        var e = EingebetteteBilder.herausloesen(kaputt);
        assertThat(e.html()).isEqualTo(kaputt);
        assertThat(e.bilder()).isEmpty();
    }

    @Test
    @DisplayName("SVG und Text außerhalb von src bleiben unangetastet")
    void nurErlaubteTypenInSrc() {
        String html = "<p>data:image/png;base64," + B64 + "</p><img src=\"data:image/svg+xml;base64," + B64 + "\">";
        var e = EingebetteteBilder.herausloesen(html);
        assertThat(e.bilder()).isEmpty();
        assertThat(e.html()).isEqualTo(html);
    }

    @Test
    @DisplayName("Im Mail-Teil: HTML zuerst, danach je Bild ein Inline-Teil mit Content-ID")
    void mimeTeile() throws Exception {
        MimeMultipart related = new MimeMultipart("related");

        EingebetteteBilder.fuegeHtmlHinzu(related, "<p>Gruß</p><img src=\"data:image/webp;base64," + B64 + "\">"
                + "<img src=\"data:image/jpeg;base64," + B64 + "\"><img src=\"data:image/gif;base64," + B64 + "\">"
                + "<img src=\"data:image/png;base64," + B64 + "\">");

        assertThat(related.getCount()).isEqualTo(5);
        assertThat((String) related.getBodyPart(0).getContent()).contains("cid:");
        BodyPart bild = related.getBodyPart(1);
        assertThat(bild.getDisposition()).isEqualTo("inline");
        assertThat(bild.getHeader("Content-ID")[0]).startsWith("<bild-").endsWith("@erp>");
        assertThat(bild.getFileName()).isEqualTo("bild-1.webp");
        assertThat(related.getBodyPart(2).getFileName()).isEqualTo("bild-2.jpg");
        assertThat(related.getBodyPart(3).getFileName()).isEqualTo("bild-3.gif");
        assertThat(related.getBodyPart(4).getFileName()).isEqualTo("bild-4.png");
    }

    @Test
    @DisplayName("Leerer Text wird zu leerem HTML-Teil")
    void leererText() throws Exception {
        MimeMultipart related = new MimeMultipart("related");
        EingebetteteBilder.fuegeHtmlHinzu(related, null);
        assertThat(related.getCount()).isEqualTo(1);
        assertThat((String) related.getBodyPart(0).getContent()).isEmpty();
    }
}
