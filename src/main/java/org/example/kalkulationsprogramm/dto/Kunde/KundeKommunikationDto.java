package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.EmailDirection;

import java.time.LocalDateTime;

@Getter
@Setter
public class KundeKommunikationDto {
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
    private java.util.List<KundeEmailAttachmentDto> attachments;
}
