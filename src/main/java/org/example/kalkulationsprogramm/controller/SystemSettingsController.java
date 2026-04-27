package org.example.kalkulationsprogramm.controller;

import java.util.Locale;
import java.util.Map;

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
}
