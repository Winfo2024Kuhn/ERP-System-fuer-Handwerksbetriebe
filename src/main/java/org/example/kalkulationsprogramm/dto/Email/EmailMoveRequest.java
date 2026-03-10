package org.example.kalkulationsprogramm.dto.Email;

import lombok.Data;

@Data
public class EmailMoveRequest {
    private String targetType; // "angebot" or "projekt"
    private Long targetId;
}

