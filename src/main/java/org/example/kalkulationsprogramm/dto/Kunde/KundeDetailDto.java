package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;

import java.util.List;

@Getter
@Setter
public class KundeDetailDto {
    private Long id;
    private String kundennummer;
    private String name;
    private String anrede;
    private String ansprechspartner;
    private String strasse;
    private String plz;
    private String ort;
    private String telefon;
    private String mobiltelefon;
    private Integer zahlungsziel;
    private List<String> kundenEmails;
    private KundeStatistikDto statistik;
    private List<KundeAggregierteEmailDto> aggregierteEmails;
    private List<KundeProjektKurzDto> projekte;
    private List<KundeAnfrageKurzDto> anfragen;
    private List<AusgangsGeschaeftsDokumentResponseDto> geschaeftsdokumente;
    private List<KundeKommunikationDto> kommunikation;
    private List<KundeNotizDto> notizen;
    /** Aus Datenschutzgründen gesperrt (Issue #264). */
    private boolean gesperrt;
    private java.time.LocalDateTime gesperrtAm;
    /** Nur bei gesperrten Kunden: Ende der Aufbewahrungsfrist. */
    private java.time.LocalDate aufbewahrenBis;
    /** Nur bei anonymisierten Kunden: Zeitpunkt der Anonymisierung. */
    private java.time.LocalDateTime anonymisiertAm;
    private KundeLoeschPruefungDto loeschPruefung;
}
