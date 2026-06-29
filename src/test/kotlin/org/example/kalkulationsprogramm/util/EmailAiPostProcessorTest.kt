package org.example.kalkulationsprogramm.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EmailAiPostProcessorTest {

    @Test
    fun removesHereIsPreface() {
        val input = "Hier ist der überarbeitete Text:\n\nSehr geehrte Damen und Herren,\nwir bedanken uns für Ihre Anfrage."
        val output = EmailAiPostProcessor.sanitizePlainText(input)
        assertEquals("Sehr geehrte Damen und Herren,\nwir bedanken uns für Ihre Anfrage.", output)
    }

    @Test
    fun keepsGerneSentence() {
        val input = "Gerne können wir den Termin verschieben."
        val output = EmailAiPostProcessor.sanitizePlainText(input)
        assertEquals("Gerne können wir den Termin verschieben.", output)
    }

    @Test
    fun removesCodeFences() {
        val input = "```text\nHallo Max,\n- Punkt 1\n- Punkt 2\n```"
        val output = EmailAiPostProcessor.sanitizePlainText(input)
        assertEquals("Hallo Max,\n- Punkt 1\n- Punkt 2", output)
    }

    @Test
    fun removesVerbesserteVersionHeading() {
        val input = "Verbesserte Version:\n\nHallo zusammen,\nbitte finden Sie anbei das Anfrage."
        val output = EmailAiPostProcessor.sanitizePlainText(input)
        assertEquals("Hallo zusammen,\nbitte finden Sie anbei das Anfrage.", output)
    }

    @Test
    fun removesMarkdownHeading() {
        val input = "### Überarbeitete E-Mail\n\nSehr geehrte Frau Müller,\n..."
        val output = EmailAiPostProcessor.sanitizePlainText(input)
        assertEquals("Sehr geehrte Frau Müller,\n...", output)
    }
}
