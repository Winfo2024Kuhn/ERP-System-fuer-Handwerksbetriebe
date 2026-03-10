package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.example.kalkulationsprogramm.domain.EmailDirection;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjektEmailDto {
    private Long id;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String body;
    private LocalDateTime sentAt;
    private List<ProjektEmailAttachmentDto> attachments;
    private EmailDirection direction;
    private Long parentId;
    private List<ProjektEmailDto> replies;
    private List<ProjektOptionDto> possibleProjects;
    private String benutzer;
    private Long frontendUserId;
}
