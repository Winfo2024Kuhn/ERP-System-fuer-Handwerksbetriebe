package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "einkauf_mail_zuordnung", uniqueConstraints = @UniqueConstraint(name = "uk_einkauf_mail_email", columnNames = "email_id"))
public class EinkaufMailZuordnung {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "email_id", nullable = false, updatable = false) private Long emailId;
    @Column(length = 24) private String typ;
    @Column(name = "vorgang_id") private Long vorgangId;
    @Column(name = "beteiligung_id") private Long beteiligungId;
    @Column(name = "revision_id") private Long revisionId;
    @Column(nullable = false, length = 32) private String status = "PRUEFEN";
    @Column(nullable = false, length = 40) private String quelle = "KEIN_EINDEUTIGER_TREFFER";
    @Column(nullable = false) private boolean bestaetigt;
    @Column(length = 1000) private String begruendung;
    @Column(name = "bestaetigt_von") private Long bestaetigtVon;
    @Column(name = "bestaetigt_am") private Instant bestaetigtAm;

    protected EinkaufMailZuordnung() {}
    public EinkaufMailZuordnung(Long emailId) { this.emailId = emailId; }
    public void automatisch(String typ, Long vorgangId, Long beteiligungId, Long revisionId, String status, String quelle) {
        if (bestaetigt) return;
        this.typ = typ; this.vorgangId = vorgangId; this.beteiligungId = beteiligungId; this.revisionId = revisionId;
        this.status = status; this.quelle = quelle;
    }
    public void bestaetigen(String typ, Long vorgangId, Long beteiligungId, Long revisionId, String begruendung, Long akteurId, String status) {
        this.typ = typ; this.vorgangId = vorgangId; this.beteiligungId = beteiligungId; this.revisionId = revisionId;
        this.status = status; this.quelle = "MANUELL_BESTAETIGT"; this.bestaetigt = true;
        this.begruendung = begruendung; this.bestaetigtVon = akteurId; this.bestaetigtAm = Instant.now();
    }
    public Long getEmailId() { return emailId; }
    public String getTyp() { return typ; }
    public Long getVorgangId() { return vorgangId; }
    public Long getBeteiligungId() { return beteiligungId; }
    public Long getRevisionId() { return revisionId; }
    public String getStatus() { return status; }
    public String getQuelle() { return quelle; }
    public boolean isBestaetigt() { return bestaetigt; }
}
