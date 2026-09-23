package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Durable acceptance fact. Consumer effects and marking this event processed must share a DB transaction. */
@Entity
@Table(name = "einkauf_versandannahmeereignis", uniqueConstraints = {
        @UniqueConstraint(name = "uk_einkauf_annahme_event_key", columnNames = "ereignis_schluessel"),
        @UniqueConstraint(name = "uk_einkauf_annahme_auftrag", columnNames = "versandauftrag_id")
})
@Getter
@NoArgsConstructor
public class EinkaufVersandAnnahmeereignis {
    public enum Typ { VERSAND_ANGENOMMEN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ereignis_schluessel", nullable = false, length = 16, updatable = false)
    private UUID ereignisSchluessel;
    @Column(name = "versandauftrag_id", nullable = false, updatable = false)
    private Long versandauftragId;
    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false, columnDefinition = "ENUM('VERSAND_ANGENOMMEN')", updatable = false)
    private Typ typ = Typ.VERSAND_ANGENOMMEN;
    @Column(name = "vorgang_typ", nullable = false, length = 40, updatable = false)
    private String vorgangTyp;
    @Column(name = "vorgang_id", nullable = false, updatable = false)
    private Long vorgangId;
    @Column(name = "revision_id", updatable = false)
    private Long revisionId;
    @Column(name = "beteiligung_id", updatable = false)
    private Long beteiligungId;
    @Column(name = "angenommen_am", nullable = false, updatable = false)
    private Instant angenommenAm;
    @Column(name = "erstellt_am", nullable = false, updatable = false)
    private Instant erstelltAm;
    @Column(name = "verarbeitet_am")
    private Instant verarbeitetAm;

    public EinkaufVersandAnnahmeereignis(Long versandauftragId, String vorgangTyp,
            Long vorgangId, Long revisionId, Long beteiligungId, Instant angenommenAm) {
        this.versandauftragId = versandauftragId;
        this.vorgangTyp = vorgangTyp;
        this.vorgangId = vorgangId;
        this.revisionId = revisionId;
        this.beteiligungId = beteiligungId;
        this.angenommenAm = angenommenAm;
        this.erstelltAm = Instant.now();
        this.ereignisSchluessel = UUID.nameUUIDFromBytes(
                ("einkauf-versand-angenommen:" + versandauftragId).getBytes(StandardCharsets.UTF_8));
    }

    public void verarbeitet(Instant zeit) {
        this.verarbeitetAm = zeit;
    }
}
