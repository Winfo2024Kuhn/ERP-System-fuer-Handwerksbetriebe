package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Datei-Anhang eines Artikels: Vorschaubild oder Zusatzdokument (Zulassung,
 * Zeichnung, Datenblatt, Montageanleitung, Sonstiges).
 *
 * Das Vorschaubild ist kein eigenes Feld an {@link Artikel}, sondern ein
 * Dokument mit {@code typ == VORSCHAUBILD}. Dass es je Artikel hoechstens
 * eines gibt, setzt der Service durch - nicht die Datenbank.
 */
@Getter
@Setter
@Entity
@Table(name = "artikel_dokument")
public class ArtikelDokument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artikel_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artikel artikel;

    @Column(nullable = false)
    private String originalDateiname;

    @Column(nullable = false)
    private String gespeicherterDateiname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtikelDokumentTyp typ;

    private String beschreibung;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id")
    private Mitarbeiter hochgeladenVon;

    private Long dateigroesseBytes;

    /** Reihenfolge in der Anzeige. {@code null} = keine bestimmte Position. */
    private Integer sortierung;
}
