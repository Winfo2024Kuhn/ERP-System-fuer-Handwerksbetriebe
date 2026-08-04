package org.example.kalkulationsprogramm.controller;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST-Controller für System-Einstellungen (API Keys, SMTP, etc.).
 * Nur für authentifizierte Admins erreichbar.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService settingsService;
    private final org.example.kalkulationsprogramm.service.DateiOrdnerService dateiOrdnerService;

    // Pragmatischer E-Mail-Regex – bewusst keine RFC-5322-Voll-Compliance, sondern
    // genau das was Anwender erwarten: nicht-leerer Local-Part, "@",
    // nicht-leerer Domain-Part mit mindestens einem Punkt, alles ohne Whitespace.
    // Ungültiges wie "@", "a@", " @ " wird abgewiesen.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+(?:\\.[^@\\s.]+)+$");

    /**
     * Obergrenze für einzelne Eingabefelder der Mail-Konten. Ein Hostname oder
     * eine E-Mail-Adresse ist nie annähernd so lang; der Wert dient nur dazu,
     * absurd große Eingaben abzuweisen, bevor sie in der Datenbank landen.
     */
    private static final int MAX_FELD_LAENGE = 500;

    // ==================== Alle Einstellungen lesen ====================

    @GetMapping
    public ResponseEntity<Map<String, String>> getAll() {
        return ResponseEntity.ok(settingsService.getAllSettings());
    }

    // ==================== SMTP ====================

    @GetMapping("/smtp")
    public ResponseEntity<SmtpSettingsResponse> getSmtp() {
        return ResponseEntity.ok(new SmtpSettingsResponse(
                settingsService.getSmtpHost(),
                settingsService.getSmtpPort(),
                settingsService.getSmtpUsername(),
                hasValue(settingsService.getSmtpPassword())
        ));
    }

    @PutMapping("/smtp")
    public ResponseEntity<Map<String, String>> saveSmtp(@RequestBody SmtpSettingsRequest req) {
        if (!hasValue(req.host())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bitte einen gültigen SMTP-Server eintragen."));
        }

        if (!hasValue(req.username())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bitte einen gültigen SMTP-Benutzernamen eintragen."));
        }

        String effectivePassword = req.password();
        if (effectivePassword == null || effectivePassword.isBlank()) {
            effectivePassword = settingsService.getSmtpPassword();
        }

        settingsService.saveSmtpSettings(req.host(), req.port(), req.username(), effectivePassword);
        return ResponseEntity.ok(Map.of("message", "SMTP-Einstellungen gespeichert."));
    }

    @PostMapping("/smtp/test")
    public ResponseEntity<TestResult> testSmtp(@RequestBody SmtpTestRequest req) {
        String host = req.host() != null ? req.host() : settingsService.getSmtpHost();
        int port = req.port() > 0 ? req.port() : settingsService.getSmtpPort();
        String username = req.username() != null ? req.username() : settingsService.getSmtpUsername();
        String password = req.password() != null ? req.password() : settingsService.getSmtpPassword();

        TestResult result = settingsService.testSmtp(host, port, username, password, req.testRecipient());
        return ResponseEntity.ok(result);
    }

    // ==================== IMAP ====================

    @GetMapping("/imap")
    public ResponseEntity<ImapSettingsResponse> getImap() {
        return ResponseEntity.ok(new ImapSettingsResponse(
                settingsService.getImapHost(),
                settingsService.getImapPort(),
                settingsService.getImapUsername(),
                hasValue(settingsService.getImapPassword())
        ));
    }

    @PutMapping("/imap")
    public ResponseEntity<Map<String, String>> saveImap(@RequestBody ImapSettingsRequest req) {
        if (!hasValue(req.host())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bitte einen gültigen IMAP-Server eintragen."));
        }
        if (!hasValue(req.username())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bitte einen gültigen IMAP-Benutzernamen eintragen."));
        }

        String effectivePassword = req.password();
        if (effectivePassword == null || effectivePassword.isBlank()) {
            effectivePassword = settingsService.getImapPassword();
        }

        int port = req.port() > 0 ? req.port() : 993;
        settingsService.saveImapSettings(req.host(), port, req.username(), effectivePassword);
        return ResponseEntity.ok(Map.of("message", "IMAP-Einstellungen gespeichert."));
    }

    @PostMapping("/imap/test")
    public ResponseEntity<TestResult> testImap(@RequestBody ImapTestRequest req) {
        String host = hasValue(req.host()) ? req.host() : settingsService.getImapHost();
        int port = req.port() > 0 ? req.port() : settingsService.getImapPort();
        String username = hasValue(req.username()) ? req.username() : settingsService.getImapUsername();
        String password = (req.password() != null && !req.password().isBlank())
                ? req.password() : settingsService.getImapPassword();

        TestResult result = settingsService.testImap(host, port, username, password);
        return ResponseEntity.ok(result);
    }

    // ==================== Kombiniertes E-Mail-Konto (Einfache Einrichtung) ====================

    /**
     * Speichert E-Mail + Passwort einmalig für SMTP und IMAP. Hosts/Ports bleiben
     * unverändert (Defaults bzw. das, was unter "Erweitert" gesetzt wurde).
     */
    @PutMapping("/email-account")
    public ResponseEntity<Map<String, String>> saveEmailAccount(@RequestBody EmailAccountRequest req) {
        if (!hasValue(req.email())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bitte eine gültige E-Mail-Adresse eintragen."));
        }

        settingsService.saveEmailAccount(req.email(), req.password());
        return ResponseEntity.ok(Map.of("message", "E-Mail-Konto gespeichert."));
    }

    // ==================== Gemini API Key ====================

    @GetMapping("/gemini")
    public ResponseEntity<GeminiSettingsResponse> getGemini() {
        return ResponseEntity.ok(new GeminiSettingsResponse(
                hasValue(settingsService.getGeminiApiKey())
        ));
    }

    @PutMapping("/gemini")
    public ResponseEntity<Map<String, String>> saveGemini(@RequestBody GeminiSettingsRequest req) {
        settingsService.saveGeminiApiKey(req.apiKey());
        return ResponseEntity.ok(Map.of("message", "Gemini API Key gespeichert."));
    }

    @PostMapping("/gemini/test")
    public ResponseEntity<TestResult> testGemini(@RequestBody GeminiTestRequest req) {
        String apiKey = req.apiKey() != null ? req.apiKey() : settingsService.getGeminiApiKey();
        TestResult result = settingsService.testGeminiApiKey(apiKey);
        return ResponseEntity.ok(result);
    }

    // ==================== Standard-Absender für Auto-Mails ====================

    @GetMapping("/mail-from")
    public ResponseEntity<MailFromResponse> getMailFrom() {
        return ResponseEntity.ok(new MailFromResponse(
                settingsService.getMailFromAddress(),
                settingsService.getSmtpUsername(),
                settingsService.getMailAbsenderName()));
    }

    @PutMapping("/mail-from")
    public ResponseEntity<Map<String, String>> saveMailFrom(@RequestBody MailFromRequest req) {
        String address = req.address() == null ? "" : req.address().trim();
        if (!address.isBlank() && !EMAIL_PATTERN.matcher(address).matches()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Bitte eine gültige E-Mail-Adresse eintragen."));
        }
        if (req.name() != null && req.name().length() > MAX_FELD_LAENGE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Der Anzeigename ist zu lang."));
        }
        settingsService.saveMailFromAddress(address);
        settingsService.saveMailAbsenderName(req.name());
        // Wortlaut bewusst ohne "automatische Mails": Rechnungen, Mahnungen,
        // Angebote und Auftragsbestaetigungen laufen ueber das Dokument-Postfach
        // (siehe SystemSettingsService#getDokumentMailKonto). Diese Adresse gilt
        // fuer den Schriftverkehr im E-Mail-Center und die Anfrage-Bestaetigung.
        return ResponseEntity.ok(Map.of("message", address.isBlank()
                ? "Absender zurückgesetzt – es wird wieder die Adresse des Postfachs verwendet."
                : "Absender gespeichert."));
    }

    // ============ Mail-Konto für Ausgangsgeschäftsdokumente ============

    @GetMapping("/dokument-mail")
    public ResponseEntity<DokumentMailResponse> getDokumentMail() {
        return ResponseEntity.ok(new DokumentMailResponse(
                settingsService.isDokumentMailKontoAktiv(),
                settingsService.getDokumentSmtpHost(),
                settingsService.getDokumentSmtpPort(),
                settingsService.getDokumentSmtpUsername(),
                hasValue(settingsService.getDokumentSmtpPassword()),
                settingsService.getDokumentMailFromAddress(),
                settingsService.getDokumentMailAbsenderName(),
                settingsService.getDokumentImapHost()));
    }

    @PutMapping("/dokument-mail")
    public ResponseEntity<Map<String, String>> saveDokumentMail(@RequestBody DokumentMailRequest req) {
        String host = req.host() == null ? "" : req.host().trim();
        String username = req.username() == null ? "" : req.username().trim();
        String fromAddress = req.fromAddress() == null ? "" : req.fromAddress().trim();

        // Laengenbegrenzung vor jeder weiteren Pruefung: Die Werte landen in
        // einer TEXT-Spalte und der Host anschliessend in einem SMTP-Connect.
        // Ueberlange Eingaben werden abgewiesen statt gespeichert.
        if (host.length() > MAX_FELD_LAENGE || username.length() > MAX_FELD_LAENGE
                || fromAddress.length() > MAX_FELD_LAENGE
                || (req.password() != null && req.password().length() > MAX_FELD_LAENGE)) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Eine der Eingaben ist zu lang. Bitte prüfen Sie Server, Adresse und Passwort."));
        }

        // Leeres Passwort heisst "unveraendert lassen" – sonst muesste der
        // Anwender es bei jeder anderen Aenderung neu eintippen.
        String effectivePassword = req.password();
        if (effectivePassword == null || effectivePassword.isBlank()) {
            effectivePassword = settingsService.getDokumentSmtpPassword();
        }

        // Nur beim Einschalten streng pruefen. Ausgeschaltet darf ruhig ein
        // halb ausgefuellter Entwurf gespeichert werden.
        if (req.aktiv()) {
            if (!hasValue(host)) {
                return ResponseEntity.badRequest().body(Map.of("message",
                        "Bitte den Mail-Server des Postfachs eintragen."));
            }
            if (!hasValue(username) || !EMAIL_PATTERN.matcher(username).matches()) {
                return ResponseEntity.badRequest().body(Map.of("message",
                        "Bitte die vollständige E-Mail-Adresse des Postfachs als Benutzernamen eintragen."));
            }
            if (!hasValue(effectivePassword)) {
                return ResponseEntity.badRequest().body(Map.of("message",
                        "Bitte das Passwort des Postfachs eintragen."));
            }
            if (!fromAddress.isBlank() && !EMAIL_PATTERN.matcher(fromAddress).matches()) {
                return ResponseEntity.badRequest().body(Map.of("message",
                        "Bitte eine gültige Absender-Adresse eintragen."));
            }
            // Absender und Postfach muessen zur selben Domain gehoeren. Sonst
            // verschickt das Postfach Mails im Namen einer fremden Domain –
            // SPF und DKIM schlagen beim Empfaenger fehl und die Mail landet
            // zuverlaessiger im Spam als vorher. Das ist genau der Fehler, den
            // die Umstellung beseitigen soll, deshalb hart abweisen.
            if (!fromAddress.isBlank() && !gleicheDomain(fromAddress, username)) {
                return ResponseEntity.badRequest().body(Map.of("message",
                        "Absender-Adresse und Postfach müssen zur selben Domain gehören. "
                                + "Sonst stuft der Empfänger die Mail als Fälschung ein. "
                                + "Erwartet wird eine Adresse auf @" + domainVon(username) + "."));
            }
        }

        settingsService.saveDokumentMailSettings(req.aktiv(), host,
                req.port() > 0 ? req.port() : 465, username, effectivePassword, fromAddress,
                req.fromName(), req.imapHost());
        return ResponseEntity.ok(Map.of("message", req.aktiv()
                ? "Rechnungen und Mahnungen gehen ab jetzt über das eigene Postfach raus."
                : "Eigenes Postfach ausgeschaltet – der Versand läuft wieder über das Standard-Konto."));
    }

    @PostMapping("/dokument-mail/test")
    public ResponseEntity<TestResult> testDokumentMail(@RequestBody DokumentMailTestRequest req) {
        String host = hasValue(req.host()) ? req.host() : settingsService.getDokumentSmtpHost();
        int port = req.port() > 0 ? req.port() : settingsService.getDokumentSmtpPort();
        String username = hasValue(req.username()) ? req.username() : settingsService.getDokumentSmtpUsername();
        String password = (req.password() != null && !req.password().isBlank())
                ? req.password() : settingsService.getDokumentSmtpPassword();

        return ResponseEntity.ok(
                settingsService.testSmtp(host, port, username, password, req.testRecipient()));
    }

    /** Domain-Teil einer E-Mail-Adresse, kleingeschrieben; leer wenn kein "@" enthalten ist. */
    private static String domainVon(String address) {
        if (address == null) {
            return "";
        }
        int at = address.lastIndexOf('@');
        return at < 0 ? "" : address.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean gleicheDomain(String a, String b) {
        String domainA = domainVon(a);
        return !domainA.isBlank() && domainA.equals(domainVon(b));
    }

    // ==================== Funnel-Spam-Filter ====================

    @GetMapping("/anfrage-funnel-spamfilter")
    public ResponseEntity<FunnelSpamFilterResponse> getFunnelSpamFilter() {
        return ResponseEntity.ok(new FunnelSpamFilterResponse(
                settingsService.isAnfrageFunnelSpamFilterAktiv()));
    }

    @PutMapping("/anfrage-funnel-spamfilter")
    public ResponseEntity<Map<String, String>> saveFunnelSpamFilter(
            @RequestBody FunnelSpamFilterRequest req) {
        settingsService.saveAnfrageFunnelSpamFilterAktiv(req.aktiv());
        return ResponseEntity.ok(Map.of(
                "message", req.aktiv()
                        ? "Spam-Filter für Webseiten-Anfragen aktiviert."
                        : "Spam-Filter für Webseiten-Anfragen deaktiviert."));
    }

    // ==================== Gemeinsamer Datei-Ordner (HiCAD/Tenado/Excel) ====================

    @GetMapping("/datei-ordner")
    public ResponseEntity<DateiOrdnerResponse> getDateiOrdner() {
        return ResponseEntity.ok(new DateiOrdnerResponse(
                settingsService.getDateiOrdnerPfad(),
                settingsService.getDateiOrdnerNetworkUrl(),
                settingsService.isDateiOrdnerConfigured()));
    }

    @PutMapping("/datei-ordner")
    public ResponseEntity<Map<String, String>> saveDateiOrdner(@RequestBody DateiOrdnerRequest req) {
        TestResult result = dateiOrdnerService.speichereOrdner(req.pfad(), req.networkUrl());
        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of("message", result.message()));
        }
        return ResponseEntity.ok(Map.of("message", result.message()));
    }

    @PostMapping("/datei-ordner/test")
    public ResponseEntity<TestResult> testDateiOrdner(@RequestBody DateiOrdnerTestRequest req) {
        return ResponseEntity.ok(dateiOrdnerService.pruefeOrdner(req.pfad()));
    }

    @PostMapping("/datei-ordner/freigeben")
    public ResponseEntity<TestResult> gebeDateiOrdnerFrei() {
        return ResponseEntity.ok(dateiOrdnerService.gebeOrdnerFrei());
    }

    // ==================== DTOs ====================

    private boolean hasValue(String val) {
        if (val == null) {
            return false;
        }

        String normalized = val.trim().toLowerCase(Locale.ROOT);
        return !normalized.isBlank()
                && !"override_in_local".equals(normalized)
                && !"smtp.example.com".equals(normalized)
                && !"change_me_strong_password".equals(normalized);
    }

    record SmtpSettingsResponse(String host, int port, String username, boolean passwordSet) {}
    record SmtpSettingsRequest(String host, int port, String username, String password) {}
    record SmtpTestRequest(String host, int port, String username, String password, String testRecipient) {}
    record ImapSettingsResponse(String host, int port, String username, boolean passwordSet) {}
    record ImapSettingsRequest(String host, int port, String username, String password) {}
    record ImapTestRequest(String host, int port, String username, String password) {}
    record EmailAccountRequest(String email, String password) {}
    record GeminiSettingsResponse(boolean apiKeySet) {}
    record GeminiSettingsRequest(String apiKey) {}
    record GeminiTestRequest(String apiKey) {}
    record FunnelSpamFilterResponse(boolean aktiv) {}
    record FunnelSpamFilterRequest(boolean aktiv) {}
    record MailFromResponse(String address, String smtpUsername, String name) {}
    record MailFromRequest(String address, String name) {}
    record DokumentMailResponse(boolean aktiv, String host, int port, String username,
            boolean passwordSet, String fromAddress, String fromName, String imapHost) {}
    record DokumentMailRequest(boolean aktiv, String host, int port, String username,
            String password, String fromAddress, String fromName, String imapHost) {}
    record DokumentMailTestRequest(String host, int port, String username, String password,
            String testRecipient) {}
    record DateiOrdnerResponse(String pfad, String networkUrl, boolean konfiguriert) {}
    record DateiOrdnerRequest(String pfad, String networkUrl) {}
    record DateiOrdnerTestRequest(String pfad) {}
}
