package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.KiHilfeService;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatMessage;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/ki-hilfe")
@RequiredArgsConstructor
public class KiHilfeController {

    private final KiHilfeService kiHilfeService;

    public record ChatRequest(List<MessageDto> messages, PageContextDto context) {}
    public record MessageDto(String role, String text) {}
    public record PageContextDto(String route, String pageTitle, String visibleContent, String errorMessages, Double latitude, Double longitude) {}

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Keine Nachricht angegeben"));
        }

        // Validate message count
        if (request.messages().size() > 50) {
            return ResponseEntity.badRequest().body(Map.of("error", "Zu viele Nachrichten"));
        }

        // Validate individual messages
        for (MessageDto msg : request.messages()) {
            if (msg.text() == null || msg.text().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Leere Nachricht"));
            }
            if (msg.text().length() > 5000) {
                return ResponseEntity.badRequest().body(Map.of("error", "Nachricht zu lang (max. 5000 Zeichen)"));
            }
            if (!"user".equals(msg.role()) && !"assistant".equals(msg.role())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Ungültige Rolle: " + msg.role()));
            }
        }

        try {
            List<ChatMessage> messages = request.messages().stream()
                    .map(m -> new ChatMessage(m.role(), m.text()))
                    .toList();

            // Map page context if provided
            PageContext pageContext = null;
            if (request.context() != null) {
                pageContext = new PageContext(
                        request.context().route(),
                        request.context().pageTitle(),
                        request.context().visibleContent(),
                        request.context().errorMessages(),
                        request.context().latitude(),
                        request.context().longitude()
                );
                log.info("\n====================================================="
                       + "\n| KI-HILFE ANFRAGE"
                       + "\n| Route:   {}"
                       + "\n| Titel:   {}"
                       + "\n| Fehler:  {}"
                       + "\n| Kontext: {} Zeichen"
                       + "\n=====================================================",
                        request.context().route(),
                        request.context().pageTitle(),
                        request.context().errorMessages() != null ? request.context().errorMessages() : "(keine)",
                        request.context().visibleContent() != null ? request.context().visibleContent().length() : 0);
            } else {
                log.info("KI-Hilfe Anfrage OHNE Seitenkontext");
            }

            String lastUserMsg = messages.stream()
                    .filter(m -> "user".equals(m.role()))
                    .reduce((a, b) -> b)
                    .map(ChatMessage::text)
                    .orElse("");
            log.info("| Frage:   {}", lastUserMsg.length() > 100 ? lastUserMsg.substring(0, 100) + "..." : lastUserMsg);

            var result = kiHilfeService.chat(messages, pageContext);

            log.info("| Antwort: {} Zeichen", result.reply().length());
            java.util.HashMap<String, Object> response = new java.util.HashMap<>();
            response.put("reply", result.reply());
            if (result.sources() != null && !result.sources().isEmpty()) {
                response.put("sources", result.sources());
            }
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("KI-Hilfe Fehler", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "KI-Hilfe ist gerade nicht verfügbar. Bitte versuche es später erneut."));
        }
    }
}
