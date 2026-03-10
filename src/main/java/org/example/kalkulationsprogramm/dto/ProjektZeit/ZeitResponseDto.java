package org.example.kalkulationsprogramm.dto.ProjektZeit;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;

@Getter
@Setter
public class ZeitResponseDto
{
    private Long id;
    private String arbeitsgangBeschreibung;
    private ProduktkategorieResponseDto produktkategorie;
    private java.math.BigDecimal anzahlInStunden;
    private java.math.BigDecimal stundensatz;
    private String mitarbeiterVorname;
    private String mitarbeiterNachname;
}
