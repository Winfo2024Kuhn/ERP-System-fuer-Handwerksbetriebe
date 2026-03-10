package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service für automatischen Import von Rechnungen von externen Anbietern.
 * Unterstützt: Microsoft Azure, Amazon Business
 * Apple: Keine API verfügbar - Rechnungen über E-Mail (GlobalEmailWatcher)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VendorInvoiceIntegrationService {

    private final LieferantenRepository lieferantenRepository;
    private final LieferantDokumentRepository dokumentRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${email.features.enabled:true}")
    private boolean emailFeaturesEnabled;

    // ============ Microsoft Azure Configuration ============
    @Value("${vendor.microsoft.enabled:false}")
    private boolean microsoftEnabled;

    @Value("${vendor.microsoft.tenant-id:}")
    private String microsoftTenantId;

    @Value("${vendor.microsoft.client-id:}")
    private String microsoftClientId;

    @Value("${vendor.microsoft.client-secret:}")
    private String microsoftClientSecret;

    @Value("${vendor.microsoft.billing-account-id:}")
    private String microsoftBillingAccountId;

    // ============ Amazon Business Configuration ============
    @Value("${vendor.amazon.enabled:false}")
    private boolean amazonEnabled;

    @Value("${vendor.amazon.client-id:}")
    private String amazonClientId;

    @Value("${vendor.amazon.client-secret:}")
    private String amazonClientSecret;

    @Value("${vendor.amazon.refresh-token:}")
    private String amazonRefreshToken;

    @Value("${vendor.amazon.region:EU}")
    private String amazonRegion;

    // ============ Storage ============
    @Value("${file.mail-attachment-dir:uploads/attachments}")
    private String attachmentDir;

    /**
     * Täglich um 6:00 Uhr alle Vendor-Rechnungen abrufen.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void scheduledFetchAllInvoices() {
        if (!emailFeaturesEnabled) {
            return;
        }
        log.info("[VendorInvoice] Starte täglichen Rechnungsabruf...");
        fetchAllVendorInvoices();
    }

    /**
     * Manueller Trigger für Rechnungsabruf.
     */
    public Map<String, Object> fetchAllVendorInvoices() {
        Map<String, Object> result = new HashMap<>();
        
        if (microsoftEnabled) {
            try {
                int count = fetchMicrosoftInvoices();
                result.put("microsoft", Map.of("success", true, "count", count));
                log.info("[VendorInvoice] Microsoft: {} Rechnungen importiert", count);
            } catch (Exception e) {
                log.error("[VendorInvoice] Microsoft-Fehler: {}", e.getMessage());
                result.put("microsoft", Map.of("success", false, "error", e.getMessage()));
            }
        } else {
            result.put("microsoft", Map.of("enabled", false));
        }

        if (amazonEnabled) {
            try {
                int count = fetchAmazonInvoices();
                result.put("amazon", Map.of("success", true, "count", count));
                log.info("[VendorInvoice] Amazon: {} Rechnungen importiert", count);
            } catch (Exception e) {
                log.error("[VendorInvoice] Amazon-Fehler: {}", e.getMessage());
                result.put("amazon", Map.of("success", false, "error", e.getMessage()));
            }
        } else {
            result.put("amazon", Map.of("enabled", false));
        }

        return result;
    }

    // ================== MICROSOFT AZURE ==================

    /**
     * Ruft Rechnungen von Microsoft Azure Billing API ab.
     * https://learn.microsoft.com/en-us/rest/api/billing/
     */
    @Transactional
    public int fetchMicrosoftInvoices() {
        if (!microsoftEnabled || microsoftTenantId.isBlank() || microsoftClientId.isBlank()) {
            log.debug("[Microsoft] Integration deaktiviert oder nicht konfiguriert");
            return 0;
        }

        // 1. OAuth Token holen
        String accessToken = getMicrosoftAccessToken();
        if (accessToken == null) {
            throw new RuntimeException("Konnte Microsoft Access Token nicht abrufen");
        }

        // 2. Invoices Liste abrufen
        String invoicesUrl = "https://management.azure.com/providers/Microsoft.Billing/billingAccounts/%s/invoices?api-version=2024-04-01".formatted(
                microsoftBillingAccountId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(invoicesUrl, HttpMethod.GET, request, Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Fehler beim Abrufen der Microsoft Invoices");
        }

        List<Map<String, Object>> invoices = (List<Map<String, Object>>) response.getBody().get("value");
        if (invoices == null || invoices.isEmpty()) {
            return 0;
        }

        // 3. Lieferant finden/anlegen
        Lieferanten microsoft = findOrLogMissingLieferant("Microsoft");
        if (microsoft == null) return 0;

        int imported = 0;
        for (Map<String, Object> invoice : invoices) {
            String invoiceId = (String) invoice.get("name");
            Map<String, Object> properties = (Map<String, Object>) invoice.get("properties");
            if (properties == null) continue;

            String downloadUrl = (String) properties.get("downloadUrl");
            String invoiceDate = (String) properties.get("invoiceDate");

            // Prüfen ob bereits importiert
            if (dokumentRepository.existsByLieferantIdAndOriginalDateinameContaining(
                    microsoft.getId(), invoiceId)) {
                continue;
            }

            // 4. PDF herunterladen und speichern
            if (downloadUrl != null && !downloadUrl.isBlank()) {
                try {
                    byte[] pdfBytes = downloadPdf(downloadUrl, accessToken);
                    String filename = "Microsoft_Invoice_%s.pdf".formatted(invoiceId);
                    saveLieferantDokument(microsoft, pdfBytes, filename, invoiceDate);
                    imported++;
                } catch (Exception e) {
                    log.warn("[Microsoft] Konnte Rechnung {} nicht herunterladen: {}", invoiceId, e.getMessage());
                }
            }
        }

        return imported;
    }

    private String getMicrosoftAccessToken() {
        String tokenUrl = "https://login.microsoftonline.com/%s/oauth2/v2.0/token".formatted(
                microsoftTenantId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", microsoftClientId);
        body.add("client_secret", microsoftClientSecret);
        body.add("scope", "https://management.azure.com/.default");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.error("[Microsoft] Token-Abruf fehlgeschlagen: {}", e.getMessage());
        }
        return null;
    }

    // ================== AMAZON BUSINESS ==================

    /**
     * Ruft Rechnungen von Amazon Business Reconciliation API ab.
     * https://developer-docs.amazon.com/amazon-business/docs
     */
    @Transactional
    public int fetchAmazonInvoices() {
        if (!amazonEnabled || amazonRefreshToken.isBlank() || amazonClientId.isBlank()) {
            log.debug("[Amazon] Integration deaktiviert oder nicht konfiguriert");
            return 0;
        }

        // 1. Access Token mit Refresh Token holen
        String accessToken = getAmazonAccessToken();
        if (accessToken == null) {
            throw new RuntimeException("Konnte Amazon Access Token nicht abrufen");
        }

        // 2. Letzte Transaktionen abrufen
        String baseUrl = amazonRegion.equalsIgnoreCase("US") 
            ? "https://sellingpartnerapi-na.amazon.com"
            : "https://sellingpartnerapi-eu.amazon.com";

        String transactionsUrl = baseUrl + "/reconciliation/2021-01-01/transactions?createdAfter=" +
            LocalDate.now().minusDays(30).toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("x-amz-access-token", accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(transactionsUrl, HttpMethod.GET, request, Map.class);
        } catch (Exception e) {
            log.warn("[Amazon] Transactions-API nicht verfügbar: {}", e.getMessage());
            return 0;
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return 0;
        }

        List<Map<String, Object>> transactions = (List<Map<String, Object>>) response.getBody().get("transactions");
        if (transactions == null || transactions.isEmpty()) {
            return 0;
        }

        // 3. Lieferant finden
        Lieferanten amazon = findOrLogMissingLieferant("Amazon");
        if (amazon == null) return 0;

        int imported = 0;
        for (Map<String, Object> transaction : transactions) {
            String orderId = (String) transaction.get("orderId");
            String invoiceUrl = (String) transaction.get("invoiceDownloadUrl");

            if (orderId == null) continue;

            // Prüfen ob bereits importiert
            if (dokumentRepository.existsByLieferantIdAndOriginalDateinameContaining(
                    amazon.getId(), orderId)) {
                continue;
            }

            if (invoiceUrl != null && !invoiceUrl.isBlank()) {
                try {
                    byte[] pdfBytes = downloadPdf(invoiceUrl, accessToken);
                    String filename = "Amazon_Invoice_%s.pdf".formatted(orderId);
                    String invoiceDate = (String) transaction.get("transactionDate");
                    saveLieferantDokument(amazon, pdfBytes, filename, invoiceDate);
                    imported++;
                } catch (Exception e) {
                    log.warn("[Amazon] Konnte Rechnung {} nicht herunterladen: {}", orderId, e.getMessage());
                }
            }
        }

        return imported;
    }

    private String getAmazonAccessToken() {
        String tokenUrl = "https://api.amazon.com/auth/o2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", amazonRefreshToken);
        body.add("client_id", amazonClientId);
        body.add("client_secret", amazonClientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.error("[Amazon] Token-Abruf fehlgeschlagen: {}", e.getMessage());
        }
        return null;
    }

    // ================== HELPER METHODS ==================

    private Lieferanten findOrLogMissingLieferant(String name) {
        return lieferantenRepository.findByLieferantennameIgnoreCase(name)
            .orElseGet(() -> {
                log.warn("[VendorInvoice] Lieferant '{}' nicht gefunden. Bitte manuell anlegen!", name);
                return null;
            });
    }

    private byte[] downloadPdf(String url, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, request, byte[].class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        }
        throw new RuntimeException("PDF-Download fehlgeschlagen: " + response.getStatusCode());
    }

    private void saveLieferantDokument(Lieferanten lieferant, byte[] pdfBytes, String filename, String invoiceDate) 
            throws IOException {
        // Speicherpfad: uploads/attachments/vendor-invoices/
        Path vendorDir = Path.of(attachmentDir, "vendor-invoices").toAbsolutePath().normalize();
        Files.createDirectories(vendorDir);

        String storedFilename = UUID.randomUUID() + "_" + filename;
        Path targetPath = vendorDir.resolve(storedFilename);
        Files.write(targetPath, pdfBytes);

        // LieferantDokument Entity erstellen
        LieferantDokument dokument = new LieferantDokument();
        dokument.setLieferant(lieferant);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        dokument.setOriginalDateiname(filename);
        dokument.setGespeicherterDateiname(storedFilename);
        dokument.setUploadDatum(LocalDateTime.now());

        dokumentRepository.save(dokument);
        log.info("[VendorInvoice] Rechnung gespeichert: {} für {}", filename, lieferant.getLieferantenname());
    }

    /**
     * Status-Übersicht für Admin-UI.
     */
    public Map<String, Object> getIntegrationStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("microsoft", Map.of(
            "enabled", microsoftEnabled,
            "configured", !microsoftTenantId.isBlank() && !microsoftClientId.isBlank(),
            "lieferantExists", lieferantenRepository.findByLieferantennameIgnoreCase("Microsoft").isPresent()
        ));
        
        status.put("amazon", Map.of(
            "enabled", amazonEnabled,
            "configured", !amazonRefreshToken.isBlank() && !amazonClientId.isBlank(),
            "lieferantExists", lieferantenRepository.findByLieferantennameIgnoreCase("Amazon").isPresent()
        ));
        
        status.put("apple", Map.of(
            "note", "Keine API verfügbar. Rechnungen via E-Mail (GlobalEmailWatcher).",
            "lieferantExists", lieferantenRepository.findByLieferantennameIgnoreCase("Apple").isPresent()
        ));
        
        return status;
    }
}
