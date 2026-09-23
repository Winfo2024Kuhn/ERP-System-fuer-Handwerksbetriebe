package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufMengenService.Mengenaktion;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "einkauf_mengenbuchung", uniqueConstraints = @UniqueConstraint(name = "uk_einkauf_mengen_idempotenz",
        columnNames = {"idempotenz_key", "bedarf_id"}))
public class EinkaufMengenbuchung {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bedarf_id", nullable = false)
    private EinkaufBedarf bedarf;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('RESERVIEREN','RESERVIERUNG_FREIGEBEN','BESTELLEN','LAGER_ENTNEHMEN','STORNO_BESTAETIGEN','LIEFERN')")
    private Mengenaktion aktion;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal menge;
    @Column(name = "vorgangsschluessel", nullable = false, length = 128)
    private String vorgangsschluessel;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "idempotenz_key", nullable = false, length = 36)
    private UUID idempotenzKey;
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;
    @Column(name = "akteur_id", nullable = false)
    private Long akteurId;
    @CreationTimestamp
    @Column(name = "zeitpunkt", nullable = false, updatable = false)
    private Instant zeitpunkt;

    protected EinkaufMengenbuchung() {}
    public EinkaufMengenbuchung(EinkaufBedarf bedarf, Mengenaktion aktion, BigDecimal menge,
            String vorgangsschluessel, UUID idempotenzKey, String payloadHash, Long akteurId) {
        this.bedarf = bedarf; this.aktion = aktion; this.menge = menge;
        this.vorgangsschluessel = vorgangsschluessel; this.idempotenzKey = idempotenzKey;
        this.payloadHash = payloadHash; this.akteurId = akteurId;
    }
    public Long getId() { return id; }
    public EinkaufBedarf getBedarf() { return bedarf; }
    public Mengenaktion getAktion() { return aktion; }
    public BigDecimal getMenge() { return menge; }
    public String getVorgangsschluessel() { return vorgangsschluessel; }
    public UUID getIdempotenzKey() { return idempotenzKey; }
    public String getPayloadHash() { return payloadHash; }
    public Long getAkteurId() { return akteurId; }
    public Instant getZeitpunkt() { return zeitpunkt; }
}
