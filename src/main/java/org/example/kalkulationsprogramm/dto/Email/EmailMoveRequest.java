package org.example.kalkulationsprogramm.dto.Email;

import lombok.Data;

@Data
public class EmailMoveRequest {
    private String targetType; // "anfrage" or "projekt"
    private Long targetId;
}

