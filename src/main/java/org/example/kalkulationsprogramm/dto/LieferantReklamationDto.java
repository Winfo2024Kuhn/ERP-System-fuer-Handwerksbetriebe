package org.example.kalkulationsprogramm.dto;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.ReklamationStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LieferantReklamationDto {
    private Long id;
    private Long lieferantId;
    private String lieferantName;

    // Lieferschein Infos
    private Long lieferscheinId;
    private String lieferscheinNummer; // Dokumentnummer aus Geschäftsdaten
    private String lieferscheinDateiname; // Fallback

    private String erstellerName;
    private LocalDateTime erstelltAm;
    private String beschreibung;
    private ReklamationStatus status;

    private List<org.example.kalkulationsprogramm.controller.LieferantenController.LieferantBildDto> bilder;
}
