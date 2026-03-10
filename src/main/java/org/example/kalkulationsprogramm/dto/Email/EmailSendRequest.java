package org.example.kalkulationsprogramm.dto.Email;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailSendRequest {
    private Long dokumentId;
    private String recipient;
    private String cc;
    private String bauvorhaben;
    private String fromAddress;
    private String subject;
    private String htmlBody;
    private String benutzer;
    private Long frontendUserId;
}
