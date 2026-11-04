package org.example.kalkulationsprogramm.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeit;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailPapierkorbServiceTest {

    private static final Authentication ERIKA = new UsernamePasswordAuthenticationToken("erika", null, List.of());
    private static final Instant JETZT = Instant.parse("2026-11-04T09:30:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    @Mock private EmailRepository emailRepository;
    @Mock private PostfachSichtbarkeitService postfachSichtbarkeitService;
    private EmailPapierkorbService service;

    private EmailAbsender info;
    private EmailAbsender max;

    @BeforeEach
    void aufbau() {
        service = new EmailPapierkorbService(emailRepository, postfachSichtbarkeitService, Clock.fixed(JETZT, ZONE));
        info = postfach(1L);
        max = postfach(3L);
        // Erika sieht info@, nicht max@
        given(postfachSichtbarkeitService.fuer(any())).willReturn(new PostfachSichtbarkeit(false, Set.of(1L)));
    }

    private static EmailAbsender postfach(long id) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        return p;
    }

    private Email mail(long id, EmailAbsender postfach) {
        Email e = new Email();
        e.setId(id);
        e.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        if (postfach != null) {
            e.ordnePostfachZu(postfach, "INBOX", id);
        }
        return e;
    }

    private void vorhanden(Email... mails) {
        given(emailRepository.findAllById(anyIterable())).willAnswer(aufruf -> {
            Collection<Long> ids = new ArrayList<>();
            aufruf.<Iterable<Long>>getArgument(0).forEach(ids::add);
            return Arrays.stream(mails).filter(m -> ids.contains(m.getId())).toList();
        });
    }

    private static void erwarte400(Runnable aufruf, String meldung) {
        assertThatThrownBy(aufruf::run).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getReason()).isEqualTo(meldung);
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Keine IDs: 400 – für beide Richtungen")
    void keineIds(List<Long> ids) {
        erwarte400(() -> service.inPapierkorb(ids, ERIKA), "Keine E-Mails ausgewählt.");
        erwarte400(() -> service.zurueckholen(ids, ERIKA), "Keine E-Mails ausgewählt.");
        verifyNoInteractions(emailRepository);
    }

    @Test
    @DisplayName("Mehr als 500 IDs: 400; genau 500 gehen")
    void zuViele() {
        List<Long> zuViele = LongStream.rangeClosed(1, 501).boxed().toList();
        erwarte400(() -> service.inPapierkorb(zuViele, ERIKA), "Höchstens 500 E-Mails auf einmal.");
        erwarte400(() -> service.zurueckholen(zuViele, ERIKA), "Höchstens 500 E-Mails auf einmal.");
        verifyNoInteractions(emailRepository);

        vorhanden();
        assertThat(service.inPapierkorb(zuViele.subList(0, 500), ERIKA)).isZero();
    }

    @Test
    @DisplayName("In den Papierkorb: setzt deletedAt = jetzt für sichtbare Mails außerhalb des Papierkorbs")
    void inPapierkorb() {
        Email eigene = mail(10, info);
        Email ohnePostfach = mail(11, null);
        Email fremde = mail(20, max);
        Email schonGeloescht = mail(12, info);
        LocalDateTime frueher = LocalDateTime.of(2026, 10, 1, 8, 0);
        schonGeloescht.setDeletedAt(frueher);
        vorhanden(eigene, ohnePostfach, fremde, schonGeloescht);

        int verschoben = service.inPapierkorb(List.of(10L, 11L, 20L, 12L, 999L), ERIKA);

        assertThat(verschoben).isEqualTo(2);
        LocalDateTime jetzt = LocalDateTime.ofInstant(JETZT, ZONE);
        assertThat(eigene.getDeletedAt()).isEqualTo(jetzt);
        assertThat(ohnePostfach.getDeletedAt()).isEqualTo(jetzt);
        assertThat(fremde.getDeletedAt()).isNull();
        assertThat(schonGeloescht.getDeletedAt()).isEqualTo(frueher);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Email>> gespeichert = ArgumentCaptor.forClass(List.class);
        verify(emailRepository).saveAll(gespeichert.capture());
        assertThat(gespeichert.getValue()).containsExactlyInAnyOrder(eigene, ohnePostfach);
    }

    @Test
    @DisplayName("Zurückholen: nur deletedAt = null; Spam, Newsletter, Zuordnung, Stern und Gelesen bleiben")
    void zurueckholen() {
        Email spam = mail(10, info);
        spam.setSpam(true);
        spam.setSpamScore(97);
        spam.setUserSpamVerdict("SPAM");
        spam.setDeletedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        Email newsletter = mail(11, info);
        newsletter.setNewsletter(true);
        newsletter.setStarred(true);
        newsletter.setRead(true);
        newsletter.setZuordnungTyp(EmailZuordnungTyp.PROJEKT);
        newsletter.setDeletedAt(LocalDateTime.of(2026, 10, 2, 8, 0));
        Email nichtImPapierkorb = mail(12, info);
        Email fremde = mail(20, max);
        fremde.setDeletedAt(LocalDateTime.of(2026, 10, 3, 8, 0));
        vorhanden(spam, newsletter, nichtImPapierkorb, fremde);

        int zurueck = service.zurueckholen(List.of(10L, 11L, 12L, 20L), ERIKA);

        assertThat(zurueck).isEqualTo(2);
        assertThat(spam.getDeletedAt()).isNull();
        assertThat(spam.isSpam()).isTrue();
        assertThat(spam.getSpamScore()).isEqualTo(97);
        assertThat(spam.getUserSpamVerdict()).isEqualTo("SPAM");
        assertThat(newsletter.getDeletedAt()).isNull();
        assertThat(newsletter.isNewsletter()).isTrue();
        assertThat(newsletter.isStarred()).isTrue();
        assertThat(newsletter.isRead()).isTrue();
        assertThat(newsletter.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.PROJEKT);
        assertThat(fremde.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Ungültige IDs (0, negativ, Long.MAX_VALUE, null, doppelt) zählen nicht und stören nicht")
    void ungueltigeIds() {
        Email eigene = mail(10, info);
        vorhanden(eigene);

        int verschoben = service.inPapierkorb(Arrays.asList(0L, -1L, Long.MAX_VALUE, null, 10L, 10L), ERIKA);

        assertThat(verschoben).isEqualTo(1);
    }

    @Test
    @DisplayName("Nichts zu tun: kein Speichern")
    void nichtsZuTun() {
        vorhanden(mail(20, max));

        assertThat(service.inPapierkorb(List.of(20L), ERIKA)).isZero();
        verify(emailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Admin sieht alle Postfächer")
    void admin() {
        given(postfachSichtbarkeitService.fuer(any())).willReturn(PostfachSichtbarkeit.ALLES);
        vorhanden(mail(20, max));

        assertThat(service.inPapierkorb(List.of(20L), ERIKA)).isEqualTo(1);
    }
}
