package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailCleanupServiceLueckenTest {

    @Mock EmailRepository emailRepository;
    @Mock EmailImportService emailImportService;
    @Mock SpamBayesService spamBayesService;
    @InjectMocks EmailCleanupService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "emailFeaturesEnabled", true);
    }

    private Email mail(long id) {
        Email e = new Email();
        e.setId(id);
        return e;
    }

    @Test
    @DisplayName("cleanupEmails: bei deaktivierten Mail-Features passiert nichts")
    void cleanupDeaktiviert() {
        ReflectionTestUtils.setField(service, "emailFeaturesEnabled", false);
        service.cleanupEmails();
        verifyNoInteractions(emailRepository, emailImportService, spamBayesService);
    }

    @Test
    @DisplayName("Papierkorb: nur Mails aelter als 30 Tage werden vom Server und aus der DB geloescht")
    void papierkorbRetention() {
        Email alt = mail(1L);
        alt.setDeletedAt(LocalDateTime.now().minusDays(31));
        Email neu = mail(2L);
        neu.setDeletedAt(LocalDateTime.now().minusDays(5));
        when(emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()).thenReturn(List.of(alt, neu));
        when(emailRepository.findSpam()).thenReturn(List.of());
        when(emailRepository.findNewsletter()).thenReturn(List.of());

        service.cleanupEmails();

        verify(emailImportService).deleteEmailFromServer(alt);
        verify(emailRepository).detachRepliesFromParent(1L);
        verify(emailRepository).flush();
        verify(emailRepository).delete(alt);
        verify(emailRepository, never()).delete(neu);
        verify(emailImportService, never()).deleteEmailFromServer(neu);
    }

    @Test
    @DisplayName("Papierkorb: hoher Spam-Score (>=85) markiert die Mail vor dem Loeschen als Spam")
    void papierkorbSpamScoreMarkiert() {
        Email alt = mail(1L);
        alt.setDeletedAt(LocalDateTime.now().minusDays(40));
        alt.setSpamScore(85);
        Email niedrig = mail(2L);
        niedrig.setDeletedAt(LocalDateTime.now().minusDays(40));
        niedrig.setSpamScore(84);
        when(emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()).thenReturn(List.of(alt, niedrig));
        when(emailRepository.findSpam()).thenReturn(List.of());
        when(emailRepository.findNewsletter()).thenReturn(List.of());

        service.cleanupEmails();

        assertTrue(alt.isSpam());
        assertFalse(niedrig.isSpam());
        verify(emailRepository).save(alt);
        verify(emailRepository, never()).save(niedrig);
    }

    @Test
    @DisplayName("Spam/Newsletter: zugeordnete Mails (Lieferant, Projekt, Anfrage) werden nie geloescht")
    void zugeordneteBleiben() {
        Email mitLieferant = mail(1L);
        mitLieferant.setSentAt(LocalDateTime.now().minusDays(90));
        mitLieferant.setLieferant(new Lieferanten());
        Email mitProjekt = mail(2L);
        mitProjekt.setSentAt(LocalDateTime.now().minusDays(90));
        mitProjekt.setProjekt(new Projekt());
        Email mitAnfrage = mail(3L);
        mitAnfrage.setSentAt(LocalDateTime.now().minusDays(90));
        mitAnfrage.setAnfrage(new Anfrage());
        when(emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()).thenReturn(List.of());
        when(emailRepository.findSpam()).thenReturn(List.of(mitLieferant, mitProjekt));
        when(emailRepository.findNewsletter()).thenReturn(List.of(mitAnfrage));

        service.cleanupEmails();

        verify(emailRepository, never()).delete(any(Email.class));
        verifyNoInteractions(emailImportService);
    }

    @Test
    @DisplayName("Spam/Newsletter: alte, nicht zugeordnete Mails werden geloescht; neue und ohne sentAt bleiben")
    void spamUndNewsletterRetention() {
        Email altSpam = mail(1L);
        altSpam.setSentAt(LocalDateTime.now().minusDays(31));
        Email neuSpam = mail(2L);
        neuSpam.setSentAt(LocalDateTime.now().minusDays(1));
        Email ohneDatum = mail(3L);
        Email altNews = mail(4L);
        altNews.setSentAt(LocalDateTime.now().minusDays(100));
        when(emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()).thenReturn(List.of());
        when(emailRepository.findSpam()).thenReturn(List.of(altSpam, neuSpam, ohneDatum));
        when(emailRepository.findNewsletter()).thenReturn(List.of(altNews));

        service.cleanupEmails();

        verify(emailRepository).delete(altSpam);
        verify(emailRepository).delete(altNews);
        verify(emailRepository, never()).delete(neuSpam);
        verify(emailRepository, never()).delete(ohneDatum);
    }

    @Test
    @DisplayName("Fehler beim Loeschen einer Mail bricht den Lauf nicht ab")
    void fehlerBrichtNichtAb() {
        Email kaputt = mail(1L);
        kaputt.setSentAt(LocalDateTime.now().minusDays(60));
        Email ok = mail(2L);
        ok.setSentAt(LocalDateTime.now().minusDays(60));
        when(emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()).thenReturn(List.of());
        when(emailRepository.findSpam()).thenReturn(List.of(kaputt, ok));
        when(emailRepository.findNewsletter()).thenReturn(List.of());
        doThrow(new RuntimeException("IMAP weg")).when(emailImportService).deleteEmailFromServer(kaputt);

        assertDoesNotThrow(() -> service.cleanupEmails());

        verify(emailRepository, never()).delete(kaputt);
        verify(emailRepository).delete(ok);
    }

    @Test
    @DisplayName("Mail ohne ID: kein detachRepliesFromParent, wird aber geloescht")
    void ohneId() {
        Email e = new Email();
        e.setSentAt(LocalDateTime.now().minusDays(60));
        when(emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()).thenReturn(List.of());
        when(emailRepository.findSpam()).thenReturn(List.of(e));
        when(emailRepository.findNewsletter()).thenReturn(List.of());

        service.cleanupEmails();

        verify(emailRepository, never()).detachRepliesFromParent(anyLong());
        verify(emailRepository).delete(e);
    }

    @Test
    @DisplayName("trainImplicitHam: deaktiviert oder Modell nicht bereit -> kein Repository-Zugriff")
    void hamUebersprungen() {
        ReflectionTestUtils.setField(service, "emailFeaturesEnabled", false);
        service.trainImplicitHam();
        verifyNoInteractions(emailRepository, spamBayesService);

        ReflectionTestUtils.setField(service, "emailFeaturesEnabled", true);
        when(spamBayesService.isModelReady()).thenReturn(false);
        service.trainImplicitHam();
        verifyNoInteractions(emailRepository);
    }

    @Test
    @DisplayName("trainImplicitHam: ohne Kandidaten wird nichts gespeichert")
    void hamOhneKandidaten() {
        when(spamBayesService.isModelReady()).thenReturn(true);
        when(emailRepository.findLongLivedInboxEmailsWithoutVerdict(any())).thenReturn(List.of());

        service.trainImplicitHam();

        verify(emailRepository, never()).saveAll(anyList());
        verify(spamBayesService, never()).train(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("trainImplicitHam: trainiert als Ham, setzt HAM_IMPLICIT, ueberspringt Fehlerfaelle")
    void hamTrainiert() {
        Email a = mail(1L);
        Email b = mail(2L);
        when(spamBayesService.isModelReady()).thenReturn(true);
        when(emailRepository.findLongLivedInboxEmailsWithoutVerdict(any())).thenReturn(List.of(a, b));
        lenient().doThrow(new IllegalStateException("kaputt")).when(spamBayesService).train(b, false);

        service.trainImplicitHam();

        verify(spamBayesService).train(a, false);
        assertEquals("HAM_IMPLICIT", a.getUserSpamVerdict());
        assertNull(b.getUserSpamVerdict());
        verify(emailRepository).saveAll(List.of(a, b));
    }
}
