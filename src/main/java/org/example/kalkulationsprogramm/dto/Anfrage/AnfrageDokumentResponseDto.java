package org.example.kalkulationsprogramm.dto.Anfrage;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnfrageDokumentResponseDto {
    private Long id;
    private String originalDateiname;
    private String gespeicherterDateiname;
    private String dateityp;
    private String url;
    private String thumbnailUrl;
    private String dokumentGruppe;
    private LocalDate uploadDatum;
    private LocalDate emailVersandDatum;

    private String anrede;
    private String rechnungsnummer;
    private LocalDate rechnungsdatum;
    private LocalDate faelligkeitsdatum;
    private String geschaeftsdokumentart;
    private BigDecimal rechnungsbetrag;
    private boolean bezahlt;
    private String netzwerkPfad;
}
