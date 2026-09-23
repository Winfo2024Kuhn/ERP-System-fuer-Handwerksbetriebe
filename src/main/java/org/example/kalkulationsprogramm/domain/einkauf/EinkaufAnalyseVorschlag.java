package org.example.kalkulationsprogramm.domain.einkauf;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "einkauf_analyse_vorschlag")
public class EinkaufAnalyseVorschlag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false) private EinkaufAnalyseJob job;
    @Column(name = "feldpfad", nullable = false, length = 180) private String feldpfad;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "wert", nullable = false, columnDefinition = "json") private JsonNode wert;
    @Column(name = "seite") private Integer seite;
    @Column(name = "zitat", length = 1000) private String zitat;
    @Column(name = "text_start") private Integer textStart;
    @Column(name = "text_ende") private Integer textEnd;
    @Column(nullable = false, precision = 5, scale = 4) private BigDecimal confidence;
    @Column(nullable = false, length = 500) private String hinweis;
    @Column(name = "quelle_belegt", nullable = false) private boolean quelleBelegt;

    protected EinkaufAnalyseVorschlag() {}
    public EinkaufAnalyseVorschlag(EinkaufAnalyseJob job, String feldpfad, JsonNode wert, Integer seite,
            String zitat, Integer textStart, Integer textEnd, BigDecimal confidence, String hinweis, boolean belegt) {
        this.job = job; this.feldpfad = feldpfad; this.wert = wert.deepCopy(); this.seite = seite;
        this.zitat = zitat; this.textStart = textStart; this.textEnd = textEnd;
        this.confidence = confidence; this.hinweis = hinweis; this.quelleBelegt = belegt;
    }
    public Long getId() { return id; }
    public EinkaufAnalyseJob getJob() { return job; }
    public String getFeldpfad() { return feldpfad; }
    public JsonNode getWert() { return wert == null ? null : wert.deepCopy(); }
    public Integer getSeite() { return seite; }
    public String getZitat() { return zitat; }
    public Integer getTextStart() { return textStart; }
    public Integer getTextEnd() { return textEnd; }
    public BigDecimal getConfidence() { return confidence; }
    public String getHinweis() { return hinweis; }
    public boolean isQuelleBelegt() { return quelleBelegt; }
}
