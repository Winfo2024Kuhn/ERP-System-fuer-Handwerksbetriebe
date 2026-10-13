package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Eine gespeicherte Fassung eines Rechtstexts. Fassungen werden nur angelegt, nie geändert oder
 * gelöscht – so bleibt nachweisbar, welcher Wortlaut zu welchem Zeitpunkt galt. Alle Spalten sind
 * deshalb {@code updatable = false}.
 */
@Entity
@Table(name = "rechtstext_fassung", uniqueConstraints =
        @UniqueConstraint(name = "uk_rechtstext_fassung_art_fassung", columnNames = { "art", "fassung" }))
@Getter
@Setter
public class RechtstextFassung
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "art", nullable = false, length = 40, updatable = false)
    private RechtstextArt art;

    /** Laufende Nummer je Art, beginnt bei 1. */
    @Column(name = "fassung", nullable = false, updatable = false)
    private int fassung;

    /** Speichertag; steht im PDF als „Stand“. */
    @Column(name = "stand", nullable = false, updatable = false)
    private LocalDate stand;

    /** Bereinigtes HTML (nur p, br, strong, b, em, i, h3, ul, ol, li). */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "inhalt_html", nullable = false, updatable = false)
    private String inhaltHtml;

    @Column(name = "geaendert_am", nullable = false, updatable = false)
    private LocalDateTime geaendertAm;
}
