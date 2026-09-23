package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "einkauf_angebot_kosten", uniqueConstraints = @UniqueConstraint(name = "uk_angebot_kosten_key", columnNames = {"version_id", "position_id", "schluessel"}))
public class AngebotKostenbestandteil {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "version_id", nullable = false) private AngebotVersion version;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "position_id") private AngebotPosition position;
    @Column(nullable = false, length = 80) private String schluessel;
    @Column(nullable = false, length = 24) private String art;
    @Column(precision = 19, scale = 6) private BigDecimal betrag;
    @Column(length = 24) private String basis;
    @Column(name = "basis_menge", precision = 19, scale = 6) private BigDecimal basisMenge;
    @Column(name = "prozent_basis_schluessel", length = 80) private String prozentBasisSchluessel;
    @Column(nullable = false) private boolean enthalten;
    @Column(nullable = false) private boolean variabel;
    @Column(length = 500) private String quelle;
    protected AngebotKostenbestandteil() {}
    public AngebotKostenbestandteil(AngebotVersion version, AngebotPosition position, String key, String art,
            BigDecimal betrag, String basis, BigDecimal basisMenge, String basisKey, boolean enthalten, boolean variabel, String quelle) {
        this.version = version; this.position = position; this.schluessel = key; this.art = art; this.betrag = betrag;
        this.basis = basis; this.basisMenge = basisMenge; this.prozentBasisSchluessel = basisKey;
        this.enthalten = enthalten; this.variabel = variabel; this.quelle = quelle;
    }
    public Long getId() { return id; } public String getSchluessel() { return schluessel; } public String getArt() { return art; }
    public Long getPositionId() { return position == null ? null : position.getId(); }
    public BigDecimal getBetrag() { return betrag; } public String getBasis() { return basis; } public BigDecimal getBasisMenge() { return basisMenge; }
    public String getProzentBasisSchluessel() { return prozentBasisSchluessel; } public boolean isEnthalten() { return enthalten; }
    public boolean isVariabel() { return variabel; } public String getQuelle() { return quelle; }
}
