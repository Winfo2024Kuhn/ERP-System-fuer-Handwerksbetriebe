package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ein Anruf auf einer Geschäftsnummer, abgeholt aus der Telefonanlage.
 * {@code nummerRoh} ist leer, wenn die Nummer unterdrückt war.
 */
@Getter
@Setter
@Entity
@Table(name = "telefon_anruf")
public class TelefonAnruf implements TelefonKontaktZuordenbar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime zeitpunkt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TelefonAnrufArt art;

    /** Index des Anrufbeantworters, wenn {@code art == ANRUFBEANTWORTER}. */
    @Column(name = "anrufbeantworter")
    private Integer anrufbeantworter;

    @Column(name = "nummer_roh", nullable = false, length = 40)
    private String nummerRoh = "";

    @Column(name = "nummer_normalisiert", length = 40)
    private String nummerNormalisiert;

    @Column(name = "eigene_nummer", nullable = false, length = 40)
    private String eigeneNummer;

    @Column(name = "dauer_minuten", nullable = false)
    private int dauerMinuten;

    @Column(name = "name_fritzbox", length = 200)
    private String nameFritzbox;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kunde_id")
    private Kunde kunde;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TelefonZuordnung zuordnung = TelefonZuordnung.KEINE;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;
}
