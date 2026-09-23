package org.example.kalkulationsprogramm.service.mail;

import java.util.List;
import java.util.Properties;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Verschluesselung;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.ArchivErgebnis;
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
    private final LocalTestMailPolicy localTestMailPolicy;

    /**
     * Ablage im "Gesendet"-Ordner des Standard-Postfachs.
     *
     * <p>Gilt fuer den manuellen Schriftverkehr. Mails, die ueber das Postfach
     * fuer Ausgangsgeschaeftsdokumente rausgehen, brauchen stattdessen
     * {@link #fuerDokumentKonto()} — sonst laege die Kopie in einem anderen
     * Postfach als dem, aus dem die Mail nachweislich versendet wurde, und
     * waere als Beleg deutlich weniger wert.</p>
     */
    @Override
    public void archiviereKopie(MimeMessage versendeteNachricht)
    {
        archiviereKopie(versendeteNachricht, systemSettingsService.getStandardImapZugang(), "HAUPT");
    }

    /**
     * Handler fuer Mails, die ueber das Postfach fuer Ausgangsgeschaeftsdokumente
     * versendet wurden. Ist kein eigenes Postfach eingerichtet, liefert
     * {@code getDokumentImapZugang()} den Standard-Zugang — das Verhalten ist
     * dann identisch zu {@link #archiviereKopie(MimeMessage)}.
     */
    public EmailService.SentCopyHandler fuerDokumentKonto()
    {
        return nachricht -> archiviereKopie(nachricht, systemSettingsService.getDokumentImapZugang(), "DOKUMENTE");
    }

    /** Stores the exact successfully sent bytes in the selected account's Sent folder. */
    public ArchivErgebnis archiviere(MailkontoService.KontoZugang konto, byte[] mime)
    {
        Store store = null;
        Folder sent = null;
        try
        {
            if (konto == null || !konto.aktiv() || konto.imap() == null || konto.sent() == null || konto.sent().isBlank())
                return new ArchivErgebnis(false, "KONTO_UNGUELTIG");
            localTestMailPolicy.pruefeNetzwerkzugriff(konto.id());
            var imap = konto.imap();
            if (mime == null || mime.length == 0) return new ArchivErgebnis(false, "MIME_FEHLT");
            if (imap.host() == null || imap.host().isBlank() || imap.username() == null || imap.username().isBlank()
                    || imap.password() == null || imap.password().isBlank() || imap.port() < 1 || imap.port() > 65535
                    || imap.tls() == null) return new ArchivErgebnis(false, "KONTO_UNGUELTIG");
            Properties props = new Properties();
            String protocol = imap.tls() == Verschluesselung.TLS ? "imaps" : "imap";
            String prefix = imap.tls() == Verschluesselung.TLS ? "mail.imaps" : "mail.imap";
            props.put(prefix + ".connectiontimeout", "15000");
            props.put(prefix + ".timeout", "30000");
            props.put(prefix + ".ssl.checkserveridentity", "true");
            if (imap.tls() == Verschluesselung.STARTTLS) {
                props.put("mail.imap.starttls.enable", "true");
                props.put("mail.imap.starttls.required", "true");
            }
            Session session = Session.getInstance(props);
            store = session.getStore(protocol);
            store.connect(imap.host(), imap.port(), imap.username(), imap.password());
            sent = store.getFolder(konto.sent());
            if (sent == null || !sent.exists()) return new ArchivErgebnis(false, "IMAP_SENT_FEHLT");
            MimeMessage message = EmailService.ausEingefrorenemMime(session, mime);
            sent.open(Folder.READ_WRITE);
            String id = message.getMessageID();
            if (id != null && sent.search(new MessageIDTerm(id)).length > 0) return new ArchivErgebnis(true, null);
            message.setFlag(Flags.Flag.SEEN, true);
            sent.appendMessages(new Message[] { message });
            return new ArchivErgebnis(true, null);
        }
        catch (Exception e)
        {
            log.warn("[EinkaufMail] IMAP-Archivierung fehlgeschlagen: {}", e.getClass().getSimpleName());
            return new ArchivErgebnis(false, e instanceof jakarta.mail.AuthenticationFailedException
                    ? "AUTHENTIFIZIERUNG_FEHLGESCHLAGEN" : "IMAP_ARCHIV");
        }
        finally
        {
            try { if (sent != null && sent.isOpen()) sent.close(false); } catch (Exception e) { log.debug("[EinkaufMail] Sent-Ordner konnte nicht geschlossen werden: {}", e.getClass().getSimpleName()); }
            try { if (store != null && store.isConnected()) store.close(); } catch (Exception e) { log.debug("[EinkaufMail] IMAP-Verbindung konnte nicht geschlossen werden: {}", e.getClass().getSimpleName()); }
        }
    }

    void archiviereKopie(MimeMessage versendeteNachricht,
            SystemSettingsService.ImapZugang zugang, String kontoId)
    {
        if (versendeteNachricht == null) return;
        if (!istAktiv())
        {
            log.debug("[SentKopie] Deaktiviert ({}=false)", SETTING_AKTIV);
            return;
        }
        if (zugang == null
                || zugang.host() == null || zugang.host().isBlank()
                || zugang.username() == null || zugang.username().isBlank()
                || zugang.password() == null || zugang.password().isBlank())
        {
            log.debug("[SentKopie] Posteingang nicht konfiguriert — keine Server-Kopie moeglich");
            return;
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "30000");

        try
        {
            localTestMailPolicy.pruefeNetzwerkzugriff(kontoId);
            try (Store store = Session.getInstance(props).getStore("imaps"))
            {
                store.connect(zugang.host(), zugang.port(), zugang.username(), zugang.password());

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
