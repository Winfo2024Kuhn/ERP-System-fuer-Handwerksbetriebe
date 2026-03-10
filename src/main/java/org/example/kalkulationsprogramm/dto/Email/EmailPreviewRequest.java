package org.example.kalkulationsprogramm.dto.Email;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailPreviewRequest {
    private Long dokumentId;
    private String anrede;
    private String benutzer;
    private String position;
    private String bauvorhaben;
    private Long frontendUserId;
}
