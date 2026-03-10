package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Entity
public class Lieferanten {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String lieferantenname;

    private Boolean istAktiv;

    private Date startZusammenarbeit;

    private String ort;
    private String strasse;
    private String plz;
    private String telefon;
    private String mobiltelefon;
    private String vertreter;

    private String lieferantenTyp;

    @Column(columnDefinition = "integer default 0")
    private Integer bestellungen = 0;

    private String eigeneKundennummer;

    @ElementCollection
    @CollectionTable(name = "lieferanten_emails", joinColumns = @JoinColumn(name = "lieferanten_id"))
    @Column(name = "email")
    private List<String> kundenEmails = new ArrayList<>();

    @OneToMany(mappedBy = "lieferant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LieferantenArtikelPreise> artikelpreise = new ArrayList<>();

}
