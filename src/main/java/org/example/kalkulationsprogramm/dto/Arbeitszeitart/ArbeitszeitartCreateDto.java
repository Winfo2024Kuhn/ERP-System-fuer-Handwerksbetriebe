package org.example.kalkulationsprogramm.dto.Arbeitszeitart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArbeitszeitartCreateDto {
    
    @NotBlank(message = "Bezeichnung ist erforderlich")
    private String bezeichnung;
    
    private String beschreibung;
    
    @NotNull(message = "Stundensatz ist erforderlich")
    @Positive(message = "Stundensatz muss positiv sein")
    private BigDecimal stundensatz;
    
    private boolean aktiv = true;
    
    private int sortierung = 0;
}
