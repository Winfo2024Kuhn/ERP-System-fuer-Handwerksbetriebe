package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table(name = "kategorie")
@Inheritance(strategy = InheritanceType.JOINED)
open class Kategorie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Int? = null

    open var beschreibung: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_kategorie_id")
    open var parentKategorie: Kategorie? = null

}