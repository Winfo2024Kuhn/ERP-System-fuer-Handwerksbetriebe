package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "einkauf_bedarf", uniqueConstraints = @UniqueConstraint(name = "uk_einkauf_bedarf_projekt_kennung",
        columnNames = {"projekt_id", "interne_kennung"}))
public class EinkaufBedarf {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "position_snapshot", nullable = false, columnDefinition = "json")
    private PositionSnapshot position;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "liefergruppe_snapshot", nullable = false, columnDefinition = "json")
    private Liefergruppe liefergruppe;

    @Column(name = "projekt_id")
    private Long projektId;

    @Column(name = "interne_kennung", length = 128)
    private String interneKennung;

    @Column(name = "bezeichnung", nullable = false, length = 255)
    private String bezeichnung;

    @Column(name = "artikel_in_projekt_id", unique = true)
    private Long artikelInProjektId;

    @Column(name = "bedarf_menge", precision = 19, scale = 6)
    private BigDecimal bedarfMenge;

    @Column(name = "lagergedeckt", nullable = false, precision = 19, scale = 6)
    private BigDecimal lagergedeckt = BigDecimal.ZERO;
    @Column(name = "reserviert", nullable = false, precision = 19, scale = 6)
    private BigDecimal reserviert = BigDecimal.ZERO;
    @Column(name = "bestellt", nullable = false, precision = 19, scale = 6)
    private BigDecimal bestellt = BigDecimal.ZERO;
    @Column(name = "geliefert", nullable = false, precision = 19, scale = 6)
    private BigDecimal geliefert = BigDecimal.ZERO;
    @Column(name = "storniert", nullable = false, precision = 19, scale = 6)
    private BigDecimal storniert = BigDecimal.ZERO;

    @Column(name = "nachpflege_erforderlich", nullable = false)
    private boolean nachpflegeErforderlich;
    @Column(name = "historisch_bestellt", nullable = false)
    private boolean historischBestellt;
    @Column(name = "historisch_aus_lager", nullable = false)
    private boolean historischAusLager;
    @Column(name = "historischer_hinweis", length = 500)
    private String historischerHinweis;

    protected EinkaufBedarf() {}

    public EinkaufBedarf(PositionSnapshot position, Liefergruppe liefergruppe, Long projektId,
            Long artikelInProjektId, boolean nachpflegeErforderlich) {
        this.position = position;
        this.liefergruppe = liefergruppe;
        this.projektId = projektId;
        this.artikelInProjektId = artikelInProjektId;
        this.interneKennung = position != null && position.art() == Positionsart.ZEICHNUNGSTEIL
                ? position.interneReferenz() : null;
        this.bezeichnung = position == null || position.bezeichnung() == null ? "Nachpflege erforderlich"
                : position.bezeichnung();
        this.bedarfMenge = position == null || position.basis() == null ? null : position.basis().menge();
        this.nachpflegeErforderlich = nachpflegeErforderlich || bedarfMenge == null;
    }

    public BigDecimal ungedeckt() {
        if (bedarfMenge == null) return BigDecimal.ZERO;
        return bedarfMenge.subtract(zero(lagergedeckt)).subtract(zero(bestellt)).max(BigDecimal.ZERO);
    }
    public BigDecimal disponierbar() { return ungedeckt().subtract(zero(reserviert)).max(BigDecimal.ZERO); }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public PositionSnapshot getPosition() { return position; }
    public void setPosition(PositionSnapshot position) { this.position = position; }
    public Liefergruppe getLiefergruppe() { return liefergruppe; }
    public void setLiefergruppe(Liefergruppe liefergruppe) { this.liefergruppe = liefergruppe; }
    public Long getProjektId() { return projektId; }
    public void setProjektId(Long projektId) { this.projektId = projektId; }
    public String getInterneKennung() { return interneKennung; }
    public void setInterneKennung(String value) { this.interneKennung = value; }
    public String getBezeichnung() { return bezeichnung; }
    public void setBezeichnung(String value) { this.bezeichnung = value; }
    public Long getArtikelInProjektId() { return artikelInProjektId; }
    public void setArtikelInProjektId(Long artikelInProjektId) { this.artikelInProjektId = artikelInProjektId; }
    public BigDecimal getBedarfMenge() { return bedarfMenge; }
    public void setBedarfMenge(BigDecimal value) { this.bedarfMenge = value; }
    public BigDecimal getLagergedeckt() { return zero(lagergedeckt); }
    public void setLagergedeckt(BigDecimal value) { this.lagergedeckt = value; }
    public BigDecimal getReserviert() { return zero(reserviert); }
    public void setReserviert(BigDecimal value) { this.reserviert = value; }
    public BigDecimal getBestellt() { return zero(bestellt); }
    public void setBestellt(BigDecimal value) { this.bestellt = value; }
    public BigDecimal getGeliefert() { return zero(geliefert); }
    public void setGeliefert(BigDecimal value) { this.geliefert = value; }
    public BigDecimal getStorniert() { return zero(storniert); }
    public void setStorniert(BigDecimal value) { this.storniert = value; }
    public boolean isNachpflegeErforderlich() { return nachpflegeErforderlich; }
    public void setNachpflegeErforderlich(boolean value) { this.nachpflegeErforderlich = value; }
    public boolean isHistorischBestellt() { return historischBestellt; }
    public void setHistorischBestellt(boolean value) { this.historischBestellt = value; }
    public boolean isHistorischAusLager() { return historischAusLager; }
    public void setHistorischAusLager(boolean value) { this.historischAusLager = value; }
    public String getHistorischerHinweis() { return historischerHinweis; }
    public void setHistorischerHinweis(String value) { this.historischerHinweis = value; }
}
