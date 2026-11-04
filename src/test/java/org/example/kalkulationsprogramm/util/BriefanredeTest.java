package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.util.HtmlUtils;

class BriefanredeTest {

    private static final String DAMEN_HERREN = "Sehr geehrte Damen und Herren";
    private static final UnaryOperator<String> OHNE_MASKE = UnaryOperator.identity();

    // ---------- Anredetext (Platzhalter {{ANREDE}} allein) ----------

    @ParameterizedTest
    @CsvSource({
            "HERR, Sehr geehrter Herr",
            "FRAU, Sehr geehrte Frau",
            "FAMILIE, Sehr geehrte Familie",
            "FIRMA, Sehr geehrte Damen und Herren",
            "DAMEN_HERREN, Sehr geehrte Damen und Herren"
    })
    @DisplayName("Anredetext je Anrede-Art")
    void anredeText(Anrede art, String erwartet) {
        assertThat(Briefanrede.anredeText(art)).isEqualTo(erwartet);
    }

    @Test
    @DisplayName("Anredetext ohne Anrede-Art: Damen und Herren")
    void anredeTextOhneArt() {
        assertThat(Briefanrede.anredeText(null)).isEqualTo(DAMEN_HERREN);
    }

    // ---------- Briefanrede (Anrede + Name) ----------

    @ParameterizedTest
    @CsvSource({
            "HERR, Sehr geehrter Herr Max Mustermann",
            "FRAU, Sehr geehrte Frau Max Mustermann",
            "FAMILIE, Sehr geehrte Familie Max Mustermann"
    })
    @DisplayName("Herr/Frau/Familie mit Name: Anredetext + Name")
    void personMitName(Anrede art, String erwartet) {
        assertThat(Briefanrede.briefanrede(art, "Max Mustermann")).isEqualTo(erwartet);
    }

    @Test
    @DisplayName("Name wird getrimmt")
    void nameGetrimmt() {
        assertThat(Briefanrede.briefanrede(Anrede.FRAU, "  Erika Musterfrau \t"))
                .isEqualTo("Sehr geehrte Frau Erika Musterfrau");
    }

    @ParameterizedTest
    @EnumSource(value = Anrede.class, names = {"FIRMA", "DAMEN_HERREN"})
    @DisplayName("Firma/Damen und Herren: Name fällt weg")
    void firmaOhneName(Anrede art) {
        assertThat(Briefanrede.briefanrede(art, "Landkreis Musterstadt Abfallwirtschaft")).isEqualTo(DAMEN_HERREN);
    }

    @Test
    @DisplayName("Ohne Anrede-Art: Damen und Herren, auch mit Name")
    void ohneArtMitName() {
        assertThat(Briefanrede.briefanrede(null, "Max Mustermann")).isEqualTo(DAMEN_HERREN);
    }

    @ParameterizedTest
    @EnumSource(value = Anrede.class, names = {"HERR", "FRAU", "FAMILIE"})
    @DisplayName("Herr/Frau/Familie ohne Name: Damen und Herren statt 'Sehr geehrter Herr ,'")
    void personOhneName(Anrede art) {
        assertThat(Briefanrede.briefanrede(art, null)).isEqualTo(DAMEN_HERREN);
        assertThat(Briefanrede.briefanrede(art, "")).isEqualTo(DAMEN_HERREN);
        assertThat(Briefanrede.briefanrede(art, "   ")).isEqualTo(DAMEN_HERREN);
    }

    @ParameterizedTest
    @CsvSource({
            "HERR, Herr Müller, Sehr geehrter Herr Müller",
            "HERR, herr Müller, Sehr geehrter Herr Müller",
            "FRAU, FRAU Musterfrau, Sehr geehrte Frau Musterfrau",
            "FAMILIE, Familie Mustermann, Sehr geehrte Familie Mustermann",
            "HERR, '  Herr   Müller ', Sehr geehrter Herr Müller"
    })
    @DisplayName("Name mit Anrede-Wort davor: kein doppeltes 'Herr Herr'")
    void anredeWortImNamen(Anrede art, String name, String erwartet) {
        assertThat(Briefanrede.briefanrede(art, name)).isEqualTo(erwartet);
    }

