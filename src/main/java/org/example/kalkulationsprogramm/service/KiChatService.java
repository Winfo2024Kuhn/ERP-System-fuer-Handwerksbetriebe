package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.KiChat;
import org.example.kalkulationsprogramm.domain.KiChatMessage;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatDetail;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatSummary;
import org.example.kalkulationsprogramm.dto.KiChatDto.MessageDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KiChatMessageRepository;
import org.example.kalkulationsprogramm.repository.KiChatRepository;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatMessage;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatResult;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KiChatService {

    /** Status of an async KI processing task. */
    public enum ProcessingStatus { PROCESSING, DONE, ERROR, CANCELLED }

    public record StatusResult(ProcessingStatus status, String error) {}

    /** In-memory state for async processing per chat. */
    private record ProcessingState(AtomicBoolean cancelled, ProcessingStatus status, String error) {}

    private final KiChatRepository kiChatRepository;
    private final KiChatMessageRepository kiChatMessageRepository;
    private final FrontendUserProfileRepository userRepository;
    private final KiHilfeService kiHilfeService;
    private final TransactionTemplate transactionTemplate;

    private final Map<Long, ProcessingState> processingMap = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public KiChatService(KiChatRepository kiChatRepository,
                         KiChatMessageRepository kiChatMessageRepository,
                         FrontendUserProfileRepository userRepository,
                         KiHilfeService kiHilfeService,
                         TransactionTemplate transactionTemplate) {
        this.kiChatRepository = kiChatRepository;
        this.kiChatMessageRepository = kiChatMessageRepository;
        this.userRepository = userRepository;
        this.kiHilfeService = kiHilfeService;
        this.transactionTemplate = transactionTemplate;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public List<ChatSummary> listChats(Long userId) {
        return kiChatRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(c -> new ChatSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList();
    }

    public ChatDetail createChat(Long userId) {
        FrontendUserProfile user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden: " + userId));
        KiChat chat = new KiChat();
        chat.setUser(user);
        chat.setTitle("Neuer Chat");
        chat = kiChatRepository.save(chat);
        return toDetail(chat);
    }

    public ChatDetail getChat(Long chatId, Long userId) {
        KiChat chat = kiChatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat nicht gefunden"));
        if (!chat.getUser().getId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Chat");
        }
        return toDetail(chat);
    }

    public void deleteChat(Long chatId, Long userId) {
        KiChat chat = kiChatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat nicht gefunden"));
        if (!chat.getUser().getId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Chat");
        }
        cancelProcessing(chatId);
        kiChatRepository.delete(chat);
    }

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

    /**
     * Sends a message asynchronously. Persists the user message immediately,
     * then processes the KI response in a background thread.
     * The frontend polls getProcessingStatus() for the result.
     */
    public void sendMessageAsync(Long chatId, Long userId, String userMessage, PageContext pageContext) {
        // Persist user message synchronously
        transactionTemplate.executeWithoutResult(status -> {
            KiChat chat = kiChatRepository.findById(chatId)
                    .orElseThrow(() -> new IllegalArgumentException("Chat nicht gefunden"));
            if (!chat.getUser().getId().equals(userId)) {
                throw new SecurityException("Kein Zugriff auf diesen Chat");
            }

            KiChatMessage userMsg = new KiChatMessage();
            userMsg.setChat(chat);
            userMsg.setRole("user");
            userMsg.setContent(userMessage);
            kiChatMessageRepository.save(userMsg);
            if (chat.getMessages() != null) {
                chat.getMessages().add(userMsg);
            }

            // Auto-title on first message
            if (chat.getMessages().size() <= 1 && "Neuer Chat".equals(chat.getTitle())) {
                String autoTitle = userMessage.length() > 60 ? userMessage.substring(0, 60) + "…" : userMessage;
                chat.setTitle(autoTitle);
            }
            chat.setUpdatedAt(LocalDateTime.now());
            kiChatRepository.save(chat);
        });

        // Set processing state
        AtomicBoolean cancelled = new AtomicBoolean(false);
        processingMap.put(chatId, new ProcessingState(cancelled, ProcessingStatus.PROCESSING, null));

        // Submit background KI processing
        executor.submit(() -> {
            try {
                // Build message history in a read transaction
                List<ChatMessage> history = transactionTemplate.execute(status -> {
                    KiChat chat = kiChatRepository.findById(chatId).orElseThrow();
                    return chat.getMessages().stream()
                            .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                            .toList();
                });

                // Call KI (potentially long-running, checks cancellation internally)
                ChatResult result = kiHilfeService.chat(history, pageContext, cancelled);

                // Persist assistant message
                transactionTemplate.executeWithoutResult(status -> {
                    KiChat chat = kiChatRepository.findById(chatId).orElseThrow();
                    KiChatMessage assistantMsg = new KiChatMessage();
                    assistantMsg.setChat(chat);
                    assistantMsg.setRole("assistant");
                    assistantMsg.setContent(result.reply());
                    kiChatMessageRepository.save(assistantMsg);
                    if (chat.getMessages() != null) {
                        chat.getMessages().add(assistantMsg);
                    }
                    chat.setUpdatedAt(LocalDateTime.now());
                    kiChatRepository.save(chat);
                });

                processingMap.put(chatId, new ProcessingState(cancelled, ProcessingStatus.DONE, null));
                log.info("KI-Chat {} async Verarbeitung abgeschlossen", chatId);

            } catch (KiHilfeService.CancelledException e) {
                processingMap.put(chatId, new ProcessingState(cancelled, ProcessingStatus.CANCELLED, null));
                log.info("KI-Chat {} wurde abgebrochen", chatId);
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unbekannter Fehler";
                processingMap.put(chatId, new ProcessingState(cancelled, ProcessingStatus.ERROR, errorMsg));
                log.error("KI-Chat {} async Fehler: {}", chatId, errorMsg);
            }
        });
    }

    /**
     * Returns the current processing status for a chat.
     * Also cleans up completed/error/cancelled entries after returning them.
     */
    public StatusResult getProcessingStatus(Long chatId) {
        ProcessingState state = processingMap.get(chatId);
        if (state == null) {
            return new StatusResult(null, null);
        }
        // Clean up terminal states after returning them
        if (state.status() != ProcessingStatus.PROCESSING) {
            processingMap.remove(chatId);
        }
        return new StatusResult(state.status(), state.error());
    }

    /** Returns true if the given chat is currently processing. */
    public boolean isProcessing(Long chatId) {
        ProcessingState state = processingMap.get(chatId);
        return state != null && state.status() == ProcessingStatus.PROCESSING;
    }

    /** Cancels the currently running KI processing for a chat. */
    public void cancelProcessing(Long chatId) {
        ProcessingState state = processingMap.get(chatId);
        if (state != null && state.status() == ProcessingStatus.PROCESSING) {
            state.cancelled().set(true);
            log.info("KI-Chat {} Abbruch angefordert", chatId);
        }
    }

    private ChatDetail toDetail(KiChat chat) {
        List<MessageDto> msgs = chat.getMessages().stream()
                .map(m -> new MessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
        return new ChatDetail(chat.getId(), chat.getTitle(), chat.getCreatedAt(), chat.getUpdatedAt(), msgs);
    }
}
