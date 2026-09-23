package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;

@Entity
@Table(name = "einkauf_anlage_version", uniqueConstraints = @UniqueConstraint(name = "uk_einkauf_anlage_revision", columnNames = {"bedarf_id", "revision"}))
public class EinkaufAnlageVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bedarf_id", nullable = false)
    private EinkaufBedarf bedarf;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "datei_id", nullable = false)
    private EinkaufDatei datei;
    @Column(name = "revision", nullable = false, length = 80)
    private String revision;
    @Column(name = "freigegeben", nullable = false)
    private boolean freigegeben;
    @Column(name = "versendet", nullable = false)
    private boolean versendet;

    protected EinkaufAnlageVersion() {}
    public EinkaufAnlageVersion(EinkaufBedarf bedarf, EinkaufDatei datei, String revision) {
        this.bedarf = bedarf; this.datei = datei; this.revision = revision;
    }
    public Long getId() { return id; }
    public EinkaufBedarf getBedarf() { return bedarf; }
    public EinkaufDatei getDatei() { return datei; }
    public String getRevision() { return revision; }
    public boolean isFreigegeben() { return freigegeben; }
    public boolean isVersendet() { return versendet; }
    public void setFreigegeben(boolean freigegeben) { this.freigegeben = freigegeben; }
    public void setVersendet(boolean versendet) { this.versendet = versendet; }
}
