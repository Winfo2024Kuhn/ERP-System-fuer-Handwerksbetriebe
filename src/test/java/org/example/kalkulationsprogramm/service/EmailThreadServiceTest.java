package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.dto.EmailThreadDto;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit-Tests für {@link EmailThreadService}.
 *
 * Szenarien:
 * <ul>
 *   <li>Single-Email-Thread (keine Parent/Replies)</li>
 *   <li>Linearer Thread (A → B → C)</li>
 *   <li>Verzweigter Thread (A → B und A → C)</li>
 *   <li>Cycle-Schutz in findRoot und collectThread</li>
 *   <li>Email nicht gefunden → 404</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmailThreadServiceTest {

    @Mock
    private EmailRepository emailRepository;

    @InjectMocks
    private EmailThreadService service;

    // ═══════════════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ═══════════════════════════════════════════════════════════════

    private Email makeEmail(long id, String subject, Email parent) {
        Email email = new Email();
        email.setId(id);
        email.setSubject(subject);
        email.setDirection(EmailDirection.IN);
        email.setFromAddress("test@example.com");
        email.setRecipient("empfaenger@example.com");
        email.setSentAt(LocalDateTime.of(2026, 3, 10, 9, 0).plusHours(id));
        email.setBody("Inhalt der E-Mail " + id);
        email.setParentEmail(parent);
        email.setReplies(new ArrayList<>());
        email.setAttachments(new ArrayList<>());
        return email;
    }

    // ═══════════════════════════════════════════════════════════════
    // TESTS
    // ═══════════════════════════════════════════════════════════════

    @Test
    void singleEmailThread_returnsOneEntry() {
        Email single = makeEmail(1L, "Anfrage", null);
        when(emailRepository.findById(1L)).thenReturn(Optional.of(single));

        EmailThreadDto result = service.loadThreadFor(1L);

        assertThat(result.getFocusedEmailId()).isEqualTo(1L);
        assertThat(result.getRootEmailId()).isEqualTo(1L);
        assertThat(result.getEmails()).hasSize(1);
        assertThat(result.getEmails().get(0).getSubject()).isEqualTo("Anfrage");
    }

    @Test
    void linearThread_chronologicalOrder() {
        // A (root) → B → C (focused)
        Email a = makeEmail(1L, "Anfrage", null);
        Email b = makeEmail(2L, "Re: Anfrage", a);
        Email c = makeEmail(3L, "Re: Re: Anfrage", b);

        a.getReplies().add(b);
        b.getReplies().add(c);

        when(emailRepository.findById(3L)).thenReturn(Optional.of(c));

        EmailThreadDto result = service.loadThreadFor(3L);

        assertThat(result.getFocusedEmailId()).isEqualTo(3L);
        assertThat(result.getRootEmailId()).isEqualTo(1L);
        assertThat(result.getEmails()).hasSize(3);
        // Chronologische Reihenfolge: A, B, C
        assertThat(result.getEmails().get(0).getId()).isEqualTo(1L);
        assertThat(result.getEmails().get(1).getId()).isEqualTo(2L);
        assertThat(result.getEmails().get(2).getId()).isEqualTo(3L);
    }

    @Test
    void branchedThread_allItemsCollected() {
        // A (root) → B und A → C
        Email a = makeEmail(1L, "Anfrage", null);
        Email b = makeEmail(2L, "Zweig 1", a);
        Email c = makeEmail(3L, "Zweig 2", a);

        a.getReplies().add(b);
        a.getReplies().add(c);

        when(emailRepository.findById(2L)).thenReturn(Optional.of(b));

        EmailThreadDto result = service.loadThreadFor(2L);

        assertThat(result.getEmails()).hasSize(3);
        assertThat(result.getEmails().stream().map(e -> e.getId()))
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void findRoot_walkToRoot() {
        Email a = makeEmail(1L, "Root", null);
        Email b = makeEmail(2L, "Child", a);
        Email c = makeEmail(3L, "Grandchild", b);

        Email root = service.findRoot(c);

        assertThat(root.getId()).isEqualTo(1L);
    }

    @Test
    void findRoot_singleEmail_returnsSelf() {
        Email single = makeEmail(5L, "Standalone", null);

        Email root = service.findRoot(single);

        assertThat(root.getId()).isEqualTo(5L);
    }

    @Test
    void findRoot_cycleProtection_doesNotHang() {
        // Künstlicher Cycle: A → B → A (unrealistisch in DB, aber robust testen)
        Email a = makeEmail(10L, "A", null);
        Email b = makeEmail(11L, "B", a);
        // Cycle: a.parentEmail = b (nach Initialisierung setzen)
        a.setParentEmail(b);

        // Sollte nicht in Endlosschleife laufen
        Email root = service.findRoot(a);
        assertThat(root).isNotNull();
    }

    @Test
    void collectThread_cycleProtection() {
        Email a = makeEmail(20L, "A", null);
        Email b = makeEmail(21L, "B", a);
        // Cycle: b hat sich selbst als Reply
        b.getReplies().add(b);
        a.getReplies().add(b);

        List<Email> thread = service.collectThread(a);

        // Kein Hang, beide einmalig enthalten
        assertThat(thread).hasSize(2);
        assertThat(thread.stream().map(Email::getId)).containsExactlyInAnyOrder(20L, 21L);
    }

    @Test
    void loadThreadFor_emailNotFound_throws404() {
        when(emailRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadThreadFor(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void snippet_isTruncated_at120Characters() {
        Email email = makeEmail(1L, "Lang", null);
        email.setBody("A".repeat(200));
        when(emailRepository.findById(1L)).thenReturn(Optional.of(email));

        EmailThreadDto result = service.loadThreadFor(1L);

        // snippet ist 120 Zeichen + "…"
        assertThat(result.getEmails().get(0).getSnippet()).endsWith("…");
        assertThat(result.getEmails().get(0).getSnippet().length()).isEqualTo(121);
    }
}
