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
    private List<KundeProjektKurzDto> projekte;
    private List<KundeAnfrageKurzDto> anfragen;
    private List<AusgangsGeschaeftsDokumentResponseDto> geschaeftsdokumente;
    /**
     * Zahl der E-Mail-Verläufe (Mails seiner Projekte und Anfragen) für die Reiter-Beschriftung; die
     * Mails lädt der Reiter seitenweise über {@code GET /api/emails/reiter/kunde/{id}}.
     */
    private long emailAnzahl;
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
