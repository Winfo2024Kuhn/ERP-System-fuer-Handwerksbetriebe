package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsart;

@Data
public class VerbrauchsgegenstandDto {
    private Long id;
    private Long raumId;
    private String name;
    private String seriennummer;
    private Verbrauchsart verbrauchsart;
    private String einheit;
    private boolean aktiv;
}
