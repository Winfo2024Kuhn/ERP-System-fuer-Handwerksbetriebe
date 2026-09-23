package org.example.kalkulationsprogramm.domain.einkauf;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "einkauf_audit")
public class EinkaufAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vorgang_typ", nullable = false, length = 80)
    private String vorgangTyp;

    @Column(name = "vorgang_id", nullable = false)
    private Long vorgangId;

    @Column(nullable = false, length = 80)
    private String aktion;

    @Column(name = "akteur_id", nullable = false)
    private Long akteurId;

    @CreationTimestamp
    @Column(name = "zeitpunkt", nullable = false, updatable = false)
    private Instant zeitpunkt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vorher_snapshot", columnDefinition = "json")
    private JsonNode vorherSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nachher_snapshot", columnDefinition = "json")
    private JsonNode nachherSnapshot;

    @Column(length = 1000)
    private String grund;

    protected EinkaufAudit() {
    }

    public EinkaufAudit(String vorgangTyp, Long vorgangId, String aktion, Long akteurId,
            JsonNode vorherSnapshot, JsonNode nachherSnapshot, String grund) {
        this.vorgangTyp = vorgangTyp;
        this.vorgangId = vorgangId;
        this.aktion = aktion;
        this.akteurId = akteurId;
        this.vorherSnapshot = vorherSnapshot;
        this.nachherSnapshot = nachherSnapshot;
        this.grund = grund;
    }

    public Long getId() { return id; }
    public String getVorgangTyp() { return vorgangTyp; }
    public Long getVorgangId() { return vorgangId; }
    public String getAktion() { return aktion; }
    public Long getAkteurId() { return akteurId; }
    public Instant getZeitpunkt() { return zeitpunkt; }
    public JsonNode getVorherSnapshot() { return vorherSnapshot; }
    public JsonNode getNachherSnapshot() { return nachherSnapshot; }
    public String getGrund() { return grund; }
}
