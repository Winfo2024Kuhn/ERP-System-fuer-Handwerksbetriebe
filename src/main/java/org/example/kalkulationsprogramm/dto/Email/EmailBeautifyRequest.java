package org.example.kalkulationsprogramm.dto.Email;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailBeautifyRequest {
    private String body;
    // Optional: ursprünglicher zitierter Text als Kontext für die KI
    private String context;
}
