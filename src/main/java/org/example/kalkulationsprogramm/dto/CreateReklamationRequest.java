package org.example.kalkulationsprogramm.dto;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.ReklamationStatus;

@Data
public class CreateReklamationRequest {
    private String beschreibung;
    private Long lieferscheinId; // Optional: ID des verknüpften Lieferscheins
    private ReklamationStatus status; // Optional, default OFFEN
}
