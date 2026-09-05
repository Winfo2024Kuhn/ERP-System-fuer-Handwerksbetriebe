package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
public class Lieferanten {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistisches Sperren: schuetzt gegen paralleles Speichern (auch aus
     * der Mobile-App, die keine Sperr-Oberflaeche hat).
     */
    @Version
    private Long version;

    @Column(nullable = false, unique = true)
    private String lieferantenname;

    /**
     * Zweiter, im Betrieb gebraeuchlicher Name ("Alias"). Viele Firmen sind intern
     * unter einem anderen Namen bekannt als unter ihrem offiziellen Firmennamen.
     * Wird bei der Freitextsuche mitdurchsucht und auf der Lieferanten-Karte angezeigt.
     */
    private String aliasName;

    private Boolean istAktiv;

    private Date startZusammenarbeit;

    private String ort;
    private String strasse;
    private String plz;
    private String telefon;
    private String mobiltelefon;
    private String vertreter;

    /**
     * @deprecated Abgeloest durch {@link #rollen}. Bleibt vorerst bestehen, bis Frontend/DTOs
     * vollstaendig auf Rollen umgestellt sind; die Spalte wird danach per Migration entfernt.
     */
    @Deprecated
    private String lieferantenTyp;

    /**
     * Rollen dieses Lieferanten (1:n) — steuert, bei welchen Artikel-Kategorien er
     * beim Preis-Eintragen vorgeschlagen wird. Siehe {@link LieferantRolle}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lieferanten_rollen", joinColumns = @JoinColumn(name = "lieferant_id"))
    @Column(name = "rolle")
    @Enumerated(EnumType.STRING)
    private Set<LieferantRolle> rollen = new HashSet<>();

    @Column(columnDefinition = "integer default 0")
    private Integer bestellungen = 0;

    private String eigeneKundennummer;

    @ElementCollection
    @CollectionTable(name = "lieferanten_emails", joinColumns = @JoinColumn(name = "lieferanten_id"))
    @Column(name = "email")
    private List<String> kundenEmails = new ArrayList<>();

    @OneToMany(mappedBy = "lieferant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LieferantenArtikelPreise> artikelpreise = new ArrayList<>();

    /**
     * Standard-Kostenstelle für diesen Lieferanten.
     * Wird beim Anlegen neuer Bestellungen / Eingangsrechnungen vorgeschlagen,
     * damit der Nutzer wiederkehrende Zuordnungen (z.B. "Apple" → "IT") nicht jedes Mal manuell setzen muss.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_kostenstelle_id")
    private Kostenstelle standardKostenstelle;

    /**
     * Bei diesem Lieferanten wird grundsaetzlich im Voraus bezahlt (Vorkasse,
     * Lastschrift, Kreditkarte im Shop). Eingehende Rechnungen sind damit schon
     * beglichen und werden beim Import automatisch als "bereits gezahlt"
     * markiert, damit sie nicht in den Offenen Posten stehen und versehentlich
     * ein zweites Mal ueberwiesen werden.
     */
    @Column(nullable = false)
    private Boolean vorauskasse = false;

}
