package org.example.kalkulationsprogramm.service.mail;

import java.util.List;
import java.util.Properties;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.stereotype.Component;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.MessageIDTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Legt jede versendete E-Mail zusaetzlich im "Gesendet"-Ordner des Mail-Providers
 * ab (IMAP APPEND).
 *
 * <p><strong>Warum:</strong> Bisher existierte eine versendete Mail nur in der
 * ERP-Datenbank. Das ist fuer die Darstellung ideal — das originale HTML bleibt
 * unveraendert, Antworten haengen sauber am Thread — taugt aber wenig als
 * Nachweis: eine selbst gefuehrte Datenbank kann der Betreiber jederzeit
 * aendern. Eine Kopie im Postfach beim Provider ist davon unabhaengig und
 * traegt dessen Zeitstempel.</p>
 *
 * <p><strong>Warum es trotzdem keine Duplikate gibt:</strong> Der IMAP-Import
 * liest den "Gesendet"-Ordner mit. Dass die Kopie nicht ein zweites Mal — und
 * dabei mit vom Server umgebautem Layout — in der Datenbank landet, verhindert
 * die Deduplizierung ueber die Message-ID: die Ausgangsmail steht mit derselben
 * ID bereits lokal. Der {@link EmailService#ERP_ORIGIN_HEADER} filtert dabei
 * <em>nicht</em>, er dient nur der Diagnose — kommt eine markierte Mail durch,
 * war die lokale Archivierung fehlgeschlagen, und die Mail wird bewusst
 * nachgeholt statt verworfen.</p>
 *
 * <p><strong>Einschraenkung, die man kennen sollte:</strong> Auch das
 * Provider-Postfach gehoert dem Absender. Fuer einen belastbaren Zustellnachweis
 * vor Gericht ersetzt das kein qualifiziertes Archivierungsverfahren — es ist
 * aber deutlich mehr wert als ein reiner Datenbankeintrag.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SentMailArchiver implements EmailService.SentCopyHandler
{
    /**
     * Uebliche Namen des "Gesendet"-Ordners. T-Online nutzt "INBOX.Sent"; die
     * weiteren Kandidaten decken andere Provider ab. Der erste existierende
     * Ordner gewinnt.
     */
    private static final List<String> SENT_ORDNER_KANDIDATEN = List.of(
            "INBOX.Sent", "INBOX.Sent Items", "INBOX.Gesendet", "Sent", "Gesendet");

    /** Schaltet die Server-Kopie ab, ohne Code-Aenderung (System-Einstellungen). */
    private static final String SETTING_AKTIV = "mail.sent-kopie.aktiv";

    private final SystemSettingsService systemSettingsService;

    @Override
    public void archiviereKopie(MimeMessage versendeteNachricht)
    {
        if (versendeteNachricht == null) return;
        if (!istAktiv())
        {
            log.debug("[SentKopie] Deaktiviert ({}=false)", SETTING_AKTIV);
            return;
        }
        if (!systemSettingsService.isImapConfigured())
        {
            log.debug("[SentKopie] IMAP nicht konfiguriert — keine Server-Kopie moeglich");
            return;
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "30000");

        try (Store store = Session.getInstance(props).getStore("imaps"))
        {
            store.connect(systemSettingsService.getImapHost(),
                    systemSettingsService.getImapPort(),
                    systemSettingsService.getImapUsername(),
                    systemSettingsService.getImapPassword());

            Folder sent = findeSentOrdner(store);
            if (sent == null)
            {
                log.warn("[SentKopie] Kein 'Gesendet'-Ordner gefunden (geprueft: {})", SENT_ORDNER_KANDIDATEN);
                return;
            }

            // Als gelesen markieren: es ist die eigene Mail, sie soll im
            // Webmailer des Inhabers nicht als ungelesen auftauchen.
            versendeteNachricht.setFlag(Flags.Flag.SEEN, true);

            sent.open(Folder.READ_WRITE);
            try
            {
                // Manche Provider legen SMTP-versendete Mails selbst im
                // "Gesendet"-Ordner ab, andere nicht (bei T-Online in Produktion
                // als unzuverlaessig nachgewiesen — siehe
                // AnfrageBestaetigungVersandService). Deshalb erst pruefen: liegt
                // die Mail schon da, waere unser APPEND ein sichtbares Duplikat
                // im Webmailer des Inhabers.
                if (istBereitsVorhanden(sent, versendeteNachricht))
                {
                    log.debug("[SentKopie] Provider hat die Mail bereits in '{}' abgelegt — kein zweites APPEND",
                            sent.getFullName());
                    return;
                }

                sent.appendMessages(new Message[] { versendeteNachricht });
                // Bewusst ohne Empfaenger-Adresse geloggt (DSGVO).
                log.debug("[SentKopie] Kopie in '{}' abgelegt: {}",
                        sent.getFullName(), versendeteNachricht.getMessageID());
            }
            finally
            {
                sent.close(false);
            }
        }
        catch (Exception e)
        {
            // Der Aufrufer verschluckt Fehler ohnehin — hier wird geloggt, damit
            // ein dauerhaft fehlschlagender APPEND (falscher Ordnername,
            // abgelaufenes Passwort) im Log sichtbar bleibt.
            log.warn("[SentKopie] Ablage im 'Gesendet'-Ordner fehlgeschlagen: {}", e.getMessage());
        }
    }

    /**
     * {@code true}, wenn im Ordner bereits eine Nachricht mit derselben
     * Message-ID liegt. Kann der Server nicht suchen (manche IMAP-Server
     * beherrschen {@code SEARCH HEADER} nur eingeschraenkt), gilt die Mail als
     * nicht vorhanden — lieber eine Kopie zu viel als der fehlende Nachweis,
     * um den es bei diesem Feature geht.
     */
    private static boolean istBereitsVorhanden(Folder sent, MimeMessage nachricht)
    {
        try
        {
            String messageId = nachricht.getMessageID();
            if (messageId == null || messageId.isBlank()) return false;
            return sent.search(new MessageIDTerm(messageId)).length > 0;
        }
        catch (Exception e)
        {
            log.debug("[SentKopie] Vorab-Suche im 'Gesendet'-Ordner nicht moeglich: {}", e.getMessage());
            return false;
        }
    }

    private boolean istAktiv()
    {
        return !"false".equalsIgnoreCase(systemSettingsService.get(SETTING_AKTIV, "true"));
    }

    /** Erster existierender Kandidat, sonst {@code null}. */
    private static Folder findeSentOrdner(Store store)
    {
        for (String name : SENT_ORDNER_KANDIDATEN)
        {
            try
            {
                Folder kandidat = store.getFolder(name);
                if (kandidat != null && kandidat.exists()) return kandidat;
            }
            catch (Exception e)
            {
                log.debug("[SentKopie] Ordner '{}' nicht pruefbar: {}", name, e.getMessage());
            }
        }
        return null;
    }
}
