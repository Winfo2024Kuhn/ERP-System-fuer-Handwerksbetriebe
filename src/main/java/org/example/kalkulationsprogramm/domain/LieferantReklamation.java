package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBestellung;
import org.example.kalkulationsprogramm.domain.einkauf.BestellungPosition;

@Getter
@Setter
@Entity
public class LieferantReklamation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistisches Sperren: schuetzt gegen paralleles Speichern (auch aus
     * der Mobile-App, die keine Sperr-Oberflaeche hat).
     */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id", nullable = false)
    private Lieferanten lieferant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferschein_id")
    private LieferantDokument lieferschein;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bestellung_id")
    private EinkaufBestellung bestellung;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bestell_position_id")
    private BestellungPosition bestellPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rechnung_id")
    private LieferantDokument rechnung;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erstellt_von_id")
    private Mitarbeiter erstelltVon;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    @Column(columnDefinition = "TEXT")
    private String beschreibung;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReklamationStatus status = ReklamationStatus.OFFEN;

    @OneToMany(mappedBy = "reklamation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LieferantBild> bilder = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now();
        }
    }
}
