package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Token-basierte digitale Freigabe eines Geschäftsdokuments durch den Kunden.
 *
 * Snapshot-Pattern: Beim Versand werden die anzeigerelevanten Felder aus dem Quelldokument
 * (Anfrage- oder ProjektGeschaeftsdokument) hierher kopiert. Damit bleibt die Freigabe
 * unabhängig nachvollziehbar, auch wenn das Original später bearbeitet/gelöscht wird.
 *
 * Der HashOriginal ist ein SHA-256-Fingerabdruck über die Geschäftsdaten + serverseitiges
 * Salt; HashAcceptance kommt beim Annehmen dazu und enthält IP, Zeitstempel und E-Mail.
 */
@Entity
@Table(name = "dokument_freigabe")
@Getter
@Setter
public class DokumentFreigabe
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "quell_typ", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private FreigabeQuellTyp quellTyp;

    @Column(name = "quell_dokument_id", nullable = false)
    private Long quellDokumentId;

    @Column(name = "dokument_nummer", nullable = false, length = 100)
    private String dokumentNummer;

    @Column(name = "dokument_art", nullable = false, length = 50)
    private String dokumentArt;

    @Column(name = "dokument_betrag", precision = 12, scale = 2)
    private BigDecimal dokumentBetrag;

    @Column(name = "dokument_datei", length = 255)
    private String dokumentDatei;

    @Column(name = "bauvorhaben", length = 500)
    private String bauvorhaben;

    @Column(name = "kunde_name", length = 255)
    private String kundeName;

    @Column(name = "kunde_email", length = 255)
    private String kundeEmail;

    @Column(name = "erstellt_am", nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime erstelltAm = LocalDateTime.now();

    @Column(name = "ablauf_datum", nullable = false)
    private LocalDateTime ablaufDatum;

    @Column(name = "hash_original", nullable = false, length = 128)
    private String hashOriginal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private FreigabeStatus status = FreigabeStatus.PENDING;

    @Column(name = "akzeptiert_am")
    private LocalDateTime akzeptiertAm;

    @Column(name = "akzeptiert_ip", length = 45)
    private String akzeptiertIp;

    @Column(name = "akzeptiert_user_agent", length = 500)
    private String akzeptiertUserAgent;

    @Column(name = "akzeptiert_email", length = 255)
    private String akzeptiertEmail;

    @Column(name = "hash_acceptance", length = 128)
    private String hashAcceptance;

    @PrePersist
    protected void onCreate()
    {
        if (erstelltAm == null)
        {
            erstelltAm = LocalDateTime.now();
        }
    }

    @Transient
    public boolean istAbgelaufen()
    {
        return ablaufDatum != null && LocalDateTime.now().isAfter(ablaufDatum);
    }
}
