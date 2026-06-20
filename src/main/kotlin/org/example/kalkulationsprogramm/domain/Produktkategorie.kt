package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
open class Produktkategorie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(nullable = false)
    open var bezeichnung: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var verrechnungseinheit: Verrechnungseinheit? = null

    open var bildUrl: String? = null

    @Column(columnDefinition = "TEXT")
    open var beschreibung: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_kategorie_id") // Original: Unterkategorie
    open var uebergeordneteKategorie: Produktkategorie? = null

    @OneToMany(mappedBy = "uebergeordneteKategorie")
    open var unterkategorien: MutableList<Produktkategorie> = ArrayList()

    @OneToMany(mappedBy = "produktkategorie")
    open var projektProduktkategorien: MutableList<ProjektProduktkategorie> = ArrayList()

}