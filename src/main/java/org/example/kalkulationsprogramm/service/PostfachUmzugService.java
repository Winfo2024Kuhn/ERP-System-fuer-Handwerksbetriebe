package org.example.kalkulationsprogramm.service;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Zieht die Mail-Konten aus den System-Einstellungen in die Postfächer um.
 *
 * <p>Läuft bei jedem Start (siehe {@code PostfachUmzugRunner}) und ist idempotent:
 * Gibt es schon ein Hauptpostfach bzw. ein Postfach für Rechnungen, passiert dafür
 * nichts. Die Alt-Einstellungen bleiben stehen – sie sind der Rückfall, solange
 * kein Postfach einen vollständigen Zugang hat.</p>
 *
 * <p>Das Passwort muss verschlüsselt werden, deshalb geschieht das hier in Java und
 * nicht in der Flyway-Migration.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostfachUmzugService {

    private final EmailAbsenderRepository repository;
    private final EmailPostfachZuordnungRepository zuordnungRepository;
    private final SystemSettingsService systemSettingsService;
    private final MailSecretService mailSecretService;

    @Transactional
    public void ziehUm() {
        EmailAbsender haupt = repository.findFirstByHauptpostfachTrueOrderByIdAsc().orElse(null);
        if (mailSecretService.isConfigured()) {
            if (haupt == null) {
                haupt = uebernehmeStandardKonto().orElse(null);
            }
            uebernehmeDokumentKonto();
        } else if (haupt == null) {
            log.warn("[Postfach] Schlüssel für Mailzugänge (mail.credentials.encryption-key) fehlt – "
                    + "die Mail-Konten bleiben in den System-Einstellungen, bis er eingerichtet ist.");
        }
        if (haupt != null) {
            int ausgang = zuordnungRepository.ordneAusgangsmailsNachAbsenderZu();
            if (ausgang > 0) {
                log.info("[Postfach] {} Ausgangsmails ohne Postfach ihrem Absender-Postfach zugeordnet", ausgang);
            }
            int neu = zuordnungRepository.ordneOhnePostfachZu(haupt.getId());
            if (neu > 0) {
                log.info("[Postfach] {} E-Mails ohne Postfach dem Hauptpostfach zugeordnet", neu);
            }
        }
    }

    /** Welcher Teil der Ersteinrichtung gespeichert wurde. */
    public enum Abgleich {
        /** SMTP: Server, Port, Anmeldung. */
        VERSAND,
        /** IMAP: Server, Port, Anmeldung. */
        ABRUF,
        /** Nur Adresse und Passwort. */
        ZUGANG
    }

    /**
     * Nach der Ersteinrichtung (System-Einstellungen → Mail-Konto): genau der dort
     * gespeicherte Teil wird ins Hauptpostfach übernommen – andere Felder bleiben, damit
     * ein älterer Stand aus den Einstellungen nichts überschreibt. Das Passwort nur, wenn
     * dort ein neues eingegeben wurde. Gibt es noch kein Hauptpostfach, läuft der normale Umzug.
     */
    @Transactional
    public void gleicheHauptpostfachAb(Abgleich teil, boolean mitPasswort) {
        Optional<EmailAbsender> haupt = repository.findFirstByHauptpostfachTrueOrderByIdAsc();
        if (haupt.isEmpty()) {
            ziehUm();
            return;
        }
        if (!mailSecretService.isConfigured()) {
            return;
        }
        EmailAbsender postfach = haupt.get();
        String alterLogin = postfach.effektiverBenutzername();
        String alterSmtpHost = postfach.getSmtpHost();
        String alterImapHost = postfach.getImapHost();
        switch (teil) {
            case VERSAND -> {
                var konto = systemSettingsService.gespeichertesStandardKonto();
                setzeAnmeldung(postfach, konto.username(), mitPasswort ? konto.password() : null);
                postfach.setSmtpHost(gesetzt(konto.host()) ? konto.host().trim() : null);
                postfach.setSmtpPort(konto.port() > 0 ? konto.port() : null);
            }
            case ABRUF -> {
                var imap = systemSettingsService.gespeicherterStandardImapZugang();
                setzeAnmeldung(postfach, imap.username(), mitPasswort ? imap.password() : null);
                postfach.setImapHost(gesetzt(imap.host()) ? imap.host().trim() : null);
                postfach.setImapPort(imap.port() > 0 ? imap.port() : null);
            }
            case ZUGANG -> {
                var konto = systemSettingsService.gespeichertesStandardKonto();
                setzeAnmeldung(postfach, konto.username(), mitPasswort ? konto.password() : null);
            }
        }
        // Neuer Server oder Anmeldename ohne neues Passwort: das alte Passwort nicht an den neuen
        // Server weiterreichen. Ohne Passwort ruft das Postfach nicht ab, bis es neu eingegeben ist.
        if (!mitPasswort && (!gleich(alterLogin, postfach.effektiverBenutzername())
                || !gleich(alterSmtpHost, postfach.getSmtpHost())
                || !gleich(alterImapHost, postfach.getImapHost()))) {
            postfach.setPasswortVerschluesselt(null);
            log.warn("[Postfach] Server oder Anmeldename des Hauptpostfachs geändert ohne neues Passwort – "
                    + "Passwort verworfen, bitte neu eingeben");
        }
        repository.save(postfach);
        log.info("[Postfach] Hauptpostfach aus der Ersteinrichtung abgeglichen ({})", teil);
    }

    private void setzeAnmeldung(EmailAbsender postfach, String benutzer, String passwort) {
        if (gesetzt(benutzer)) {
            String login = benutzer.trim();
            postfach.setBenutzername(login.equalsIgnoreCase(postfach.getEmailAdresse()) ? null : login);
        }
        if (gesetzt(passwort)) {
            postfach.setPasswortVerschluesselt(mailSecretService.encrypt(passwort));
        }
    }

    private Optional<EmailAbsender> uebernehmeStandardKonto() {
        var konto = systemSettingsService.gespeichertesStandardKonto();
        var imap = systemSettingsService.gespeicherterStandardImapZugang();
        if (!gesetzt(konto.username()) || !gesetzt(konto.password())) {
            return Optional.empty();
        }
        String adresse = adresseOder(konto.fromAddress(), konto.username());
        if (adresse == null) {
            return Optional.empty();
        }
        EmailAbsender postfach = repository.findByEmailAdresseIgnoreCase(adresse).orElseGet(() -> neu(adresse));
        if (!gesetzt(postfach.getAnzeigename()) && gesetzt(konto.fromName())) {
            postfach.setAnzeigename(konto.fromName().trim());
        }
        setzeZugang(postfach, konto.username(), konto.password(), konto.host(), konto.port(), imap.host(), imap.port());
        postfach.setAktiv(true);
        postfach.setHauptpostfach(true);
        EmailAbsender gespeichert = repository.save(postfach);
        log.info("[Postfach] Mail-Konto aus den Einstellungen als Hauptpostfach übernommen (Postfach {})",
                gespeichert.getId());
        return Optional.of(gespeichert);
    }

    private void uebernehmeDokumentKonto() {
        if (repository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc().isPresent()) {
            return;
        }
        if (!systemSettingsService.isDokumentMailKontoAktiv() || !systemSettingsService.isDokumentMailKontoConfigured()) {
            return;
        }
        String adresse = adresseOder(systemSettingsService.getDokumentMailFromAddress(),
                systemSettingsService.getDokumentSmtpUsername());
        if (adresse == null) {
            return;
        }
        EmailAbsender postfach = repository.findByEmailAdresseIgnoreCase(adresse).orElseGet(() -> neu(adresse));
        if (!gesetzt(postfach.getPasswortVerschluesselt())) {
            setzeZugang(postfach, systemSettingsService.getDokumentSmtpUsername(),
                    systemSettingsService.getDokumentSmtpPassword(),
                    systemSettingsService.getDokumentSmtpHost(), systemSettingsService.getDokumentSmtpPort(),
                    systemSettingsService.getDokumentImapHost(), systemSettingsService.getDokumentImapPort());
        }
        if (!gesetzt(postfach.getAnzeigename()) && gesetzt(systemSettingsService.getDokumentMailAbsenderName())) {
            postfach.setAnzeigename(systemSettingsService.getDokumentMailAbsenderName().trim());
        }
        postfach.setAktiv(true);
        postfach.setFuerGeschaeftsdokumente(true);
        EmailAbsender gespeichert = repository.save(postfach);
        log.info("[Postfach] Konto für Rechnungen und Mahnungen als Postfach übernommen (Postfach {})",
                gespeichert.getId());
    }

    private void setzeZugang(EmailAbsender postfach, String benutzer, String passwort,
            String smtpHost, int smtpPort, String imapHost, int imapPort) {
        setzeAnmeldung(postfach, benutzer, passwort);
        postfach.setSmtpHost(gesetzt(smtpHost) ? smtpHost.trim() : null);
        postfach.setSmtpPort(smtpPort > 0 ? smtpPort : null);
        postfach.setImapHost(gesetzt(imapHost) ? imapHost.trim() : null);
        postfach.setImapPort(imapPort > 0 ? imapPort : null);
    }

    private static EmailAbsender neu(String adresse) {
        EmailAbsender postfach = new EmailAbsender();
        postfach.setEmailAdresse(adresse);
        return postfach;
    }

    /** Erste Adresse mit "@", sonst {@code null}. */
    private static String adresseOder(String bevorzugt, String ersatz) {
        if (gesetzt(bevorzugt) && bevorzugt.contains("@")) {
            return bevorzugt.trim();
        }
        if (gesetzt(ersatz) && ersatz.contains("@")) {
            return ersatz.trim();
        }
        return null;
    }

    private static boolean gleich(String a, String b) {
        return (a == null ? "" : a.trim()).equalsIgnoreCase(b == null ? "" : b.trim());
    }

    private static boolean gesetzt(String wert) {
        return wert != null && !wert.isBlank();
    }
}
