package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Versendet die automatische Bestätigungsmail an Leads, die über den
 * öffentlichen Webseiten-Funnel eine Anfrage abschicken. Trigger:
 * {@link AnfrageFunnelService#verarbeiteFunnelAnfrage}.
 *
 * <p>Architektur-Vorbild: {@link AutoAuftragsbestaetigungVersandService} —
 * gleicher Aufbau, gleicher Versand-Pfad (Template → Signatur → Absender →
 * SMTP). So bleibt das Verhalten konsistent zu allen anderen
 * System-generierten Mails.</p>
 *
 * <p><b>Fehlerverhalten:</b> Alle Exceptions werden geschluckt und geloggt. Die
 * Bestätigungsmail ist Komfort, kein harter Bestandteil der Funnel-Verarbeitung
 * — ein SMTP-Ausfall darf nicht dazu führen, dass der Lead-Datensatz im ERP
 * verloren geht. Der Aufruf erfolgt asynchron nach dem Commit der
 * Funnel-Transaktion (afterCommit + TaskExecutor), damit weder SMTP-Latenz
 * noch DB-Lock-Kontention die Antwort an die Webseite blockieren.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnfrageBestaetigungVersandService {

    /**
     * Dokumenttyp-Key der DB-Vorlage. Bewusst eigener Wert (kein Dokumenttyp-
     * Enum-Member), weil eine Lead-Bestätigung kein Geschäftsdokument ist —
     * sie wird im UI nur über die Kategorie WEBSITE gruppiert.
     */
    public static final String VORLAGE_DOKUMENT_TYP = "WEBSITE_ANFRAGE_BESTAETIGUNG";

    private static final DateTimeFormatter DATUM_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final EmailTextTemplateService emailTextTemplateService;
    private final EmailSignatureService emailSignatureService;
    private final SystemSettingsService systemSettingsService;
    private final EmailOutboundPersistenceService outboundPersistenceService;

    /**
     * Versendet eine Bestätigungsmail an den ersten in der Anfrage hinterlegten
     * Kunden-E-Mail-Empfänger. Liefert {@code true} bei erfolgreichem Versand,
     * {@code false} bei jedem Problem (kein Empfänger, keine aktive Vorlage,
     * SMTP-Fehler …). Wirft nie.
     *
     * @param anfrage  persistierte Funnel-Anfrage
     * @param vorname  Vorname aus dem Funnel-Payload (für ANREDE/KUNDENNAME)
     * @param nachname Nachname aus dem Funnel-Payload
     * @param nachricht freier Beschreibungstext des Leads (wird HTML-escaped)
     */
    public boolean versendeBestaetigung(Anfrage anfrage, String vorname, String nachname, String nachricht) {
        if (anfrage == null) {
            return false;
        }
        String empfaenger = ersterEmpfaenger(anfrage);
        if (empfaenger == null) {
            log.info("Anfrage-Bestaetigung uebersprungen: keine Empfaenger-Mail in Anfrage id={}", anfrage.getId());
            return false;
        }

        try {
            Map<String, String> ctx = baueKontext(anfrage, vorname, nachname, nachricht);
            EmailService.EmailContent content = emailTextTemplateService.render(VORLAGE_DOKUMENT_TYP, ctx);
            if (content == null || content.subject() == null || content.subject().isBlank()) {
                log.warn("Anfrage-Bestaetigung uebersprungen: keine aktive Vorlage '{}'", VORLAGE_DOKUMENT_TYP);
                return false;
            }

            if (!systemSettingsService.isSmtpConfigured()) {
                log.warn("Anfrage-Bestaetigung uebersprungen: SMTP ist nicht konfiguriert");
                return false;
            }

            String htmlMitSignatur = emailSignatureService
                    .appendSystemSignatureIfConfigured(content.htmlBody());
            String absender = systemSettingsService.getMailFromAddress();

            EmailService emailService = baueEmailService();
            String messageId = emailService.sendEmailAndReturnMessageId(
                    empfaenger,
                    null,
                    absender,
                    content.subject(),
                    htmlMitSignatur,
                    null,
                    null);

            persistiereAusgangsEmail(anfrage, empfaenger, absender,
                    content.subject(), htmlMitSignatur, messageId);

            // Bewusst ohne Empfaenger-Adresse geloggt (DSGVO) — die anfrageId
            // reicht, um die Mail im E-Mail-Center wiederzufinden.
            log.info("Anfrage-Bestaetigung versendet (anfrageId={})", anfrage.getId());
            return true;
        } catch (Exception e) {
            // Bewusst Exception-fangen: die Funnel-Persistenz darf nicht
            // scheitern, nur weil SMTP/Template/Signatur kurz aerger machen.
            log.error("Anfrage-Bestaetigung fuer Anfrage {} fehlgeschlagen: {}",
                    anfrage.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Anzahl der Speicherversuche fuer die OUT-Email. Der IMAP-Import-Job
     * schreibt alle 60s in dieselbe email-Tabelle — kollidiert unser Insert
     * mit dessen Locks (in Produktion beobachtet: Lock wait timeout nach 50s,
     * anfrageId=146), probieren wir es nach kurzer Pause erneut, statt den
     * Eintrag zu verlieren.
     */
    private static final int MAX_SPEICHER_VERSUCHE = 3;

    /**
     * Basis-Pause zwischen den Speicherversuchen (waechst linear pro Versuch).
     * Paketsichtbar, damit Unit-Tests sie auf 0 setzen koennen und nicht echt
     * schlafen muessen.
     */
    long retryPauseMillis = 2_000L;

    /**
     * Persistiert die gerade per SMTP versandte Bestaetigung als OUT-Email und
     * ordnet sie direkt der Anfrage zu — so taucht sie sofort im E-Mail-Center
     * der Anfrage auf.
     *
     * <p><b>Wichtig:</b> Dieser DB-Eintrag ist die einzige verlaessliche
     * Persistenz. Der IMAP-Sent-Poll ist KEIN Sicherheitsnetz — T-Online legt
     * per SMTP versendete Mails nicht zuverlaessig in INBOX.Sent ab (in
     * Produktion nachgewiesen: Bestaetigungen der Anfragen 139 und 146 wurden
     * nie nachimportiert). Deshalb wird der Insert bei Lock-/Commit-Konflikten
     * bis zu {@value #MAX_SPEICHER_VERSUCHE}-mal wiederholt. Die Dedup ueber
     * den Unique-Index auf {@code messageId} verhindert Duplikate, falls der
     * Sent-Poll die Mail doch importiert.</p>
     *
     * <p>Fehler werden nach dem letzten Versuch geschluckt — die SMTP-Mail ist
     * an dieser Stelle bereits raus; ein DB-Problem darf den Funnel-Flow nicht
     * kippen.</p>
     */
    private void persistiereAusgangsEmail(Anfrage anfrage, String empfaenger, String absender,
                                          String subject, String htmlBody, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            log.error("Anfrage-Bestaetigung ohne Message-ID — kein DB-Eintrag moeglich (anfrageId={})",
                    anfrage.getId());
            return;
        }
        for (int versuch = 1; versuch <= MAX_SPEICHER_VERSUCHE; versuch++) {
            try {
                if (outboundPersistenceService.existsByMessageId(messageId)) {
                    return;
                }
                Email email = new Email();
                email.assignToAnfrage(anfrage);
                email.setMessageId(messageId);
                email.setDirection(EmailDirection.OUT);
                email.setFromAddress(absender);
                email.setRecipient(empfaenger);
                email.setSubject(subject);
                email.setHtmlBody(htmlBody);
                email.setBody(EmailHtmlSanitizer.htmlToPlainText(htmlBody));
                email.setSentAt(LocalDateTime.now());
                email.setRead(true);
                // Rein lokaler Ausgangsdatensatz: kein IMAP-Ordner und keine
                // serverseitige Sent-Kopie. Der Gesendet-Filter nutzt direction=OUT.
                outboundPersistenceService.speichereOutEmail(email);
                return;
            } catch (org.springframework.dao.DataIntegrityViolationException race) {
                // Race-Ausgang: eine parallele Quelle (z.B. Sent-Poll) hat die
                // Mail zwischen existsByMessageId-Check und save persistiert.
                // Der Unique-Constraint auf messageId hat den Doppel-Insert
                // verhindert — kein Fehler im fachlichen Sinn.
                log.info("Anfrage-Bestaetigung bereits anderweitig persistiert (race) — anfrageId={}, messageId={}",
                        anfrage.getId(), messageId);
                return;
            } catch (Exception e) {
                // Lock-Timeout, Deadlock, UnexpectedRollback der REQUIRES_NEW-Tx …
                // — alles Kandidaten fuer einen Retry. Wir laufen hier in einem
                // Hintergrund-Thread (afterCommit + TaskExecutor), die Pause
                // blockiert also keinen Web-Request.
                if (versuch == MAX_SPEICHER_VERSUCHE) {
                    log.error("Konnte Anfrage-Bestaetigung nach {} Versuchen nicht in email-Tabelle persistieren "
                                    + "(anfrageId={}, messageId={}): {}",
                            versuch, anfrage.getId(), messageId, e.getMessage(), e);
                    return;
                }
                log.warn("Anfrage-Bestaetigung speichern fehlgeschlagen (Versuch {}/{}, anfrageId={}): {} — neuer Versuch in {}ms",
                        versuch, MAX_SPEICHER_VERSUCHE, anfrage.getId(), e.getMessage(), retryPauseMillis * versuch);
                try {
                    Thread.sleep(retryPauseMillis * versuch);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Paketsichtbar, damit der Unit-Test einen Mock-EmailService injecten kann
     * ohne echten SMTP-Connect zu versuchen.
     */
    EmailService baueEmailService() {
        return new EmailService(
                systemSettingsService.getSmtpHost(),
                systemSettingsService.getSmtpPort(),
                systemSettingsService.getSmtpUsername(),
                systemSettingsService.getSmtpPassword());
    }

    private static String ersterEmpfaenger(Anfrage anfrage) {
        if (anfrage.getKundenEmails() == null) return null;
        return anfrage.getKundenEmails().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private static Map<String, String> baueKontext(Anfrage anfrage, String vorname, String nachname, String nachricht) {
        Map<String, String> ctx = new HashMap<>();
        String voll = (safe(vorname) + " " + safe(nachname)).trim();
        // Bei Leads kennen wir keine formale Anrede (Herr/Frau) — wir bleiben
        // mit "Hallo {Vorname Nachname}" bewusst informell, das passt zur
        // Handwerker-Tonalitaet und vermeidet falsch geratene Anreden.
        ctx.put("ANREDE", voll.isEmpty() ? "Hallo" : "Hallo " + escape(voll));
        ctx.put("KUNDENNAME", escape(voll));
        ctx.put("VORNAME", escape(safe(vorname)));
        ctx.put("NACHNAME", escape(safe(nachname)));
        ctx.put("BAUVORHABEN", escape(safe(anfrage.getBauvorhaben())));
        ctx.put("NACHRICHT", escape(safe(nachricht)));
        ctx.put("ANFRAGE_DATUM", anfrage.getAnlegedatum() != null
                ? anfrage.getAnlegedatum().format(DATUM_DE)
                : "");
        ctx.put("ANFRAGENUMMER", anfrage.getId() != null ? anfrage.getId().toString() : "");
        return ctx;
    }

    /**
     * HTML-escape gegen XSS — die Werte landen 1:1 im HTML-Body, der per
     * Mailclient gerendert wird. Ein boeswilliger Lead koennte sonst per
     * NACHRICHT-Feld Skript-Inhalte einschleusen, die der Empfaenger (der
     * Handwerker, der seine Anfragenmails sichtet) zu sehen bekommt.
     */
    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
