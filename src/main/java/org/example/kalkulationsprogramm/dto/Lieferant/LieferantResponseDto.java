package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LieferantResponseDto {
    private Long id;
    private String lieferantenname;
    private List<String> kundenEmails;
}
