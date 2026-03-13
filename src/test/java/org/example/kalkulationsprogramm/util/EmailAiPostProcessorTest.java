package org.example.kalkulationsprogramm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailAiPostProcessorTest {

    @Test
    void removesHereIsPreface() {
        String in = "Hier ist der überarbeitete Text:\n\nSehr geehrte Damen und Herren,\nwir bedanken uns für Ihre Anfrage.";
        String out = EmailAiPostProcessor.sanitizePlainText(in);
        assertEquals("Sehr geehrte Damen und Herren,\nwir bedanken uns für Ihre Anfrage.", out);
    }

    @Test
    void keepsGerneSentence() {
        String in = "Gerne können wir den Termin verschieben.";
        String out = EmailAiPostProcessor.sanitizePlainText(in);
        assertEquals("Gerne können wir den Termin verschieben.", out);
    }

    @Test
    void removesCodeFences() {
        String in = "```text\nHallo Max,\n- Punkt 1\n- Punkt 2\n```";
        String out = EmailAiPostProcessor.sanitizePlainText(in);
        assertEquals("Hallo Max,\n- Punkt 1\n- Punkt 2", out);
    }

    @Test
    void removesVerbesserteVersionHeading() {
        String in = "Verbesserte Version:\n\nHallo zusammen,\nbitte finden Sie anbei das Anfrage.";
        String out = EmailAiPostProcessor.sanitizePlainText(in);
        assertEquals("Hallo zusammen,\nbitte finden Sie anbei das Anfrage.", out);
    }

    @Test
    void removesMarkdownHeading() {
        String in = "### Überarbeitete E-Mail\n\nSehr geehrte Frau Müller,\n...";
        String out = EmailAiPostProcessor.sanitizePlainText(in);
        assertEquals("Sehr geehrte Frau Müller,\n...", out);
    }
}

