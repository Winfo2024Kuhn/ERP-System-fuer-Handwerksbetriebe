package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "einkauf_angebot", uniqueConstraints = @UniqueConstraint(name = "uk_einkauf_angebot_beteiligung", columnNames = "beteiligung_id"))
public class EinkaufAngebot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private Long version;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "beteiligung_id", nullable = false) private AnfrageLieferant beteiligung;
    @Column(nullable = false, length = 24) private String status = "ERFASST";
    @OneToMany(mappedBy = "angebot", cascade = CascadeType.ALL) @OrderBy("nummer ASC") private List<AngebotVersion> versionen = new ArrayList<>();
    protected EinkaufAngebot() {}
    public EinkaufAngebot(AnfrageLieferant beteiligung) { this.beteiligung = beteiligung; }
    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public AnfrageLieferant getBeteiligung() { return beteiligung; }
    public String getStatus() { return status; }
    public List<AngebotVersion> getVersionen() { return List.copyOf(versionen); }
    public void addVersion(AngebotVersion version) { versionen.add(version); }
    public void setStatus(String status) { this.status = status; }
}
