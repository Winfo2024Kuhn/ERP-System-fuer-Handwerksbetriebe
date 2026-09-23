package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import java.math.BigDecimal;

@Entity
@Table(name = "einkaufsanfrage_herkunft")
public class AnfrageHerkunft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "position_id", nullable = false) private AnfragePosition position;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bedarf_id", nullable = false) private EinkaufBedarf bedarf;
    @Column(name = "bedarf_version", nullable = false) private long bedarfVersion;
    @Column(nullable = false, precision = 19, scale = 6) private BigDecimal menge;
    protected AnfrageHerkunft() {}
    public AnfrageHerkunft(AnfragePosition position, EinkaufBedarf bedarf, long bedarfVersion, BigDecimal menge) { this.position = position; this.bedarf = bedarf; this.bedarfVersion = bedarfVersion; this.menge = menge; }
    public Long getId() { return id; } public AnfragePosition getPosition() { return position; }
    public EinkaufBedarf getBedarf() { return bedarf; } public long getBedarfVersion() { return bedarfVersion; }
    public BigDecimal getMenge() { return menge; }
}
