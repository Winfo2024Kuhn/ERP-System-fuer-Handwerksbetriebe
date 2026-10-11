package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Abwesenheitsnotiz: Zwischen {@link #startAt} und {@link #endAt} beantwortet das ERP
 * eingehende Mails in den gewählten {@link #postfaecher Postfächern} automatisch – aus dem
 * Postfach, in dem die Mail ankam.
 */
@Getter
@Setter
@Entity
@Table(name = "out_of_office_schedule")
public class OutOfOfficeSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "start_at", nullable = false)
    private LocalDate startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDate endAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "signature_id")
    private EmailSignature signature;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "subject_template", length = 300)
    private String subjectTemplate;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "body_template")
    private String bodyTemplate;

    /**
     * Postfächer, für die die Notiz gilt. Leer nur bei alten Notizen aus Betrieben ganz ohne
     * Postfach – die beantworten weiter Mails des Standard-Kontos aus den Einstellungen.
     */
    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(name = "out_of_office_schedule_postfach",
            joinColumns = @JoinColumn(name = "schedule_id"),
            inverseJoinColumns = @JoinColumn(name = "postfach_id"))
    private Set<EmailAbsender> postfaecher = new LinkedHashSet<>();
}
