package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table
open class Werkstoff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "name", unique = true)
    open var name: String? = null

    @OneToMany(mappedBy = "werkstoff")
    open var artikel: MutableList<Artikel> = ArrayList()

}