package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.service.VendorInvoiceIntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin-Endpoints für Vendor Invoice Integration.
 * Ermöglicht manuellen Trigger und Status-Abfrage.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/vendor-invoices")
public class VendorInvoiceController {

    private final VendorInvoiceIntegrationService integrationService;

    /**
     * Status der Integration abrufen.
     * Zeigt ob Microsoft/Amazon/Apple konfiguriert und aktiviert sind.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(integrationService.getIntegrationStatus());
    }

    /**
     * Manueller Abruf aller Vendor-Rechnungen.
     */
    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchInvoices() {
        Map<String, Object> result = integrationService.fetchAllVendorInvoices();
        return ResponseEntity.ok(result);
    }

    /**
     * Nur Microsoft-Rechnungen abrufen.
     */
    @PostMapping("/fetch/microsoft")
    public ResponseEntity<Map<String, Object>> fetchMicrosoftInvoices() {
        try {
            int count = integrationService.fetchMicrosoftInvoices();
            return ResponseEntity.ok(Map.of("success", true, "count", count));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Nur Amazon-Rechnungen abrufen.
     */
    @PostMapping("/fetch/amazon")
    public ResponseEntity<Map<String, Object>> fetchAmazonInvoices() {
        try {
            int count = integrationService.fetchAmazonInvoices();
            return ResponseEntity.ok(Map.of("success", true, "count", count));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
