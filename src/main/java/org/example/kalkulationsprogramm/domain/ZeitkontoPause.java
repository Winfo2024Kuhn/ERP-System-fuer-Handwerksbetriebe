package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

/** Inklusive Grenzen für ausdrücklich ausgeschaltete Konten. */
@Entity
@Getter
@Setter
@Table(name = "zeitkonto_pause", uniqueConstraints = @UniqueConstraint(
        name = "uk_zeitkonto_pause_mitarbeiter_von", columnNames = {"mitarbeiter_id", "gueltig_von"}))
public class ZeitkontoPause {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version @Column(nullable = false)
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;
    @Column(nullable = false)
    private LocalDate gueltigVon;
    private LocalDate gueltigBis;
}
