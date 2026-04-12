package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.KiChat;
import org.example.kalkulationsprogramm.domain.KiChatMessage;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatDetail;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatSummary;
import org.example.kalkulationsprogramm.dto.KiChatDto.MessageDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KiChatRepository;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatMessage;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatResult;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KiChatService {

    private final KiChatRepository kiChatRepository;
    private final FrontendUserProfileRepository userRepository;
    private final KiHilfeService kiHilfeService;

    @Transactional(readOnly = true)
    public List<ChatSummary> listChats(Long userId) {
        return kiChatRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(c -> new ChatSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ChatDetail createChat(Long userId) {
        FrontendUserProfile user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden: " + userId));
        KiChat chat = new KiChat();
        chat.setUser(user);
        chat.setTitle("Neuer Chat");
        chat = kiChatRepository.save(chat);
        return toDetail(chat);
    }

    @Transactional(readOnly = true)
    public ChatDetail getChat(Long chatId, Long userId) {
        KiChat chat = kiChatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat nicht gefunden"));
        if (!chat.getUser().getId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Chat");
        }
        return toDetail(chat);
    }

    @Transactional
    public void deleteChat(Long chatId, Long userId) {
        KiChat chat = kiChatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat nicht gefunden"));
        if (!chat.getUser().getId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Chat");
        }
        kiChatRepository.delete(chat);
    }

    @Transactional
    public ChatSummary renameChat(Long chatId, Long userId, String newTitle) {
        KiChat chat = kiChatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat nicht gefunden"));
        if (!chat.getUser().getId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Chat");
        }
        chat.setTitle(newTitle.length() > 300 ? newTitle.substring(0, 300) : newTitle);
        chat.setUpdatedAt(LocalDateTime.now());
        chat = kiChatRepository.save(chat);
        return new ChatSummary(chat.getId(), chat.getTitle(), chat.getCreatedAt(), chat.getUpdatedAt());
    }

    @Transactional
    public ChatDetail sendMessage(Long chatId, Long userId, String userMessage, PageContext pageContext)
            throws IOException, InterruptedException {
        KiChat chat = kiChatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat nicht gefunden"));
        if (!chat.getUser().getId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Chat");
        }

        // Persist user message
        KiChatMessage userMsg = new KiChatMessage();
        userMsg.setChat(chat);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        chat.getMessages().add(userMsg);

        // Build message history for Gemini
        List<ChatMessage> history = chat.getMessages().stream()
                .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                .toList();

        // Call KI
        ChatResult result = kiHilfeService.chat(history, pageContext);

        // Persist assistant message
        KiChatMessage assistantMsg = new KiChatMessage();
        assistantMsg.setChat(chat);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(result.reply());
        chat.getMessages().add(assistantMsg);

        // Auto-title on first message
        if (chat.getMessages().size() <= 2 && "Neuer Chat".equals(chat.getTitle())) {
            String autoTitle = userMessage.length() > 60 ? userMessage.substring(0, 60) + "…" : userMessage;
            chat.setTitle(autoTitle);
        }

        chat.setUpdatedAt(LocalDateTime.now());
        kiChatRepository.save(chat);

        return toDetail(chat);
    }

    private ChatDetail toDetail(KiChat chat) {
        List<MessageDto> msgs = chat.getMessages().stream()
                .map(m -> new MessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
        return new ChatDetail(chat.getId(), chat.getTitle(), chat.getCreatedAt(), chat.getUpdatedAt(), msgs);
    }
}
