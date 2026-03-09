package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.EmailDirection;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class LieferantKommunikationDto {
    private Long id;
    private Long referenzId;
    private String referenzTyp;
    private String referenzName;
    private String subject;
    private String absender;
    private String empfaenger;
    private LocalDateTime zeitpunkt;
    private EmailDirection direction;
    private String snippet;
    private String body;
    private List<LieferantAttachmentViewDto> attachments;
}
