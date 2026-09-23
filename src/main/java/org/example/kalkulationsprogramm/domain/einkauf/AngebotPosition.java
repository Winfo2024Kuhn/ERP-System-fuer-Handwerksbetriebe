package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.ZeugnisZusage;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "einkauf_angebot_position")
public class AngebotPosition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "version_id", nullable = false) private AngebotVersion version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "anfrage_position_id", nullable = false) private AnfragePosition anfragePosition;
    @Column(name = "original_nummer", length = 120) private String originalNummer;
    @Column(name = "original_text", length = 2000) private String originalText;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "angebotene_menge", columnDefinition = "json") private Mengenbasis angeboten;
    @Column(name = "mindestmenge", precision = 19, scale = 6) private BigDecimal mindestmenge;
    @Column(name = "verpackungseinheit", precision = 19, scale = 6) private BigDecimal verpackungseinheit;
    @Column(name = "liefertermin") private LocalDate liefertermin;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "abweichungen", nullable = false, columnDefinition = "json") private List<String> abweichungen;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "zeugnisse", nullable = false, columnDefinition = "json") private List<ZeugnisZusage> zeugnisse;
    protected AngebotPosition() {}
    public AngebotPosition(AngebotVersion version, AnfragePosition requestPosition, String originalNummer, String originalText,
            Mengenbasis angeboten, BigDecimal mindestmenge, BigDecimal verpackungseinheit, LocalDate liefertermin,
            List<String> abweichungen, List<ZeugnisZusage> zeugnisse) {
        this.version = version; this.anfragePosition = requestPosition; this.originalNummer = originalNummer;
        this.originalText = originalText; this.angeboten = angeboten; this.mindestmenge = mindestmenge;
        this.verpackungseinheit = verpackungseinheit; this.liefertermin = liefertermin;
        this.abweichungen = abweichungen == null ? List.of() : List.copyOf(abweichungen);
        this.zeugnisse = zeugnisse == null ? List.of() : List.copyOf(zeugnisse);
    }
    public Long getId() { return id; } public Long getAnfragePositionId() { return anfragePosition.getId(); }
    public AnfragePosition getAnfragePosition() { return anfragePosition; }
    public String getOriginalNummer() { return originalNummer; } public String getOriginalText() { return originalText; }
    public Mengenbasis getAngeboten() { return angeboten; } public BigDecimal getMindestmenge() { return mindestmenge; }
    public BigDecimal getVerpackungseinheit() { return verpackungseinheit; } public LocalDate getLiefertermin() { return liefertermin; }
    public List<String> getAbweichungen() { return abweichungen; } public List<ZeugnisZusage> getZeugnisse() { return zeugnisse; }
}
