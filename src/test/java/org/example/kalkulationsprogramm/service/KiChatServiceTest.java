package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.KiChat;
import org.example.kalkulationsprogramm.domain.KiChatMessage;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatDetail;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatSummary;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KiChatMessageRepository;
import org.example.kalkulationsprogramm.repository.KiChatRepository;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatResult;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class KiChatServiceTest {

    @Mock private KiChatRepository kiChatRepository;
    @Mock private KiChatMessageRepository kiChatMessageRepository;
    @Mock private FrontendUserProfileRepository userRepository;
    @Mock private KiHilfeService kiHilfeService;
    @Mock private TransactionTemplate transactionTemplate;

    private KiChatService service;
    private FrontendUserProfile testUser;
    private KiChat testChat;

    @BeforeEach
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void setUp() {
        service = new KiChatService(
                kiChatRepository,
                kiChatMessageRepository,
                userRepository,
                kiHilfeService,
                transactionTemplate);

        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().when(kiChatMessageRepository.save(any(KiChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(kiChatRepository.save(any(KiChat.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        testUser = new FrontendUserProfile();
        testUser.setId(1L);

        testChat = new KiChat();
        testChat.setId(10L);
        testChat.setUser(testUser);
        testChat.setTitle("Neuer Chat");
        testChat.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        testChat.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        testChat.setMessages(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Nested
    class ListChats {

        @Test
        void shouldReturnEmptyListWhenNoChats() {
            when(kiChatRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Collections.emptyList());

            List<ChatSummary> result = service.listChats(1L);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldMapFieldsCorrectly() {
            when(kiChatRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(testChat));

            List<ChatSummary> result = service.listChats(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(10L);
            assertThat(result.get(0).title()).isEqualTo("Neuer Chat");
        }
    }

    @Nested
    class CreateChat {

        @Test
        void shouldCreateChatForExistingUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(invocation -> {
                KiChat saved = invocation.getArgument(0);
                saved.setId(10L);
                return saved;
            });

            ChatDetail result = service.createChat(1L);

            assertThat(result.title()).isEqualTo("Neuer Chat");
            assertThat(result.messages()).isEmpty();
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createChat(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Benutzer nicht gefunden");
        }
    }

    @Nested
    class GetChat {

        @Test
        void shouldReturnChatForOwner() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            ChatDetail result = service.getChat(10L, 1L);

            assertThat(result.id()).isEqualTo(10L);
        }

        @Test
        void shouldThrowWhenUserDoesNotOwnChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            assertThatThrownBy(() -> service.getChat(10L, 2L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Kein Zugriff");
        }
    }

    @Nested
    class DeleteChat {

        @Test
        void shouldDeleteOwnChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            service.deleteChat(10L, 1L);

            verify(kiChatRepository).delete(testChat);
        }
    }

    @Nested
    class RenameChat {

        @Test
        void shouldRenameOwnChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            ChatSummary result = service.renameChat(10L, 1L, "Mein Projekt-Chat");

            assertThat(result.title()).isEqualTo("Mein Projekt-Chat");
        }

        @Test
        void shouldTruncateTitleOver300Chars() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            ChatSummary result = service.renameChat(10L, 1L, "A".repeat(400));

            assertThat(result.title()).hasSize(300);
        }
    }

    @Nested
    class SendMessageAsync {

        @Test
        void shouldPersistMessagesAndCompleteAsyncProcessing() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), isNull(), any(AtomicBoolean.class)))
                    .thenReturn(new ChatResult("KI-Antwort", List.of()));

            service.sendMessageAsync(10L, 1L, "Kurze Frage", null);

            verify(kiHilfeService, timeout(1000)).chat(anyList(), isNull(), any(AtomicBoolean.class));
            verify(kiChatMessageRepository, timeout(1000).times(2)).save(any(KiChatMessage.class));

            KiChatService.StatusResult status = awaitTerminalStatus(10L);
            assertThat(status.status()).isEqualTo(KiChatService.ProcessingStatus.DONE);
            assertThat(testChat.getTitle()).isEqualTo("Kurze Frage");
            assertThat(testChat.getMessages()).hasSize(2);
            assertThat(service.getProcessingStatus(10L).status()).isNull();
        }

        @Test
        void shouldTruncateAutoTitleOver60Chars() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), isNull(), any(AtomicBoolean.class)))
                    .thenReturn(new ChatResult("Antwort", List.of()));

            service.sendMessageAsync(10L, 1L, "A".repeat(100), null);

            awaitTerminalStatus(10L);
            assertThat(testChat.getTitle()).hasSize(61);
            assertThat(testChat.getTitle()).endsWith("…");
        }

        @Test
        void shouldKeepCustomTitleAfterAsyncSend() throws Exception {
            testChat.setTitle("Mein Chat");
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), isNull(), any(AtomicBoolean.class)))
                    .thenReturn(new ChatResult("Antwort", List.of()));

            service.sendMessageAsync(10L, 1L, "Neue Frage", null);

            awaitTerminalStatus(10L);
            assertThat(testChat.getTitle()).isEqualTo("Mein Chat");
        }

        @Test
        void shouldPassPageContextToKiHilfe() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any(PageContext.class), any(AtomicBoolean.class)))
                    .thenReturn(new ChatResult("Antwort", List.of()));

            PageContext context = new PageContext("/projekte", "Projekte", "Inhalt", null, null, null);
            service.sendMessageAsync(10L, 1L, "Frage", context);

            verify(kiHilfeService, timeout(1000)).chat(anyList(), eq(context), any(AtomicBoolean.class));
        }

        @Test
        void shouldMarkStatusCancelledWhenCancellationIsRequested() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), isNull(), any(AtomicBoolean.class)))
                    .thenAnswer(invocation -> {
                        AtomicBoolean cancelled = invocation.getArgument(2);
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                        while (!cancelled.get() && System.nanoTime() < deadline) {
                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                        }
                        throw new KiHilfeService.CancelledException();
                    });

            service.sendMessageAsync(10L, 1L, "Abbruch bitte", null);
            verify(kiHilfeService, timeout(1000)).chat(anyList(), isNull(), any(AtomicBoolean.class));

            service.cancelProcessing(10L);

            KiChatService.StatusResult status = awaitTerminalStatus(10L);
            assertThat(status.status()).isEqualTo(KiChatService.ProcessingStatus.CANCELLED);
        }

        @Test
        void shouldExposeErrorStatusWhenKiFails() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), isNull(), any(AtomicBoolean.class)))
                    .thenThrow(new IOException("Gemini API nicht erreichbar"));

            service.sendMessageAsync(10L, 1L, "Frage", null);

            KiChatService.StatusResult status = awaitTerminalStatus(10L);
            assertThat(status.status()).isEqualTo(KiChatService.ProcessingStatus.ERROR);
            assertThat(status.error()).contains("Gemini API nicht erreichbar");
        }

        @Test
        void shouldThrowWhenSendingToOtherUsersChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            assertThatThrownBy(() -> service.sendMessageAsync(10L, 2L, "Hack attempt", null))
                    .isInstanceOf(SecurityException.class);

            verifyNoInteractions(kiHilfeService);
        }

        @Test
        void shouldBuildHistoryFromExistingMessages() throws Exception {
            KiChatMessage existing = new KiChatMessage();
            existing.setChat(testChat);
            existing.setRole("user");
            existing.setContent("Vorherige Frage");
            testChat.getMessages().add(existing);

            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), isNull(), any(AtomicBoolean.class)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        List<KiHilfeService.ChatMessage> messages = (List<KiHilfeService.ChatMessage>) invocation.getArgument(0);
                        assertThat(messages).hasSize(2);
                        assertThat(messages.get(0).text()).isEqualTo("Vorherige Frage");
                        assertThat(messages.get(1).text()).isEqualTo("Neue Frage");
                        return new ChatResult("Antwort", List.of());
                    });

            service.sendMessageAsync(10L, 1L, "Neue Frage", null);

            verify(kiHilfeService, timeout(1000)).chat(anyList(), isNull(), any(AtomicBoolean.class));
        }
    }

    private KiChatService.StatusResult awaitTerminalStatus(Long chatId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            KiChatService.StatusResult status = service.getProcessingStatus(chatId);
            if (status.status() != null && status.status() != KiChatService.ProcessingStatus.PROCESSING) {
                return status;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        throw new AssertionError("Kein terminaler Status fuer Chat " + chatId + " innerhalb des Zeitlimits");
    }
}
