package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
public class Urlaubsantrag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "abteilungen", "dokumente", "loginToken",
            "hibernateLazyInitializer", "handler" })
    private Mitarbeiter mitarbeiter;

    @Column(nullable = false)
    private LocalDate vonDatum;

    @Column(nullable = false)
    private LocalDate bisDatum;

    @Column(length = 2000)
    private String bemerkung;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OFFEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Typ typ = Typ.URLAUB;

    public enum Status {
        OFFEN, GENEHMIGT, ABGELEHNT, STORNIERT
    }

    public enum Typ {
        URLAUB, KRANKHEIT, FORTBILDUNG, ZEITAUSGLEICH, ARBEIT, PAUSE
    }
}
