package org.example.kalkulationsprogramm.domain.einkauf;
import jakarta.persistence.*;import java.time.Instant;
@Entity @Table(name="einkauf_charge")
public class EinkaufCharge {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="lieferung_position_id",nullable=false)private LieferungPosition position;@Column(nullable=false,length=120)private String kennung;@Column(name="schmelznummer",length=120)private String schmelznummer;@Column(name="erfasst_am",nullable=false)private Instant erfasstAm=Instant.now();protected EinkaufCharge(){}public EinkaufCharge(LieferungPosition p,String c,String m){position=p;kennung=c;schmelznummer=m;}public Long getId(){return id;}public String getKennung(){return kennung;}public String getSchmelznummer(){return schmelznummer;}
}
