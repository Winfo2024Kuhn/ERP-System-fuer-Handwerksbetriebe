package org.example.kalkulationsprogramm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDto {
    private String id; // composite id e.g. "LIEFERANT_1"
    private String name;
    private String email;
    private String type; // LIEFERANT, KUNDE, PROJEKT, ANFRAGE
    private String context; // e.g. Bauvorhaben name
}
