package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "email_text_template", uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_text_template_doktyp", columnNames = "dokument_typ")
})
public class EmailTextTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dokument_typ", nullable = false, length = 40)
    private String dokumentTyp;

    /**
     * Fachliche Gruppierung (Dokument / Mahnwesen / Webseite / System). Nur
     * für die UI relevant — die Versand-Logik bleibt unverändert über
     * {@link #dokumentTyp} verdrahtet. Datenbankseitig nullable, damit alte
     * Zeilen aus V218/V250 ohne weiteren Schritt bestehen — die Migration
     * V306 backfillt anschließend alle bekannten Typen.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kategorie", length = 20)
    private EmailTextTemplateKategorie kategorie;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "subject_template", nullable = false, length = 500)
    private String subjectTemplate;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "html_body", nullable = false)
    private String htmlBody;

    @Column(name = "aktiv", nullable = false)
    private boolean aktiv = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
