package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import java.time.LocalDate

@Entity
open class MitarbeiterDokument : Dokument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override var id: Long? = null

    @Column(nullable = false)
    override var originalDateiname: String? = null

    @Column(nullable = false, unique = true)
    override var gespeicherterDateiname: String? = null

    override var dateityp: String? = null

    override var dateigroesse: Long? = null

    override var uploadDatum: LocalDate? = null

    override var emailVersandDatum: LocalDate? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    override var dokumentGruppe: DokumentGruppe = DokumentGruppe.DIVERSE_DOKUMENTE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id")
    open var mitarbeiter: Mitarbeiter? = null

}