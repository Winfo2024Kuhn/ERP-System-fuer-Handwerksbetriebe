package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "einkauf_angebot_version", uniqueConstraints = @UniqueConstraint(name = "uk_angebot_version_nummer", columnNames = {"angebot_id", "nummer"}))
public class AngebotVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "angebot_id", nullable = false) private EinkaufAngebot angebot;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "anfrage_revision_id", nullable = false) private AnfrageRevision anfrageRevision;
    @Column(nullable = false) private int nummer;
    @Column(nullable = false, length = 24) private String status = "ERFASST";
    @Column(name = "angebotsnummer", length = 120) private String angebotsnummer;
    private LocalDate datum;
    @Column(name = "gueltig_bis") private LocalDate gueltigBis;
    @Column(nullable = false, length = 3) private String waehrung;
    @Column(name = "zahlungsbedingungen", length = 2000) private String zahlungsbedingungen;
    @Column(name = "skonto_prozent", precision = 9, scale = 6) private BigDecimal skontoProzent;
    @Column(name = "skonto_tage") private Integer skontoTage;
    @Column(name = "email_id") private Long emailId;
    @Column(name = "original_datei_id") private Long originalDateiId;
    @Column(name = "bestaetigt_von") private Long bestaetigtVon;
    @Column(name = "bestaetigt_am") private Instant bestaetigtAm;
    @Column(name = "abweichung_bestaetigt_von") private Long abweichungBestaetigtVon;
    @Column(name = "abweichung_bestaetigt_am") private Instant abweichungBestaetigtAm;
    @Column(name = "abweichung_bestaetigung", length = 1000) private String abweichungBestaetigung;
    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL) @OrderBy("id ASC") private List<AngebotPosition> positionen = new ArrayList<>();
    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL) @OrderBy("id ASC") private List<AngebotKostenbestandteil> kosten = new ArrayList<>();
    protected AngebotVersion() {}
    public AngebotVersion(EinkaufAngebot angebot, AnfrageRevision revision, int nummer, String nummerExtern,
            LocalDate datum, LocalDate gueltigBis, String waehrung, String zahlungsbedingungen,
            BigDecimal skontoProzent, Integer skontoTage, Long emailId, Long originalDateiId) {
        this.angebot = angebot; this.anfrageRevision = revision; this.nummer = nummer; this.angebotsnummer = nummerExtern;
        this.datum = datum; this.gueltigBis = gueltigBis; this.waehrung = waehrung; this.zahlungsbedingungen = zahlungsbedingungen;
        this.skontoProzent = skontoProzent; this.skontoTage = skontoTage; this.emailId = emailId; this.originalDateiId = originalDateiId;
    }
    public Long getId() { return id; } public Long getVersion() { return version; } public EinkaufAngebot getAngebot() { return angebot; }
    public AnfrageRevision getAnfrageRevision() { return anfrageRevision; } public int getNummer() { return nummer; }
    public String getStatus() { return status; } public String getAngebotsnummer() { return angebotsnummer; }
    public LocalDate getDatum() { return datum; } public LocalDate getGueltigBis() { return gueltigBis; }
    public String getWaehrung() { return waehrung; } public String getZahlungsbedingungen() { return zahlungsbedingungen; }
    public BigDecimal getSkontoProzent() { return skontoProzent; } public Integer getSkontoTage() { return skontoTage; }
    public Long getEmailId() { return emailId; } public Long getOriginalDateiId() { return originalDateiId; }
    public Long getBestaetigtVon() { return bestaetigtVon; }
    public Long getAbweichungBestaetigtVon() { return abweichungBestaetigtVon; }
    public Instant getAbweichungBestaetigtAm() { return abweichungBestaetigtAm; }
    public String getAbweichungBestaetigung() { return abweichungBestaetigung; }
    public List<AngebotPosition> getPositionen() { return List.copyOf(positionen); }
    public List<AngebotKostenbestandteil> getKosten() { return List.copyOf(kosten); }
    public void addPosition(AngebotPosition position) { positionen.add(position); }
    public void addKosten(AngebotKostenbestandteil kosten) { this.kosten.add(kosten); }
    public void bestaetigen(Long userId) { status = "GEPRUEFT"; bestaetigtVon = userId; bestaetigtAm = Instant.now(); }
    public void bestaetigeAbweichung(Long userId, String begruendung) {
        if (abweichungBestaetigtVon != null) {
            if (abweichungBestaetigtVon.equals(userId) && abweichungBestaetigung.equals(begruendung)) return;
            throw new IllegalStateException("Die technische Abweichung wurde bereits ausdrücklich bestätigt.");
        }
        if (!"GEPRUEFT".equals(status) || userId == null || userId <= 0 || begruendung == null || begruendung.isBlank())
            throw new IllegalStateException("Die technische Abweichung kann erst nach Angebotsprüfung ausdrücklich bestätigt werden.");
        if (positionen.stream().noneMatch(p -> p.getAbweichungen() != null && !p.getAbweichungen().isEmpty()))
            throw new IllegalStateException("Dieses Angebot enthält keine technische Abweichung.");
        abweichungBestaetigtVon = userId; abweichungBestaetigtAm = Instant.now(); abweichungBestaetigung = begruendung;
    }
    public void abloesen() { status = "ABGELOEST"; }
}
