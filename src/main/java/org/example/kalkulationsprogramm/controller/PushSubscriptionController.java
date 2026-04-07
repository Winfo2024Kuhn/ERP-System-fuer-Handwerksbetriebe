package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.WebPushService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final WebPushService webPushService;
    private final MitarbeiterRepository mitarbeiterRepository;

    /**
     * Returns the VAPID public key needed for PushManager.subscribe() in the frontend.
     */
    @GetMapping("/vapid-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey(@RequestParam String token) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null);
        if (mitarbeiter == null || !Boolean.TRUE.equals(mitarbeiter.getAktiv())) {
            return ResponseEntity.status(401).build();
        }

        if (!webPushService.isEnabled()) {
            return ResponseEntity.ok(Map.of("publicKey", "", "enabled", "false"));
        }

        return ResponseEntity.ok(Map.of(
                "publicKey", webPushService.getVapidPublicKey(),
                "enabled", "true"
        ));
    }

    public record PushSubscribeRequest(
            String endpoint,
            String p256dh,
            String auth
    ) {}

    /**
     * Subscribe a device for push notifications.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Boolean>> subscribe(
            @RequestParam String token,
            @RequestBody PushSubscribeRequest request) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null);
        if (mitarbeiter == null || !Boolean.TRUE.equals(mitarbeiter.getAktiv())) {
            return ResponseEntity.status(401).build();
        }

        if (!webPushService.isEnabled()) {
            return ResponseEntity.ok(Map.of("subscribed", false));
        }

        webPushService.subscribe(
                mitarbeiter.getId(),
                request.endpoint(),
                request.p256dh(),
                request.auth()
        );

        return ResponseEntity.ok(Map.of("subscribed", true));
    }

    /**
     * Unsubscribe a device from push notifications.
     */
    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, Boolean>> unsubscribe(
            @RequestParam String token,
            @RequestBody Map<String, String> body) {
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null);
        if (mitarbeiter == null || !Boolean.TRUE.equals(mitarbeiter.getAktiv())) {
            return ResponseEntity.status(401).build();
        }

        String endpoint = body.get("endpoint");
        if (endpoint != null && !endpoint.isBlank()) {
            webPushService.unsubscribe(endpoint);
        }

        return ResponseEntity.ok(Map.of("unsubscribed", true));
    }
}
