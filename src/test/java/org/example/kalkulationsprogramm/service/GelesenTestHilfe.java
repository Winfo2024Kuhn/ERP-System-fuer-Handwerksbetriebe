package org.example.kalkulationsprogramm.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Email;

/**
 * Testhilfe für Tests, in denen {@link EmailGelesenService} nur eine Attrappe ist: Sie verhält
 * sich wie das gemeinsame Feld „gelesen“ der Mail. So prüfen diese Tests weiter Sichtbarkeit und
 * Abläufe; „gelesen pro Person“ selbst prüfen {@code EmailGelesenServiceTest} und
 * {@code BenachrichtigungMailSichtbarkeitTest} mit echter Datenbank.
 */
public final class GelesenTestHilfe {

    private GelesenTestHilfe() {
    }

    public static void wieGemeinsam(EmailGelesenService attrappe) {
        // doReturn/doAnswer statt when(...): führt die Attrappe beim Einrichten nicht aus.
        lenient().doReturn(GelesenStand.GEMEINSAM).when(attrappe).fuer(any());
        lenient().doReturn(GelesenStand.GEMEINSAM).when(attrappe).fuerBenutzer(any());
        lenient().doAnswer(aufruf -> {
            Collection<Email> mails = aufruf.getArgument(1);
            List<Email> neu = mails.stream().filter(e -> !e.isRead()).toList();
            neu.forEach(e -> {
                e.setRead(true);
                if (e.getFirstViewedAt() == null) {
                    e.setFirstViewedAt(LocalDateTime.now());
                }
            });
            return neu;
        }).when(attrappe).alsGelesenMarkieren(any(), any());
    }
}
