package org.example.kalkulationsprogramm.domain.einkauf;
import jakarta.persistence.*;import java.math.BigDecimal;import java.util.*;import org.hibernate.annotations.JdbcTypeCode;import org.hibernate.type.SqlTypes;
@Entity @Table(name="einkauf_lieferung_position")
public class LieferungPosition {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="lieferung_id",nullable=false)private EinkaufLieferung lieferung;
 @ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="bestell_position_id",nullable=false)private BestellungPosition bestellPosition;@Column(nullable=false,precision=19,scale=6)private BigDecimal menge;@Column(length=120)private String charge;@Column(name="schmelznummer",length=120)private String schmelznummer;
 @JdbcTypeCode(SqlTypes.JSON)@Column(name="projekt_anteile",nullable=false,columnDefinition="json")private List<Map<String,Object>> projektAnteile;
 protected LieferungPosition(){}public LieferungPosition(EinkaufLieferung l,BestellungPosition p,BigDecimal q,String c,String m,List<Map<String,Object>> shares){lieferung=l;bestellPosition=p;menge=q;charge=c;schmelznummer=m;projektAnteile=List.copyOf(shares);}public Long getId(){return id;}public BigDecimal getMenge(){return menge;}public BestellungPosition getBestellPosition(){return bestellPosition;}public String getCharge(){return charge;}public String getSchmelznummer(){return schmelznummer;}public List<Map<String,Object>> getProjektAnteile(){return projektAnteile==null?List.of():List.copyOf(projektAnteile);}
}
