package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "kunde_notiz")
open class KundeNotiz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kunde_id", nullable = false)
    open var kunde: Kunde? = null

    @Column(columnDefinition = "TEXT", nullable = false)
    open var text: String? = null

    @Column(nullable = false)
    open var erstelltAm: LocalDateTime? = null

    @PrePersist
    protected open fun onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now()
        }
    }
}
