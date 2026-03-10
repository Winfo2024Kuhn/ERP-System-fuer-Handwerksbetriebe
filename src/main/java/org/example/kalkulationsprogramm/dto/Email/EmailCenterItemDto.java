package org.example.kalkulationsprogramm.dto.Email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.kalkulationsprogramm.domain.EmailDirection;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailCenterItemDto {
    // "projekt" or "angebot"
    private String type;
    private Long id;
    // ID des zugehörigen Containers (Projekt- oder Angebots-ID), falls vorhanden
    private Long containerId;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String body;
    private LocalDateTime sentAt;
    private List<EmailCenterAttachmentDto> attachments;
    private EmailDirection direction;
    private Long parentId;
    private List<EmailCenterItemDto> replies;

    // Backwards-compatible constructor without containerId to satisfy existing tests/usages
    // Order matches the previous Lombok-generated constructor
    public EmailCenterItemDto(String type,
                              Long id,
                              String sender,
                              java.util.List<String> recipients,
                              String subject,
                              String body,
                              java.time.LocalDateTime sentAt,
                              java.util.List<EmailCenterAttachmentDto> attachments,
                              org.example.kalkulationsprogramm.domain.EmailDirection direction,
                              Long parentId,
                              java.util.List<EmailCenterItemDto> replies) {
        this.type = type;
        this.id = id;
        this.containerId = null;
        this.sender = sender;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
        this.sentAt = sentAt;
        this.attachments = attachments;
        this.direction = direction;
        this.parentId = parentId;
        this.replies = replies;
    }
}

