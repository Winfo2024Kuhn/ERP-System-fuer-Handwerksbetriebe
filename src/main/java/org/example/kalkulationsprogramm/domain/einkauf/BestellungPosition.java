package org.example.kalkulationsprogramm.domain.einkauf;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes; import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
@Entity @Table(name="einkauf_bestellung_position")
public class BestellungPosition {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="revision_id",nullable=false) private BestellungRevision revision;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="position_snapshot",nullable=false,columnDefinition="json") private PositionSnapshot position;
 @Column(nullable=false,precision=19,scale=6) private BigDecimal menge;
 @Column(name="netto_einzelpreis",precision=19,scale=6) private BigDecimal nettoEinzelpreis;
 @Column(nullable=false,length=3) private String waehrung;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="kosten_snapshot",nullable=false,columnDefinition="json") private List<Map<String,Object>> kosten;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="liefergruppe_snapshot",nullable=false,columnDefinition="json") private Map<String,Object> liefergruppe;
 @OneToMany(mappedBy="position",cascade=CascadeType.ALL) private List<BestellungHerkunft> herkuenfte=new ArrayList<>();
 protected BestellungPosition() {}
 public BestellungPosition(BestellungRevision revision,PositionSnapshot position,BigDecimal menge,BigDecimal preis,String currency,List<Map<String,Object>> costs,Map<String,Object> group){this.revision=revision;this.position=position;this.menge=menge;this.nettoEinzelpreis=preis;this.waehrung=currency;this.kosten=List.copyOf(costs);this.liefergruppe=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(group));}
 public void addHerkunft(BestellungHerkunft h){herkuenfte.add(h);} public Long getId(){return id;} public BestellungRevision getRevision(){return revision;} public PositionSnapshot getPosition(){return position;} public BigDecimal getMenge(){return menge;} public BigDecimal getNettoEinzelpreis(){return nettoEinzelpreis;} public Map<String,Object> getLiefergruppe(){return liefergruppe;} public List<Map<String,Object>> getKosten(){return kosten;} public List<BestellungHerkunft> getHerkuenfte(){return List.copyOf(herkuenfte);}
}
