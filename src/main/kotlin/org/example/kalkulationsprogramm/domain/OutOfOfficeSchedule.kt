package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "out_of_office_schedule")
open class OutOfOfficeSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, length = 200)
    open var title: String? = null

    @Column(name = "start_at", nullable = false)
    open var startAt: LocalDate? = null

    @Column(name = "end_at", nullable = false)
    open var endAt: LocalDate? = null

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "signature_id")
    open var signature: EmailSignature? = null

    @Column(name = "active", nullable = false)
    open var active: Boolean = false

    @Column(name = "subject_template", length = 300)
    open var subjectTemplate: String? = null

    @Lob
    @Column(name = "body_template", columnDefinition = "longtext")
    open var bodyTemplate: String? = null

    open fun isActive(): Boolean = active

}
