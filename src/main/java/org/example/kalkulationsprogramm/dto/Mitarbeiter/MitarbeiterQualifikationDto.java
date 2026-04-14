package org.example.kalkulationsprogramm.dto.Mitarbeiter;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MitarbeiterQualifikationDto {
    private Long id;
    private String bezeichnung;
    private String beschreibung;
    private LocalDate datum;
    // Verlinktes Dokument (befüllt wenn beim Anlegen eine Datei hochgeladen wurde)
    private Long dokumentId;
    private String dokumentAnzeigename;    // originalDateiname
    private String dokumentGespeicherterName; // gespeicherterDateiname (für URL-Bildung)
    private String dokumentDateityp;
    private LocalDate dokumentUploadDatum;
    private LocalDateTime erstelltAm;
}
