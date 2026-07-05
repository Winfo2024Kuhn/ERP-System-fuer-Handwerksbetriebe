package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class VendorInvoiceIntegrationService(
    private val lieferantenRepository: LieferantenRepository,
    private val dokumentRepository: LieferantDokumentRepository,
) {
    private val restTemplate = RestTemplate()

    val integrationStatus: Map<String, Any>
        get() = mapOf(
            "microsoft" to mapOf(
                "enabled" to microsoftEnabled,
                "configured" to (microsoftTenantId.isNotBlank() && microsoftClientId.isNotBlank()),
                "lieferantExists" to lieferantenRepository.findByLieferantennameIgnoreCase("Microsoft").isPresent,
            ),
            "amazon" to mapOf(
                "enabled" to amazonEnabled,
                "configured" to (amazonRefreshToken.isNotBlank() && amazonClientId.isNotBlank()),
                "lieferantExists" to lieferantenRepository.findByLieferantennameIgnoreCase("Amazon").isPresent,
            ),
            "apple" to mapOf(
                "note" to "Keine API verfügbar. Rechnungen via E-Mail (GlobalEmailWatcher).",
                "lieferantExists" to lieferantenRepository.findByLieferantennameIgnoreCase("Apple").isPresent,
            ),
        )

    @Value("\${email.features.enabled:true}")
    private var emailFeaturesEnabled: Boolean = true

    @Value("\${vendor.microsoft.enabled:false}")
    private var microsoftEnabled: Boolean = false

    @Value("\${vendor.microsoft.tenant-id:}")
    private lateinit var microsoftTenantId: String

    @Value("\${vendor.microsoft.client-id:}")
    private lateinit var microsoftClientId: String

    @Value("\${vendor.microsoft.client-secret:}")
    private lateinit var microsoftClientSecret: String

    @Value("\${vendor.microsoft.billing-account-id:}")
    private lateinit var microsoftBillingAccountId: String

    @Value("\${vendor.amazon.enabled:false}")
    private var amazonEnabled: Boolean = false

    @Value("\${vendor.amazon.client-id:}")
    private lateinit var amazonClientId: String

    @Value("\${vendor.amazon.client-secret:}")
    private lateinit var amazonClientSecret: String

    @Value("\${vendor.amazon.refresh-token:}")
    private lateinit var amazonRefreshToken: String

    @Value("\${vendor.amazon.region:EU}")
    private lateinit var amazonRegion: String

    @Value("\${file.mail-attachment-dir:uploads/attachments}")
    private lateinit var attachmentDir: String

    @Scheduled(cron = "0 0 6 * * *")
    fun scheduledFetchAllInvoices() {
        if (!emailFeaturesEnabled) return
        log.info("[VendorInvoice] Starte täglichen Rechnungsabruf...")
        fetchAllVendorInvoices()
    }

    fun fetchAllVendorInvoices(): Map<String, Any> {
        val result = HashMap<String, Any>()
        if (microsoftEnabled) {
            try {
                val count = fetchMicrosoftInvoices()
                result["microsoft"] = mapOf("success" to true, "count" to count)
                log.info("[VendorInvoice] Microsoft: {} Rechnungen importiert", count)
            } catch (e: Exception) {
                log.error("[VendorInvoice] Microsoft-Fehler: {}", e.message)
                result["microsoft"] = mapOf("success" to false, "error" to e.message)
            }
        } else {
            result["microsoft"] = mapOf("enabled" to false)
        }

        if (amazonEnabled) {
            try {
                val count = fetchAmazonInvoices()
                result["amazon"] = mapOf("success" to true, "count" to count)
                log.info("[VendorInvoice] Amazon: {} Rechnungen importiert", count)
            } catch (e: Exception) {
                log.error("[VendorInvoice] Amazon-Fehler: {}", e.message)
                result["amazon"] = mapOf("success" to false, "error" to e.message)
            }
        } else {
            result["amazon"] = mapOf("enabled" to false)
        }
        return result
    }

    @Transactional
    fun fetchMicrosoftInvoices(): Int {
        if (!microsoftEnabled || microsoftTenantId.isBlank() || microsoftClientId.isBlank()) {
            log.debug("[Microsoft] Integration deaktiviert oder nicht konfiguriert")
            return 0
        }

        val accessToken = getMicrosoftAccessToken()
            ?: throw RuntimeException("Konnte Microsoft Access Token nicht abrufen")
        val invoicesUrl =
            "https://management.azure.com/providers/Microsoft.Billing/billingAccounts/$microsoftBillingAccountId/invoices?api-version=2024-04-01"

        val headers = HttpHeaders()
        headers.setBearerAuth(accessToken)
        val response = restTemplate.exchange(invoicesUrl, HttpMethod.GET, HttpEntity<Void>(headers), Map::class.java)
        if (!response.statusCode.is2xxSuccessful || response.body == null) {
            throw RuntimeException("Fehler beim Abrufen der Microsoft Invoices")
        }

        val invoices = response.body!!["value"] as? List<*> ?: return 0
        if (invoices.isEmpty()) return 0

        val microsoft = findOrLogMissingLieferant("Microsoft") ?: return 0
        var imported = 0
        for (rawInvoice in invoices) {
            val invoice = rawInvoice as? Map<*, *> ?: continue
            val invoiceId = invoice["name"] as? String
            val properties = invoice["properties"] as? Map<*, *> ?: continue
            val downloadUrl = properties["downloadUrl"] as? String
            val invoiceDate = properties["invoiceDate"] as? String

            if (invoiceId != null && dokumentRepository.existsByLieferantIdAndOriginalDateinameContaining(microsoft.id, invoiceId)) {
                continue
            }

            if (!downloadUrl.isNullOrBlank() && invoiceId != null) {
                try {
                    val pdfBytes = downloadPdf(downloadUrl, accessToken)
                    saveLieferantDokument(microsoft, pdfBytes, "Microsoft_Invoice_$invoiceId.pdf", invoiceDate)
                    imported++
                } catch (e: Exception) {
                    log.warn("[Microsoft] Konnte Rechnung {} nicht herunterladen: {}", invoiceId, e.message)
                }
            }
        }
        return imported
    }

    private fun getMicrosoftAccessToken(): String? {
        val tokenUrl = "https://login.microsoftonline.com/$microsoftTenantId/oauth2/v2.0/token"
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        val body: MultiValueMap<String, String> = LinkedMultiValueMap()
        body.add("grant_type", "client_credentials")
        body.add("client_id", microsoftClientId)
        body.add("client_secret", microsoftClientSecret)
        body.add("scope", "https://management.azure.com/.default")

        return try {
            val response = restTemplate.postForEntity(tokenUrl, HttpEntity(body, headers), Map::class.java)
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                response.body!!["access_token"] as? String
            } else {
                null
            }
        } catch (e: Exception) {
            log.error("[Microsoft] Token-Abruf fehlgeschlagen: {}", e.message)
            null
        }
    }

    @Transactional
    fun fetchAmazonInvoices(): Int {
        if (!amazonEnabled || amazonRefreshToken.isBlank() || amazonClientId.isBlank()) {
            log.debug("[Amazon] Integration deaktiviert oder nicht konfiguriert")
            return 0
        }

        val accessToken = getAmazonAccessToken()
            ?: throw RuntimeException("Konnte Amazon Access Token nicht abrufen")
        val baseUrl = if (amazonRegion.equals("US", ignoreCase = true)) {
            "https://sellingpartnerapi-na.amazon.com"
        } else {
            "https://sellingpartnerapi-eu.amazon.com"
        }
        val transactionsUrl = baseUrl + "/reconciliation/2021-01-01/transactions?createdAfter=" +
            LocalDate.now().minusDays(30)

        val headers = HttpHeaders()
        headers.setBearerAuth(accessToken)
        headers["x-amz-access-token"] = accessToken
        val response = try {
            restTemplate.exchange(transactionsUrl, HttpMethod.GET, HttpEntity<Void>(headers), Map::class.java)
        } catch (e: Exception) {
            log.warn("[Amazon] Transactions-API nicht verfügbar: {}", e.message)
            return 0
        }

        if (!response.statusCode.is2xxSuccessful || response.body == null) return 0
        val transactions = response.body!!["transactions"] as? List<*> ?: return 0
        if (transactions.isEmpty()) return 0

        val amazon = findOrLogMissingLieferant("Amazon") ?: return 0
        var imported = 0
        for (rawTransaction in transactions) {
            val transaction = rawTransaction as? Map<*, *> ?: continue
            val orderId = transaction["orderId"] as? String ?: continue
            val invoiceUrl = transaction["invoiceDownloadUrl"] as? String
            if (dokumentRepository.existsByLieferantIdAndOriginalDateinameContaining(amazon.id, orderId)) {
                continue
            }
            if (!invoiceUrl.isNullOrBlank()) {
                try {
                    val pdfBytes = downloadPdf(invoiceUrl, accessToken)
                    val invoiceDate = transaction["transactionDate"] as? String
                    saveLieferantDokument(amazon, pdfBytes, "Amazon_Invoice_$orderId.pdf", invoiceDate)
                    imported++
                } catch (e: Exception) {
                    log.warn("[Amazon] Konnte Rechnung {} nicht herunterladen: {}", orderId, e.message)
                }
            }
        }
        return imported
    }

    private fun getAmazonAccessToken(): String? {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        val body: MultiValueMap<String, String> = LinkedMultiValueMap()
        body.add("grant_type", "refresh_token")
        body.add("refresh_token", amazonRefreshToken)
        body.add("client_id", amazonClientId)
        body.add("client_secret", amazonClientSecret)

        return try {
            val response = restTemplate.postForEntity("https://api.amazon.com/auth/o2/token", HttpEntity(body, headers), Map::class.java)
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                response.body!!["access_token"] as? String
            } else {
                null
            }
        } catch (e: Exception) {
            log.error("[Amazon] Token-Abruf fehlgeschlagen: {}", e.message)
            null
        }
    }

    private fun findOrLogMissingLieferant(name: String): Lieferanten? =
        lieferantenRepository.findByLieferantennameIgnoreCase(name).orElseGet {
            log.warn("[VendorInvoice] Lieferant '{}' nicht gefunden. Bitte manuell anlegen!", name)
            null
        }

    private fun validateDownloadUrl(url: String) {
        try {
            val uri = URI.create(url)
            val host = uri.host
            val scheme = uri.scheme
            if (host == null || scheme == null) {
                throw SecurityException("Ungültige Download-URL: fehlender Host oder Schema")
            }
            if (!scheme.equals("https", ignoreCase = true)) {
                throw SecurityException("Nur HTTPS-Downloads erlaubt, erhalten: $scheme")
            }
            val allowed = allowedDownloadHosts.any { host.equals(it, ignoreCase = true) || host.endsWith(".$it") }
            if (!allowed) {
                throw SecurityException("Download-Host nicht erlaubt: $host")
            }
        } catch (e: IllegalArgumentException) {
            throw SecurityException("Ungültige Download-URL: ${e.message}")
        }
    }

    private fun downloadPdf(url: String, accessToken: String?): ByteArray {
        validateDownloadUrl(url)
        val headers = HttpHeaders()
        if (accessToken != null) {
            headers.setBearerAuth(accessToken)
        }
        headers.accept = listOf(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM)
        val response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), ByteArray::class.java)
        if (response.statusCode.is2xxSuccessful && response.body != null) {
            return response.body!!
        }
        throw RuntimeException("PDF-Download fehlgeschlagen: ${response.statusCode}")
    }

    @Throws(IOException::class)
    private fun saveLieferantDokument(lieferant: Lieferanten, pdfBytes: ByteArray, filename: String, invoiceDate: String?) {
        val vendorDir = Path.of(attachmentDir, "vendor-invoices").toAbsolutePath().normalize()
        Files.createDirectories(vendorDir)

        val safeFilename = Path.of(filename).fileName.toString()
        val storedFilename = UUID.randomUUID().toString() + "_" + safeFilename
        val targetPath = vendorDir.resolve(storedFilename).normalize()
        if (!targetPath.startsWith(vendorDir)) {
            throw SecurityException("Ungültiger Dateipfad: Verzeichnistraversal erkannt")
        }
        Files.write(targetPath, pdfBytes)

        val dokument = LieferantDokument()
        dokument.lieferant = lieferant
        dokument.typ = LieferantDokumentTyp.RECHNUNG
        dokument.originalDateiname = filename
        dokument.gespeicherterDateiname = storedFilename
        dokument.uploadDatum = LocalDateTime.now()

        dokumentRepository.save(dokument)
        log.info("[VendorInvoice] Rechnung gespeichert: {} für {}", filename, lieferant.lieferantenname)
    }

    companion object {
        private val log = LoggerFactory.getLogger(VendorInvoiceIntegrationService::class.java)
        private val allowedDownloadHosts = setOf(
            "management.azure.com",
            "sellingpartnerapi-na.amazon.com",
            "sellingpartnerapi-eu.amazon.com",
            "m.media-amazon.com",
            "invoices.amazon.com",
        )
    }
}
