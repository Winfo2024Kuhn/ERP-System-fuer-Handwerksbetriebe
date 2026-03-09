package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;

@Data
public class KostenstelleDto {
    private Long id;
    private Long mietobjektId;
    private String name;
    private String beschreibung;
    private boolean umlagefaehig = true;
    private Long standardSchluesselId;
}
