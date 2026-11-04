package org.example.kalkulationsprogramm.dto.Anfrage;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class AnfrageResponseDto {
    private Long id;
    private Long kundenId;
    private String kundenName;
    private String bauvorhaben;
    private String kundennummer;
    private String anfragesnummer;
    private BigDecimal betrag;
    private List<String> kundenEmails;
    private LocalDate emailVersandDatum;
    private Long projektId;
    private LocalDate anlegedatum;
    private String bildUrl;
    private String projektStrasse;
    private String projektPlz;
    private String projektOrt;
    private String kurzbeschreibung;
    private boolean abgeschlossen;
    private java.time.LocalDateTime createdAt;
    /**
     * Kam über das Anfrage-Formular der Webseite herein. Nur in der Seitenliste
     * befüllt, sonst {@code null} (= nicht ermittelt).
     */
    private Boolean ausWebseite;
    /**
     * Zahl der E-Mail-Verläufe für die Reiter-Beschriftung „E-Mails (140)“. Nur in der Detailantwort
     * ({@code GET /api/anfragen/{id}}) befüllt, sonst {@code null}; die Mails lädt der Reiter seitenweise
     * über {@code GET /api/emails/reiter/anfrage/{id}}.
     */
    private Long emailAnzahl;
    // Erweiterte Kundendaten
    private String kundenStrasse;
    private String kundenPlz;
    private String kundenOrt;
    private String kundenTelefon;
    private String kundenMobiltelefon;
    private String kundenAnsprechpartner;
    private String kundenAnrede;
}
