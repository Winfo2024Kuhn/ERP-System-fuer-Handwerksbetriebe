package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProjektSearchResponseDto {
    private List<ProjektResponseDto> projekte;
    private long gesamt;
    private int seite;
    private int seitenGroesse;
}
