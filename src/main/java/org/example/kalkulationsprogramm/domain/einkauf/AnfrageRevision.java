package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "einkaufsanfrage_revision", uniqueConstraints = @UniqueConstraint(name = "uk_anfrage_revision_nummer", columnNames = {"anfrage_id", "nummer"}))
public class AnfrageRevision {
    @OrderBy("id ASC") @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = false) private java.util.List<AnfragePosition> positionen = new java.util.ArrayList<>();
    @OrderBy("id ASC") @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = false) private java.util.List<AnfrageLieferant> lieferanten = new java.util.ArrayList<>();
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "anfrage_id", nullable = false) private Einkaufsanfrage anfrage;
    @Column(nullable = false) private int nummer;
    @Column(name = "antwortfrist") private LocalDate antwortfrist;
    @Column(name = "liefertermin") private LocalDate liefertermin;
    @Column(nullable = false, length = 24) private String status = "ENTWURF";
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "idempotenz_key", nullable = false, length = 36, unique = true) private java.util.UUID idempotenzKey;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    protected AnfrageRevision() {}
    public AnfrageRevision(Einkaufsanfrage anfrage, int nummer, LocalDate antwortfrist, LocalDate liefertermin, java.util.UUID idempotenzKey, String payloadHash) {
        this.anfrage = anfrage; this.nummer = nummer; this.antwortfrist = antwortfrist; this.liefertermin = liefertermin; this.idempotenzKey = idempotenzKey; this.payloadHash = payloadHash;
    }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Einkaufsanfrage getAnfrage() { return anfrage; } public int getNummer() { return nummer; }
    public LocalDate getAntwortfrist() { return antwortfrist; } public LocalDate getLiefertermin() { return liefertermin; }
    public String getStatus() { return status; }
    public java.util.UUID getIdempotenzKey() { return idempotenzKey; }
    public String getPayloadHash() { return payloadHash; }
    public java.util.List<AnfragePosition> getPositionen() { return java.util.List.copyOf(positionen); }
    public java.util.List<AnfrageLieferant> getLieferanten() { return java.util.List.copyOf(lieferanten); }
    public void addPosition(AnfragePosition position) { positionen.add(position); }
    public void addLieferant(AnfrageLieferant lieferant) { lieferanten.add(lieferant); }
}
