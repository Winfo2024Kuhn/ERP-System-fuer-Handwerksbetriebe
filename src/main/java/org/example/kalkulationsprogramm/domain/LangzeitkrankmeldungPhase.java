package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kind-Entitaet einer {@link Langzeitkrankmeldung}: eine zeitlich begrenzte
 * Phase (Lohnfortzahlung, Krankengeld oder Wiedereingliederung).
 *
 * <p>Bewusst <b>kein</b> eigenes {@link Version @Version}-Feld: Kind-
 * Entitaeten werden immer ueber ihren Wurzel-Aggregat ({@link
 * Langzeitkrankmeldung}) gespeichert, dessen Version als Waechter fuer den
 * gesamten Aggregatsbaum reicht (Begruendung siehe Kopfkommentar von
 * {@code V364__aggregat_versionsspalten.sql}).
 */
@Getter
@Setter
@Entity
public class LangzeitkrankmeldungPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "langzeitkrankmeldung_id")
    private Langzeitkrankmeldung langzeitkrankmeldung;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LangzeitkrankmeldungPhaseTyp typ;

    @Column(name = "von_datum", nullable = false)
    private LocalDate vonDatum;

    @Column(name = "bis_datum")
    private LocalDate bisDatum;

    @Column(name = "stunden_pro_tag", precision = 4, scale = 2)
    private BigDecimal stundenProTag;
}
