package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.example.kalkulationsprogramm.domain.LieferantDokument;

@Entity
@Table(name="einkauf_beleg_zuordnung", uniqueConstraints={@UniqueConstraint(name="uk_beleg_zuordnung_idempotenz",columnNames="idempotenz_key"), @UniqueConstraint(name="uk_beleg_zuordnung_dokument",columnNames="dokument_id")})
public class EinkaufBelegZuordnung {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="dokument_id",nullable=false) private LieferantDokument dokument;
    @Column(name="bestellung_id",nullable=false) private Long bestellungId;
    @Column(nullable=false,length=24) private String art;
    @Column(name="bezugs_dokument_id") private Long bezugsDokumentId;
    @Column(name="idempotenz_key",nullable=false,length=36) @JdbcTypeCode(SqlTypes.VARCHAR) private UUID idempotenzKey;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="payload_hash",nullable=false,length=64) private String payloadHash;
    @Column(name="akteur_id",nullable=false) private Long akteurId;
    @Column(name="zugeordnet_am",nullable=false) private Instant zugeordnetAm=Instant.now();
    protected EinkaufBelegZuordnung(){}
    public EinkaufBelegZuordnung(LieferantDokument dokument,Long bestellungId,String art,Long bezug,UUID key,String hash,Long akteur){this.dokument=dokument;this.bestellungId=bestellungId;this.art=art;this.bezugsDokumentId=bezug;this.idempotenzKey=key;this.payloadHash=hash;this.akteurId=akteur;}
    public Long getId(){return id;} public LieferantDokument getDokument(){return dokument;} public Long getBestellungId(){return bestellungId;} public String getArt(){return art;} public Long getBezugsDokumentId(){return bezugsDokumentId;} public UUID getIdempotenzKey(){return idempotenzKey;} public String getPayloadHash(){return payloadHash;}
}
