package org.example.kalkulationsprogramm.service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.PushSubscription;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.PushSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final KalenderEintragRepository kalenderEintragRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ObjectMapper objectMapper;

    @Value("${push.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${push.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${push.vapid.subject:mailto:noreply@handwerk-erp.de}")
    private String vapidSubject;

    @Value("${zeiterfassung.base-url:https://localhost}")
    private String baseUrl;

    private PushService pushService;
    private String rawVapidPublicKey; // Uncompressed EC point, base64url – for browser PushManager

    // Track sent notifications to avoid duplicates (key: "appointmentId_type")
    private final Set<String> sentNotifications = Collections.synchronizedSet(new HashSet<>());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd. MMM", Locale.GERMAN);

    @PostConstruct
    public void init() {
        if (vapidPublicKey.isBlank() || vapidPrivateKey.isBlank()) {
            log.warn("Web Push VAPID keys not configured. Push notifications disabled. " +
                    "Generate keys and set PUSH_VAPID_PUBLIC_KEY / PUSH_VAPID_PRIVATE_KEY environment variables.");
            return;
        }

        try {
            Security.addProvider(new BouncyCastleProvider());

            byte[] pubBytes = Base64.getUrlDecoder().decode(vapidPublicKey);
            byte[] privBytes = Base64.getUrlDecoder().decode(vapidPrivateKey);

            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            KeyPair keyPair = new KeyPair(publicKey, privateKey);

            // Extract raw uncompressed EC point (65 bytes: 0x04 || x || y) for browser API
            ECPublicKey ecPub = (ECPublicKey) publicKey;
            byte[] x = ecPub.getW().getAffineX().toByteArray();
            byte[] y = ecPub.getW().getAffineY().toByteArray();
            byte[] rawPoint = new byte[65];
            rawPoint[0] = 0x04;
            // Copy x and y, right-aligned in 32-byte fields (skip leading 0x00 sign byte if present)
            System.arraycopy(x, Math.max(0, x.length - 32), rawPoint, 1 + Math.max(0, 32 - x.length), Math.min(32, x.length));
            System.arraycopy(y, Math.max(0, y.length - 32), rawPoint, 33 + Math.max(0, 32 - y.length), Math.min(32, y.length));
            rawVapidPublicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPoint);

            pushService = new PushService(keyPair, vapidSubject);
            log.info("Web Push Service initialized with VAPID keys");
        } catch (Exception e) {
            log.error("Failed to initialize Web Push Service: {}", e.getMessage(), e);
        }
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    public String getVapidPublicKey() {
        return rawVapidPublicKey;
    }

    /**
     * Subscribe a device for push notifications.
     */
    @Transactional
    public void subscribe(Long mitarbeiterId, String endpoint, String p256dh, String auth) {
        // Remove existing subscription for this endpoint (re-subscribe)
        pushSubscriptionRepository.findByEndpoint(endpoint).ifPresent(existing -> {
            pushSubscriptionRepository.delete(existing);
            pushSubscriptionRepository.flush();
        });

        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter not found: " + mitarbeiterId));

        PushSubscription sub = new PushSubscription();
        sub.setMitarbeiter(mitarbeiter);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        pushSubscriptionRepository.save(sub);

        log.info("Push subscription saved for Mitarbeiter {}", mitarbeiterId);
    }

    /**
     * Unsubscribe a device.
     */
    @Transactional
    public void unsubscribe(String endpoint) {
        pushSubscriptionRepository.deleteByEndpoint(endpoint);
        log.info("Push subscription removed for endpoint");
    }

    /**
     * Scheduled task: Check every 2 minutes for upcoming appointments
     * and send push notifications 24h and 1h before.
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    public void checkAndSendNotifications() {
        if (!isEnabled()) return;

        try {
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            LocalDate dayAfter = today.plusDays(2);

            // Fetch appointments for today, tomorrow, and day after (covers 24h window)
            List<KalenderEintrag> appointments = kalenderEintragRepository.findByDatumBetween(today, dayAfter);

            LocalDateTime now = LocalDateTime.now();

            for (KalenderEintrag apt : appointments) {
                LocalDateTime aptDateTime = getAppointmentDateTime(apt);
                if (aptDateTime.isBefore(now)) continue;

                long minutesUntil = ChronoUnit.MINUTES.between(now, aptDateTime);

                // 24h notification: between 23h50m and 24h10m before
                if (minutesUntil >= 1430 && minutesUntil <= 1450) {
                    sendNotificationForAppointment(apt, "24h");
                }

                // 1h notification: between 50min and 70min before
                if (minutesUntil >= 50 && minutesUntil <= 70) {
                    sendNotificationForAppointment(apt, "1h");
                }
            }

            // Cleanup old sent-tracking entries (older than 3 days)
            cleanupSentTracking();
        } catch (Exception e) {
            log.error("Error checking appointment notifications: {}", e.getMessage());
        }
    }

    private LocalDateTime getAppointmentDateTime(KalenderEintrag apt) {
        if (apt.isGanztaegig() || apt.getStartZeit() == null) {
            return apt.getDatum().atTime(8, 0); // Default 08:00 for all-day events
        }
        return apt.getDatum().atTime(apt.getStartZeit());
    }

    private void sendNotificationForAppointment(KalenderEintrag apt, String type) {
        String trackingKey = apt.getId() + "_" + type + "_" + apt.getDatum();
        if (sentNotifications.contains(trackingKey)) return;

        // Find all mitarbeiter who should receive this notification
        Set<Long> mitarbeiterIds = new HashSet<>();

        // Ersteller (creator)
        if (apt.getErsteller() != null) {
            mitarbeiterIds.add(apt.getErsteller().getId());
        }

        // Teilnehmer (participants)
        if (apt.getTeilnehmer() != null) {
            for (Mitarbeiter t : apt.getTeilnehmer()) {
                mitarbeiterIds.add(t.getId());
            }
        }

        // If no specific people assigned (company calendar), send to all active employees
        if (mitarbeiterIds.isEmpty()) {
            mitarbeiterRepository.findAll().stream()
                    .filter(m -> Boolean.TRUE.equals(m.getAktiv()))
                    .forEach(m -> mitarbeiterIds.add(m.getId()));
        }

        // Build notification payload
        String title;
        String body;
        String timeStr = apt.isGanztaegig() || apt.getStartZeit() == null
                ? "Ganztägig"
                : apt.getStartZeit().format(DateTimeFormatter.ofPattern("HH:mm")) + " Uhr";

        if ("24h".equals(type)) {
            title = "Termin morgen: " + apt.getTitel();
            body = apt.getDatum().format(DATE_FORMATTER) + " um " + timeStr;
        } else {
            title = "Termin in 1 Stunde: " + apt.getTitel();
            body = timeStr;
        }

        String notificationUrl = baseUrl + "/zeiterfassung/kalender?termin=" + apt.getId();

        // Send to all subscriptions for relevant mitarbeiter
        for (Long mitarbeiterId : mitarbeiterIds) {
            List<PushSubscription> subscriptions = pushSubscriptionRepository.findByMitarbeiterId(mitarbeiterId);
            for (PushSubscription sub : subscriptions) {
                sendPush(sub, title, body, notificationUrl, apt.getId(), type);
            }
        }

        sentNotifications.add(trackingKey);
    }

    private void sendPush(PushSubscription sub, String title, String body, String url, Long appointmentId, String type) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("body", body);
            payload.put("url", url);
            payload.put("appointmentId", appointmentId);
            payload.put("type", type);
            payload.put("timestamp", System.currentTimeMillis());

            String payloadJson = objectMapper.writeValueAsString(payload);

            Notification notification = new Notification(
                    sub.getEndpoint(),
                    sub.getP256dh(),
                    sub.getAuth(),
                    payloadJson.getBytes()
            );

            pushService.send(notification);
            log.debug("Push sent to endpoint for appointment {}: {}", appointmentId, title);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            // 410 Gone or 404: subscription expired, remove it
            if (errorMsg.contains("410") || errorMsg.contains("404") || errorMsg.contains("expired")) {
                log.info("Removing expired push subscription: {}", sub.getEndpoint().substring(0, Math.min(50, sub.getEndpoint().length())));
                try {
                    pushSubscriptionRepository.delete(sub);
                } catch (Exception deleteErr) {
                    log.warn("Failed to delete expired subscription: {}", deleteErr.getMessage());
                }
            } else {
                log.warn("Failed to send push notification: {}", errorMsg);
            }
        }
    }

    private void cleanupSentTracking() {
        // Keep tracking set manageable - remove entries for past dates
        LocalDate yesterday = LocalDate.now().minusDays(1);
        sentNotifications.removeIf(key -> {
            try {
                String datePart = key.substring(key.lastIndexOf('_') + 1);
                LocalDate entryDate = LocalDate.parse(datePart);
                return entryDate.isBefore(yesterday);
            } catch (Exception e) {
                return true; // remove malformed entries
            }
        });
    }
}
