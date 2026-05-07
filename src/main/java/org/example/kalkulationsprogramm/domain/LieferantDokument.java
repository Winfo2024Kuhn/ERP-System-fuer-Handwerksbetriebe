package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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
    @BatchSize(size = 50)
    private Lieferanten lieferant;

    // Referenz auf Email-Anhang (optional - kann auch manuell hochgeladen sein)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    @BatchSize(size = 50)
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
    @BatchSize(size = 50)
    private Mitarbeiter uploadedBy;

    // Geschäftsmetadaten (1:1 optional) - durch KI extrahiert
    @OneToOne(mappedBy = "dokument", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private LieferantGeschaeftsdokument geschaeftsdaten;

    // Prozentuale Zuordnung zu Projekten (ersetzt alte M:N)
    @OneToMany(mappedBy = "dokument", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<LieferantDokumentProjektAnteil> projektAnteile = new HashSet<>();

    // Rekursive Verknüpfung: Dokument-Kette (z.B. Rechnung -> Lieferschein -> AB ->
    // Anfrage)
    @ManyToMany
    @JoinTable(name = "lieferant_dokument_verknuepfung", joinColumns = @JoinColumn(name = "dokument_id"), inverseJoinColumns = @JoinColumn(name = "verknuepft_id"))
    @BatchSize(size = 50)
    private Set<LieferantDokument> verknuepfteDokumente = new HashSet<>();

    // Inverse Seite der Verknüpfung (Dokumente, die DIESES Dokument verknüpft
    // haben)
    @ManyToMany(mappedBy = "verknuepfteDokumente")
    @BatchSize(size = 50)
    private Set<LieferantDokument> verknuepftVon = new HashSet<>();

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
