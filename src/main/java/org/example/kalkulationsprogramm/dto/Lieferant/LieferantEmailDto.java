package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.EmailDirection;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class LieferantEmailDto {
    private Long id;
    private EmailDirection direction;
    private String from;
    private String to;
    private String subject;
    private String bodyHtml;
    private LocalDateTime sentAt;
    private List<LieferantEmailAttachmentDto> attachments;
}
