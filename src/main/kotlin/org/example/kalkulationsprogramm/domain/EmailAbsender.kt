package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table
open class EmailAbsender {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "email_adresse", nullable = false, length = 255)
    open var emailAdresse: String? = null

    @Column(name = "anzeigename", length = 255)
    open var anzeigename: String? = null

    @Column(name = "aktiv", nullable = false)
    open var aktiv: Boolean = true

    @Column(name = "sortierung", nullable = false)
    open var sortierung: Int = 0

}