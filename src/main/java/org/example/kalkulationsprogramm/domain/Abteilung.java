package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
public class Abteilung {

    @Column(nullable = false)
    private Boolean darfMonatAbschliessen = false;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "abteilung", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Arbeitsgang> arbeitsgaenge = new ArrayList<>();

    /**
     * Darf Eingangsrechnungen sehen und genehmigen (Büro-Rolle).
     * Wenn true: sieht ALLE offenen Rechnungen und darf genehmigen.
     */
    @Column(nullable = false)
    private Boolean darfRechnungenGenehmigen = false;

    /**
     * Darf genehmigte Eingangsrechnungen sehen (Buchhaltungs-Rolle).
     * Wenn true (und darfRechnungenGenehmigen=false): sieht NUR genehmigte Rechnungen.
     */
    @Column(nullable = false)
    private Boolean darfRechnungenSehen = false;

    /**
     * Steuert, ob Mitarbeiter dieser Abteilung eine Push-Benachrichtigung
     * bekommen, sobald ein Kunde ein Dokument digital annimmt
     * ("Angebot angenommen", "Auftragsbestätigung angenommen").
     */
    @Column(nullable = false)
    private Boolean darfFreigabeAnnahmePushen = true;

    /**
     * Steuert, ob Mitarbeiter dieser Abteilung eine Push-Benachrichtigung
     * auf dem Handy-Sperrbildschirm bekommen, sobald über das öffentliche
     * Webseiten-Funnel-Formular eine neue Anfrage eingegangen ist.
     */
    @Column(nullable = false)
    private Boolean darfWebseitenAnfragenPushen = true;
}
