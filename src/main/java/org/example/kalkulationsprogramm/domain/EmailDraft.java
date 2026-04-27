package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Automatisch gespeicherter E-Mail-Entwurf.
 * Wird beim Senden gelöscht und beim Schließen des Compose-Dialogs angeboten.
 */
@Entity
@Table(name = "email_draft")
@Getter
@Setter
@NoArgsConstructor
public class EmailDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String recipient;

    @Column(columnDefinition = "TEXT")
    private String cc;

    @Column(columnDefinition = "TEXT")
    private String subject;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String body;

    @Column(length = 255)
    private String fromAddress;

    /** ID der E-Mail, auf die geantwortet wird (Thread-Verknüpfung) */
    private Long replyEmailId;

    /** Zugeordnetes Projekt */
    private Long projektId;

    /** Zugeordnete Anfrage */
    private Long anfrageId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
