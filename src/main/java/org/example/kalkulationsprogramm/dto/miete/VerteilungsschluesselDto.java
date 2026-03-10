package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselTyp;

import java.util.ArrayList;
import java.util.List;

@Data
public class VerteilungsschluesselDto {
    private Long id;
    private Long mietobjektId;
    private String name;
    private String beschreibung;
    private VerteilungsschluesselTyp typ;
    private List<VerteilungsschluesselEintragDto> eintraege = new ArrayList<>();
}
