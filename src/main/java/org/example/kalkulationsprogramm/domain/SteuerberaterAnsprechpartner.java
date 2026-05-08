package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Ansprechpartner eines Steuerberaters.
 *
 * <p>Ein Steuerberater kann mehrere Personen haben (z.B. einen für die
 * Lohnbuchhaltung, einen für die BWA). Über {@link #istLohnAnsprechpartner}
 * wird markiert, an wen die monatliche Stundenaufstellung geschickt wird.
 */
@Getter
@Setter
@Entity
@Table(name = "steuerberater_ansprechpartner")
public class SteuerberaterAnsprechpartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "steuerberater_id", nullable = false)
    @JsonBackReference
    private SteuerberaterKontakt steuerberater;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Anrede anrede;

    private String vorname;

    @Column(nullable = false)
    private String nachname;

    private String email;

    private String telefon;

    /**
     * Markiert den Ansprechpartner, an den die monatliche Stundenaufstellung
     * für die Lohnabrechnung geschickt wird. Pro Steuerberater sollte genau
     * einer dieses Flag haben (wird im Service erzwungen).
     */
    @Column(nullable = false)
    private Boolean istLohnAnsprechpartner = false;

    @Column(length = 500)
    private String notizen;
}
