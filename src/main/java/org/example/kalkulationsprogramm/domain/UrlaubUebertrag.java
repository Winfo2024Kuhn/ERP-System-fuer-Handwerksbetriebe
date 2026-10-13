package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Vom Büro festgelegter Resturlaub-Übertrag eines Mitarbeiters in ein Jahr.
 *
 * <p>Ohne Eintrag rechnet {@code UrlaubskontoService} den Übertrag aus dem Vorjahr
 * selbst aus. Ein Eintrag hier ersetzt diese Rechnung für genau dieses Jahr.
 */
@Entity
@Table(name = "urlaub_uebertrag", uniqueConstraints =
        @UniqueConstraint(name = "uk_urlaub_uebertrag_mitarbeiter_jahr", columnNames = { "mitarbeiter_id", "jahr" }))
@Getter
@Setter
public class UrlaubUebertrag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mitarbeiter_id", nullable = false)
    private Long mitarbeiterId;

    /** Jahr, in das der Rest übertragen wird. */
    @Column(name = "jahr", nullable = false)
    private Integer jahr;

    /** Tage aus dem Vorjahr, halbe Tage erlaubt. */
    @Column(name = "tage", nullable = false, precision = 5, scale = 1)
    private BigDecimal tage;

    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;

    /** Benutzer-Profil ({@code frontend_user_profile}), das den Wert zuletzt festgelegt hat. */
    @Column(name = "geaendert_von_id")
    private Long geaendertVonId;
}
