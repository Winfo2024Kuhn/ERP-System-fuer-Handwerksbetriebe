package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

@Entity
@Table(name = "einkauf_analyse_job", uniqueConstraints = @UniqueConstraint(
        name = "uk_einkauf_analyse_job_quelle", columnNames = {"email_id", "anlagen_hash", "parser_version"}))
public class EinkaufAnalyseJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "angebot_id", nullable = false) private EinkaufAngebot angebot;
    @Column(name = "email_id", nullable = false) private Long emailId;
    @Column(name = "email_attachment_id", nullable = false) private Long emailAttachmentId;
    @Column(name = "anlagen_hash", nullable = false, length = 64) private String anlagenHash;
    @Column(name = "parser_version", nullable = false, length = 40) private String parserVersion;
    @Column(nullable = false, length = 20) private String status = "EINGEREIHT";
    @Column(name = "fehler_hinweis", length = 500) private String fehlerHinweis;
    @Column(name = "erstellt_am", nullable = false) private Instant erstelltAm = Instant.now();
    @Column(name = "beendet_am") private Instant beendetAm;
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EinkaufAnalyseVorschlag> vorschlaege = new ArrayList<>();

    protected EinkaufAnalyseJob() {}
    public EinkaufAnalyseJob(EinkaufAngebot angebot, Long emailId, Long attachmentId, String hash, String parserVersion) {
        this.angebot = angebot; this.emailId = emailId; this.emailAttachmentId = attachmentId;
        this.anlagenHash = hash; this.parserVersion = parserVersion;
    }
    public Long getId() { return id; }
    public EinkaufAngebot getAngebot() { return angebot; }
    public Long getEmailId() { return emailId; }
    public Long getEmailAttachmentId() { return emailAttachmentId; }
    public String getAnlagenHash() { return anlagenHash; }
    public String getParserVersion() { return parserVersion; }
    public String getStatus() { return status; }
    public String getFehlerHinweis() { return fehlerHinweis; }
    public Instant getErstelltAm() { return erstelltAm; }
    public Instant getBeendetAm() { return beendetAm; }
    public List<EinkaufAnalyseVorschlag> getVorschlaege() { return List.copyOf(vorschlaege); }
    public void addVorschlag(EinkaufAnalyseVorschlag vorschlag) { vorschlaege.add(vorschlag); }
    public void starten() { status = "LAEUFT"; fehlerHinweis = null; }
    public void erneutEinreihen() {
        if (!"FEHLER".equals(status)) throw new IllegalStateException("Nur fehlgeschlagene Analyseaufträge können erneut eingereiht werden.");
        status = "EINGEREIHT"; beendetAm = null; fehlerHinweis = null;
    }
    public void abschliessen() { status = "FERTIG"; beendetAm = Instant.now(); fehlerHinweis = null; }
    public void fehlschlagen(String hinweis) { status = "FEHLER"; beendetAm = Instant.now(); fehlerHinweis = hinweis; }
}
