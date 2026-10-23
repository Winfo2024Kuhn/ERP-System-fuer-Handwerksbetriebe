package org.example.kalkulationsprogramm.util;

import org.openpdf.text.Chunk;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EinfachesHtmlPdfTest {

    private static final Font TEXT = new Font(Font.HELVETICA, 10f, Font.NORMAL);
    private static final Font FETT = new Font(Font.HELVETICA, 10f, Font.BOLD);
    private static final Font KURSIV = new Font(Font.HELVETICA, 10f, Font.ITALIC);
    private static final Font FETT_KURSIV = new Font(Font.HELVETICA, 10f, Font.BOLDITALIC);
    private static final Font TITEL = new Font(Font.HELVETICA, 11f, Font.BOLD);
    private static final EinfachesHtmlPdf.Schriften S =
            new EinfachesHtmlPdf.Schriften(TEXT, FETT, KURSIV, FETT_KURSIV, TITEL);

    private static String text(Paragraph p) {
        return p.getChunks().stream().map(e -> ((Chunk) e).getContent()).collect(Collectors.joining());
    }

    private static List<Chunk> chunks(Paragraph p) {
        return p.getChunks().stream().map(Chunk.class::cast).toList();
    }

    @Test
    void leer() {
        assertThat(EinfachesHtmlPdf.zuAbsaetzen(null, S)).isEmpty();
        assertThat(EinfachesHtmlPdf.zuAbsaetzen("  ", S)).isEmpty();
        assertThat(EinfachesHtmlPdf.zuAbsaetzen("<p> </p><h3> </h3><ul><li> </li></ul>", S)).isEmpty();
    }

    @Test
    void ueberschriftAbsatzUndAuszeichnungen() {
        List<Paragraph> absaetze = EinfachesHtmlPdf.zuAbsaetzen(
                "<h3>Widerrufsrecht</h3>\n<p>\n  Max <strong>fett <em>beides</em></strong> <i>kursiv</i> <b>b</b><br>neu </p>", S);

        assertThat(absaetze).hasSize(2);
        assertThat(text(absaetze.get(0))).isEqualTo("Widerrufsrecht");
        assertThat(absaetze.get(0).getFont().getStyle()).isEqualTo(Font.BOLD);
        Paragraph p = absaetze.get(1);
        assertThat(text(p)).isEqualTo("Max fett beides kursiv b\nneu");
        List<Chunk> c = chunks(p);
        assertThat(c.get(0).getFont()).isSameAs(TEXT);
        assertThat(c.get(1).getFont()).isSameAs(FETT);
        assertThat(c.get(2).getFont()).isSameAs(FETT_KURSIV);
        assertThat(c.stream().filter(x -> x.getContent().equals("kursiv")).findFirst().orElseThrow().getFont())
                .isSameAs(KURSIV);
    }

    @Test
    void listen() {
        List<Paragraph> absaetze = EinfachesHtmlPdf.zuAbsaetzen(
                "<ul><li><p>eins</p><p>zwei</p></li><li>drei</li><span>kein li</span></ul><ol><li>a</li><li>b</li></ol>", S);

        assertThat(absaetze).extracting(EinfachesHtmlPdfTest::text)
                .containsExactly("– eins\nzwei", "– drei", "1. a", "2. b");
        assertThat(absaetze.get(0).getIndentationLeft()).isEqualTo(12f);
    }

    @Test
    void loserTextUndAndereBloecke() {
        List<Paragraph> absaetze = EinfachesHtmlPdf.zuAbsaetzen(
                "Vorweg <b>fett</b><p>Absatz</p>danach<div>Block</div><!-- Kommentar -->", S);

        assertThat(absaetze).extracting(EinfachesHtmlPdfTest::text)
                .containsExactly("Vorweg fett", "Absatz", "danach", "Block");
    }

    @Test
    void markupImTextBleibtText() {
        List<Paragraph> absaetze = EinfachesHtmlPdf.zuAbsaetzen("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", S);

        assertThat(text(absaetze.get(0))).isEqualTo("<script>alert(1)</script>");
    }
}
