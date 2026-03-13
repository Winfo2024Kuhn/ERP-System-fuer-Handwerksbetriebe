package org.example.kalkulationsprogramm.dto.Email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageOptionDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektOptionDto;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailMoveOptionsDto {
    private List<AnfrageOptionDto> anfragen;
    private List<ProjektOptionDto> projekte;
}

