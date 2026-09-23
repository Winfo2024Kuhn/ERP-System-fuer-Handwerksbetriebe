package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/** Verknüpft einen vorhandenen PDF-Beleg nachvollziehbar mit Anforderungen und Lieferchargen. */
@Entity
@Table(name="einkauf_zeugnis_zuordnung", uniqueConstraints=@UniqueConstraint(name="uk_zeugnis_zuordnung", columnNames={"charge_status_id","datei_id"}))
public class EinkaufZeugnisZuordnung {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable=false) private Long version;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="zeugnis_id",nullable=false) private EinkaufZeugnisErwartung zeugnis;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="datei_id",nullable=false) private EinkaufDatei datei;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="charge_status_id",nullable=false) private EinkaufZeugnisChargeStatus chargeStatus;
    @Column(name="klaerung_noetig",nullable=false) private boolean klaerungNoetig;
    protected EinkaufZeugnisZuordnung() {}
    public EinkaufZeugnisZuordnung(EinkaufZeugnisErwartung zeugnis,EinkaufDatei datei,EinkaufZeugnisChargeStatus chargeStatus,boolean klaerungNoetig){this.zeugnis=zeugnis;this.datei=datei;this.chargeStatus=chargeStatus;this.klaerungNoetig=klaerungNoetig;}
    public Long getId(){return id;} public Long getVersion(){return version;} public EinkaufZeugnisErwartung getZeugnis(){return zeugnis;} public EinkaufDatei getDatei(){return datei;}
    public EinkaufZeugnisChargeStatus getChargeStatus(){return chargeStatus;} public boolean isKlaerungNoetig(){return klaerungNoetig;}
}
