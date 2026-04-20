package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Angebot eines Lieferanten fuer eine einzelne {@link PreisanfragePosition}.
 * In V1 werden Preise manuell erfasst; {@code erfasstDurch} dokumentiert die Quelle.
 */
@Entity
@Table(name = "preisanfrage_angebot")
@Getter
@Setter
@NoArgsConstructor
public class PreisanfrageAngebot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preisanfrage_lieferant_id", nullable = false)
    private PreisanfrageLieferant preisanfrageLieferant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preisanfrage_position_id", nullable = false)
    private PreisanfragePosition preisanfragePosition;

    @Column(precision = 12, scale = 4)
    private BigDecimal einzelpreis;

    @Column(precision = 14, scale = 2)
    private BigDecimal gesamtpreis;

    @Column(name = "mwst_prozent", precision = 5, scale = 2)
    private BigDecimal mwstProzent;

    @Column(name = "lieferzeit_tage")
    private Integer lieferzeitTage;

    @Column(name = "gueltig_bis")
    private LocalDate gueltigBis;

    @Column(columnDefinition = "TEXT")
    private String bemerkung;

    @Column(name = "erfasst_am", nullable = false)
    private LocalDateTime erfasstAm;

    @Column(name = "erfasst_durch", length = 100)
    private String erfasstDurch;

    @PrePersist
    protected void onCreate() {
        if (erfasstAm == null) {
            erfasstAm = LocalDateTime.now();
        }
    }
}
