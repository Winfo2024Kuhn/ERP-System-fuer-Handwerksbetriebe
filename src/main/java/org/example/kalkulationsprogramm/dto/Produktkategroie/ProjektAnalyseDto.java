package org.example.kalkulationsprogramm.dto.Produktkategroie;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProjektAnalyseDto {
    private Long id;
    private String projektname;
    private String bildUrl;
    private String auftragsnummer;
    private String kunde;
    private double masseinheit;
    private double zeitGesamt;
    private List<ProjektArbeitsgangAnalyseDto> arbeitsgaenge;
}
