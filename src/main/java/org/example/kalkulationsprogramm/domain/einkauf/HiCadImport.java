package org.example.kalkulationsprogramm.domain.einkauf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "hicad_import", indexes = @Index(name = "idx_hicad_hash_projekt", columnList = "projekt_id,datei_hash"))
public class HiCadImport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "projekt_id", nullable = false) private Long projektId;
    @Column(name = "datei_hash", nullable = false, length = 64) private String dateiHash;
    @Column(name = "import_instanz", nullable = false, length = 36) private String importInstanz = UUID.randomUUID().toString();
    @Column(name = "akteur_id", nullable = false) private Long akteurId;
    @Column(name = "duplikat", nullable = false) private boolean duplikat;
    @Column(name = "idempotenz_key", length = 36) private String idempotenzKey;
    @Column(name = "payload_hash", length = 64) private String payloadHash;
    @Column(name = "result_json", length = 20000) private String resultJson;
    @OneToMany(mappedBy = "importVorgang", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("zeilennummer ASC") private List<HiCadImportZeile> zeilen = new ArrayList<>();
    protected HiCadImport() {}
    public HiCadImport(Long projektId, String dateiHash, Long akteurId, boolean duplikat) {
        this.projektId = projektId; this.dateiHash = dateiHash; this.akteurId = akteurId; this.duplikat = duplikat;
    }
    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public Long getProjektId() { return projektId; }
    public String getDateiHash() { return dateiHash; }
    public String getImportInstanz() { return importInstanz; }
    public Long getAkteurId() { return akteurId; }
    public boolean isDuplikat() { return duplikat; }
    public String getIdempotenzKey() { return idempotenzKey; }
    public void setIdempotenzKey(String value) { idempotenzKey = value; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String value) { payloadHash = value; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String value) { resultJson = value; }
    public List<HiCadImportZeile> getZeilen() { return zeilen; }
    public void addZeile(HiCadImportZeile zeile) { zeile.setImportVorgang(this); zeilen.add(zeile); }
}
