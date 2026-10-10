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
import org.example.kalkulationsprogramm.repository.EmailDraftRepository;
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

    @Mock
    private EmailDraftRepository emailDraftRepository;

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
    void computeThreadLastActivityAt_returnsLatestReply_evenWhenCalledOnRoot() {
        // Regressions-Bug: Eingehende Antworten in einem Thread sollten den Thread im Ordner
        // (z.B. Lieferanten/Gesendet) nach oben holen - dafuer muss die Wurzel den juengsten
        // sentAt-Wert ueber ALLE Mitglieder als threadLastActivityAt zurueckliefern.
        Email root = makeEmail(1L, "Anfrage", null);
        Email reply = makeEmail(2L, "Re: Anfrage", root);
        root.setSentAt(LocalDateTime.of(2026, 3, 10, 9, 0));
        reply.setSentAt(LocalDateTime.of(2026, 3, 15, 14, 30));
        root.getReplies().add(reply);

        LocalDateTime result = service.computeThreadLastActivityAt(root);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 15, 14, 30));
    }

    @Test
    void computeThreadLastActivityAt_walksUpToRoot_thenBackDownToFindLatest() {
        // Wenn die Methode auf einem KIND aufgerufen wird (z.B. weil ein "Gesendet"-Ordner
        // nur eine OUT-Reply liefert), muss sie trotzdem die juengste Aktivitaet im Thread liefern.
        Email root = makeEmail(1L, "Anfrage", null);
        Email midOut = makeEmail(2L, "Re: Anfrage", root);   // unsere OUT-Antwort
        Email newIn = makeEmail(3L, "Re: Re: Anfrage", midOut); // neue eingehende Antwort
        root.setSentAt(LocalDateTime.of(2026, 3, 10, 9, 0));
        midOut.setSentAt(LocalDateTime.of(2026, 3, 11, 10, 0));
        newIn.setSentAt(LocalDateTime.of(2026, 3, 20, 16, 45));
        root.getReplies().add(midOut);
        midOut.getReplies().add(newIn);

        LocalDateTime result = service.computeThreadLastActivityAt(midOut);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 20, 16, 45));
    }

    @Test
    void computeThreadLastActivityAt_standaloneEmail_returnsOwnSentAt() {
        Email standalone = makeEmail(7L, "Allein", null);
        standalone.setSentAt(LocalDateTime.of(2026, 5, 1, 12, 0));

        assertThat(service.computeThreadLastActivityAt(standalone))
                .isEqualTo(LocalDateTime.of(2026, 5, 1, 12, 0));
    }

    @Test
    void computeThreadLastActivityAt_cycleSafe() {
        Email a = makeEmail(30L, "A", null);
        Email b = makeEmail(31L, "B", a);
        a.getReplies().add(b);
        b.getReplies().add(a); // kuenstlicher Cycle
        a.setSentAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        b.setSentAt(LocalDateTime.of(2026, 2, 1, 0, 0));

        LocalDateTime result = service.computeThreadLastActivityAt(a);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
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

    // ═══════════════════════════════════════════════════════════════
    // Kennzahlen für die Liste und Antwort-Bezug für den Versand
    // ═══════════════════════════════════════════════════════════════

    @Test
    void kennzahlen_sindFuerAlleMitgliederGleich_auchBeiVerzweigung() {
        Email root = makeEmail(1, "Anfrage", null);
        Email antwort = makeEmail(2, "Re: Anfrage", root);
        Email nachfrage = makeEmail(3, "Re: Anfrage", antwort);
        Email zweigAntwort = makeEmail(4, "Re: Anfrage", root);
        root.getReplies().addAll(List.of(antwort, zweigAntwort));
        antwort.getReplies().add(nachfrage);

        for (Email mitglied : List.of(root, antwort, nachfrage, zweigAntwort)) {
            EmailThreadService.ThreadKennzahlen k = service.kennzahlenFuer(mitglied);
            assertThat(k.rootId()).isEqualTo(1L);
            assertThat(k.anzahl()).isEqualTo(4);
            assertThat(k.letzteAktivitaet()).isEqualTo(zweigAntwort.getSentAt());
        }
    }

    @Test
    void kennzahlen_einzelneMail() {
        Email einzeln = makeEmail(7, "Hallo", null);
        assertThat(service.kennzahlenFuer(einzeln))
                .isEqualTo(new EmailThreadService.ThreadKennzahlen(7L, 1, einzeln.getSentAt()));
    }

    @Test
    void antwortBezug_inReplyToUndReferencesAeltesteZuerst() {
        Email root = makeEmail(1, "Anfrage", null);
        root.setMessageId("<a@example.com>");
        Email ohneId = makeEmail(2, "Re: Anfrage", root);
        Email beantwortet = makeEmail(3, "Re: Anfrage", ohneId);
        beantwortet.setMessageId("<c@example.com>");

        var bezug = service.antwortBezugFuer(beantwortet);

        assertThat(bezug.inReplyTo()).isEqualTo("<c@example.com>");
        assertThat(bezug.references()).containsExactly("<a@example.com>");
        assertThat(bezug.istLeer()).isFalse();
    }

    @Test
    void antwortBezug_ohneMessageIdIstLeerUndZyklusSicher() {
        Email a = makeEmail(1, "x", null);
        Email b = makeEmail(2, "x", a);
        a.setParentEmail(b);
        var bezug = service.antwortBezugFuer(a);
        assertThat(bezug.istLeer()).isTrue();
        assertThat(bezug.references()).isEmpty();
    }

    @Test
    void threadEintrag_enthaeltCcUndReplyTo() {
        Email email = makeEmail(5, "Termin", null);
        email.setCc("erika@example.com");
        email.setReplyToAddress("auftraege@example.com");
        when(emailRepository.findById(5L)).thenReturn(java.util.Optional.of(email));

        var entry = service.loadThreadFor(5L).getEmails().getFirst();

        assertThat(entry.getCc()).isEqualTo("erika@example.com");
        assertThat(entry.getReplyToAddress()).isEqualTo("auftraege@example.com");
    }

    @Test
    void antwortEmpfaenger_gewaehltVorReplyToVorAbsender() {
        Email original = makeEmail(1, "x", null);
        original.setFromAddress("noreply@formular.example");
        assertThat(EmailThreadService.antwortEmpfaenger(List.of(" max@example.com ", ""), original)).isEqualTo("max@example.com");
        assertThat(EmailThreadService.antwortEmpfaenger(List.of(), original)).isEqualTo("noreply@formular.example");
        original.setReplyToAddress("kunde@example.com");
        assertThat(EmailThreadService.antwortEmpfaenger(null, original)).isEqualTo("kunde@example.com");
        original.setReplyToAddress(" ");
        original.setFromAddress(null);
        assertThat(EmailThreadService.antwortEmpfaenger(null, original)).isNull();
    }

    @Test
    void verbindeAdressen_verwirftLeere() {
        assertThat(EmailThreadService.verbindeAdressen(null)).isNull();
        assertThat(EmailThreadService.verbindeAdressen(List.of(" ", ""))).isNull();
        assertThat(EmailThreadService.verbindeAdressen(java.util.Arrays.asList(" a@example.com ", null, "b@example.com")))
                .isEqualTo("a@example.com, b@example.com");
    }

    // ═══════════════════════════════════════════════════════════════
    // GEFILTERTER VERLAUF (Postfach-Sichtbarkeit)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void gefilterterVerlaufZeigtNurErlaubteMailsUndDerenEntwuerfe() {
        // A (sichtbar) → B (fremdes Postfach) → C (sichtbar, fokussiert)
        Email a = makeEmail(1L, "Anfrage", null);
        Email b = makeEmail(2L, "Re: Anfrage", a);
        Email c = makeEmail(3L, "Re: Re: Anfrage", b);
        a.getReplies().add(b);
        b.getReplies().add(c);
        when(emailRepository.findById(3L)).thenReturn(Optional.of(c));
        org.example.kalkulationsprogramm.domain.EmailDraft entwurf = new org.example.kalkulationsprogramm.domain.EmailDraft();
        entwurf.setId(50L);
        entwurf.setReplyEmailId(1L);
        org.mockito.ArgumentCaptor<java.util.Collection<Long>> ids = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        when(emailDraftRepository.findByReplyEmailIdIn(ids.capture())).thenReturn(new ArrayList<>(List.of(entwurf)));

        EmailThreadDto result = service.loadThreadFor(3L, email -> email.getId() != 2L);

        assertThat(result.getEmails()).extracting(e -> e.getId() == null ? e.getDraftId() : e.getId())
                .containsExactly(1L, 3L, 50L);
        assertThat(ids.getValue()).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void gefilterterVerlaufNichtErlaubteMail404() {
        Email a = makeEmail(1L, "Anfrage", null);
        when(emailRepository.findById(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.loadThreadFor(1L, email -> false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
