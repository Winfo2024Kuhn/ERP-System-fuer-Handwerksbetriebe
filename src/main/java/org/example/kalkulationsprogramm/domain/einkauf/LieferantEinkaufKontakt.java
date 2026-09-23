package org.example.kalkulationsprogramm.domain.einkauf;

import org.example.kalkulationsprogramm.domain.Lieferanten;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lieferant_einkauf_kontakt")
@Getter
@Setter
public class LieferantEinkaufKontakt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieferant_id", nullable = false)
    private Lieferanten lieferant;

    @Column(length = 160)
    private String name;

    @Column(length = 40)
    private String anrede;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "standard_anfrage", nullable = false)
    private boolean standardAnfrage;

    @Column(name = "standard_bestellung", nullable = false)
    private boolean standardBestellung;

    @Column(nullable = false)
    private boolean aktiv = true;
}
