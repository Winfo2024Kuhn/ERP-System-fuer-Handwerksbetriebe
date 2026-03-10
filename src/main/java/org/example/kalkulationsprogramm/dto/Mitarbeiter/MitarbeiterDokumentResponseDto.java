package org.example.kalkulationsprogramm.dto.Mitarbeiter;

import lombok.Data;
import java.time.LocalDate;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;

@Data
public class MitarbeiterDokumentResponseDto {
    private Long id;
    private String originalDateiname;
    private String dateityp;
    private Long dateigroesse;
    private LocalDate uploadDatum;
    private DokumentGruppe dokumentGruppe;
    private String url;
}
