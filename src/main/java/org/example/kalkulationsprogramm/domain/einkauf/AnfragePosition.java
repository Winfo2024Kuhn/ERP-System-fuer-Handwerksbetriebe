package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

@Entity
@Table(name = "einkaufsanfrage_position")
public class AnfragePosition {
    @OrderBy("id ASC") @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, orphanRemoval = false) private java.util.List<AnfrageHerkunft> herkuenfte = new java.util.ArrayList<>();
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "revision_id", nullable = false) private AnfrageRevision revision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "position_snapshot", nullable = false, columnDefinition = "json") private PositionSnapshot snapshot;
    @Column(nullable = false, precision = 19, scale = 6) private BigDecimal menge;
    protected AnfragePosition() {}
    public AnfragePosition(AnfrageRevision revision, PositionSnapshot snapshot, BigDecimal menge) { this.revision = revision; this.snapshot = snapshot; this.menge = menge; }
    public Long getId() { return id; } public AnfrageRevision getRevision() { return revision; }
    public PositionSnapshot getSnapshot() { return snapshot; } public BigDecimal getMenge() { return menge; }
    public java.util.List<AnfrageHerkunft> getHerkuenfte() { return java.util.List.copyOf(herkuenfte); }
    public void addHerkunft(AnfrageHerkunft herkunft) { herkuenfte.add(herkunft); }
}
