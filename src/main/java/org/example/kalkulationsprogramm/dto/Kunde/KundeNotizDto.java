package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KundeNotizDto {
    private Long id;
    private String text;
    private LocalDateTime erstelltAm;
}
