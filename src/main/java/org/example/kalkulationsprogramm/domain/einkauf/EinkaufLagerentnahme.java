package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "einkauf_lagerentnahme", uniqueConstraints =
        @UniqueConstraint(name = "uk_lagerentnahme_idempotenz", columnNames = "idempotenz_key"))
public class EinkaufLagerentnahme {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "projekt_id", nullable = false)
    private Long projektId;

    @Column(name = "bedarf_id", nullable = false)
    private Long bedarfId;

    @Column(name = "bedarf_version", nullable = false)
    private long bedarfVersion;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal menge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Einheit einheit;

    @Column(name = "preis_je_einheit", precision = 19, scale = 6)
    private BigDecimal preisJeEinheit;

    @Column(name = "preis_quelle", length = 255)
    private String preisQuelle;

    @Column(name = "bewerteter_betrag", precision = 19, scale = 2)
    private BigDecimal bewerteterBetrag;

    @Column(name = "offener_bedarf_nach_entnahme", nullable = false, precision = 19, scale = 6)
    private BigDecimal offenerBedarf;

    @Column(name = "entnommen_am", nullable = false)
    private Instant entnommenAm;

    @Column(name = "idempotenz_key", nullable = false, columnDefinition = "char(36)")
    private UUID idempotenzKey;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "akteur_id", nullable = false)
    private Long akteurId;

    @Column(name = "mitarbeiter_id", nullable = false)
    private Long mitarbeiterId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "einkauf_lagerentnahme_bewertung",
            joinColumns = @JoinColumn(name = "lagerentnahme_id"))
    @OrderColumn(name = "reihenfolge")
    private List<Bewertung> bewertungen = new ArrayList<>();

    protected EinkaufLagerentnahme() {}

    public EinkaufLagerentnahme(Long projektId, Long bedarfId, long bedarfVersion, BigDecimal menge,
            Einheit einheit, BigDecimal preisJeEinheit, String preisQuelle, BigDecimal offenerBedarf,
            Instant entnommenAm, UUID idempotenzKey, String payloadHash, Long akteurId, Long mitarbeiterId) {
        this.projektId = projektId;
        this.bedarfId = bedarfId;
        this.bedarfVersion = bedarfVersion;
        this.menge = menge;
        this.einheit = einheit;
        this.offenerBedarf = offenerBedarf;
        this.entnommenAm = entnommenAm;
        this.idempotenzKey = idempotenzKey;
        this.payloadHash = payloadHash;
        this.akteurId = akteurId;
        this.mitarbeiterId = mitarbeiterId;
        if (preisJeEinheit != null) fuegeBewertungHinzu(preisJeEinheit, preisQuelle, entnommenAm, akteurId, mitarbeiterId);
    }

    public void fuegeBewertungHinzu(BigDecimal preis, String quelle, Instant zeit, Long akteur, Long mitarbeiter) {
        bewertungen.add(new Bewertung(preis, quelle, zeit, akteur, mitarbeiter));
        preisJeEinheit = preis;
        preisQuelle = quelle;
        bewerteterBetrag = preis.multiply(menge).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isBewertungOffen() { return preisJeEinheit == null; }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjektId() { return projektId; }
    public Long getBedarfId() { return bedarfId; }
    public long getBedarfVersion() { return bedarfVersion; }
    public BigDecimal getMenge() { return menge; }
    public Einheit getEinheit() { return einheit; }
    public BigDecimal getPreisJeEinheit() { return preisJeEinheit; }
    public String getPreisQuelle() { return preisQuelle; }
    public BigDecimal getBewerteterBetrag() { return bewerteterBetrag; }
    public BigDecimal getOffenerBedarf() { return offenerBedarf; }
    public Instant getEntnommenAm() { return entnommenAm; }
    public UUID getIdempotenzKey() { return idempotenzKey; }
    public String getPayloadHash() { return payloadHash; }
    public Long getAkteurId() { return akteurId; }
    public Long getMitarbeiterId() { return mitarbeiterId; }
    public List<Bewertung> getBewertungen() { return List.copyOf(bewertungen); }

    @Embeddable
    public static class Bewertung {
        @Column(name = "preis_je_einheit", nullable = false, precision = 19, scale = 6)
        private BigDecimal preisJeEinheit;
        @Column(name = "preis_quelle", nullable = false, length = 255)
        private String preisQuelle;
        @Column(name = "bewertet_am", nullable = false)
        private Instant bewertetAm;
        @Column(name = "akteur_id", nullable = false)
        private Long akteurId;
        @Column(name = "mitarbeiter_id", nullable = false)
        private Long mitarbeiterId;

        protected Bewertung() {}
        public Bewertung(BigDecimal preisJeEinheit, String preisQuelle, Instant bewertetAm,
                Long akteurId, Long mitarbeiterId) {
            this.preisJeEinheit = preisJeEinheit;
            this.preisQuelle = preisQuelle;
            this.bewertetAm = bewertetAm;
            this.akteurId = akteurId;
            this.mitarbeiterId = mitarbeiterId;
        }
        public BigDecimal getPreisJeEinheit() { return preisJeEinheit; }
        public String getPreisQuelle() { return preisQuelle; }
        public Instant getBewertetAm() { return bewertetAm; }
        public Long getAkteurId() { return akteurId; }
        public Long getMitarbeiterId() { return mitarbeiterId; }
    }
}
