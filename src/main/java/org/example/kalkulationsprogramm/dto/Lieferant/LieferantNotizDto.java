package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LieferantNotizDto {
    private Long id;
    private String text;
    private LocalDateTime erstelltAm;
}
