package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "push_subscription")
open class PushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    open var mitarbeiter: Mitarbeiter? = null

    @Column(nullable = false, length = 2048)
    open var endpoint: String? = null

    @Column(nullable = false, length = 512)
    open var p256dh: String? = null

    @Column(nullable = false, length = 512)
    open var auth: String? = null

    @Column(nullable = false)
    open var erstelltAm: LocalDateTime? = null

    @PrePersist
    open fun onCreate() {
        if (erstelltAm == null) erstelltAm = LocalDateTime.now()
    }

}