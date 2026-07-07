package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "lagerort",
    uniqueConstraints = [UniqueConstraint(name = "uk_lagerort_code", columnNames = ["code"])],
)
open class Lagerort {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false, length = 80)
    open var code: String = ""

    @Column(nullable = false, length = 160)
    open var name: String = ""

    @Column(length = 80)
    open var regal: String? = null

    @Column(length = 80)
    open var fach: String? = null

    @Column(nullable = false)
    open var aktiv: Boolean = true
}
