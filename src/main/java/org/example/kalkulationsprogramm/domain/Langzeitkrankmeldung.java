package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate Root fuer eine Langzeitkrankmeldung eines Mitarbeiters. Buendelt
 * die zeitliche Abfolge aus Lohnfortzahlung, Krankengeld und optionaler
 * Wiedereingliederung ueber ihre {@link LangzeitkrankmeldungPhase}n.
 *
 * <p>Traegt als Wurzel-Aggregat das optimistische Sperren ({@link Version}),
 * analog zur Konvention aus
 * {@code V364__aggregat_versionsspalten.sql}: Kind-Entitaeten (hier
 * {@link LangzeitkrankmeldungPhase}) bekommen bewusst keine eigene
 * Versionsspalte, weil sie immer ueber diese Wurzel gespeichert werden und
 * deren Version als Waechter fuer den gesamten Aggregatsbaum ausreicht.
 */
@Getter
@Setter
@Entity
public class Langzeitkrankmeldung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id")
    // Whitelist statt Blacklist, analog Urlaubsantrag.java: nur die vom
    // Frontend benoetigten Felder serialisieren.
    @JsonIncludeProperties({ "id", "vorname", "nachname" })
    private Mitarbeiter mitarbeiter;

    @Column(nullable = false)
    private LocalDate beginn;

    @Column
    private LocalDate ende;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LangzeitkrankmeldungStatus status = LangzeitkrankmeldungStatus.LAUFEND;

    @Column(name = "lohnfortzahlung_bis", nullable = false)
    private LocalDate lohnfortzahlungBis;

    @Column(length = 500)
    private String notiz;

    @Version
    private Long version;

    @OneToMany(mappedBy = "langzeitkrankmeldung", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LangzeitkrankmeldungPhase> phasen = new ArrayList<>();
}
