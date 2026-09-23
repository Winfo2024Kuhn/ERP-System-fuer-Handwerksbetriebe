package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;

/** Prüf- und Freigabestand eines Zeugnis-Solls für genau eine tatsächlich gelieferte Charge. */
@Entity
@Table(name="einkauf_zeugnis_charge_status",uniqueConstraints=@UniqueConstraint(name="uk_zeugnis_soll_charge",columnNames={"zeugnis_id","charge_id"}))
public class EinkaufZeugnisChargeStatus {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable=false) private Long version;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="zeugnis_id",nullable=false) private EinkaufZeugnisErwartung zeugnis;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="charge_id",nullable=false) private EinkaufCharge charge;
    @Enumerated(EnumType.STRING) @Column(nullable=false,columnDefinition="enum('ERWARTET','EINGEGANGEN','ZUGEORDNET','GEPRUEFT','KLAERUNG_NOETIG')") private EinkaufZeugnisErwartung.Status status=EinkaufZeugnisErwartung.Status.ERWARTET;
    @Column(name="material_freigegeben",nullable=false) private boolean materialFreigegeben;
    protected EinkaufZeugnisChargeStatus() {}
    public EinkaufZeugnisChargeStatus(EinkaufZeugnisErwartung zeugnis,EinkaufCharge charge){this.zeugnis=zeugnis;this.charge=charge;}
    public void eingegangen(){if(status!=EinkaufZeugnisErwartung.Status.GEPRUEFT){status=EinkaufZeugnisErwartung.Status.EINGEGANGEN;materialFreigegeben=false;}}
    public void zuordnen(boolean widerspruch){if(widerspruch){status=EinkaufZeugnisErwartung.Status.KLAERUNG_NOETIG;materialFreigegeben=false;}else if(status!=EinkaufZeugnisErwartung.Status.GEPRUEFT){status=EinkaufZeugnisErwartung.Status.ZUGEORDNET;materialFreigegeben=false;}}
    public void pruefen(boolean bestanden){status=bestanden?EinkaufZeugnisErwartung.Status.GEPRUEFT:EinkaufZeugnisErwartung.Status.KLAERUNG_NOETIG;materialFreigegeben=bestanden;}
    public Long getId(){return id;} public Long getVersion(){return version;} public EinkaufZeugnisErwartung getZeugnis(){return zeugnis;} public EinkaufCharge getCharge(){return charge;}
    public EinkaufZeugnisErwartung.Status getStatus(){return status;} public boolean isMaterialFreigegeben(){return materialFreigegeben;}
}
