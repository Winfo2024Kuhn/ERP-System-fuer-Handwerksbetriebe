package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.security.SecureRandom;
import java.util.HexFormat;

@Entity
@Table(name = "einkaufsanfrage_lieferant", uniqueConstraints = @UniqueConstraint(name = "uk_anfrage_reply_code", columnNames = "rueckmeldecode"))
public class AnfrageLieferant {
    private static final SecureRandom RANDOM = new SecureRandom();
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "revision_id", nullable = false) private AnfrageRevision revision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "kontakt_snapshot", nullable = false, columnDefinition = "json") private Snapshot kontakt;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "rueckmeldecode", nullable = false, length = 32, unique = true) private String rueckmeldecode = neuerCode();
    @Column(nullable = false, length = 24) private String status = "AUSSTEHEND";
    @Column(name = "antwort_am") private java.time.Instant antwortAm;
    @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "versand_annahmeereignis", length = 36) private java.util.UUID versandAnnahmeereignis;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "versandversuche", nullable = false, columnDefinition = "json") private java.util.List<String> versandversuche = new java.util.ArrayList<>();
    protected AnfrageLieferant() {}
    public AnfrageLieferant(AnfrageRevision revision, Snapshot kontakt) { this.revision = revision; this.kontakt = kontakt; }
    private static String neuerCode() { byte[] bytes = new byte[16]; RANDOM.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    public Long getId() { return id; } public Long getVersion() { return version; } public void setVersion(Long version) { this.version = version; } public AnfrageRevision getRevision() { return revision; }
    public Snapshot getKontakt() { return kontakt; } public String getRueckmeldecode() { return rueckmeldecode; }
    public String getStatus() { return status; } public java.time.Instant getAntwortAm() { return antwortAm; }
    public java.util.List<String> getVersandversuche() { return java.util.List.copyOf(versandversuche); }
    public void setStatus(String status) { this.status = status; }
    public void setAntwortAm(java.time.Instant antwortAm) { this.antwortAm = antwortAm; }
    public java.util.UUID getVersandAnnahmeereignis() { return versandAnnahmeereignis; }
    public void versandAngenommen(java.util.UUID ereignisSchluessel) {
        this.versandAnnahmeereignis = ereignisSchluessel;
        this.status = "VERSENDET";
    }
}
