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
 * Ein Eintrag der Benachrichtigungs-Glocke, den ein Benutzer erledigt (weggeklickt) hat.
 * Der Schlüssel ist Art und Kennung des Eintrags, z. B. {@code TERMIN:123}.
 */
@Getter
@Setter
@Entity
@Table(name = "benachrichtigung_erledigt",
        uniqueConstraints = @UniqueConstraint(name = "uk_benachrichtigung_erledigt_benutzer_schluessel",
                columnNames = { "benutzer_id", "schluessel" }),
        indexes = @Index(name = "idx_benachrichtigung_erledigt_am", columnList = "erledigt_am"))
public class BenachrichtigungErledigt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benutzer_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FrontendUserProfile benutzer;

    @Column(nullable = false, length = 120)
    private String schluessel;

    @Column(name = "erledigt_am", nullable = false)
    private LocalDateTime erledigtAm;
}
