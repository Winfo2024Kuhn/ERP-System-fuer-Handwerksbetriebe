package org.example.kalkulationsprogramm.service.mail;

import java.util.Optional;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailkonto;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Response;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Update;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Verschluesselung;
import org.example.kalkulationsprogramm.repository.EinkaufMailkontoRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MailkontoService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(?:\\.[^@\\s.]+)+$");
    private final EinkaufMailkontoRepository repository;
    private final SystemSettingsService settings;
    private final MailSecretService secrets;
    private final EinkaufBerechtigungService berechtigungen;

    public KontoZugang resolve(String kontoId) {
        if (kontoId == null) throw new IllegalArgumentException("Das Mailkonto ist ungültig.");
        return switch (kontoId) {
            case "HAUPT" -> adaptiereHauptkonto();
            case "DOKUMENTE" -> adaptiereDokumentkonto();
            case "EINKAUF" -> ladeEinkaufZugang();
            default -> throw new IllegalArgumentException("Das Mailkonto ist ungültig.");
        };
    }

    @Transactional(readOnly = true)
    public Response lesen(Authentication authentication) {
        berechtigungen.verlangeAktivenAdmin(authentication);
        return response(repository.findById("EINKAUF").orElseGet(EinkaufMailkonto::new));
    }

    @Transactional
    public Response speichern(Authentication authentication, Update update) {
        berechtigungen.verlangeAktivenAdmin(authentication);
        if (update == null) throw new IllegalArgumentException("Die Mailkonto-Einstellungen fehlen.");
        validiere(update);

        EinkaufMailkonto konto = repository.findById("EINKAUF").orElseGet(EinkaufMailkonto::new);
        if (update.version() == null || update.version() != konto.getVersion()) {
            throw new IllegalStateException("Die Mailkonto-Einstellungen wurden zwischenzeitlich geändert. Bitte neu laden.");
        }
        boolean smtpSecretSet = hatWert(konto.getSmtpPasswordCiphertext());
        boolean imapSecretSet = hatWert(konto.getImapPasswordCiphertext());
        boolean neuesSmtpSecret = hatWert(update.smtpPassword());
        boolean neuesImapSecret = hatWert(update.imapPassword());
        boolean aktivesKontoMitSecrets = update.aktiv() && (smtpSecretSet || imapSecretSet);
        if ((neuesSmtpSecret || neuesImapSecret || aktivesKontoMitSecrets) && !secrets.isConfigured()) {
            secrets.ensureConfigured();
        }
        if (update.aktiv() && (!smtpSecretSet && !neuesSmtpSecret || !imapSecretSet && !neuesImapSecret)) {
            throw new IllegalArgumentException("Zum Einschalten müssen SMTP- und IMAP-Passwort vollständig eingerichtet sein.");
        }

        konto.setId("EINKAUF");
        konto.setAktiv(update.aktiv());
        konto.setFromAddress(sauber(update.fromAddress()));
        konto.setFromName(sauber(update.fromName()));
        konto.setSmtpHost(sauber(update.smtpHost()));
        konto.setSmtpPort(update.smtpPort());
        konto.setSmtpUsername(sauber(update.smtpUsername()));
        konto.setSmtpTls(update.smtpTls().name());
        konto.setImapHost(sauber(update.imapHost()));
        konto.setImapPort(update.imapPort());
        konto.setImapUsername(sauber(update.imapUsername()));
        konto.setImapTls(update.imapTls().name());
        konto.setInbox(sauber(update.inbox()));
        konto.setSent(sauber(update.sent()));
        if (neuesSmtpSecret) konto.setSmtpPasswordCiphertext(secrets.encrypt(update.smtpPassword()));
        if (neuesImapSecret) konto.setImapPasswordCiphertext(secrets.encrypt(update.imapPassword()));
        try {
            return response(repository.saveAndFlush(konto));
        } catch (OptimisticLockingFailureException ex) {
            throw new IllegalStateException(
                    "Die Mailkonto-Einstellungen wurden zwischenzeitlich geändert. Bitte neu laden.", ex);
        }
    }

    private void validiere(Update r) {
        if (r.smtpTls() == null || r.imapTls() == null) throw new IllegalArgumentException("Bitte die Verschlüsselung für SMTP und IMAP auswählen.");
        feld(r.fromAddress(), 254, "Absender-Adresse");
        feld(r.fromName(), 120, "Absendername");
        feld(r.smtpHost(), 253, "SMTP-Server");
        feld(r.smtpUsername(), 254, "SMTP-Benutzername");
        feld(r.imapHost(), 253, "IMAP-Server");
        feld(r.imapUsername(), 254, "IMAP-Benutzername");
        feld(r.inbox(), 255, "Posteingangsordner");
        feld(r.sent(), 255, "Gesendet-Ordner");
        optionalFeld(r.smtpPassword(), 2000, "SMTP-Passwort");
        optionalFeld(r.imapPassword(), 2000, "IMAP-Passwort");
        port(r.smtpPort(), "SMTP");
        port(r.imapPort(), "IMAP");
        if (r.aktiv()) {
            if (!EMAIL.matcher(r.fromAddress()).matches()) throw new IllegalArgumentException("Bitte eine gültige Absender-Adresse eintragen.");
            if (r.smtpHost().isBlank() || r.smtpUsername().isBlank() || r.imapHost().isBlank()
                    || r.imapUsername().isBlank() || r.fromName().isBlank()
                    || r.inbox().isBlank() || r.sent().isBlank()) {
                throw new IllegalArgumentException("Zum Einschalten müssen SMTP-, IMAP-Benutzer und Ordner vollständig eingetragen sein.");
            }
        }
    }

    private void feld(String value, int max, String label) {
        if (value == null || value.length() > max || hatSteuerzeichen(value)) {
            throw new IllegalArgumentException(label + " ist ungültig oder zu lang.");
        }
    }

    private void optionalFeld(String value, int max, String label) {
        if (value != null && (value.length() > max || hatSteuerzeichen(value))) {
            throw new IllegalArgumentException(label + " ist ungültig oder zu lang.");
        }
    }

    private boolean hatSteuerzeichen(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private void port(int port, String protokoll) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Der " + protokoll + "-Port muss zwischen 1 und 65535 liegen.");
    }

    private KontoZugang adaptiereHauptkonto() {
        var smtp = settings.getStandardMailKonto();
        var imap = settings.getStandardImapZugang();
        return new KontoZugang("HAUPT", true, smtp.fromAddress(), smtp.fromName(),
                new MailkontoDto.ServerZugang(smtp.host(), smtp.port(), smtp.username(), smtp.password(), Verschluesselung.TLS),
                new MailkontoDto.ServerZugang(imap.host(), imap.port(), imap.username(), imap.password(), Verschluesselung.TLS), "INBOX", "Sent");
    }

    private KontoZugang adaptiereDokumentkonto() {
        var smtp = settings.getDokumentMailKonto();
        var imap = settings.getDokumentImapZugang();
        return new KontoZugang("DOKUMENTE", true, smtp.fromAddress(), smtp.fromName(),
                new MailkontoDto.ServerZugang(smtp.host(), smtp.port(), smtp.username(), smtp.password(), Verschluesselung.TLS),
                new MailkontoDto.ServerZugang(imap.host(), imap.port(), imap.username(), imap.password(), Verschluesselung.TLS), "INBOX", "Sent");
    }

    private KontoZugang ladeEinkaufZugang() {
        Optional<EinkaufMailkonto> optional = repository.findById("EINKAUF");
        if (optional.isEmpty()) return new KontoZugang("EINKAUF", false, "", "", leeresServer(), leeresServer(), "INBOX", "Sent");
        EinkaufMailkonto k = optional.get();
        if (k.isAktiv()) {
            if (!komplett(k)) throw new IllegalStateException("Das aktive Einkaufs-Mailkonto ist unvollständig konfiguriert.");
            secrets.ensureConfigured();
        }
        return new KontoZugang("EINKAUF", k.isAktiv(), k.getFromAddress(), k.getFromName(),
                new MailkontoDto.ServerZugang(k.getSmtpHost(), k.getSmtpPort(), k.getSmtpUsername(), k.isAktiv() ? secrets.decrypt(k.getSmtpPasswordCiphertext()) : null, Verschluesselung.valueOf(k.getSmtpTls())),
                new MailkontoDto.ServerZugang(k.getImapHost(), k.getImapPort(), k.getImapUsername(), k.isAktiv() ? secrets.decrypt(k.getImapPasswordCiphertext()) : null, Verschluesselung.valueOf(k.getImapTls())), k.getInbox(), k.getSent());
    }

    private boolean komplett(EinkaufMailkonto k) {
        return hatWert(k.getFromAddress()) && hatWert(k.getFromName()) && hatWert(k.getSmtpHost())
                && hatWert(k.getSmtpUsername()) && hatWert(k.getSmtpPasswordCiphertext()) && k.getSmtpPort() > 0
                && hatWert(k.getImapHost()) && hatWert(k.getImapUsername()) && hatWert(k.getImapPasswordCiphertext())
                && k.getImapPort() > 0 && hatWert(k.getInbox()) && hatWert(k.getSent());
    }

    private MailkontoDto.ServerZugang leeresServer() {
        return new MailkontoDto.ServerZugang("", 465, "", null, Verschluesselung.TLS);
    }

    private Response response(EinkaufMailkonto k) {
        return new Response("EINKAUF", k.getVersion(), k.isAktiv(), k.getFromAddress(), k.getFromName(),
                k.getSmtpHost(), k.getSmtpPort(), k.getSmtpUsername(), enumValue(k.getSmtpTls()),
                k.getImapHost(), k.getImapPort(), k.getImapUsername(), enumValue(k.getImapTls()),
                k.getInbox(), k.getSent(), hatWert(k.getSmtpPasswordCiphertext()), hatWert(k.getImapPasswordCiphertext()),
                k.getLetzterAbruf(), k.getLetzterFehler());
    }

    private Verschluesselung enumValue(String value) {
        try { return Verschluesselung.valueOf(value); } catch (RuntimeException ex) { return Verschluesselung.TLS; }
    }

    private String sauber(String value) { return value == null ? "" : value.trim(); }
    private boolean hatWert(String value) { return value != null && !value.isBlank(); }

    public record KontoZugang(String id, boolean aktiv, String fromAddress, String fromName,
            MailkontoDto.ServerZugang smtp, MailkontoDto.ServerZugang imap, String inbox, String sent) {
        @Override
        public String toString() {
            return "KontoZugang[id=" + id + ", aktiv=" + aktiv + ", fromAddress=" + fromAddress
                    + ", fromName=" + fromName + ", smtp=" + smtp + ", imap=" + imap
                    + ", inbox=" + inbox + ", sent=" + sent + "]";
        }
    }
}
