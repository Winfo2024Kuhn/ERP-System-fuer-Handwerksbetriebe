package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class Kunde {
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
    private String kundennummer;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Anrede anrede;

    private String ansprechspartner;

    private String strasse;
    private String plz;
    private String ort;

    private String telefon;
    private String mobiltelefon;

    private Integer zahlungsziel = 8;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kunden_emails", joinColumns = @JoinColumn(name = "kunden_id"))
    // Nicht eindeutig: Dieselbe Adresse darf bei mehreren Kunden stehen, z. B. eine
    // Hausverwaltung fuer mehrere WEGs (V411).
    @Column(name = "email", nullable = false)
    private List<String> kundenEmails = new ArrayList<>();

    @OneToMany(mappedBy = "kundenId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Projekt> projekts = new ArrayList<>();

    /**
     * Aus Datenschutzgründen gesperrt (Art. 18 DSGVO, Issue #264): Was nicht aufbewahrt werden
     * muss, ist gelöscht; der Kunde erscheint nicht mehr in Listen und lässt sich nicht mehr
     * ändern. {@code null} = nicht gesperrt.
     */
    @Column(name = "gesperrt_am")
    private LocalDateTime gesperrtAm;

    /**
     * Ende der längsten Aufbewahrungsfrist der Unterlagen beim Sperren; danach anonymisierbar. Ein späteres
     * Storno oder eine spätere Gutschrift zieht es nach. Maßgeblich (Anzeige, Anonymisieren) ist immer das
     * frisch aus allen Unterlagen berechnete Fristende, siehe {@code KundeDatenschutzService}.
     */
    @Column(name = "aufbewahren_bis")
    private LocalDate aufbewahrenBis;

    /** Nach Fristende anonymisiert (Name/Anschrift entfernt). {@code null} = nicht anonymisiert. */
    @Column(name = "anonymisiert_am")
    private LocalDateTime anonymisiertAm;

    public boolean istGesperrt() {
        return gesperrtAm != null;
    }

    public boolean istAnonymisiert() {
        return anonymisiertAm != null;
    }

}
