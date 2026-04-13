package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitResponseDto;
import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenResponseDto;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelInProjektResponseDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeResponseDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class ProjektResponseDto {
    private Long id;
    private String bauvorhaben;
    private String strasse;
    private String plz;
    private String ort;
    private String kunde;
    private KundeResponseDto kundeDto;
    private Long kundenId;
    private String kundennummer;
    private List<String> kundenEmails;
    private String kurzbeschreibung;
    private String auftragsnummer;
    private LocalDate anlegedatum;
    private LocalDate abschlussdatum;

    private String bildUrl;
    private BigDecimal bruttoPreis;
    private List<MaterialkostenResponseDto> materialkosten;
    private List<ArtikelInProjektResponseDto> artikel;
    private List<MaterialKilogrammDto> kilogrammProMaterial;
    private BigDecimal gesamtKilogramm;
    private boolean bezahlt;
    private boolean abgeschlossen;
    private String projektArt;
    private String excKlasse;
    private boolean produktiv;
    private List<ProjektProduktkategorieResponseDto> produktkategorien;
    private List<ZeitResponseDto> zeiten;
    private List<AnfrageResponseDto> anfragen;
    private List<ProjektEmailDto> emails;
}
