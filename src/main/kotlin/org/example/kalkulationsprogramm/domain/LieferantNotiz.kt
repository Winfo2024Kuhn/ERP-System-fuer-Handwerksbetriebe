package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "lieferant_notiz")
open class LieferantNotiz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id", nullable = false)
    open var lieferant: Lieferanten? = null

    @Column(columnDefinition = "TEXT", nullable = false)
    open var text: String? = null

    @Column(nullable = false)
    open var erstelltAm: LocalDateTime? = null

    @PrePersist
    open fun onCreate() {
        if (erstelltAm == null) erstelltAm = LocalDateTime.now()
    }

}