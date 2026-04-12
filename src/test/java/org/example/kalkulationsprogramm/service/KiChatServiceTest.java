package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.KiChat;
import org.example.kalkulationsprogramm.domain.KiChatMessage;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatDetail;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatSummary;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KiChatRepository;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatResult;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KiChatServiceTest {

    @Mock private KiChatRepository kiChatRepository;
    @Mock private FrontendUserProfileRepository userRepository;
    @Mock private KiHilfeService kiHilfeService;

    @InjectMocks private KiChatService service;

    private FrontendUserProfile testUser;
    private KiChat testChat;

    @BeforeEach
    void setUp() {
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

    /* ═══════════════════════════════════════════
       listChats
       ═══════════════════════════════════════════ */

    @Nested
    class ListChats {

        @Test
        void shouldReturnEmptyListWhenNoChats() {
            when(kiChatRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Collections.emptyList());

            List<ChatSummary> result = service.listChats(1L);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnChatsOrderedByUpdatedAt() {
            KiChat chat2 = new KiChat();
            chat2.setId(11L);
            chat2.setUser(testUser);
            chat2.setTitle("Zweiter Chat");
            chat2.setCreatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
            chat2.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
            chat2.setMessages(new ArrayList<>());

            when(kiChatRepository.findByUserIdOrderByUpdatedAtDesc(1L))
                    .thenReturn(List.of(chat2, testChat));

            List<ChatSummary> result = service.listChats(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(11L);
            assertThat(result.get(0).title()).isEqualTo("Zweiter Chat");
            assertThat(result.get(1).id()).isEqualTo(10L);
        }

        @Test
        void shouldMapFieldsCorrectly() {
            when(kiChatRepository.findByUserIdOrderByUpdatedAtDesc(1L))
                    .thenReturn(List.of(testChat));

            List<ChatSummary> result = service.listChats(1L);

            assertThat(result).hasSize(1);
            ChatSummary summary = result.get(0);
            assertThat(summary.id()).isEqualTo(10L);
            assertThat(summary.title()).isEqualTo("Neuer Chat");
            assertThat(summary.createdAt()).isEqualTo(testChat.getCreatedAt());
            assertThat(summary.updatedAt()).isEqualTo(testChat.getUpdatedAt());
        }
    }

    /* ═══════════════════════════════════════════
       createChat
       ═══════════════════════════════════════════ */

    @Nested
    class CreateChat {

        @Test
        void shouldCreateChatForExistingUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> {
                KiChat saved = inv.getArgument(0);
                saved.setId(10L);
                return saved;
            });

            ChatDetail result = service.createChat(1L);

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("Neuer Chat");
            assertThat(result.messages()).isEmpty();
            verify(kiChatRepository).save(any(KiChat.class));
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createChat(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Benutzer nicht gefunden");

            verify(kiChatRepository, never()).save(any());
        }
    }

    /* ═══════════════════════════════════════════
       getChat
       ═══════════════════════════════════════════ */

    @Nested
    class GetChat {

        @Test
        void shouldReturnChatForOwner() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            ChatDetail result = service.getChat(10L, 1L);

            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.title()).isEqualTo("Neuer Chat");
        }

        @Test
        void shouldReturnChatWithMessages() {
            KiChatMessage msg = new KiChatMessage();
            msg.setId(100L);
            msg.setChat(testChat);
            msg.setRole("user");
            msg.setContent("Hallo");
            msg.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 5));
            testChat.getMessages().add(msg);

            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            ChatDetail result = service.getChat(10L, 1L);

            assertThat(result.messages()).hasSize(1);
            assertThat(result.messages().get(0).role()).isEqualTo("user");
            assertThat(result.messages().get(0).content()).isEqualTo("Hallo");
        }

        @Test
        void shouldThrowWhenChatNotFound() {
            when(kiChatRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getChat(999L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chat nicht gefunden");
        }

        @Test
        void shouldThrowWhenUserDoesNotOwnChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            assertThatThrownBy(() -> service.getChat(10L, 2L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Kein Zugriff");
        }
    }

    /* ═══════════════════════════════════════════
       deleteChat
       ═══════════════════════════════════════════ */

    @Nested
    class DeleteChat {

        @Test
        void shouldDeleteOwnChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            service.deleteChat(10L, 1L);

            verify(kiChatRepository).delete(testChat);
        }

        @Test
        void shouldThrowWhenDeletingOtherUsersChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            assertThatThrownBy(() -> service.deleteChat(10L, 2L))
                    .isInstanceOf(SecurityException.class);

            verify(kiChatRepository, never()).delete(any());
        }

        @Test
        void shouldThrowWhenChatNotFoundOnDelete() {
            when(kiChatRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteChat(999L, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* ═══════════════════════════════════════════
       renameChat
       ═══════════════════════════════════════════ */

    @Nested
    class RenameChat {

        @Test
        void shouldRenameOwnChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            ChatSummary result = service.renameChat(10L, 1L, "Mein Projekt-Chat");

            assertThat(result.title()).isEqualTo("Mein Projekt-Chat");
            verify(kiChatRepository).save(any(KiChat.class));
        }

        @Test
        void shouldTruncateTitleOver300Chars() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            String longTitle = "A".repeat(400);
            ChatSummary result = service.renameChat(10L, 1L, longTitle);

            assertThat(result.title()).hasSize(300);
        }

        @Test
        void shouldThrowWhenRenamingOtherUsersChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            assertThatThrownBy(() -> service.renameChat(10L, 2L, "Neuer Titel"))
                    .isInstanceOf(SecurityException.class);

            verify(kiChatRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenChatNotFoundOnRename() {
            when(kiChatRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.renameChat(999L, 1L, "Titel"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* ═══════════════════════════════════════════
       sendMessage
       ═══════════════════════════════════════════ */

    @Nested
    class SendMessage {

        @Test
        void shouldSendMessageAndPersistBothMessages() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any()))
                    .thenReturn(new ChatResult("KI-Antwort", List.of()));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            ChatDetail result = service.sendMessage(10L, 1L, "Wie erstelle ich ein Angebot?", null);

            assertThat(result.messages()).hasSize(2);
            assertThat(result.messages().get(0).role()).isEqualTo("user");
            assertThat(result.messages().get(0).content()).isEqualTo("Wie erstelle ich ein Angebot?");
            assertThat(result.messages().get(1).role()).isEqualTo("assistant");
            assertThat(result.messages().get(1).content()).isEqualTo("KI-Antwort");
        }

        @Test
        void shouldAutoTitleFromFirstMessage() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any()))
                    .thenReturn(new ChatResult("Antwort", List.of()));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            ChatDetail result = service.sendMessage(10L, 1L, "Kurze Frage", null);

            assertThat(result.title()).isEqualTo("Kurze Frage");
        }

        @Test
        void shouldTruncateAutoTitleOver60Chars() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any()))
                    .thenReturn(new ChatResult("Antwort", List.of()));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            String longMessage = "A".repeat(100);
            ChatDetail result = service.sendMessage(10L, 1L, longMessage, null);

            assertThat(result.title()).hasSize(61); // 60 chars + "…"
            assertThat(result.title()).endsWith("…");
        }

        @Test
        void shouldNotAutoTitleIfAlreadyRenamed() throws Exception {
            testChat.setTitle("Mein Chat");
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any()))
                    .thenReturn(new ChatResult("Antwort", List.of()));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            ChatDetail result = service.sendMessage(10L, 1L, "Neue Frage", null);

            assertThat(result.title()).isEqualTo("Mein Chat");
        }

        @Test
        void shouldNotAutoTitleAfterSecondMessage() throws Exception {
            // Pre-populate with existing messages (first exchange already happened)
            KiChatMessage existing1 = new KiChatMessage();
            existing1.setChat(testChat);
            existing1.setRole("user");
            existing1.setContent("Erste Frage");
            KiChatMessage existing2 = new KiChatMessage();
            existing2.setChat(testChat);
            existing2.setRole("assistant");
            existing2.setContent("Erste Antwort");
            testChat.getMessages().addAll(List.of(existing1, existing2));

            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any()))
                    .thenReturn(new ChatResult("Zweite Antwort", List.of()));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            ChatDetail result = service.sendMessage(10L, 1L, "Zweite Frage", null);

            // Title should NOT be overwritten - there are now 4 messages (>2)
            assertThat(result.title()).isEqualTo("Neuer Chat");
        }

        @Test
        void shouldPassPageContextToKiHilfe() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any()))
                    .thenReturn(new ChatResult("Antwort", List.of()));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            PageContext ctx = new PageContext("/projekte", "Projekte", "Inhalt", null, null, null);
            service.sendMessage(10L, 1L, "Frage", ctx);

            verify(kiHilfeService).chat(anyList(), any(PageContext.class));
        }

        @Test
        void shouldSendWithNullPageContext() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any()))
                    .thenReturn(new ChatResult("Antwort", List.of()));
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            ChatDetail result = service.sendMessage(10L, 1L, "Frage ohne Kontext", null);

            assertThat(result).isNotNull();
        }

        @Test
        void shouldThrowWhenSendingToOtherUsersChat() {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));

            assertThatThrownBy(() -> service.sendMessage(10L, 2L, "Hack attempt", null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void shouldThrowWhenChatNotFoundOnSend() {
            when(kiChatRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendMessage(999L, 1L, "Frage", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldPropagateKiHilfeException() throws Exception {
            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any()))
                    .thenThrow(new IOException("Gemini API nicht erreichbar"));

            assertThatThrownBy(() -> service.sendMessage(10L, 1L, "Frage", null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Gemini API");
        }

        @Test
        void shouldBuildHistoryFromExistingMessages() throws Exception {
            KiChatMessage existing = new KiChatMessage();
            existing.setChat(testChat);
            existing.setRole("user");
            existing.setContent("Vorherige Frage");
            testChat.getMessages().add(existing);

            when(kiChatRepository.findById(10L)).thenReturn(Optional.of(testChat));
            when(kiHilfeService.chat(anyList(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        List<KiHilfeService.ChatMessage> msgs = (List<KiHilfeService.ChatMessage>) inv.getArgument(0);
                        // Should contain the existing message + the new user message
                        assertThat(msgs).hasSize(2);
                        assertThat(msgs.get(0).text()).isEqualTo("Vorherige Frage");
                        assertThat(msgs.get(1).text()).isEqualTo("Neue Frage");
                        return new ChatResult("Antwort", List.of());
                    });
            when(kiChatRepository.save(any(KiChat.class))).thenAnswer(inv -> inv.getArgument(0));

            service.sendMessage(10L, 1L, "Neue Frage", null);

            verify(kiHilfeService).chat(anyList(), any());
        }
    }
}
