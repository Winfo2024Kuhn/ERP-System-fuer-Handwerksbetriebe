package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class HtmlKlartextTest {

    @Test
    void entferntEinfacheTags() {
        assertThat(HtmlKlartext.entferneTags("<p>Hallo <b>Max Mustermann</b></p>")).isEqualTo("Hallo Max Mustermann");
    }

    @Test
    void entferntGeschachtelteTagsBisZumFixpunkt() {
        assertThat(HtmlKlartext.entferneTags("<<script>script>alert(1)</script>")).isEqualTo("alert(1)");
        assertThat(HtmlKlartext.entferneTags("<scr<script>ipt>bad</scr</script>ipt>")).isEqualTo("bad");
    }

    @Test
    void setztErsatzWoertlichEinAuchMitSonderzeichenDerRegexErsetzung() {
        assertThat(HtmlKlartext.entferneTags("<h1>Titel</h1><p>Absatz</p>", " ")).isEqualTo(" Titel  Absatz ");
        assertThat(HtmlKlartext.entferneTags("a<br>b", "$1\\")).isEqualTo("a$1\\b");
        assertThat(HtmlKlartext.entferneTags("a<br>b", null)).isEqualTo("ab");
    }

    @Test
    void entferntBeiUebermaessigerSchachtelungFailClosedAlleKlammern() {
        // Jeder Durchlauf löst nur eine Ebene "<>" auf; nach 200 Durchläufen greift die Notbremse.
        String verschachtelt = "<".repeat(250) + ">".repeat(250) + "Tor";
        assertThat(HtmlKlartext.entferneTags(verschachtelt)).isEqualTo("Tor");
    }

    @Test
    void bleibtBeiVielenOffenenKlammernSchnell() {
        String ohneSchluss = "<".repeat(50_000) + "x";
        String ergebnis = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> HtmlKlartext.entferneTags(ohneSchluss));
        assertThat(ergebnis).isEqualTo(ohneSchluss);
    }

    @Test
    void entpacktAlleBekanntenEntitaeten() {
        assertThat(HtmlKlartext.entpackeEntitaeten("Treppe&nbsp;&amp; Gel&auml;nder &lt;90&gt; &quot;A&quot; &#39;B&#39;"))
                .isEqualTo("Treppe & Gel&auml;nder <90> \"A\" 'B'");
    }

    @Test
    void entpacktGenauEinmalStattDoppelt() {
        assertThat(HtmlKlartext.entpackeEntitaeten("&amp;lt;script&amp;gt;")).isEqualTo("&lt;script&gt;");
        assertThat(HtmlKlartext.entpackeEntitaeten("&amp;amp;")).isEqualTo("&amp;");
    }

    @Test
    void zuTextEntferntErstTagsUndEntpacktDann() {
        assertThat(HtmlKlartext.zuText("<p>Max &amp; Erika</p><p>&lt;b&gt;fett&lt;/b&gt;</p>"))
                .isEqualTo("Max & Erika<b>fett</b>");
        assertThat(HtmlKlartext.zuText("Zeile 1\n  <i>Zeile 2</i>")).isEqualTo("Zeile 1\n  Zeile 2");
    }

    @Test
    void behandeltLeereEingabenSauber() {
        assertThat(HtmlKlartext.entferneTags(null)).isEmpty();
        assertThat(HtmlKlartext.entferneTags("")).isEmpty();
        assertThat(HtmlKlartext.entpackeEntitaeten(null)).isEmpty();
        assertThat(HtmlKlartext.entpackeEntitaeten("")).isEmpty();
        assertThat(HtmlKlartext.zuText(null)).isEmpty();
    }
}
