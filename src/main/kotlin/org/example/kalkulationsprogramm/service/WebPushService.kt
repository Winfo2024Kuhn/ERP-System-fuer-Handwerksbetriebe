package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.example.kalkulationsprogramm.domain.KalenderEintrag
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.PushSubscription
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.PushSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.KeyFactory
import java.security.KeyPair
import java.security.Security
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Collections
import java.util.Locale

@Service
class WebPushService(
    private val pushSubscriptionRepository: PushSubscriptionRepository,
    private val kalenderEintragRepository: KalenderEintragRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val objectMapper: ObjectMapper,
) {
    @Value("\${push.vapid.public-key:}")
    private lateinit var configuredVapidPublicKey: String

    @Value("\${push.vapid.private-key:}")
    private lateinit var vapidPrivateKey: String

    @Value("\${push.vapid.subject:mailto:noreply@handwerk-erp.de}")
    private lateinit var vapidSubject: String

    @Value("\${zeiterfassung.base-url:https://localhost}")
    private lateinit var baseUrl: String

    private var pushService: PushService? = null
    private var rawVapidPublicKey: String? = null
    private val sentNotifications: MutableSet<String> = Collections.synchronizedSet(HashSet())

    val isEnabled: Boolean
        get() = pushService != null

    val vapidPublicKey: String
        get() = rawVapidPublicKey.orEmpty()

    @PostConstruct
    fun init() {
        if (configuredVapidPublicKey.isBlank() || vapidPrivateKey.isBlank()) {
            log.warn(
                "Web Push VAPID keys not configured. Push notifications disabled. " +
                    "Generate keys and set PUSH_VAPID_PUBLIC_KEY / PUSH_VAPID_PRIVATE_KEY environment variables.",
            )
            return
        }

        try {
            Security.addProvider(BouncyCastleProvider())
            val pubBytes = Base64.getUrlDecoder().decode(configuredVapidPublicKey)
            val privBytes = Base64.getUrlDecoder().decode(vapidPrivateKey)

            val keyFactory = KeyFactory.getInstance("EC", "BC")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
            val keyPair = KeyPair(publicKey, privateKey)

            val ecPub = publicKey as ECPublicKey
            val x = ecPub.w.affineX.toByteArray()
            val y = ecPub.w.affineY.toByteArray()
            val rawPoint = ByteArray(65)
            rawPoint[0] = 0x04
            System.arraycopy(x, maxOf(0, x.size - 32), rawPoint, 1 + maxOf(0, 32 - x.size), minOf(32, x.size))
            System.arraycopy(y, maxOf(0, y.size - 32), rawPoint, 33 + maxOf(0, 32 - y.size), minOf(32, y.size))
            rawVapidPublicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPoint)

            pushService = PushService(keyPair, vapidSubject)
            log.info("Web Push Service initialized with VAPID keys")
        } catch (e: Exception) {
            log.error("Failed to initialize Web Push Service: {}", e.message, e)
        }
    }

    fun notifyAll(title: String, body: String, url: String) {
        if (!isEnabled) {
            log.debug("WebPush nicht aktiv – notifyAll wird ignoriert")
            return
        }
        try {
            val alle = pushSubscriptionRepository.findAll()
            for (sub in alle) {
                sendPush(sub, title, body, url, null, "freigabe")
            }
        } catch (e: Exception) {
            log.warn("notifyAll fehlgeschlagen: {}", e.message)
        }
    }

    fun notifyFreigabeAnnahme(title: String, body: String, url: String) {
        if (!isEnabled) {
            log.debug("WebPush nicht aktiv – notifyFreigabeAnnahme wird ignoriert")
            return
        }
        try {
            val alle = pushSubscriptionRepository.findAll()
            for (sub in alle) {
                val ma = sub.mitarbeiter ?: continue
                val erlaubt = ma.abteilungen?.any { it.darfFreigabeAnnahmePushen == true } == true
                if (!erlaubt) continue
                sendPush(sub, title, body, url, null, "freigabe")
            }
        } catch (e: Exception) {
            log.warn("notifyFreigabeAnnahme fehlgeschlagen: {}", e.message)
        }
    }

    fun notifyWebseitenAnfrage(title: String, body: String, url: String) {
        if (!isEnabled) {
            log.debug("WebPush nicht aktiv – notifyWebseitenAnfrage wird ignoriert")
            return
        }
        try {
            val alle = pushSubscriptionRepository.findAll()
            for (sub in alle) {
                val ma = sub.mitarbeiter ?: continue
                val erlaubt = ma.abteilungen?.any { it.darfWebseitenAnfragenPushen == true } == true
                if (!erlaubt) continue
                sendPush(sub, title, body, url, null, "anfrage")
            }
        } catch (e: Exception) {
            log.warn("notifyWebseitenAnfrage fehlgeschlagen: {}", e.message)
        }
    }

    @Transactional
    fun subscribe(mitarbeiterId: Long?, endpoint: String?, p256dh: String?, auth: String?) {
        requireNotNull(mitarbeiterId) { "Mitarbeiter ID fehlt" }
        require(!endpoint.isNullOrBlank()) { "Endpoint fehlt" }
        require(!p256dh.isNullOrBlank()) { "p256dh fehlt" }
        require(!auth.isNullOrBlank()) { "auth fehlt" }

        pushSubscriptionRepository.findByEndpoint(endpoint).ifPresent { existing ->
            pushSubscriptionRepository.delete(existing)
            pushSubscriptionRepository.flush()
        }

        val mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
            .orElseThrow { IllegalArgumentException("Mitarbeiter not found: $mitarbeiterId") }

        val sub = PushSubscription().apply {
            this.mitarbeiter = mitarbeiter
            this.endpoint = endpoint
            this.p256dh = p256dh
            this.auth = auth
        }
        pushSubscriptionRepository.save(sub)
        log.info("Push subscription saved for Mitarbeiter {}", mitarbeiterId)
    }

    @Transactional
    fun unsubscribe(endpoint: String) {
        pushSubscriptionRepository.deleteByEndpoint(endpoint)
        log.info("Push subscription removed for endpoint")
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    fun checkAndSendNotifications() {
        if (!isEnabled) return

        try {
            val today = LocalDate.now()
            val dayAfter = today.plusDays(2)
            val appointments = kalenderEintragRepository.findByDatumBetween(today, dayAfter)
            val now = LocalDateTime.now()

            for (apt in appointments) {
                val aptDateTime = getAppointmentDateTime(apt)
                if (aptDateTime.isBefore(now)) continue

                val minutesUntil = ChronoUnit.MINUTES.between(now, aptDateTime)
                if (minutesUntil in 1430..1450) {
                    sendNotificationForAppointment(apt, "24h")
                }
                if (minutesUntil in 50..70) {
                    sendNotificationForAppointment(apt, "1h")
                }
            }

            cleanupSentTracking()
        } catch (e: Exception) {
            log.error("Error checking appointment notifications: {}", e.message)
        }
    }

    private fun getAppointmentDateTime(apt: KalenderEintrag): LocalDateTime =
        if (apt.isGanztaegig() || apt.startZeit == null) {
            (apt.datum ?: LocalDate.now()).atTime(8, 0)
        } else {
            (apt.datum ?: LocalDate.now()).atTime(apt.startZeit)
        }

    private fun sendNotificationForAppointment(apt: KalenderEintrag, type: String) {
        val trackingKey = "${apt.id}_${type}_${apt.datum}"
        if (sentNotifications.contains(trackingKey)) return

        val mitarbeiterIds = HashSet<Long>()
        apt.ersteller?.id?.let { mitarbeiterIds.add(it) }
        apt.teilnehmer?.forEach { teilnehmer -> teilnehmer.id?.let { mitarbeiterIds.add(it) } }

        if (mitarbeiterIds.isEmpty()) {
            mitarbeiterRepository.findAll()
                .filter { it.aktiv == true }
                .forEach { it.id?.let(mitarbeiterIds::add) }
        }

        val timeStr =
            if (apt.isGanztaegig() || apt.startZeit == null) {
                "Ganztägig"
            } else {
                val startZeit = apt.startZeit
                "${startZeit?.format(DateTimeFormatter.ofPattern("HH:mm"))} Uhr"
            }

        val title: String
        val body: String
        if (type == "24h") {
            title = "Termin morgen: ${apt.titel}"
            body = "${(apt.datum ?: LocalDate.now()).format(DATE_FORMATTER)} um $timeStr"
        } else {
            title = "Termin in 1 Stunde: ${apt.titel}"
            body = timeStr
        }

        val notificationUrl = "$baseUrl/zeiterfassung/kalender?termin=${apt.id}"
        for (mitarbeiterId in mitarbeiterIds) {
            val subscriptions = pushSubscriptionRepository.findByMitarbeiterId(mitarbeiterId)
            for (sub in subscriptions) {
                sendPush(sub, title, body, notificationUrl, apt.id, type)
            }
        }

        sentNotifications.add(trackingKey)
    }

    private fun sendPush(
        sub: PushSubscription,
        title: String,
        body: String,
        url: String,
        appointmentId: Long?,
        type: String,
    ) {
        try {
            val payload = HashMap<String, Any?>()
            payload["title"] = title
            payload["body"] = body
            payload["url"] = url
            payload["appointmentId"] = appointmentId
            payload["type"] = type
            payload["timestamp"] = System.currentTimeMillis()

            val payloadJson = objectMapper.writeValueAsString(payload)
            val notification = Notification(
                sub.endpoint,
                sub.p256dh,
                sub.auth,
                payloadJson.toByteArray(),
            )

            pushService?.send(notification)
            log.debug("Push sent to endpoint for appointment {}: {}", appointmentId, title)
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("410") || errorMsg.contains("404") || errorMsg.contains("expired")) {
                val endpoint = sub.endpoint.orEmpty()
                log.info("Removing expired push subscription: {}", endpoint.substring(0, minOf(50, endpoint.length)))
                try {
                    pushSubscriptionRepository.delete(sub)
                } catch (deleteErr: Exception) {
                    log.warn("Failed to delete expired subscription: {}", deleteErr.message)
                }
            } else {
                log.warn("Failed to send push notification: {}", errorMsg)
            }
        }
    }

    private fun cleanupSentTracking() {
        val yesterday = LocalDate.now().minusDays(1)
        sentNotifications.removeIf { key ->
            try {
                val datePart = key.substring(key.lastIndexOf('_') + 1)
                val entryDate = LocalDate.parse(datePart)
                entryDate.isBefore(yesterday)
            } catch (e: Exception) {
                true
            }
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(WebPushService::class.java)
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd. MMM", Locale.GERMAN)
    }
}
