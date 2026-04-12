package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.example.kalkulationsprogramm.dto.KiChatDto.ChatDetail;
import org.example.kalkulationsprogramm.dto.KiChatDto.ChatSummary;
import org.example.kalkulationsprogramm.dto.KiChatDto.MessageDto;
import org.example.kalkulationsprogramm.service.KiChatService;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(KiChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class KiChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private KiChatService kiChatService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0);

    private ChatSummary sampleSummary() {
        return new ChatSummary(10L, "Test Chat", NOW, NOW);
    }

    private ChatDetail sampleDetail() {
        return new ChatDetail(10L, "Test Chat", NOW, NOW, List.of(
                new MessageDto(1L, "user", "Hallo", NOW),
                new MessageDto(2L, "assistant", "Hi!", NOW)
        ));
    }

    /* ═══════════════════════════════════════════
       GET /api/ki-chat
       ═══════════════════════════════════════════ */

    @Nested
    class ListChats {

        @Test
        void shouldReturnChatListForValidUser() throws Exception {
            when(kiChatService.listChats(1L)).thenReturn(List.of(sampleSummary()));

            mockMvc.perform(get("/api/ki-chat").param("userId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id", is(10)))
                    .andExpect(jsonPath("$[0].title", is("Test Chat")));
        }

        @Test
        void shouldReturnEmptyListWhenNoChats() throws Exception {
            when(kiChatService.listChats(1L)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/ki-chat").param("userId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void shouldReturn400ForInvalidUserId() throws Exception {
            mockMvc.perform(get("/api/ki-chat").param("userId", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400ForNegativeUserId() throws Exception {
            mockMvc.perform(get("/api/ki-chat").param("userId", "-1"))
                    .andExpect(status().isBadRequest());
        }
    }

    /* ═══════════════════════════════════════════
       POST /api/ki-chat
       ═══════════════════════════════════════════ */

    @Nested
    class CreateChat {

        @Test
        void shouldCreateChatSuccessfully() throws Exception {
            ChatDetail detail = new ChatDetail(10L, "Neuer Chat", NOW, NOW, List.of());
            when(kiChatService.createChat(1L)).thenReturn(detail);

            mockMvc.perform(post("/api/ki-chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(10)))
                    .andExpect(jsonPath("$.title", is("Neuer Chat")));
        }

        @Test
        void shouldReturn400ForInvalidUserId() throws Exception {
            mockMvc.perform(post("/api/ki-chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldReturn400ForNullUserId() throws Exception {
            mockMvc.perform(post("/api/ki-chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldReturn400WhenUserNotFound() throws Exception {
            when(kiChatService.createChat(999L))
                    .thenThrow(new IllegalArgumentException("Benutzer nicht gefunden: 999"));

            mockMvc.perform(post("/api/ki-chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":999}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("Benutzer nicht gefunden: 999")));
        }
    }

    /* ═══════════════════════════════════════════
       GET /api/ki-chat/{chatId}
       ═══════════════════════════════════════════ */

    @Nested
    class GetChat {

        @Test
        void shouldReturnChatWithMessages() throws Exception {
            when(kiChatService.getChat(10L, 1L)).thenReturn(sampleDetail());

            mockMvc.perform(get("/api/ki-chat/10").param("userId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(10)))
                    .andExpect(jsonPath("$.messages", hasSize(2)))
                    .andExpect(jsonPath("$.messages[0].role", is("user")))
                    .andExpect(jsonPath("$.messages[1].role", is("assistant")));
        }

        @Test
        void shouldReturn400ForInvalidUserId() throws Exception {
            mockMvc.perform(get("/api/ki-chat/10").param("userId", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn403WhenNotOwner() throws Exception {
            when(kiChatService.getChat(10L, 2L)).thenThrow(new SecurityException("Kein Zugriff"));

            mockMvc.perform(get("/api/ki-chat/10").param("userId", "2"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("Kein Zugriff")));
        }

        @Test
        void shouldReturn400WhenChatNotFound() throws Exception {
            when(kiChatService.getChat(999L, 1L))
                    .thenThrow(new IllegalArgumentException("Chat nicht gefunden"));

            mockMvc.perform(get("/api/ki-chat/999").param("userId", "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("Chat nicht gefunden")));
        }
    }

    /* ═══════════════════════════════════════════
       DELETE /api/ki-chat/{chatId}
       ═══════════════════════════════════════════ */

    @Nested
    class DeleteChat {

        @Test
        void shouldReturn204OnSuccessfulDelete() throws Exception {
            doNothing().when(kiChatService).deleteChat(10L, 1L);

            mockMvc.perform(delete("/api/ki-chat/10").param("userId", "1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReturn400ForInvalidUserId() throws Exception {
            mockMvc.perform(delete("/api/ki-chat/10").param("userId", "-5"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn403WhenNotOwner() throws Exception {
            doThrow(new SecurityException("Kein Zugriff")).when(kiChatService).deleteChat(10L, 2L);

            mockMvc.perform(delete("/api/ki-chat/10").param("userId", "2"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("Kein Zugriff")));
        }

        @Test
        void shouldReturn400WhenChatNotFound() throws Exception {
            doThrow(new IllegalArgumentException("Chat nicht gefunden")).when(kiChatService).deleteChat(999L, 1L);

            mockMvc.perform(delete("/api/ki-chat/999").param("userId", "1"))
                    .andExpect(status().isBadRequest());
        }
    }

    /* ═══════════════════════════════════════════
       PATCH /api/ki-chat/{chatId}
       ═══════════════════════════════════════════ */

    @Nested
    class RenameChat {

        @Test
        void shouldRenameChatSuccessfully() throws Exception {
            ChatSummary renamed = new ChatSummary(10L, "Neuer Titel", NOW, NOW);
            when(kiChatService.renameChat(10L, 1L, "Neuer Titel")).thenReturn(renamed);

            mockMvc.perform(patch("/api/ki-chat/10")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Neuer Titel\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is("Neuer Titel")));
        }

        @Test
        void shouldReturn400ForBlankTitle() throws Exception {
            mockMvc.perform(patch("/api/ki-chat/10")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldReturn400ForWhitespaceOnlyTitle() throws Exception {
            mockMvc.perform(patch("/api/ki-chat/10")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldReturn400ForNullTitle() throws Exception {
            mockMvc.perform(patch("/api/ki-chat/10")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldReturn400ForInvalidUserId() throws Exception {
            mockMvc.perform(patch("/api/ki-chat/10")
                            .param("userId", "0")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Titel\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn403WhenNotOwner() throws Exception {
            when(kiChatService.renameChat(10L, 2L, "Titel"))
                    .thenThrow(new SecurityException("Kein Zugriff"));

            mockMvc.perform(patch("/api/ki-chat/10")
                            .param("userId", "2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Titel\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    /* ═══════════════════════════════════════════
       POST /api/ki-chat/{chatId}/messages
       ═══════════════════════════════════════════ */

    @Nested
    class SendMessage {

        @Test
        void shouldSendMessageSuccessfully() throws Exception {
            when(kiChatService.sendMessage(eq(10L), eq(1L), eq("Hallo"), isNull()))
                    .thenReturn(sampleDetail());

            mockMvc.perform(post("/api/ki-chat/10/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":1,\"message\":\"Hallo\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(10)))
                    .andExpect(jsonPath("$.messages", hasSize(2)));
        }

        @Test
        void shouldSendMessageWithPageContext() throws Exception {
            when(kiChatService.sendMessage(eq(10L), eq(1L), eq("Frage"), any()))
                    .thenReturn(sampleDetail());

            String body = """
                    {
                      "userId": 1,
                      "message": "Frage",
                      "context": {
                        "route": "/projekte",
                        "pageTitle": "Projekte",
                        "visibleContent": "Inhalt"
                      }
                    }
                    """;

            mockMvc.perform(post("/api/ki-chat/10/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldReturn400ForInvalidUserId() throws Exception {
            mockMvc.perform(post("/api/ki-chat/10/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":0,\"message\":\"Test\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("userId ungültig"));
        }

        @Test
        void shouldReturn400ForBlankMessage() throws Exception {
            mockMvc.perform(post("/api/ki-chat/10/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":1,\"message\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Nachricht darf nicht leer sein"));
        }

        @Test
        void shouldReturn400ForNullMessage() throws Exception {
            mockMvc.perform(post("/api/ki-chat/10/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Nachricht darf nicht leer sein"));
        }

        @Test
        void shouldReturn400ForMessageTooLong() throws Exception {
            String longMsg = "A".repeat(5001);
            String body = "{\"userId\":1,\"message\":\"" + longMsg + "\"}";

            mockMvc.perform(post("/api/ki-chat/10/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Nachricht zu lang (max. 5000 Zeichen)"));
        }

        @Test
        void shouldReturn403WhenNotOwner() throws Exception {
            when(kiChatService.sendMessage(eq(10L), eq(2L), anyString(), any()))
                    .thenThrow(new SecurityException("Kein Zugriff"));

            mockMvc.perform(post("/api/ki-chat/10/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":2,\"message\":\"Test\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("Kein Zugriff")));
        }

        @Test
        void shouldReturn400WhenChatNotFound() throws Exception {
            when(kiChatService.sendMessage(eq(999L), eq(1L), anyString(), any()))
                    .thenThrow(new IllegalArgumentException("Chat nicht gefunden"));

            mockMvc.perform(post("/api/ki-chat/999/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":1,\"message\":\"Test\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn500WhenKiServiceFails() throws Exception {
            when(kiChatService.sendMessage(eq(10L), eq(1L), anyString(), any()))
                    .thenThrow(new IOException("Gemini API down"));

            mockMvc.perform(post("/api/ki-chat/10/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":1,\"message\":\"Test\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("KI-Fehler: Gemini API down"));
        }

        @Test
        void shouldAcceptMessageExactly5000Chars() throws Exception {
            String exactMsg = "A".repeat(5000);
            when(kiChatService.sendMessage(eq(10L), eq(1L), eq(exactMsg), any()))
                    .thenReturn(sampleDetail());

            String body = "{\"userId\":1,\"message\":\"" + exactMsg + "\"}";

            mockMvc.perform(post("/api/ki-chat/10/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }
    }
}
