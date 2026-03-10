package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ArtikelSearchResponseDto {
    private List<ArtikelResponseDto> artikel;
    private long gesamt;
    private int seite;
    private int seitenGroesse;
}

