package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "einkauf_zeugnis_erwartung", uniqueConstraints = @UniqueConstraint(name="uk_zeugnis_revision_position_soll", columnNames={"revision_id","bestell_position_id","anforderungs_index"}))
public class EinkaufZeugnisErwartung {
    public enum Status { ANGEFORDERT, ERWARTET, EINGEGANGEN, ZUGEORDNET, GEPRUEFT, KLAERUNG_NOETIG }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable=false) private Long version;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="revision_id", nullable=false) private BestellungRevision revision;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="bestell_position_id", nullable=false) private BestellungPosition bestellPosition;
    @Column(name="anforderungs_index",nullable=false) private int anforderungsIndex;
    @ManyToMany @JoinTable(name="einkauf_zeugnis_datei", joinColumns=@JoinColumn(name="zeugnis_id"), inverseJoinColumns=@JoinColumn(name="datei_id")) private List<EinkaufDatei> dateien=new ArrayList<>();
    @Enumerated(EnumType.STRING) @Column(nullable=false, columnDefinition="enum('ZEUGNIS_2_1','ZEUGNIS_2_2','ZEUGNIS_3_1','ZEUGNIS_3_2','LEISTUNGSERKLAERUNG','CE_NACHWEIS')") private Dokumentart art;
    @Column(nullable=false, length=1000) private String grundlage;
    @Column(name="grundlage_version", nullable=false, length=120) private String grundlageVersion;
    @Column(name="frist") private LocalDate frist;
    @Enumerated(EnumType.STRING) @Column(nullable=false, columnDefinition="enum('ANGEFORDERT','ERWARTET','EINGEGANGEN','ZUGEORDNET','GEPRUEFT','KLAERUNG_NOETIG')") private Status status;
    @Column(name="material_freigegeben", nullable=false) private boolean materialFreigegeben;
    @Column(name="eingegangen_am") private Instant eingegangenAm;
    @ManyToMany @JoinTable(name="einkauf_zeugnis_lieferposition", joinColumns=@JoinColumn(name="zeugnis_id"), inverseJoinColumns=@JoinColumn(name="lieferposition_id")) private List<LieferungPosition> lieferPositionen=new ArrayList<>();
    @ManyToMany @JoinTable(name="einkauf_zeugnis_charge", joinColumns=@JoinColumn(name="zeugnis_id"), inverseJoinColumns=@JoinColumn(name="charge_id")) private List<EinkaufCharge> chargen=new ArrayList<>();
    @OneToMany(mappedBy="zeugnis", cascade=CascadeType.PERSIST) @OrderBy("geprueftAm DESC, id DESC") private List<EinkaufDokumentPruefung> pruefungen=new ArrayList<>();
    protected EinkaufZeugnisErwartung() {}
    public EinkaufZeugnisErwartung(BestellungRevision revision, BestellungPosition position, int anforderungsIndex, Dokumentart art,
            String grundlage, String grundlageVersion, LocalDate frist) {
        this.revision=revision; this.bestellPosition=position; this.art=art; this.grundlage=grundlage;
        this.anforderungsIndex=anforderungsIndex; this.grundlageVersion=grundlageVersion; this.frist=frist;
        this.status=frist==null?Status.KLAERUNG_NOETIG:Status.ANGEFORDERT;
    }
    public void eingegangen(EinkaufDatei datei, Instant zeit){if(!dateien.contains(datei))dateien.add(datei);this.eingegangenAm=zeit;if(status!=Status.GEPRUEFT){this.status=Status.EINGEGANGEN;this.materialFreigegeben=false;}}
    public void zuordnen(List<LieferungPosition> positionen,List<EinkaufCharge> charges, boolean widerspruch){positionen.stream().filter(p->!lieferPositionen.contains(p)).forEach(lieferPositionen::add);charges.stream().filter(c->!chargen.contains(c)).forEach(chargen::add);if(widerspruch)this.status=Status.KLAERUNG_NOETIG;this.materialFreigegeben=false;}
    public void aktualisiereChargenstand(List<EinkaufZeugnisChargeStatus> staende){
        materialFreigegeben=!staende.isEmpty()&&staende.stream().allMatch(s->s.getStatus()==Status.GEPRUEFT&&s.isMaterialFreigegeben());
        if(staende.stream().anyMatch(s->s.getStatus()==Status.KLAERUNG_NOETIG))status=Status.KLAERUNG_NOETIG;
        else if(materialFreigegeben)status=Status.GEPRUEFT;
        else if(staende.stream().anyMatch(s->s.getStatus()==Status.ZUGEORDNET))status=Status.ZUGEORDNET;
        else if(!dateien.isEmpty())status=Status.EINGEGANGEN;
        else if(frist==null)status=Status.KLAERUNG_NOETIG;
        else status=Status.ANGEFORDERT;
    }
    public void pruefungAbgelegt(EinkaufDokumentPruefung p){pruefungen.add(p);}
    public void materialfreigeben(){if(status!=Status.GEPRUEFT)throw new IllegalStateException("Nur positiv geprüfte Zeugnisse erlauben die Materialfreigabe.");materialFreigegeben=true;}
    public Long getId(){return id;} public Long getVersion(){return version;} public BestellungRevision getRevision(){return revision;} public BestellungPosition getBestellPosition(){return bestellPosition;}
    public int getAnforderungsIndex(){return anforderungsIndex;}
    public List<EinkaufDatei> getDateien(){return List.copyOf(dateien);} public Dokumentart getArt(){return art;} public String getGrundlage(){return grundlage;}
    public String getGrundlageVersion(){return grundlageVersion;} public LocalDate getFrist(){return frist;} public Status getStatus(){return status;}
    public boolean isMaterialFreigegeben(){return materialFreigegeben;} public Instant getEingegangenAm(){return eingegangenAm;}
    public List<LieferungPosition> getLieferPositionen(){return List.copyOf(lieferPositionen);} public List<EinkaufCharge> getChargen(){return List.copyOf(chargen);}
    public List<EinkaufDokumentPruefung> getPruefungen(){return List.copyOf(pruefungen);}
}
