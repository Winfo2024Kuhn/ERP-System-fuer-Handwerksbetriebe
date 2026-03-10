package org.example.kalkulationsprogramm.dto.Schnittbilder;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchnittbildResponseDto {
    private Long id;
    private String bildUrlSchnittbild;
    private String form;
    private Integer kategorieId;
}

