package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "einkaufsanfrage", uniqueConstraints = {
        @UniqueConstraint(name = "uk_einkaufsanfrage_pa", columnNames = "pa_nummer"),
        @UniqueConstraint(name = "uk_einkaufsanfrage_idempotenz", columnNames = "idempotenz_key")})
public class Einkaufsanfrage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "pa_nummer", nullable = false, length = 32) private String paNummer;
    @Column(name = "zustaendig_id") private Long zustaendigId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "idempotenz_key", nullable = false, length = 36) private UUID idempotenzKey;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "aktuelle_revision_id") private AnfrageRevision aktuelleRevision;
    @Column(name = "angelegt_am", nullable = false, updatable = false) private Instant angelegtAm = Instant.now();
    @Column(name = "geloescht_am") private Instant geloeschtAm;
    protected Einkaufsanfrage() {}
    public Einkaufsanfrage(String paNummer, Long zustaendigId, UUID idempotenzKey, String payloadHash) {
        this.paNummer = paNummer; this.zustaendigId = zustaendigId; this.idempotenzKey = idempotenzKey; this.payloadHash = payloadHash;
    }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; } public void setVersion(Long version) { this.version = version; } public String getPaNummer() { return paNummer; }
    public Long getZustaendigId() { return zustaendigId; } public void setZustaendigId(Long id) { this.zustaendigId = id; }
    public UUID getIdempotenzKey() { return idempotenzKey; } public String getPayloadHash() { return payloadHash; }
    public AnfrageRevision getAktuelleRevision() { return aktuelleRevision; }
    public void setAktuelleRevision(AnfrageRevision revision) { this.aktuelleRevision = revision; }
    public Instant getAngelegtAm() { return angelegtAm; }
    public Instant getGeloeschtAm() { return geloeschtAm; }
    public boolean isGeloescht() { return geloeschtAm != null; }
    public void markiereGeloescht(Instant zeitpunkt) { this.geloeschtAm = zeitpunkt; }
}
