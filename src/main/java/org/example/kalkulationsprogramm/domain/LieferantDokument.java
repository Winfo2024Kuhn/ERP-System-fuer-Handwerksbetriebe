package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Dokument eines Lieferanten (Anfrage, Auftragsbestätigung, Lieferschein,
 * Rechnung).
 * Unterstützt:
 * - Referenz auf LieferantEmailAttachment (kein Kopieren von Dateien)
 * - Prozentuale Zuordnung zu Projekten
 * - Rekursive Verknüpfung für Dokumentenketten
 * - Optionale Geschäftsmetadaten (1:1 mit LieferantGeschaeftsdokument)
 */
@Getter
@Setter
@Entity
@Table(name = "lieferant_dokument")
public class LieferantDokument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id", nullable = false)
    private Lieferanten lieferant;

    // Referenz auf Email-Anhang (optional - kann auch manuell hochgeladen sein)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    private EmailAttachment attachment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LieferantDokumentTyp typ;

    // Fallback für manuell hochgeladene Dokumente ohne Email-Anhang
    private String originalDateiname;
    private String gespeicherterDateiname;

    @Column(nullable = false)
    private LocalDateTime uploadDatum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private Mitarbeiter uploadedBy;

    // Geschäftsmetadaten (1:1 optional) - durch KI extrahiert
    @OneToOne(mappedBy = "dokument", cascade = CascadeType.ALL, orphanRemoval = true)
    private LieferantGeschaeftsdokument geschaeftsdaten;

    // Prozentuale Zuordnung zu Projekten (ersetzt alte M:N)
    @OneToMany(mappedBy = "dokument", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LieferantDokumentProjektAnteil> projektAnteile = new HashSet<>();

    // Rekursive Verknüpfung: Dokument-Kette (z.B. Rechnung -> Lieferschein -> AB ->
    // Anfrage)
    @ManyToMany
    @JoinTable(name = "lieferant_dokument_verknuepfung", joinColumns = @JoinColumn(name = "dokument_id"), inverseJoinColumns = @JoinColumn(name = "verknuepft_id"))
    private Set<LieferantDokument> verknuepfteDokumente = new HashSet<>();

    // Inverse Seite der Verknüpfung (Dokumente, die DIESES Dokument verknüpft
    // haben)
    @ManyToMany(mappedBy = "verknuepfteDokumente")
    private Set<LieferantDokument> verknuepftVon = new HashSet<>();

    // EN 1090 / mobile Lieferschein-Prüfung: Ware beim Wareneingang geprüft?
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean wareGeprueft = false;

    @PrePersist
    protected void onCreate() {
        if (uploadDatum == null) {
            uploadDatum = LocalDateTime.now();
        }
    }

    /**
     * Gibt den Dateinamen zurück - priorisiert Attachment, dann Fallback.
     */
    public String getEffektiverDateiname() {
        if (attachment != null) {
            return attachment.getOriginalFilename();
        }
        return originalDateiname;
    }

    /**
     * Gibt den gespeicherten Dateinamen zurück - priorisiert Attachment, dann
     * Fallback.
     */
    public String getEffektiverGespeicherterDateiname() {
        if (attachment != null) {
            return attachment.getStoredFilename();
        }
        return gespeicherterDateiname;
    }
}
