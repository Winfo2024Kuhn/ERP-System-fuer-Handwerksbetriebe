package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "einkauf_anforderungsvorlage")
public class EinkaufAnforderungsVorlage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "artikel_id") private Long artikelId;
    @Column(name = "projekt_id") private Long projektId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "enum('ZEUGNIS_2_1','ZEUGNIS_2_2','ZEUGNIS_3_1','ZEUGNIS_3_2','LEISTUNGSERKLAERUNG','CE_NACHWEIS')")
    private Dokumentart art;
    @Column(nullable = false, length = 1000) private String grundlage;
    @Column(name = "versionsnummer", nullable = false) private int versionsnummer;
    @Column(name = "bestaetigt_von", nullable = false) private Long bestaetigtVon;
    @Column(name = "bestaetigt_am", nullable = false) private Instant bestaetigtAm;
    @Column(name = "aktiv", nullable = false) private boolean aktiv = true;

    protected EinkaufAnforderungsVorlage() {}
    public EinkaufAnforderungsVorlage(Long artikelId, Long projektId, Dokumentart art, String grundlage,
            int versionsnummer, Long bestaetigtVon, Instant bestaetigtAm) {
        this.artikelId=artikelId; this.projektId=projektId; this.art=art; this.grundlage=grundlage;
        this.versionsnummer=versionsnummer; this.bestaetigtVon=bestaetigtVon; this.bestaetigtAm=bestaetigtAm;
    }
    public Long getId(){return id;} public Long getArtikelId(){return artikelId;} public Long getProjektId(){return projektId;}
    public Dokumentart getArt(){return art;} public String getGrundlage(){return grundlage;}
    public int getVersionsnummer(){return versionsnummer;} public Long getBestaetigtVon(){return bestaetigtVon;}
    public Instant getBestaetigtAm(){return bestaetigtAm;} public boolean isAktiv(){return aktiv;}
}
