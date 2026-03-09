package org.example.kalkulationsprogramm.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OpenExternalResponse {
    private final String type;
    private final String protocolUrl;
    private final String token;
}

