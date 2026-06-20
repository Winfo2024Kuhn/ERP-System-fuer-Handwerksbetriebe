package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table
open class AbteilungDokumentBerechtigung {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "abteilung_id", nullable = false)
    open var abteilung: Abteilung? = null

    @Convert(converter = org.example.kalkulationsprogramm.domain.converter.LieferantDokumentTypConverter::class)
    @Column(name = "dokument_typ", nullable = false, length = 50)
    open var dokumentTyp: LieferantDokumentTyp? = null

    @Column(nullable = false)
    open var darfSehen: Boolean = false

    @Column(nullable = false)
    open var darfScannen: Boolean = false

}