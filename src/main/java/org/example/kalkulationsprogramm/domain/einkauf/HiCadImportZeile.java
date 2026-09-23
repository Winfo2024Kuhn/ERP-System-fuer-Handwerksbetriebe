package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "hicad_import_zeile", uniqueConstraints = @UniqueConstraint(name = "uk_hicad_import_zeilennummer", columnNames = {"import_id", "zeilennummer"}))
public class HiCadImportZeile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "import_id", nullable = false) private HiCadImport importVorgang;
    @Column(name = "zeilennummer", nullable = false) private int zeilennummer;
    @Column(name = "rohtext", nullable = false, length = 4000) private String rohtext;
    @Column(name = "snapshot_json", length = 8000) private String snapshotJson;
    @Column(name = "bild_datei_ids_json", length = 4000) private String bildDateiIdsJson;
    @Column(name = "uebernommen", nullable = false) private boolean uebernommen;
    @Column(name = "uebernommene_menge", nullable = false, precision = 15, scale = 6) private BigDecimal uebernommeneMenge = BigDecimal.ZERO;
    protected HiCadImportZeile() {}
    public HiCadImportZeile(int zeilennummer, String rohtext, String snapshotJson) {
        this.zeilennummer = zeilennummer; this.rohtext = rohtext; this.snapshotJson = snapshotJson;
    }
    public Long getId() { return id; }
    public HiCadImport getImportVorgang() { return importVorgang; }
    public void setImportVorgang(HiCadImport value) { importVorgang = value; }
    public int getZeilennummer() { return zeilennummer; }
    public String getRohtext() { return rohtext; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getBildDateiIdsJson() { return bildDateiIdsJson; }
    public boolean isUebernommen() { return uebernommen; }
    public BigDecimal getUebernommeneMenge() { return uebernommeneMenge == null ? BigDecimal.ZERO : uebernommeneMenge; }
    public void setUebernommen(boolean value) { uebernommen = value; }
    public void setUebernommeneMenge(BigDecimal value) { uebernommeneMenge = value == null ? BigDecimal.ZERO : value; }
    public void setSnapshotJson(String value) { snapshotJson = value; }
    public void setBildDateiIdsJson(String value) { bildDateiIdsJson = value; }
}
