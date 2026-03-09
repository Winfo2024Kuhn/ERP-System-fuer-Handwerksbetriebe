package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Dokument eines Lieferanten (Angebot, Auftragsbestätigung, Lieferschein,
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
    // Angebot)
    @ManyToMany
    @JoinTable(name = "lieferant_dokument_verknuepfung", joinColumns = @JoinColumn(name = "dokument_id"), inverseJoinColumns = @JoinColumn(name = "verknuepft_id"))
    private Set<LieferantDokument> verknuepfteDokumente = new HashSet<>();

    // Inverse Seite der Verknüpfung (Dokumente, die DIESES Dokument verknüpft
    // haben)
    @ManyToMany(mappedBy = "verknuepfteDokumente")
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
