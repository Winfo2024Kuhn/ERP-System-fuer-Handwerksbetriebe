package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="einkauf_bestellung_herkunft")
public class BestellungHerkunft {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="bestell_position_id", nullable=false) private BestellungPosition position;
    @Column(name="bedarf_id", nullable=false) private Long bedarfId;
    @Column(name="bedarf_version", nullable=false) private long bedarfVersion;
    @Column(name="quell_version", nullable=false) private long quellVersion;
    @Column(name="reservierungs_version", nullable=false) private long reservierungsVersion;
    @Column(nullable=false, precision=19, scale=6) private BigDecimal menge;
    protected BestellungHerkunft() {}
    public BestellungHerkunft(BestellungPosition position, Long bedarfId, long bedarfVersion, BigDecimal menge) {
        this(position,bedarfId,bedarfVersion,menge,bedarfVersion,bedarfVersion);
    }
    public BestellungHerkunft(BestellungPosition position, Long bedarfId, long bedarfVersion, BigDecimal menge,long quellVersion,long reservierungsVersion) {
        this.position=position; this.bedarfId=bedarfId; this.bedarfVersion=bedarfVersion; this.menge=menge;this.quellVersion=quellVersion;this.reservierungsVersion=reservierungsVersion;
    }
    public Long getId(){return id;} public Long getBedarfId(){return bedarfId;} public long getBedarfVersion(){return bedarfVersion;} public void setBedarfVersion(long version){this.bedarfVersion=version;} public long getQuellVersion(){return quellVersion;} public long getReservierungsVersion(){return reservierungsVersion;} public BigDecimal getMenge(){return menge;}
}