    @Test
    @DisplayName("Geschütztes Leerzeichen im Namen zählt wie ein normales (wie im Frontend)")
    void geschuetztesLeerzeichenImNamen() {
        assertThat(Briefanrede.briefanrede(Anrede.HERR, "Herr\u00A0Müller\u00A0")).isEqualTo("Sehr geehrter Herr Müller");
        assertThat(Briefanrede.briefanrede(Anrede.FRAU, "\u00A0")).isEqualTo(DAMEN_HERREN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Herr", "herr ", "Frau", "Familie", " Familie\u00A0"})
    @DisplayName("Name nur 'Herr'/'Frau'/'Familie' ohne Nachnamen gilt als leer")
    void nurAnredeWortAlsName(String name) {
        assertThat(Briefanrede.briefanrede(Anrede.HERR, name)).isEqualTo(DAMEN_HERREN);
    }

    @Test
    @DisplayName("Namen, die nur mit Herr/Frau beginnen, bleiben ganz")
    void nameBeginntMitHerrOhneLeerzeichen() {
        assertThat(Briefanrede.briefanrede(Anrede.HERR, "Herrmann")).isEqualTo("Sehr geehrter Herr Herrmann");
        assertThat(Briefanrede.briefanrede(Anrede.FRAU, "Frauke Muster")).isEqualTo("Sehr geehrte Frau Frauke Muster");
    }

    // ---------- Kontext ----------

    @Test
    @DisplayName("inKontext legt Anredetext und Anrede-Art ab; artAus liest sie zurück")
    void kontext() {
        Map<String, String> ctx = new HashMap<>();
        Briefanrede.inKontext(ctx, Anrede.HERR);
        assertThat(ctx).containsEntry("ANREDE", "Sehr geehrter Herr");
        assertThat(Briefanrede.artAus(ctx)).isEqualTo(Anrede.HERR);

        Map<String, String> ohne = new HashMap<>();
        Briefanrede.inKontext(ohne, null);
        assertThat(ohne).containsEntry("ANREDE", DAMEN_HERREN);
        assertThat(Briefanrede.artAus(ohne)).isNull();
        assertThat(Briefanrede.artAus(new HashMap<>())).isNull();
        assertThat(Briefanrede.artAus(null)).isNull();
    }

    @Test
    @DisplayName("Der interne Schlüssel ist kein gültiger Platzhalter-Name")
    void schluesselKeinPlatzhalter() {
        assertThat(Briefanrede.ART_SCHLUESSEL).doesNotMatch("[A-Za-z0-9_äöüÄÖÜß]+");
    }

    // ---------- Paar-Erkennung beim Ersetzen ----------

    /** Einzel-Platzhalter wie im Dokument-Ersetzer: Groß/klein egal, Unbekanntes bleibt stehen. */
    private static final Briefanrede.Muster MUSTER =
            Briefanrede.muster("\\{\\{\\s*+([a-zA-Z0-9_]++)\\s*+\\}\\}");

    private static String ersetze(String text, Map<String, String> ctx, UnaryOperator<String> maskierung) {
        return Briefanrede.ersetze(text, ctx, MUSTER,
                token -> ctx == null ? null : ctx.get(token.toUpperCase(java.util.Locale.ROOT)), maskierung);
    }

    private static Map<String, String> kontext(Anrede art, String kundenname, String ansprechpartner) {
        Map<String, String> ctx = new HashMap<>();
        Briefanrede.inKontext(ctx, art);
        ctx.put("KUNDENNAME", kundenname);
        ctx.put("ANSPRECHPARTNER", ansprechpartner);
        return ctx;
    }

    @Test
    @DisplayName("Firma: '{{ANREDE}} {{KUNDENNAME}},' wird 'Sehr geehrte Damen und Herren,'")
    void paarFirma() {
        Map<String, String> ctx = kontext(Anrede.DAMEN_HERREN, "Landkreis Musterstadt", "");
        assertThat(ersetze("<p>{{ANREDE}} {{KUNDENNAME}},</p>", ctx, OHNE_MASKE))
                .isEqualTo("<p>Sehr geehrte Damen und Herren,</p>");
    }

    @Test
    @DisplayName("Herr mit Ansprechpartner: Paar mit {{Ansprechpartner}} in gemischter Schreibweise")
    void paarAnsprechpartner() {
        Map<String, String> ctx = kontext(Anrede.HERR, "Musterbau GmbH", "Max Mustermann");
        assertThat(ersetze("{{Anrede}} {{Ansprechpartner}},", ctx, OHNE_MASKE))
                .isEqualTo("Sehr geehrter Herr Max Mustermann,");
        assertThat(ersetze("{{ anrede }} {{ ANSPRECHPARTNER }},", ctx, OHNE_MASKE))
                .isEqualTo("Sehr geehrter Herr Max Mustermann,");
    }

    @Test
    @DisplayName("Frau ohne Ansprechpartner: Damen und Herren, kein 'DAMEN_HERREN ,'")
    void paarOhneName() {
        Map<String, String> ctx = kontext(Anrede.FRAU, "", "");
        assertThat(ersetze("{{ANREDE}} {{ANSPRECHPARTNER}},", ctx, OHNE_MASKE))
                .isEqualTo("Sehr geehrte Damen und Herren,");
    }

    @Test
    @DisplayName("Fehlt der zweite Wert im Kontext, gilt er als leer")
    void paarWertFehlt() {
        Map<String, String> ctx = new HashMap<>();
        Briefanrede.inKontext(ctx, Anrede.HERR);
        assertThat(ersetze("{{ANREDE}} {{ANSPRECHPARTNER}},", ctx, OHNE_MASKE))
                .isEqualTo("Sehr geehrte Damen und Herren,");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "&nbsp;", "&NBSP;", "\u00A0", " &nbsp; \u00A0", "\n\t"})
    @DisplayName("Leerraum zwischen den Platzhaltern: Leerzeichen, &nbsp;, geschütztes Leerzeichen, keiner")
    void paarLeerraum(String zwischen) {
        Map<String, String> ctx = kontext(Anrede.HERR, "Max Mustermann", "");
        assertThat(ersetze("{{ANREDE}}" + zwischen + "{{KUNDENNAME}},", ctx, OHNE_MASKE))
                .isEqualTo("Sehr geehrter Herr Max Mustermann,");
    }

    @Test
    @DisplayName("Anderer Text zwischen den Platzhaltern: kein Paar, beide einzeln ersetzt")
    void keinPaarBeiText() {
        Map<String, String> ctx = kontext(Anrede.HERR, "Max Mustermann", "");
        assertThat(ersetze("{{ANREDE}} lieber {{KUNDENNAME}},", ctx, OHNE_MASKE))
                .isEqualTo("Sehr geehrter Herr lieber Max Mustermann,");
    }

    @Test
    @DisplayName("Andere Platzhalter und mehrere Paare im Text")
    void mehrerePaare() {
        Map<String, String> ctx = kontext(Anrede.FIRMA, "Landkreis Musterstadt", "Max Mustermann");
        String text = "{{ANREDE}} {{KUNDENNAME}}, Bauvorhaben {{BAUVORHABEN}} – {{ANREDE}} {{ANSPRECHPARTNER}}";
        assertThat(ersetze(text, ctx, OHNE_MASKE))
                .isEqualTo("Sehr geehrte Damen und Herren, Bauvorhaben {{BAUVORHABEN}} – Sehr geehrte Damen und Herren");
    }

    @Test
    @DisplayName("Ohne Anrede-Art im Kontext bleibt alles wie bisher (z. B. 'Hallo Max Mustermann')")
    void ohneArtKeinePaarRegel() {
        Map<String, String> ctx = new HashMap<>();
        ctx.put("ANREDE", "Hallo Max Mustermann");
        ctx.put("KUNDENNAME", "Max Mustermann");
        String text = "{{ANREDE}} {{KUNDENNAME}},";
        assertThat(ersetze(text, ctx, OHNE_MASKE)).isEqualTo("Hallo Max Mustermann Max Mustermann,");
        assertThat(ersetze(text, null, OHNE_MASKE)).isEqualTo(text);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Leerer Text bleibt leer")
    void leererText(String text) {
        assertThat(ersetze(text, kontext(Anrede.HERR, "X", "Y"), OHNE_MASKE)).isEqualTo(text);
    }

    @Test
    @DisplayName("Maskierung: Name mit Skript wird im HTML maskiert")
    void maskierung() {
        Map<String, String> ctx = kontext(Anrede.HERR, "<script>alert(1)</script>", "");
        String html = ersetze("<p>{{ANREDE}} {{KUNDENNAME}},</p>", ctx,
                wert -> HtmlUtils.htmlEscape(wert, "UTF-8"));
        assertThat(html).isEqualTo("<p>Sehr geehrter Herr &lt;script&gt;alert(1)&lt;/script&gt;,</p>");
    }

    @Test
    @DisplayName("Sonderzeichen im Namen werden nicht als Regex-Ersetzung gelesen")
    void sonderzeichenImNamen() {
        Map<String, String> ctx = kontext(Anrede.HERR, "Max $1 \\ Mustermann", "");
        assertThat(ersetze("{{ANREDE}} {{KUNDENNAME}}", ctx, OHNE_MASKE))
                .isEqualTo("Sehr geehrter Herr Max $1 \\ Mustermann");
    }

    @Test
    @DisplayName("Langer Leerraum ohne zweiten Platzhalter läuft schnell durch (kein ReDoS)")
    void keinReDoS() {
        Map<String, String> ctx = kontext(Anrede.HERR, "X", "");
        String text = "{{ANREDE}}" + "&nbsp; ".repeat(50_000) + "{{KUNDE";
        long start = System.nanoTime();
        assertThat(ersetze(text, ctx, OHNE_MASKE)).isEqualTo("Sehr geehrter Herr" + text.substring("{{ANREDE}}".length()));
        assertThat(System.nanoTime() - start).isLessThan(2_000_000_000L);
    }

    @Test
    @DisplayName("Platzhalter im eingesetzten Namen werden nicht noch einmal ersetzt (ein Durchlauf)")
    void keineDoppelErsetzung() {
        Map<String, String> ctx = kontext(Anrede.HERR, "{{DOKUMENTNUMMER}}", "");
        ctx.put("DOKUMENTNUMMER", "RE-2026-0001");
        assertThat(ersetze("{{ANREDE}} {{KUNDENNAME}}, Rechnung {{DOKUMENTNUMMER}}", ctx, OHNE_MASKE))
                .isEqualTo("Sehr geehrter Herr {{DOKUMENTNUMMER}}, Rechnung RE-2026-0001");
    }

    @Test
    @DisplayName("Einzel-Platzhalter werden ersetzt; ohne Anrede-Art auch ANREDE und KUNDENNAME einzeln")
    void einzelPlatzhalter() {
        Map<String, String> mitArt = kontext(Anrede.FIRMA, "Landkreis Musterstadt", "");
        assertThat(ersetze("{{ANREDE}}, {{KUNDENNAME}}", mitArt, OHNE_MASKE))
                .isEqualTo("Sehr geehrte Damen und Herren, Landkreis Musterstadt");
        Map<String, String> ohneArt = new HashMap<>(Map.of("ANREDE", "Hallo", "KUNDENNAME", "Max Mustermann"));
        assertThat(ersetze("{{ANREDE}} {{KUNDENNAME}}", ohneArt, OHNE_MASKE)).isEqualTo("Hallo Max Mustermann");
    }

    @Test
    @DisplayName("Paar ist Groß/klein-unabhängig, Einzel-Platzhalter folgen dem Muster des Aufrufers")
    void einzelMusterNurGrossbuchstaben() {
        Briefanrede.Muster gross = Briefanrede.muster("\\{\\{\\s*+([A-Z0-9_]++)\\s*+\\}\\}");
        Map<String, String> ctx = kontext(Anrede.HERR, "Max Mustermann", "");
        assertThat(Briefanrede.ersetze("{{anrede}} {{kundenname}} / {{kundenname}} / {{KUNDENNAME}}", ctx, gross,
                ctx::get, OHNE_MASKE))
                .isEqualTo("Sehr geehrter Herr Max Mustermann / {{kundenname}} / Max Mustermann");
    }
}
