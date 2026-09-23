package org.example.kalkulationsprogramm.domain.einkauf;
import jakarta.persistence.*; import java.time.Instant; import java.util.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
@Entity @Table(name="einkauf_bestellung_revision",uniqueConstraints=@UniqueConstraint(name="uk_bestellung_revision_nummer",columnNames={"bestellung_id","nummer"}))
public class BestellungRevision {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="bestellung_id",nullable=false) private EinkaufBestellung bestellung;
 @Column(nullable=false) private int nummer;
 @Version @Column(nullable=false) private Long version;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="snapshot",nullable=false,columnDefinition="json") private Map<String,Object> snapshot;
 @Column(name="sha256",nullable=false,length=64) private String sha256;
 @Column(name="geaendert_von",nullable=false) private Long geaendertVon;
 @Column(name="geaendert_am",nullable=false) private Instant geaendertAm=Instant.now();
 @Column(name="versand_id") private Long versandId;
 @Column(name="angenommen_am") private Instant angenommenAm;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="externer_nachweis",columnDefinition="json") private Map<String,Object> externerNachweis;
 public Instant getAngenommenAm(){return angenommenAm;}
 public boolean istAngenommen(){return angenommenAm!=null;}
 public void angenommen(Instant zeit){if(angenommenAm==null)angenommenAm=zeit;}
 public Map<String,Object> getExternerNachweis(){return externerNachweis==null?Map.of():Map.copyOf(externerNachweis);}
 public void externerNachweis(Map<String,Object> nachweis){if(externerNachweis!=null)throw new IllegalStateException("Versandnachweis besteht bereits.");externerNachweis=Map.copyOf(nachweis);}
 @OneToMany(mappedBy="revision",cascade=CascadeType.ALL) private List<BestellungPosition> positionen=new ArrayList<>();
 protected BestellungRevision() {}
 public BestellungRevision(EinkaufBestellung bestellung,int nummer,Map<String,Object> snapshot,String sha256,Long actor){this.bestellung=bestellung;this.nummer=nummer;this.snapshot=Map.copyOf(snapshot);this.sha256=sha256;this.geaendertVon=actor;}
 public void addPosition(BestellungPosition p){positionen.add(p);} public Long getId(){return id;} public EinkaufBestellung getBestellung(){return bestellung;} public int getNummer(){return nummer;} public Long getVersion(){return version;} public Map<String,Object> getSnapshot(){return snapshot;} public String getSha256(){return sha256;} public Long getVersandId(){return versandId;} public void setVersandId(Long id){versandId=id;} public List<BestellungPosition> getPositionen(){return List.copyOf(positionen);}
}
