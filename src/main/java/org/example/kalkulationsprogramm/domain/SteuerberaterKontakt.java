package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Steuerberater-Kontakt für automatische E-Mail-Verarbeitung.
 * E-Mails von diesem Absender werden automatisch als BWA-relevante
 * Dokumente erkannt und zur Verarbeitung vorgeschlagen.
 */
@Getter
@Setter
@Entity
@Table(name = "steuerberater_kontakt")
public class SteuerberaterKontakt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // z.B. "Claus Müller Steuerberatung"

    @Column(nullable = false)
    private String email; // z.B. "christine.mueller@clausmueller-steuerberater.de"

    private String telefon;

    private String ansprechpartner;

    /**
     * Automatische Verarbeitung von E-Mails aktiviert.
     * Bei true werden Anhänge von dieser E-Mail-Adresse automatisch
     * als potenzielle BWAs erkannt.
     */
    @Column(nullable = false)
    private Boolean autoProcessEmails = true;

    @Column(nullable = false)
    private Boolean aktiv = true;

    @Column(length = 500)
    private String notizen;
    
    private java.time.LocalDate gueltigAb;
    
    private java.time.LocalDate gueltigBis;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "steuerberater_kontakt_emails", joinColumns = @JoinColumn(name = "steuerberater_id"))
    @Column(name = "email")
    private java.util.Set<String> weitereEmails = new java.util.HashSet<>();

    /**
     * LAZY (nicht EAGER), weil {@link #weitereEmails} ebenfalls eine Collection
     * ist und zwei EAGER-Collections am selben Owner zu Hibernate-MultipleBagFetch
     * bzw. kartesischen Produkten führen. Service-Methoden, die die Liste
     * brauchen, laufen unter {@code @Transactional} und initialisieren sie
     * implizit beim Mappen aufs DTO.
     */
    @OneToMany(mappedBy = "steuerberater", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private java.util.List<SteuerberaterAnsprechpartner> ansprechpartnerListe = new java.util.ArrayList<>();
}
