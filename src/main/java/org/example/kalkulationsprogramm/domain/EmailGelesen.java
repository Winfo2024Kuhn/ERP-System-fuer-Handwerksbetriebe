package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Ein Benutzer hat eine Mail gelesen. „Gelesen“ gilt pro Person: Öffnet der Chef eine Mail,
 * bleibt sie für die Kollegen ungelesen und ihre Glocke meldet sie weiter.
 *
 * <p>Das gemeinsame Feld {@link Email#isRead()} heißt nur noch „irgendwer hat sie schon
 * geöffnet“ und wird nicht mehr angezeigt.
 */
@Getter
@Setter
@Entity
@Table(name = "email_gelesen",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_gelesen_benutzer_email",
                columnNames = { "benutzer_id", "email_id" }),
        indexes = @Index(name = "idx_email_gelesen_email", columnList = "email_id"))
public class EmailGelesen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benutzer_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FrontendUserProfile benutzer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Email email;

    @Column(name = "gelesen_am", nullable = false)
    private LocalDateTime gelesenAm;
}
