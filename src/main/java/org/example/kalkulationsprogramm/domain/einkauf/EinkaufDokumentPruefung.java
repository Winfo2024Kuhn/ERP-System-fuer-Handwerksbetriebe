package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "einkauf_dokument_pruefung")
public class EinkaufDokumentPruefung {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "zeugnis_id", nullable = false)
    private EinkaufZeugnisErwartung zeugnis;
    @Column(name = "akteur_id", nullable = false) private Long akteurId;
    @Column(name = "geprueft_am", nullable = false) private Instant geprueftAm;
    @Column(nullable = false, length = 32) private String ergebnis;
    @Column(nullable = false, length = 2000) private String begruendung;
    @Column(name = "grundlage_version", nullable = false, length = 120) private String grundlageVersion;

    protected EinkaufDokumentPruefung() {}
    public EinkaufDokumentPruefung(EinkaufZeugnisErwartung zeugnis, Long akteurId, Instant zeit,
            String ergebnis, String begruendung, String grundlageVersion) {
        this.zeugnis = zeugnis; this.akteurId = akteurId; this.geprueftAm = zeit;
        this.ergebnis = ergebnis; this.begruendung = begruendung; this.grundlageVersion = grundlageVersion;
    }
    public Long getId() { return id; }
    public EinkaufZeugnisErwartung getZeugnis() { return zeugnis; }
    public Long getAkteurId() { return akteurId; }
    public Instant getGeprueftAm() { return geprueftAm; }
    public String getErgebnis() { return ergebnis; }
    public String getBegruendung() { return begruendung; }
    public String getGrundlageVersion() { return grundlageVersion; }
}
