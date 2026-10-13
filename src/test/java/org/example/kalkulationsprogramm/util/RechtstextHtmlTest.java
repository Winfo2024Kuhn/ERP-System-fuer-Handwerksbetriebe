package org.example.kalkulationsprogramm.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RechtstextHtmlTest {

    @Test
    void bereinige_behaeltNurPositivliste() {
        String html = "<h3 style=\"color:red\">Titel</h3><p class=\"x\">Max <b>Mustermann</b> <em>k</em><br>"
                + "<a href=\"javascript:alert(1)\">Link</a></p><ul><li><p>eins</p></li></ul><ol><li>zwei</li></ol>"
                + "<h1>gross</h1><img src=x onerror=alert(1)><script>alert(1)</script><iframe src=\"x\"></iframe>";

        assertThat(RechtstextHtml.bereinige(html)).isEqualTo(
                "<h3>Titel</h3><p>Max <b>Mustermann</b> <em>k</em><br>Link</p><ul><li><p>eins</p></li></ul>"
                        + "<ol><li>zwei</li></ol>gross");
    }

    @Test
    void bereinige_null() {
        assertThat(RechtstextHtml.bereinige(null)).isEmpty();
    }

    @Test
    void istLeer() {
        assertThat(RechtstextHtml.istLeer(null)).isTrue();
        assertThat(RechtstextHtml.istLeer("<p> </p><br>")).isTrue();
        assertThat(RechtstextHtml.istLeer("<p>&nbsp;</p>")).isTrue();
        assertThat(RechtstextHtml.istLeer("<p>x</p>")).isFalse();
    }
}
