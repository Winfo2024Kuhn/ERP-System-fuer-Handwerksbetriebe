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
@Table(name = "einkauf_mailantwort_vorschau")
public class EinkaufMailantwortVorschau {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "freigabe_token", length = 64, updatable = false) private String freigabeToken;
    @Column(name = "email_id", nullable = false, updatable = false) private Long emailId;
    @Column(name = "konto_id", nullable = false, length = 16, updatable = false) private String kontoId;
    @Column(name = "einkauf_typ", nullable = false, length = 24, updatable = false) private String einkaufTyp;
    @Column(name = "vorgang_id", nullable = false, updatable = false) private Long vorgangId;
    @Column(name = "beteiligung_id", updatable = false) private Long beteiligungId;
    @Column(name = "revision_id", updatable = false) private Long revisionId;
    @Column(name = "source_message_id", nullable = false, length = 512, updatable = false) private String sourceMessageId;
    @Column(name = "in_reply_to", nullable = false, length = 1000, updatable = false) private String inReplyTo;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "references_header", nullable = false, columnDefinition = "json", updatable = false)
    private List<String> references;
    @Column(nullable = false, length = 1000, updatable = false) private String empfaenger;
    @Column(nullable = false, length = 998, updatable = false) private String subject;
    @Column(name = "html_body", nullable = false, columnDefinition = "LONGTEXT", updatable = false) private String htmlBody;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json", updatable = false) private List<AnlageSnapshot> anlagen;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "inhalt_sha256", nullable = false, length = 64, updatable = false) private String inhaltSha256;
    @Column(name = "versand_id", unique = true) private Long versandId;
    @Column(name = "angenommen_am") private Instant angenommenAm;
    @Column(name = "erstellt_am", nullable = false, updatable = false) private Instant erstelltAm;
    @Column(name = "gueltig_bis", nullable = false, updatable = false) private Instant gueltigBis;

    protected EinkaufMailantwortVorschau() {}

    public EinkaufMailantwortVorschau(String token, Long emailId, String kontoId, String einkaufTyp, Long vorgangId,
            Long beteiligungId, Long revisionId, String sourceMessageId, String inReplyTo, List<String> references,
            String empfaenger, String subject, String htmlBody, List<AnlageSnapshot> anlagen, String inhaltSha256,
            Instant erstelltAm, Instant gueltigBis) {
        this.freigabeToken = token; this.emailId = emailId; this.kontoId = kontoId; this.einkaufTyp = einkaufTyp;
        this.vorgangId = vorgangId; this.beteiligungId = beteiligungId; this.revisionId = revisionId;
        this.sourceMessageId = sourceMessageId; this.inReplyTo = inReplyTo;
        this.references = references == null ? List.of() : List.copyOf(references);
        this.empfaenger = empfaenger; this.subject = subject; this.htmlBody = htmlBody;
        this.anlagen = anlagen == null ? List.of() : List.copyOf(anlagen);
        this.inhaltSha256 = inhaltSha256; this.erstelltAm = erstelltAm; this.gueltigBis = gueltigBis;
    }

    public String getFreigabeToken() { return freigabeToken; }
    public Long getEmailId() { return emailId; }
    public String getKontoId() { return kontoId; }
    public String getEinkaufTyp() { return einkaufTyp; }
    public Long getVorgangId() { return vorgangId; }
    public Long getBeteiligungId() { return beteiligungId; }
    public Long getRevisionId() { return revisionId; }
    public String getSourceMessageId() { return sourceMessageId; }
    public String getInReplyTo() { return inReplyTo; }
    public List<String> getReferences() { return references == null ? List.of() : List.copyOf(references); }
    public String getEmpfaenger() { return empfaenger; }
    public String getSubject() { return subject; }
    public String getHtmlBody() { return htmlBody; }
    public List<AnlageSnapshot> getAnlagen() { return anlagen == null ? List.of() : List.copyOf(anlagen); }
    public String getInhaltSha256() { return inhaltSha256; }
    public Instant getErstelltAm() { return erstelltAm; }
    public Instant getGueltigBis() { return gueltigBis; }
    public Long getVersandId() { return versandId; }
    public Instant getAngenommenAm() { return angenommenAm; }

    public void setVersandId(Long versandId) {
        if (versandId == null || versandId <= 0) throw new IllegalArgumentException("Der Versandauftrag ist ungültig.");
        if (this.versandId != null && !this.versandId.equals(versandId))
            throw new IllegalStateException("Eine Antwortvorschau darf nur einmal versendet werden.");
        this.versandId = versandId;
    }

    public void markiereAngenommen(Instant zeit) {
        if (versandId == null || zeit == null) throw new IllegalStateException("Der angenommene Versand ist nicht gebunden.");
        if (angenommenAm == null) angenommenAm = zeit;
    }

    public record AnlageSnapshot(Long anlageId, String sha256) {}
}
