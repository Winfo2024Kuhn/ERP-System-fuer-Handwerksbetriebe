package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Append-only Historie: jeder Abschluss und jedes Öffnen bleibt ein eigener Eintrag. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Immutable
@Table(name = "monatsabschluss_audit", indexes = @Index(
        name = "idx_monatsabschluss_audit_monat", columnList = "mitarbeiter_id,jahr,monat,zeitpunkt,id"))
public class MonatsabschlussAudit {
    public enum Aktion { ABSCHLIESSEN, OEFFNEN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false, updatable = false)
    private Mitarbeiter mitarbeiter;

    @Column(nullable = false, updatable = false)
    private Integer jahr;

    @Column(nullable = false, updatable = false)
    private Integer monat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Aktion aktion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "akteur_id", nullable = false, updatable = false)
    private Mitarbeiter akteur;

    @Column(nullable = false, updatable = false)
    private LocalDateTime zeitpunkt;

    public MonatsabschlussAudit(Mitarbeiter mitarbeiter, int jahr, int monat,
            Aktion aktion, Mitarbeiter akteur, LocalDateTime zeitpunkt) {
        if (jahr < 1000 || jahr > 9999 || monat < 1 || monat > 12) {
            throw new IllegalArgumentException("Ungültiger Abschlussmonat");
        }
        this.mitarbeiter = Objects.requireNonNull(mitarbeiter);
        this.jahr = jahr;
        this.monat = monat;
        this.aktion = Objects.requireNonNull(aktion);
        this.akteur = Objects.requireNonNull(akteur);
        this.zeitpunkt = Objects.requireNonNull(zeitpunkt);
    }

    @PreRemove
    private void verhindereLoeschung() {
        throw new IllegalStateException("Die Monatsabschluss-Historie darf nicht gelöscht werden");
    }
}
