package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table
open class AusgangsGeschaeftsDokumentCounter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "monat_key", nullable = false, length = 10)
    open var monatKey: String? = null

    @Column(nullable = false)
    open var zaehler: Long = 0L

}