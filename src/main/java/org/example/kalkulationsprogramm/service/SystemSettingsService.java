package org.example.kalkulationsprogramm.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Verwaltet System-Einstellungen die zur Laufzeit im Frontend änderbar sind.
 * DB-Werte überschreiben die Defaults aus application.properties.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingsService {

    private final SystemSettingRepository repository;

    // Defaults aus application.properties
    @Value("${smtp.host:}")
    private String defaultSmtpHost;
    @Value("${smtp.port:465}")
    private int defaultSmtpPort;
    @Value("${smtp.username:}")
    private String defaultSmtpUsername;
    @Value("${smtp.password:}")
    private String defaultSmtpPassword;
    @Value("${ai.gemini.api-key:}")
    private String defaultGeminiApiKey;

    // ==================== Lesen ====================

    /**
     * Liefert den Wert einer Einstellung, oder den Default aus properties.
     */
    public String get(String key, String defaultValue) {
        return repository.findById(key)
                .map(SystemSetting::getValue)
                .orElse(defaultValue);
    }

    public String getSmtpHost() {
        return get("smtp.host", defaultSmtpHost);
    }

    public int getSmtpPort() {
        String val = get("smtp.port", String.valueOf(defaultSmtpPort));
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultSmtpPort;
        }
    }

    public String getSmtpUsername() {
        return get("smtp.username", defaultSmtpUsername);
    }

    public String getSmtpPassword() {
        return get("smtp.password", defaultSmtpPassword);
    }

    public String getGeminiApiKey() {
        return get("ai.gemini.api-key", defaultGeminiApiKey);
    }

    public boolean isInitialConfigurationRequired() {
        return !hasConfiguredValue(getGeminiApiKey()) || !isSmtpConfigured();
    }

    public boolean isSmtpConfigured() {
        return hasConfiguredValue(getSmtpHost())
                && getSmtpPort() > 0
                && hasConfiguredValue(getSmtpUsername())
                && hasConfiguredValue(getSmtpPassword());
    }

    /**
     * Liefert alle Einstellungen als Map (sensible Werte werden maskiert).
     */
    public Map<String, String> getAllSettings() {
        Map<String, String> settings = new LinkedHashMap<>();

        settings.put("smtp.host", getSmtpHost());
        settings.put("smtp.port", String.valueOf(getSmtpPort()));
        settings.put("smtp.username", getSmtpUsername());
        settings.put("smtp.password", maskValue(getSmtpPassword()));
        settings.put("ai.gemini.api-key", maskValue(getGeminiApiKey()));

        return settings;
    }

    // ==================== Schreiben ====================

    @Transactional
    public void save(String key, String value, String beschreibung) {
        SystemSetting setting = repository.findById(key)
                .orElse(new SystemSetting(key, null, beschreibung));
        setting.setValue(value);
        if (beschreibung != null) {
            setting.setBeschreibung(beschreibung);
        }
        repository.save(setting);
    }

    @Transactional
    public void saveSmtpSettings(String host, int port, String username, String password) {
        save("smtp.host", host, "SMTP Mail-Server Hostname");
        save("smtp.port", String.valueOf(port), "SMTP Port (465 = SSL, 587 = STARTTLS)");
        save("smtp.username", username, "SMTP Benutzername / E-Mail-Adresse");
        save("smtp.password", password, "SMTP Passwort");
        log.info("SMTP-Einstellungen aktualisiert (Host: {}, Port: {}, User: {})", host, port, username);
    }

    @Transactional
    public void saveGeminiApiKey(String apiKey) {
        save("ai.gemini.api-key", apiKey, "Google Gemini API Key");
        log.info("Gemini API Key aktualisiert");
    }

    // ==================== Tests ====================

    /**
     * Testet die SMTP-Verbindung mit den angegebenen Zugangsdaten.
     * Sendet optional eine Test-E-Mail.
     */
    public TestResult testSmtp(String host, int port, String username, String password, String testRecipient) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.socketFactory.port", String.valueOf(port));
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        try {
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            Transport transport = session.getTransport("smtp");
            transport.connect(host, port, username, password);

            // Optionale Test-E-Mail senden
            if (testRecipient != null && !testRecipient.isBlank()) {
                MimeMessage msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(username));
                msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(testRecipient));
                msg.setSubject("Kalkulationsprogramm - SMTP Test");
                msg.setText("Diese E-Mail bestätigt, dass die SMTP-Einstellungen korrekt konfiguriert sind.\n\n"
                        + "Server: " + host + ":" + port + "\n"
                        + "Benutzer: " + username);
                Transport.send(msg);
                transport.close();
                return TestResult.success("Verbindung erfolgreich! Test-E-Mail an " + testRecipient + " gesendet.");
            }

            transport.close();
            return TestResult.success("SMTP-Verbindung erfolgreich hergestellt.");
        } catch (AuthenticationFailedException e) {
            log.warn("SMTP-Auth fehlgeschlagen: {}", e.getMessage());
            return TestResult.failure("Authentifizierung fehlgeschlagen. Benutzername oder Passwort falsch.");
        } catch (MessagingException e) {
            log.warn("SMTP-Verbindung fehlgeschlagen: {}", e.getMessage());
            return TestResult.failure("Verbindung fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * Testet den Gemini API Key durch einen einfachen API-Aufruf.
     */
    public TestResult testGeminiApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return TestResult.failure("Kein API Key angegeben.");
        }

        try {
            // Einfacher Model-List-Aufruf um den Key zu verifizieren
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return TestResult.success("API Key gültig! Verbindung zu Google Gemini erfolgreich.");
            } else if (response.statusCode() == 400 || response.statusCode() == 403) {
                return TestResult.failure("API Key ungültig oder keine Berechtigung (HTTP " + response.statusCode() + ").");
            } else {
                return TestResult.failure("Unerwartete Antwort von Google (HTTP " + response.statusCode() + ").");
            }
        } catch (Exception e) {
            log.warn("Gemini API Test fehlgeschlagen: {}", e.getMessage());
            return TestResult.failure("Verbindungsfehler: " + e.getMessage());
        }
    }

    // ==================== Hilfsmethoden ====================

    private String maskValue(String value) {
        if (value == null || value.isBlank() || "OVERRIDE_IN_LOCAL".equals(value)) {
            return "";
        }
        if (value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }

    private boolean hasConfiguredValue(String value) {
        return value != null && !value.isBlank() && !"OVERRIDE_IN_LOCAL".equals(value);
    }

    /**
     * Ergebnis eines Verbindungstests.
     */
    public record TestResult(boolean success, String message) {
        public static TestResult success(String message) {
            return new TestResult(true, message);
        }

        public static TestResult failure(String message) {
            return new TestResult(false, message);
        }
    }
}
