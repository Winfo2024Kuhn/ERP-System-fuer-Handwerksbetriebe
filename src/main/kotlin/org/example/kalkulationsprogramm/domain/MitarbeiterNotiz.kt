package org.example.kalkulationsprogramm.domain

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
open class MitarbeiterNotiz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(columnDefinition = "TEXT")
    open var inhalt: String? = null

    @Column(nullable = false)
    open var erstelltAm: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id")
    @JsonBackReference
    open var mitarbeiter: Mitarbeiter? = null

    @PrePersist
    open fun onCreate() {
        if (erstelltAm == null) erstelltAm = LocalDateTime.now()
    }

}