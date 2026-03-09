package org.example.kalkulationsprogramm.dto.Bestellung;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BestellungEmailSendRequest {
    private Long lieferantId;
    private Long projektId;
    private String recipient;
    private String cc;
    private String fromAddress;
    private String subject;
    private String htmlBody;
    private String benutzer;
    private Long frontendUserId;
}
