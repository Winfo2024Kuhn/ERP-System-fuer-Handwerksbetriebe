package org.example.kalkulationsprogramm.dto;

import java.time.LocalDateTime;
import java.util.List;

public class KiChatDto {

    public record ChatSummary(
            Long id,
            String title,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record ChatDetail(
            Long id,
            String title,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<MessageDto> messages
    ) {}

    public record MessageDto(
            Long id,
            String role,
            String content,
            LocalDateTime createdAt
    ) {}

    public record CreateChatRequest(
            Long userId
    ) {}

    public record SendMessageRequest(
            Long userId,
            String message,
            PageContextDto context
    ) {}

    public record PageContextDto(
            String route,
            String pageTitle,
            String visibleContent,
            String errorMessages,
            Double latitude,
            Double longitude
    ) {}

    public record RenameChatRequest(
            String title
    ) {}

    private KiChatDto() {}
}
