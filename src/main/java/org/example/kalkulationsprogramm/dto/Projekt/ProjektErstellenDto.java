package org.example.kalkulationsprogramm.dto.Projekt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenErfassenDto;
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieErfassenDto;
import org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class ProjektErstellenDto {
    // Attribute
    private String bauvorhaben;
    private String kunde;
    private String kundennummer;
    private Long kundenId;
    private String strasse;
    private String plz;
    private String ort;
    private List<String> kundenEmails;
    private String kurzbeschreibung;
    private String auftragsnummer;
    private LocalDate anlegedatum;
    private LocalDate abschlussdatum;
    private BigDecimal bruttoPreis;
    private List<MaterialkostenErfassenDto> materialkosten;
    private boolean bezahlt;
    private boolean abgeschlossen;
    private String projektArt;

    // Fremdschlüssel
    private List<Long> anfrageIds;
    private List<Long> angebotIds;
    private List<ZeitErfassenDto> zeitPositionen;
    private List<ProjektProduktkategorieErfassenDto> produktkategorien;

}
