package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "beleg")
open class Beleg {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "beleg_kategorie", nullable = false, length = 40)
    open var belegKategorie: BelegKategorie = BelegKategorie.UNZUGEORDNET

    @Enumerated(EnumType.STRING)
    @Column(name = "dokument_typ", length = 30)
    open var dokumentTyp: LieferantDokumentTyp? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: BelegStatus = BelegStatus.NEU

    @Enumerated(EnumType.STRING)
    @Column(name = "ki_analyse_status", nullable = false, length = 20)
    open var kiAnalyseStatus: BelegKiAnalyseStatus = BelegKiAnalyseStatus.PENDING

    @Enumerated(EnumType.STRING)
    @Column(name = "aufteilungs_modus", nullable = false, length = 20)
    open var aufteilungsModus: BelegAufteilungsModus = BelegAufteilungsModus.VOLLSTAENDIG

    @Column(name = "betrag_firma_netto", precision = 15, scale = 2)
    open var betragFirmaNetto: BigDecimal? = null

    @Column(name = "betrag_firma_brutto", precision = 15, scale = 2)
    open var betragFirmaBrutto: BigDecimal? = null

    @Column(name = "betrag_firma_mwst", precision = 15, scale = 2)
    open var betragFirmaMwst: BigDecimal? = null

    @Column(name = "beleg_datum")
    open var belegDatum: LocalDate? = null

    @Column(name = "beleg_nummer", length = 100)
    open var belegNummer: String? = null

    @Column(length = 500)
    open var beschreibung: String? = null

    @Column(name = "betrag_netto", precision = 15, scale = 2)
    open var betragNetto: BigDecimal? = null

    @Column(name = "betrag_brutto", precision = 15, scale = 2)
    open var betragBrutto: BigDecimal? = null

    @Column(name = "mwst_satz", precision = 5, scale = 2)
    open var mwstSatz: BigDecimal? = null

    @Column(length = 40)
    open var zahlungsart: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    open var lieferant: Lieferanten? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sachkonto_id")
    open var sachkonto: Sachkonto? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kostenstelle_id")
    open var kostenstelle: Kostenstelle? = null

    @Column(name = "ki_vorgeschlagener_lieferant", length = 255)
    open var kiVorgeschlagenerLieferant: String? = null

    @Column(name = "ki_confidence", precision = 3, scale = 2)
    open var kiConfidence: BigDecimal? = null

    @Column(name = "ki_vorgeschlagener_kostenstelle_id")
    open var kiVorgeschlagenerKostenstelleId: Long? = null

    @Column(name = "ki_vorgeschlagener_sachkonto_id")
    open var kiVorgeschlagenerSachkontoId: Long? = null

    @Column(name = "ki_kostenkonto_confidence", precision = 3, scale = 2)
    open var kiKostenkontoConfidence: BigDecimal? = null

    @Column(name = "ki_kostenkonto_begruendung", length = 500)
    open var kiKostenkontoBegruendung: String? = null

    @Lob
    @Column(name = "ki_extraktion_json", columnDefinition = "LONGTEXT")
    open var kiExtraktionJson: String? = null

    @Column(name = "ki_fehler_text", length = 1000)
    open var kiFehlerText: String? = null

    @Column(name = "original_dateiname", length = 255)
    open var originalDateiname: String? = null

    @Column(name = "gespeicherter_dateiname", length = 255)
    open var gespeicherterDateiname: String? = null

    @Column(name = "ist_umbuchung", nullable = false)
    open var istUmbuchung: Boolean? = false

    @Column(name = "mime_type", length = 120)
    open var mimeType: String? = null

    @Column(name = "upload_datum", nullable = false)
    open var uploadDatum: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    open var uploadedBy: Mitarbeiter? = null

    @Column(name = "validiert_am")
    open var validiertAm: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validiert_von_id")
    open var validiertVon: Mitarbeiter? = null

    @Column(length = 1000)
    open var notiz: String? = null

    @PrePersist
    protected open fun onCreate() {
        if (uploadDatum == null) {
            uploadDatum = LocalDateTime.now()
        }
    }
}
