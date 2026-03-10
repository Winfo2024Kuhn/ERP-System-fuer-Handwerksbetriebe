package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

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

    @Lob
    @Column(name = "body_template", columnDefinition = "longtext")
    private String bodyTemplate;

}
