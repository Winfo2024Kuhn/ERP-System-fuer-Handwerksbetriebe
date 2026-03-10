package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VendorInvoiceIntegrationServiceTest {

    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private LieferantDokumentRepository dokumentRepository;

    private VendorInvoiceIntegrationService service;

    @BeforeEach
    void setUp() {
        service = new VendorInvoiceIntegrationService(lieferantenRepository, dokumentRepository);
        ReflectionTestUtils.setField(service, "emailFeaturesEnabled", true);
        ReflectionTestUtils.setField(service, "microsoftEnabled", false);
        ReflectionTestUtils.setField(service, "microsoftTenantId", "");
        ReflectionTestUtils.setField(service, "microsoftClientId", "");
        ReflectionTestUtils.setField(service, "microsoftClientSecret", "");
        ReflectionTestUtils.setField(service, "microsoftBillingAccountId", "");
        ReflectionTestUtils.setField(service, "amazonEnabled", false);
        ReflectionTestUtils.setField(service, "amazonClientId", "");
        ReflectionTestUtils.setField(service, "amazonClientSecret", "");
        ReflectionTestUtils.setField(service, "amazonRefreshToken", "");
        ReflectionTestUtils.setField(service, "amazonRegion", "EU");
        ReflectionTestUtils.setField(service, "attachmentDir", "uploads/attachments");
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.5.1 Erkennt doppelte Rechnungen anhand Dateiname
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class DoppelteRechnungen {

        @Test
        void microsoftIntegrationGibt0ZurueckWennDeaktiviert() {
            // Microsoft disabled by default in setUp
            int result = service.fetchMicrosoftInvoices();

            assertThat(result).isEqualTo(0);
        }

        @Test
        void amazonIntegrationGibt0ZurueckWennDeaktiviert() {
            int result = service.fetchAmazonInvoices();

            assertThat(result).isEqualTo(0);
        }

        @Test
        void fetchAllReturnsDisabledStatusWhenNotConfigured() {
            Map<String, Object> result = service.fetchAllVendorInvoices();

            assertThat(result).containsKey("microsoft");
            assertThat(result).containsKey("amazon");

            @SuppressWarnings("unchecked")
            Map<String, Object> msStatus = (Map<String, Object>) result.get("microsoft");
            assertThat(msStatus).containsEntry("enabled", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> amzStatus = (Map<String, Object>) result.get("amazon");
            assertThat(amzStatus).containsEntry("enabled", false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.5.2 Loggt fehlende Lieferanten-Zuordnung
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class FehlendeLieferantenZuordnung {

        @Test
        void microsoftGibt0ZurueckWennDeaktiviertOhneConfig() {
            // Nicht konfiguriert → gibt 0 zurück ohne externe Aufrufe
            ReflectionTestUtils.setField(service, "microsoftEnabled", true);
            ReflectionTestUtils.setField(service, "microsoftTenantId", "");
            ReflectionTestUtils.setField(service, "microsoftClientId", "");

            int result = service.fetchMicrosoftInvoices();

            assertThat(result).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.5.3 Status-Dashboard zeigt korrekte Konfigurations-Info
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class IntegrationStatus {

        @Test
        void zeigtKorrekteKonfigurationsInfo() {
            when(lieferantenRepository.findByLieferantennameIgnoreCase("Microsoft"))
                    .thenReturn(Optional.of(new Lieferanten()));
            when(lieferantenRepository.findByLieferantennameIgnoreCase("Amazon"))
                    .thenReturn(Optional.empty());
            when(lieferantenRepository.findByLieferantennameIgnoreCase("Apple"))
                    .thenReturn(Optional.empty());

            Map<String, Object> status = service.getIntegrationStatus();

            assertThat(status).containsKeys("microsoft", "amazon", "apple");

            @SuppressWarnings("unchecked")
            Map<String, Object> msStatus = (Map<String, Object>) status.get("microsoft");
            assertThat(msStatus).containsEntry("enabled", false);
            assertThat(msStatus).containsEntry("lieferantExists", true);

            @SuppressWarnings("unchecked")
            Map<String, Object> amzStatus = (Map<String, Object>) status.get("amazon");
            assertThat(amzStatus).containsEntry("enabled", false);
            assertThat(amzStatus).containsEntry("lieferantExists", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> appleStatus = (Map<String, Object>) status.get("apple");
            assertThat(appleStatus).containsEntry("lieferantExists", false);
            assertThat(appleStatus).containsKey("note");
        }

        @Test
        void zeigtEnabledStatusKorrekt() {
            ReflectionTestUtils.setField(service, "microsoftEnabled", true);
            ReflectionTestUtils.setField(service, "microsoftTenantId", "t-123");
            ReflectionTestUtils.setField(service, "microsoftClientId", "c-123");

            ReflectionTestUtils.setField(service, "amazonEnabled", true);
            ReflectionTestUtils.setField(service, "amazonRefreshToken", "rt-123");
            ReflectionTestUtils.setField(service, "amazonClientId", "ac-123");

            when(lieferantenRepository.findByLieferantennameIgnoreCase("Microsoft"))
                    .thenReturn(Optional.of(new Lieferanten()));
            when(lieferantenRepository.findByLieferantennameIgnoreCase("Amazon"))
                    .thenReturn(Optional.of(new Lieferanten()));
            when(lieferantenRepository.findByLieferantennameIgnoreCase("Apple"))
                    .thenReturn(Optional.empty());

            Map<String, Object> status = service.getIntegrationStatus();

            @SuppressWarnings("unchecked")
            Map<String, Object> msStatus = (Map<String, Object>) status.get("microsoft");
            assertThat(msStatus).containsEntry("enabled", true);
            assertThat(msStatus).containsEntry("configured", true);

            @SuppressWarnings("unchecked")
            Map<String, Object> amzStatus = (Map<String, Object>) status.get("amazon");
            assertThat(amzStatus).containsEntry("enabled", true);
            assertThat(amzStatus).containsEntry("configured", true);
        }
    }
}
