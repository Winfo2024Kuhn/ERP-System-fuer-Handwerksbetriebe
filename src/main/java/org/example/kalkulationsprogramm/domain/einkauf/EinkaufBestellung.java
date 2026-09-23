package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;

@Entity @Table(name="einkauf_bestellung", uniqueConstraints={@UniqueConstraint(name="uk_einkauf_bestellung_nummer",columnNames="nummer"),@UniqueConstraint(name="uk_einkauf_bestellung_idempotenz",columnNames="idempotenz_key")})
public class EinkaufBestellung {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version @Column(nullable=false) private Long version;
 @Column(nullable=false,length=32) private String nummer;
 @Column(name="lieferant_id",nullable=false) private Long lieferantId;
 @Column(name="angebotsversion_id") private Long angebotsversionId;
 @Column(name="anfrage_revision_id") private Long anfrageRevisionId;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="empfaenger_snapshot",nullable=false,columnDefinition="json") private Snapshot empfaenger;
 @Enumerated(EnumType.STRING) @Column(nullable=false,columnDefinition="ENUM('ENTWURF','BESTELLT','TEILGELIEFERT','GELIEFERT','STORNIERT')") private BestellungStatus status=BestellungStatus.ENTWURF;
 @Enumerated(EnumType.STRING) @Column(name="lieferanten_status",nullable=false,columnDefinition="ENUM('AUSSTEHEND','BESTAETIGT','ABWEICHUNG')") private LieferantenBestellstatus lieferantenStatus=LieferantenBestellstatus.AUSSTEHEND;
 @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name="idempotenz_key",nullable=false,length=36) private UUID idempotenzKey;
 @Column(name="payload_hash",nullable=false,length=64) private String payloadHash;
 @Column(name="angelegt_von",nullable=false) private Long angelegtVon;
 @Column(name="angelegt_am",nullable=false,updatable=false) private Instant angelegtAm=Instant.now();
 @Column(name="geaendert_am",nullable=false) private Instant geaendertAm=Instant.now();
 @OneToMany(mappedBy="bestellung",cascade=CascadeType.ALL) @OrderBy("nummer ASC") private List<BestellungRevision> revisionen=new ArrayList<>();
 protected EinkaufBestellung() {}
 public EinkaufBestellung(String nummer,Long lieferantId,Long angebotId,Long anfrageRevisionId,Snapshot empfaenger,UUID key,String hash,Long actor){this.nummer=nummer;this.lieferantId=lieferantId;this.angebotsversionId=angebotId;this.anfrageRevisionId=anfrageRevisionId;this.empfaenger=empfaenger;this.idempotenzKey=key;this.payloadHash=hash;this.angelegtVon=actor;}
 public void addRevision(BestellungRevision r){revisionen.add(r);geaendertAm=Instant.now();} public Long getId(){return id;} public Long getVersion(){return version;} public void setVersion(Long v){version=v;} public String getNummer(){return nummer;} public Long getLieferantId(){return lieferantId;} public Long getAngebotsversionId(){return angebotsversionId;} public Long getAnfrageRevisionId(){return anfrageRevisionId;} public Snapshot getEmpfaenger(){return empfaenger;} public BestellungStatus getStatus(){return status;} public void setStatus(BestellungStatus s){status=s;geaendertAm=Instant.now();} public LieferantenBestellstatus getLieferantenStatus(){return lieferantenStatus;} public void setLieferantenStatus(LieferantenBestellstatus s){lieferantenStatus=s;geaendertAm=Instant.now();} public UUID getIdempotenzKey(){return idempotenzKey;} public String getPayloadHash(){return payloadHash;} public Long getAngelegtVon(){return angelegtVon;} public List<BestellungRevision> getRevisionen(){return List.copyOf(revisionen);}
}
