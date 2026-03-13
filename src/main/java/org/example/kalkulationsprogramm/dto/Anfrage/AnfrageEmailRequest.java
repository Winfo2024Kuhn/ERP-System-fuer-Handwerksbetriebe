package org.example.kalkulationsprogramm.dto.Anfrage;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnfrageEmailRequest {
    private String recipient;
    private String cc;
    private String anrede;
    private String benutzer;
    private String position;
    private String bauvorhaben;
}
