package org.example.kalkulationsprogramm.dto.AnfrageEmail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.kalkulationsprogramm.domain.EmailDirection;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnfrageEmailDto {
    private Long id;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String body;
    private LocalDateTime sentAt;
    private List<AnfrageEmailAttachmentDto> attachments;
    private EmailDirection direction;
    private Long parentId;
    private List<AnfrageEmailDto> replies;
    private String benutzer;
    private Long frontendUserId;
}
