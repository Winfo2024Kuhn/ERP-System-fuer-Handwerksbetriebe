package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailButtonHtmlTest {

    private static String button(String href) {
        return "<table data-email-button=\"\"><tbody><tr><td bgcolor=\"#500010\">"
                + "<a href=\"" + href + "\">Jetzt Bewertung abgeben</a></td></tr></tbody></table>";
    }

    @Test
    void htmlOhneButtonBleibtUnveraendert() {
        String html = "<p>Sehr geehrter Herr Mustermann,</p><p>   <b>danke</b></p>";

        assertThat(EmailButtonHtml.entferneButtonsOhneZiel(html)).isSameAs(html);
    }

    @Test
    void nullBleibtNull() {
        assertThat(EmailButtonHtml.entferneButtonsOhneZiel(null)).isNull();
        assertThat(EmailButtonHtml.alsSichereAdresse(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://www.beispiel.de", "http://beispiel.de/seite", "mailto:info@beispiel.de", "tel:+49123456"})
    void buttonMitKlickbaremZielBleibtErhalten(String href) {
        String html = "<p>Text</p>" + button(href);

        assertThat(EmailButtonHtml.entferneButtonsOhneZiel(html)).isSameAs(html);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "https://", "javascript:alert(1)", "data:text/html,x", "{{REVIEW_URL}}"})
    void buttonOhneGueltigesZielWirdEntfernt(String href) {
        String html = "<p>Text</p>" + button(href) + "<p>Gruß</p>";

        assertThat(EmailButtonHtml.entferneButtonsOhneZiel(html)).isEqualTo("<p>Text</p><p>Gruß</p>");
    }

    @Test
    void nurDerUngueltigeVonZweiButtonsWirdEntfernt() {
        String html = button("") + button("https://www.beispiel.de");

        String result = EmailButtonHtml.entferneButtonsOhneZiel(html);

        assertThat(result).contains("https://www.beispiel.de").containsOnlyOnce("data-email-button");
    }

    @Test
    void buttonOhneLinkWirdEntfernt() {
        String html = "<table data-email-button=\"\"><tbody><tr><td>Nur Text</td></tr></tbody></table><p>Rest</p>";

        assertThat(EmailButtonHtml.entferneButtonsOhneZiel(html)).isEqualTo("<p>Rest</p>");
    }

    @Test
    void sichereAdresseLaesstNurWebAdressenZu() {
        assertThat(EmailButtonHtml.alsSichereAdresse("  https://g.page/r/beispiel  ")).isEqualTo("https://g.page/r/beispiel");
        assertThat(EmailButtonHtml.alsSichereAdresse("javascript:alert(1)")).isEmpty();
        assertThat(EmailButtonHtml.alsSichereAdresse("www.beispiel.de")).isEmpty();
        assertThat(EmailButtonHtml.alsSichereAdresse("https://bei spiel.de")).isEmpty();
    }

    @Test
    void sichereAdresseMaskiertAttributSprengendeZeichen() {
        assertThat(EmailButtonHtml.alsSichereAdresse("https://x.de/\"><script>"))
                .isEqualTo("https://x.de/%22%3E%3Cscript%3E");
    }
}
