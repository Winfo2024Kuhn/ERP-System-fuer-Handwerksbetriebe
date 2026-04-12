package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.KiChatDto.ChatDetail;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatSummary;
import org.example.kalkulationsprogramm.dto.KiChatDto.CreateChatRequest;
import org.example.kalkulationsprogramm.dto.KiChatDto.RenameChatRequest;
import org.example.kalkulationsprogramm.dto.KiChatDto.SendMessageRequest;
import org.example.kalkulationsprogramm.service.KiChatService;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/ki-chat")
@RequiredArgsConstructor
public class KiChatController {

    private final KiChatService kiChatService;

    /** List all chats for a user */
    @GetMapping
    public ResponseEntity<List<ChatSummary>> listChats(@RequestParam Long userId) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(kiChatService.listChats(userId));
    }

    /** Create a new chat */
    @PostMapping
    public ResponseEntity<?> createChat(@RequestBody CreateChatRequest request) {
        if (request.userId() == null || request.userId() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId ungültig"));
        }
        try {
            return ResponseEntity.ok(kiChatService.createChat(request.userId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Get a chat with all messages */
    @GetMapping("/{chatId}")
    public ResponseEntity<?> getChat(@PathVariable Long chatId, @RequestParam Long userId) {
        if (userId == null || userId <= 0) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok(kiChatService.getChat(chatId, userId));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Delete a chat */
    @DeleteMapping("/{chatId}")
    public ResponseEntity<?> deleteChat(@PathVariable Long chatId, @RequestParam Long userId) {
        if (userId == null || userId <= 0) return ResponseEntity.badRequest().build();
        try {
            kiChatService.deleteChat(chatId, userId);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Rename a chat */
    @PatchMapping("/{chatId}")
    public ResponseEntity<?> renameChat(
            @PathVariable Long chatId,
            @RequestParam Long userId,
            @RequestBody RenameChatRequest request) {
        if (userId == null || userId <= 0) return ResponseEntity.badRequest().build();
        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Titel darf nicht leer sein"));
        }
        try {
            return ResponseEntity.ok(kiChatService.renameChat(chatId, userId, request.title()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Send a message and get AI response */
    @PostMapping("/{chatId}/messages")
    public ResponseEntity<?> sendMessage(
            @PathVariable Long chatId,
            @RequestBody SendMessageRequest request) {
        if (request.userId() == null || request.userId() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId ungültig"));
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nachricht darf nicht leer sein"));
        }
        if (request.message().length() > 5000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nachricht zu lang (max. 5000 Zeichen)"));
        }

        try {
            PageContext pageContext = null;
            if (request.context() != null) {
                var ctx = request.context();
                pageContext = new PageContext(
                        ctx.route(), ctx.pageTitle(), ctx.visibleContent(),
                        ctx.errorMessages(), ctx.latitude(), ctx.longitude());
            }

            ChatDetail result = kiChatService.sendMessage(chatId, request.userId(), request.message(), pageContext);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Fehler bei KI-Chat Nachricht", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "KI-Fehler: " + e.getMessage()));
        }
    }
}
