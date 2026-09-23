package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "einkauf_kommunikation_vorschau")
public class EinkaufKommunikationVorschau {
    @Id
    @Column(name = "freigabe_token", length = 64, updatable = false)
    private String freigabeToken;
    @Column(name = "anfrage_id", nullable = false, updatable = false) private Long anfrageId;
    @Column(name = "beteiligung_id", nullable = false, updatable = false) private Long beteiligungId;
    @Column(name = "revision_id", nullable = false, updatable = false) private Long revisionId;
    @Column(name = "vorlage_id", nullable = false, updatable = false) private Long vorlageId;
    @Column(name = "vorlage_version", nullable = false, updatable = false) private Long vorlageVersion;
    @Column(name = "subject", nullable = false, length = 998, updatable = false) private String subject;
    @Column(name = "html_body", nullable = false, columnDefinition = "LONGTEXT", updatable = false) private String htmlBody;
    @Column(name = "empfaenger", nullable = false, length = 1000, updatable = false) private String empfaenger;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "anlage_version_ids", nullable = false, columnDefinition = "json", updatable = false)
    private List<Long> anlageVersionIds;
    @Column(name = "pdf_datei_id", nullable = false, updatable = false) private Long pdfDateiId;
    @Column(name = "pdf_sha256", nullable = false, length = 64, updatable = false) private String pdfSha256;
    @Column(name = "inhalt_sha256", nullable = false, length = 64, updatable = false) private String inhaltSha256;
    @Column(name = "erstellt_am", nullable = false, updatable = false) private Instant erstelltAm;
    @Column(name = "gueltig_bis", nullable = false, updatable = false) private Instant gueltigBis;

    protected EinkaufKommunikationVorschau() {}

    public EinkaufKommunikationVorschau(String token, Long anfrageId, Long beteiligungId, Long revisionId,
            Long vorlageId, Long vorlageVersion, String subject, String htmlBody, String empfaenger,
            List<Long> anlageVersionIds, Long pdfDateiId, String pdfSha256, String inhaltSha256,
            Instant erstelltAm, Instant gueltigBis) {
        this.freigabeToken = token;
        this.anfrageId = anfrageId;
        this.beteiligungId = beteiligungId;
        this.revisionId = revisionId;
        this.vorlageId = vorlageId;
        this.vorlageVersion = vorlageVersion;
        this.subject = subject;
        this.htmlBody = htmlBody;
        this.empfaenger = empfaenger;
        this.anlageVersionIds = List.copyOf(anlageVersionIds);
        this.pdfDateiId = pdfDateiId;
        this.pdfSha256 = pdfSha256;
        this.inhaltSha256 = inhaltSha256;
        this.erstelltAm = erstelltAm;
        this.gueltigBis = gueltigBis;
    }

    public String getFreigabeToken() { return freigabeToken; }
    public Long getAnfrageId() { return anfrageId; }
    public Long getBeteiligungId() { return beteiligungId; }
    public Long getRevisionId() { return revisionId; }
    public Long getVorlageId() { return vorlageId; }
    public Long getVorlageVersion() { return vorlageVersion; }
    public String getSubject() { return subject; }
    public String getHtmlBody() { return htmlBody; }
    public String getEmpfaenger() { return empfaenger; }
    public List<Long> getAnlageVersionIds() { return anlageVersionIds == null ? List.of() : List.copyOf(anlageVersionIds); }
    public Long getPdfDateiId() { return pdfDateiId; }
    public String getPdfSha256() { return pdfSha256; }
    public String getInhaltSha256() { return inhaltSha256; }
    public Instant getErstelltAm() { return erstelltAm; }
    public Instant getGueltigBis() { return gueltigBis; }
}
