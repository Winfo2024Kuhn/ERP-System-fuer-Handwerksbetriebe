package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Verschickt den Bestätigungscode für die digitale Freigabe.
 *
 * <p>Läuft über dasselbe Konto wie die Ausgangsgeschäftsdokumente – der Kunde bekommt
 * den Code also vom selben Absender wie sein Angebot. Bewusst ohne Kopie im
 * "Gesendet"-Ordner und ohne Ablage im E-Mail-Center: Ein Einmal-Code gehört nirgends
 * dauerhaft hin.</p>
 *
 * <p>Mit Zeitlimit: Der Versand läuft in der Transaktion, die die Freigabe-Zeile sperrt. Ein
 * hängender Mailserver bricht nach {@link #ZEITLIMIT} ab, statt Sperre und Datenbankverbindung
 * festzuhalten. Mit System-Signatur wie die übrigen Geschäftsmails (Pflichtangaben, und der
 * Kunde erkennt den Absender).</p>
 *
 * <p>Optik wie der Freigabe-Block der Angebots-Mail
 * ({@link DokumentFreigabeService#buildFreigabeBlockHtml}): linker Akzent-Border in
 * #500010, hellgrauer Hintergrund, Arial.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FreigabeCodeMailService
{
    static final Duration ZEITLIMIT = Duration.ofSeconds(15);
    private static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm");

    private final SystemSettingsService systemSettingsService;
    private final FirmeninformationService firmeninformationService;
    private final EmailSignatureService emailSignatureService;

    /**
     * Schickt den Code an die Adresse(n) der Freigabe. Wirft bei jedem Versandfehler –
     * der Aufrufer rollt dann die Transaktion zurück, damit kein Code gespeichert ist,
     * den der Kunde nie bekommen hat.
     *
     * <p>{@code NOT_SUPPORTED}: Firmendaten und Signatur werden außerhalb der Transaktion des
     * Aufrufers gelesen. Ein Lesefehler dort kann sie so nicht als rollback-only markieren –
     * sonst gingen im Annahme-Weg die gezählten Fehlversuche verloren.</p>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendeCode(DokumentFreigabe freigabe, String code, LocalDateTime gueltigBis) throws Exception
    {
        SystemSettingsService.MailKonto konto = systemSettingsService.getDokumentMailKonto();
        EmailService emailService = new EmailService(konto.host(), konto.port(), konto.username(), konto.password())
                .mitAbsenderName(konto.fromName())
                .mitZeitlimit(ZEITLIMIT);

        String html = mitSignatur(buildCodeMailHtml(freigabe, code, gueltigBis, firmentelefon()));
        emailService.sendEmailAndReturnMessageId(
                freigabe.getKundeEmail(),
                null,
                konto.fromAddress(),
                betreff(freigabe),
                html,
                null,
                null);
    }

    static String betreff(DokumentFreigabe freigabe)
    {
        return "Ihr Bestätigungscode für " + dokumentBezeichnung(freigabe);
    }

    /**
     * HTML der Code-Mail. Der Code steht groß und in Dreiergruppen, damit er sich
     * leicht abtippen lässt; Telefon nur, wenn in den Firmendaten gepflegt.
     */
    static String buildCodeMailHtml(DokumentFreigabe freigabe, String code, LocalDateTime gueltigBis, String telefon)
    {
        String lesbarerCode = code.length() == 6 ? code.substring(0, 3) + " " + code.substring(3) : code;
        String bis = gueltigBis == null ? "" : " (bis " + gueltigBis.format(UHRZEIT) + " Uhr)";
        String hilfe = telefon == null
                ? "Haben Sie diesen Code nicht angefordert? Dann melden Sie sich bitte bei uns."
                : "Haben Sie diesen Code nicht angefordert? Dann rufen Sie uns bitte an: "
                        + "<strong>" + escapeHtml(telefon) + "</strong>.";

        return "<div style=\"margin:0;padding:0;font-family:Arial,Helvetica,sans-serif;color:#1e293b;\">"
                + "<p style=\"margin:0 0 16px 0;line-height:1.5;\">Guten Tag,</p>"
                + "<p style=\"margin:0 0 16px 0;line-height:1.5;\">"
                + "mit diesem Code bestätigen Sie die Freigabe von <strong>"
                + escapeHtml(dokumentBezeichnung(freigabe)) + "</strong>:</p>"
                + "<div style=\"margin:0 0 20px 0;padding:16px 18px;border-left:3px solid #500010;background:#fafafa;\">"
                + "<p style=\"margin:0 0 6px 0;color:#475569;font-size:14px;\">Ihr Bestätigungscode</p>"
                + "<p style=\"margin:0;font-size:32px;font-weight:700;letter-spacing:4px;color:#1e293b;font-family:Arial,Helvetica,sans-serif;\">"
                + escapeHtml(lesbarerCode) + "</p>"
                + "<p style=\"margin:8px 0 0 0;color:#94a3b8;font-size:13px;\">Der Code gilt 15 Minuten" + bis + ".</p>"
                + "</div>"
                + "<p style=\"margin:0 0 12px 0;color:#475569;line-height:1.5;\">"
                + "Geben Sie den Code auf der Freigabe-Seite ein, die Sie gerade geöffnet haben.</p>"
                + "<p style=\"margin:0;color:#475569;line-height:1.5;\">" + hilfe + "</p>"
                + "</div>";
    }

    private static String dokumentBezeichnung(DokumentFreigabe freigabe)
    {
        String art = freigabe.getDokumentArt() == null || freigabe.getDokumentArt().isBlank()
                ? "Ihr Dokument" : freigabe.getDokumentArt();
        String nummer = freigabe.getDokumentNummer();
        return nummer == null || nummer.isBlank() ? art : art + " " + nummer;
    }

    /** Hängt die System-Signatur an; hakt das Laden, geht die Mail ohne sie raus. */
    String mitSignatur(String html)
    {
        try
        {
            return emailSignatureService.appendSystemSignatureIfConfigured(html);
        }
        catch (RuntimeException e)
        {
            log.warn("System-Signatur für die Code-Mail nicht ladbar: {}", e.getMessage());
            return html;
        }
    }

    /** Telefon aus den Firmendaten – fehlt es oder hakt das Laden, entfällt die Nummer in der Mail. */
    String firmentelefon()
    {
        try
        {
            FirmeninformationDto firma = firmeninformationService.getFirmeninformation();
            String telefon = firma == null ? null : firma.getTelefon();
            return telefon == null || telefon.isBlank() ? null : telefon.trim();
        }
        catch (RuntimeException e)
        {
            log.warn("Firmentelefon für die Code-Mail nicht ladbar: {}", e.getMessage());
            return null;
        }
    }

    private static String escapeHtml(String s)
    {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
