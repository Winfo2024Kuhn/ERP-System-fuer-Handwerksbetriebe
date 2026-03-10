package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;

@Data
public class MietobjektDto {
    private Long id;
    private String name;
    private String strasse;
    private String plz;
    private String ort;
}
