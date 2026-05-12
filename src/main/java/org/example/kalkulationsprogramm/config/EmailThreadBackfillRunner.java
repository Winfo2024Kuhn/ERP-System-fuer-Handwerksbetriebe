package org.example.kalkulationsprogramm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

/**
 * Verknüpft beim Serverstart rückwirkend alle E-Mails ohne {@code parentEmail},
 * deren Betreff sie als Antwort/Weiterleitung ausweist, mit ihrer Original-Mail.
 *
 * <p>Hintergrund: Die primäre Thread-Erkennung läuft über
 * {@code In-Reply-To}/{@code References}-Header während des Imports
 * ({@code EmailImportService.findParentEmail}). Wenn diese Header fehlen
 * (manche Mailclients, ältere Importe vor Header-Persistierung) bleibt nur
 * Subject-Matching. Der Backfill stellt sicher, dass auch nachträgliche
 * Verbesserungen an der Subject-Normalisierung (z. B. Erkennung von
 * "[Ticket#…] RE: …") für bestehende E-Mails wirksam werden.
 *
 * <p>Idempotent: E-Mails, die bereits einen {@code parentEmail} haben,
 * werden übersprungen. Lässt sich beliebig oft neu starten ohne Schaden.
 *
 * <p>Läuft auf {@link ApplicationReadyEvent} (Spring fully started, Webserver
 * läuft), nicht beim DI-Bootstrap. So blockiert der Backfill keinen Startup
 * und Fehler torpedieren den Server nicht.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EmailThreadBackfillRunner {

    private final EmailImportService emailImportService;

    @EventListener(ApplicationReadyEvent.class)
    @Order(100) // nach kritischen Bootstrappern (Frontend-User, Schema-Fix)
    public void backfillEmailThreadsOnStartup() {
        try {
            log.info("[EmailThreadBackfill] Starte Subject-basierten Backfill für Thread-Erkennung...");
            int verknuepft = emailImportService.backfillParentEmails();
            if (verknuepft > 0) {
                log.info("[EmailThreadBackfill] {} E-Mails nachträglich an Thread-Parent verknüpft.", verknuepft);
            } else {
                log.debug("[EmailThreadBackfill] Keine offenen E-Mails — nichts zu tun.");
            }
        } catch (Exception ex) {
            // Backfill darf den Startup NICHT torpedieren — Fehler loggen, weitermachen.
            log.error("[EmailThreadBackfill] Backfill fehlgeschlagen, überspringe.", ex);
        }
    }
}
