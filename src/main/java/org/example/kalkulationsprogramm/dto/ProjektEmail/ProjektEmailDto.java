package org.example.kalkulationsprogramm.dto.ProjektEmail;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.EmailDirection;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ProjektEmailDto {
    private Long id;
    private EmailDirection direction;
    private String from;
    private String to;
    private String subject;
    private LocalDateTime sentAt;
    private String bodyHtml;
    private List<ProjektEmailFileDto> attachments;
    private String benutzer;
    private Long frontendUserId;

    // Felder für E-Mail-Versand
    private String sender;
    private String body;
    private List<String> recipients;
    private List<String> cc;

    // Explizite Zuordnung beim Antworten
    private Long projektId;
    private Long angebotId;
    private Long lieferantId;
}
