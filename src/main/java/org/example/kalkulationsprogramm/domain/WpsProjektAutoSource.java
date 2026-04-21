package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit-Eintrag: Diese WPS wurde diesem Projekt automatisch zugeordnet,
 * weil die genannte Leistung beim Dokument-Speichern ausgewählt war.
 *
 * Die eigentliche Zuordnung lebt weiterhin in {@code wps_projekt}
 * ({@link Wps#getProjekte()}); diese Tabelle dient nur als Nachweis
 * der Herkunft ("Auto (Leistung X)" vs. "Manuell") für das WPK-Dashboard.
 */
@Getter
@Setter
@Entity
@Table(
    name = "wps_projekt_auto_source",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_wps_projekt_auto",
        columnNames = {"wps_id", "projekt_id", "leistung_id"}
    )
)
public class WpsProjektAutoSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wps_id", nullable = false)
    private Wps wps;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id", nullable = false)
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leistung_id", nullable = false)
    private Leistung leistung;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();
}
