package org.example.kalkulationsprogramm.dto.Email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.kalkulationsprogramm.dto.Angebot.AngebotOptionDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektOptionDto;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailMoveOptionsDto {
    private List<AngebotOptionDto> angebote;
    private List<ProjektOptionDto> projekte;
}

