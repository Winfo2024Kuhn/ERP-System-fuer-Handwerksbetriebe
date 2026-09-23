package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.example.kalkulationsprogramm.domain.LieferantDokument;

@Entity
@Table(name="einkauf_beleg_position", uniqueConstraints = @UniqueConstraint(name="uk_beleg_position_original",
        columnNames={"dokument_id", "original_positionsnummer"}))
public class EinkaufBelegPosition {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="dokument_id", nullable=false) private LieferantDokument dokument;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="bestell_position_id") private BestellungPosition bestellPosition;
    @Column(name="original_positionsnummer", length=100, nullable=false) private String originalPositionsnummer;
    @Column(precision=19,scale=6) private BigDecimal menge;
    @Enumerated(EnumType.STRING) @Column(length=24) private Einheit einheit;
    @Column(name="original_menge",precision=19,scale=6) private BigDecimal originalMenge;
    @Enumerated(EnumType.STRING) @Column(name="original_einheit",length=24) private Einheit originalEinheit;
    @Column(precision=19,scale=6) private BigDecimal einzelpreis;
    @Column(name="original_einzelpreis",precision=19,scale=6) private BigDecimal originalEinzelpreis;
    @Column(name="preis_basis_menge",precision=19,scale=6) private BigDecimal preisBasisMenge;
    @Column(name="nur_preis_korrektur",nullable=false) private boolean nurPreisKorrektur;
    @Column(length=3) private String waehrung;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="kosten_snapshot", nullable=false, columnDefinition="json") private List<Map<String,Object>> kosten=List.of();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="quellen_snapshot", nullable=false, columnDefinition="json") private List<Map<String,Object>> quellen=List.of();
    @Column(nullable=false) private boolean pruefen;
    protected EinkaufBelegPosition() {}
    public EinkaufBelegPosition(LieferantDokument dokument, String nummer, BestellungPosition position, BigDecimal menge,
            Einheit einheit, BigDecimal originalMenge, Einheit originalEinheit, BigDecimal einzelpreis, String waehrung,
            List<Map<String,Object>> kosten, List<Map<String,Object>> quellen, boolean pruefen) {
        this.dokument=dokument; this.originalPositionsnummer=nummer; this.bestellPosition=position; this.menge=menge;
        this.einheit=einheit; this.originalMenge=originalMenge; this.originalEinheit=originalEinheit; this.einzelpreis=einzelpreis; this.waehrung=waehrung;
        this.kosten=kosten==null?List.of():List.copyOf(kosten); this.quellen=quellen==null?List.of():List.copyOf(quellen); this.pruefen=pruefen;
    }
    public Long getId(){return id;} public LieferantDokument getDokument(){return dokument;} public BestellungPosition getBestellPosition(){return bestellPosition;}
    public String getOriginalPositionsnummer(){return originalPositionsnummer;} public BigDecimal getMenge(){return menge;} public Einheit getEinheit(){return einheit;}
    public BigDecimal getOriginalMenge(){return originalMenge;} public Einheit getOriginalEinheit(){return originalEinheit;}
    public BigDecimal getEinzelpreis(){return einzelpreis;} public String getWaehrung(){return waehrung;} public List<Map<String,Object>> getKosten(){return kosten;}
    public List<Map<String,Object>> getQuellen(){return quellen;} public boolean isPruefen(){return pruefen;}
    public void preisBasis(BigDecimal preis,BigDecimal basis,boolean nurPreis){originalEinzelpreis=preis;preisBasisMenge=basis;nurPreisKorrektur=nurPreis;}
    public boolean isNurPreisKorrektur(){return nurPreisKorrektur;}
    public void zuordnen(BestellungPosition position, boolean pruefen){this.bestellPosition=position;this.pruefen=pruefen;}
}
