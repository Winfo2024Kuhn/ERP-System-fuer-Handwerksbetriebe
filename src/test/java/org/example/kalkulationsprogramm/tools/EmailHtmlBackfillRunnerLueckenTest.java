package org.example.kalkulationsprogramm.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailHtmlBackfillRunnerLueckenTest {

    @Mock EmailRepository emailRepository;

    private EmailHtmlBackfillRunner runner() {
        return new EmailHtmlBackfillRunner(emailRepository);
    }

    @Test
    @DisplayName("Ohne E-Mails wird nichts gespeichert")
    void keineMails() {
        when(emailRepository.findAll()).thenReturn(List.of());
        runner().run();
        verify(emailRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Mail ohne jeden Body wird uebersprungen")
    void ohneBody() {
        Email e = new Email();
        e.setBody("  ");
        when(emailRepository.findAll()).thenReturn(List.of(e));
        runner().run();
        verify(emailRepository, never()).saveAll(anyList());
        assertEquals("  ", e.getBody());
    }

    @Test
    @DisplayName("Rohtext wird bereinigt: Script entfernt, HTML und Klartext neu gesetzt")
    void bereinigtRohtext() {
        Email e = new Email();
        e.setRawBody("<p>Hallo <b>Max</b></p><script>alert(1)</script>");
        e.setHtmlBody("alt");
        e.setBody("alt");
        when(emailRepository.findAll()).thenReturn(List.of(e));

        runner().run();

        assertFalse(e.getHtmlBody().contains("script"), e.getHtmlBody());
        assertTrue(e.getHtmlBody().contains("<b>Max</b>"));
        assertEquals("Hallo Max", e.getBody());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Email>> cap = ArgumentCaptor.forClass(List.class);
        verify(emailRepository).saveAll(cap.capture());
        assertEquals(List.of(e), cap.getValue());
    }

    @Test
    @DisplayName("Bereits aktuelle Mails werden nicht erneut gespeichert (idempotent)")
    void idempotent() {
        Email e = new Email();
        e.setRawBody("<p>Hallo</p>");
        when(emailRepository.findAll()).thenReturn(List.of(e));
        runner().run();
        verify(emailRepository, times(1)).saveAll(anyList());
        String html = e.getHtmlBody();
        String text = e.getBody();

        clearInvocations(emailRepository);
        runner().run();

        verify(emailRepository, never()).saveAll(anyList());
        assertEquals(html, e.getHtmlBody());
        assertEquals(text, e.getBody());
    }

    @Test
    @DisplayName("Ohne Rohtext dient der vorhandene HTML-Body als Quelle, danach der Klartext")
    void quellenReihenfolge() {
        Email nurHtml = new Email();
        nurHtml.setHtmlBody("<div onclick=\"x()\">Text</div>");
        Email nurPlain = new Email();
        nurPlain.setBody("Nur Text");
        when(emailRepository.findAll()).thenReturn(List.of(nurHtml, nurPlain));

        runner().run();

        assertFalse(nurHtml.getHtmlBody().contains("onclick"));
        assertEquals("Text", nurHtml.getBody());
        assertEquals("Nur Text", nurPlain.getBody());
        assertNotNull(nurPlain.getHtmlBody());
    }
}
