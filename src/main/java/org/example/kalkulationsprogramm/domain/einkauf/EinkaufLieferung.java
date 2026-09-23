package org.example.kalkulationsprogramm.domain.einkauf;
import jakarta.persistence.*;import java.time.Instant;import java.util.*;
@Entity @Table(name="einkauf_lieferung",uniqueConstraints=@UniqueConstraint(name="uk_einkauf_lieferung_idempotenz",columnNames="idempotenz_key"))
public class EinkaufLieferung {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="bestellung_id",nullable=false)private EinkaufBestellung bestellung;
 @Column(name="revision_id",nullable=false)private Long revisionId;@Column(name="lieferschein_id",nullable=false)private Long lieferscheinId;@Column(nullable=false)private Instant eingang;
 @Column(name="idempotenz_key",nullable=false,length=36)private UUID idempotenzKey;@Column(name="akteur_id",nullable=false)private Long akteurId;@Column(name="angelegt_am",nullable=false)private Instant angelegtAm=Instant.now();@OneToMany(mappedBy="lieferung",cascade=CascadeType.ALL)private List<LieferungPosition> positionen=new ArrayList<>();
 protected EinkaufLieferung(){}public EinkaufLieferung(EinkaufBestellung b,Long rev,Long dokument,Instant eingang,UUID key,Long actor){bestellung=b;revisionId=rev;lieferscheinId=dokument;this.eingang=eingang;idempotenzKey=key;akteurId=actor;}public void addPosition(LieferungPosition p){positionen.add(p);}public Long getId(){return id;}public EinkaufBestellung getBestellung(){return bestellung;}public Long getRevisionId(){return revisionId;}public Long getLieferscheinId(){return lieferscheinId;}public Instant getEingang(){return eingang;}public List<LieferungPosition> getPositionen(){return List.copyOf(positionen);}
}
