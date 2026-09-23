package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/** Verknüpft einen vorhandenen PDF-Beleg nachvollziehbar mit Anforderungen und Lieferchargen. */
@Entity
@Table(name="einkauf_zeugnis_zuordnung", uniqueConstraints=@UniqueConstraint(name="uk_zeugnis_zuordnung", columnNames={"zeugnis_id","datei_id"}))
public class EinkaufZeugnisZuordnung {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="zeugnis_id",nullable=false) private EinkaufZeugnisErwartung zeugnis;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="datei_id",nullable=false) private EinkaufDatei datei;
    @ManyToMany @JoinTable(name="einkauf_zeugnis_zuordnung_lieferposition",joinColumns=@JoinColumn(name="zuordnung_id"),inverseJoinColumns=@JoinColumn(name="lieferposition_id")) private List<LieferungPosition> lieferPositionen=new ArrayList<>();
    @ManyToMany @JoinTable(name="einkauf_zeugnis_zuordnung_charge",joinColumns=@JoinColumn(name="zuordnung_id"),inverseJoinColumns=@JoinColumn(name="charge_id")) private List<EinkaufCharge> chargen=new ArrayList<>();
    @Column(name="klaerung_noetig",nullable=false) private boolean klaerungNoetig;
    protected EinkaufZeugnisZuordnung() {}
    public EinkaufZeugnisZuordnung(EinkaufZeugnisErwartung zeugnis,EinkaufDatei datei,List<LieferungPosition> positionen,List<EinkaufCharge> charges,boolean klaerungNoetig){this.zeugnis=zeugnis;this.datei=datei;this.lieferPositionen.addAll(positionen);this.chargen.addAll(charges);this.klaerungNoetig=klaerungNoetig;}
    public Long getId(){return id;} public EinkaufZeugnisErwartung getZeugnis(){return zeugnis;} public EinkaufDatei getDatei(){return datei;}
    public List<LieferungPosition> getLieferPositionen(){return List.copyOf(lieferPositionen);} public List<EinkaufCharge> getChargen(){return List.copyOf(chargen);} public boolean isKlaerungNoetig(){return klaerungNoetig;}
}
