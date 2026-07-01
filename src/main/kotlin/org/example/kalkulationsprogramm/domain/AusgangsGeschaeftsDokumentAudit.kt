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
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Entity
@Table(name = "ausgangs_geschaeftsdokument_audit")
open class AusgangsGeschaeftsDokumentAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "chain_index")
    open var chainIndex: Long? = null

    @Column(name = "dokument_id", nullable = false)
    open var dokumentId: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    open var aktion: AusgangsGeschaeftsDokumentAuditAktion? = null

    @Column(name = "dokument_nummer", nullable = false, length = 20)
    open var dokumentNummer: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    open var typ: AusgangsGeschaeftsDokumentTyp? = null

    open var datum: LocalDate? = null

    @Column(length = 500)
    open var betreff: String? = null

    @Column(name = "betrag_netto", precision = 12, scale = 2)
    open var betragNetto: BigDecimal? = null

    @Column(name = "betrag_brutto", precision = 12, scale = 2)
    open var betragBrutto: BigDecimal? = null

    @Column(name = "mwst_satz", precision = 5, scale = 4)
    open var mwstSatz: BigDecimal? = null

    @Column(name = "abschlags_nummer")
    open var abschlagsNummer: Int? = null

    @Column(name = "projekt_id")
    open var projektId: Long? = null

    @Column(name = "anfrage_id")
    open var anfrageId: Long? = null

    @Column(name = "kunde_id")
    open var kundeId: Long? = null

    @Column(name = "vorgaenger_id")
    open var vorgaengerId: Long? = null

    @Column(name = "versand_datum")
    open var versandDatum: LocalDate? = null

    @Column(nullable = false)
    open var gebucht: Boolean = false

    @Column(name = "gebucht_am")
    open var gebuchtAm: LocalDate? = null

    @Column(nullable = false)
    open var storniert: Boolean = false

    @Column(name = "storniert_am")
    open var storniertAm: LocalDate? = null

    @Column(name = "digital_angenommen", nullable = false)
    open var digitalAngenommen: Boolean = false

    @Column(name = "inhalt_hash", columnDefinition = "CHAR(64)")
    open var inhaltHash: String? = null

    @Column(name = "previous_hash", columnDefinition = "CHAR(64)")
    open var previousHash: String? = null

    @Column(name = "entry_hash", columnDefinition = "CHAR(64)")
    open var entryHash: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geaendert_von_id")
    open var geaendertVon: FrontendUserProfile? = null

    @Column(name = "geaendert_am", nullable = false)
    open var geaendertAm: LocalDateTime? = null

    @Column(columnDefinition = "TEXT")
    open var aenderungsgrund: String? = null

    @Column(name = "ip_adresse", length = 45)
    open var ipAdresse: String? = null

    open fun canonicalForm(): String =
        buildString(512) {
            append(emptyIfNull(chainIndex)).append('|')
            append(emptyIfNull(dokumentId)).append('|')
            append(aktion?.name ?: "").append('|')
            append(emptyIfNull(dokumentNummer)).append('|')
            append(typ?.name ?: "").append('|')
            append(datum?.toString() ?: "").append('|')
            append(emptyIfNull(betreff)).append('|')
            append(plain(betragNetto)).append('|')
            append(plain(betragBrutto)).append('|')
            append(plain(mwstSatz)).append('|')
            append(emptyIfNull(abschlagsNummer)).append('|')
            append(emptyIfNull(projektId)).append('|')
            append(emptyIfNull(anfrageId)).append('|')
            append(emptyIfNull(kundeId)).append('|')
            append(emptyIfNull(vorgaengerId)).append('|')
            append(versandDatum?.toString() ?: "").append('|')
            append(gebucht).append('|')
            append(gebuchtAm?.toString() ?: "").append('|')
            append(storniert).append('|')
            append(storniertAm?.toString() ?: "").append('|')
            append(digitalAngenommen).append('|')
            append(emptyIfNull(inhaltHash)).append('|')
            append(geaendertVon?.id?.toString() ?: "").append('|')
            append(geaendertAm?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "").append('|')
            append(emptyIfNull(aenderungsgrund)).append('|')
            append(emptyIfNull(ipAdresse))
        }

    open fun computeEntryHash(): String? =
        sha256(canonicalForm() + "|" + emptyIfNull(previousHash))

    open fun isGebucht(): Boolean = gebucht

    open fun isStorniert(): Boolean = storniert

    open fun isDigitalAngenommen(): Boolean = digitalAngenommen

    companion object {
        @JvmStatic
        fun fromDokument(
            dokument: AusgangsGeschaeftsDokument,
            aktion: AusgangsGeschaeftsDokumentAuditAktion,
            bearbeiter: FrontendUserProfile?,
            aenderungsgrund: String?,
            ipAdresse: String?,
        ): AusgangsGeschaeftsDokumentAudit =
            AusgangsGeschaeftsDokumentAudit().apply {
                dokumentId = dokument.id
                this.aktion = aktion
                dokumentNummer = dokument.dokumentNummer
                typ = dokument.typ
                datum = dokument.datum
                betreff = dokument.betreff
                betragNetto = dokument.betragNetto
                betragBrutto = dokument.betragBrutto
                mwstSatz = dokument.mwstSatz
                abschlagsNummer = dokument.abschlagsNummer
                projektId = dokument.projekt?.id
                anfrageId = dokument.anfrage?.id
                kundeId = dokument.kunde?.id
                vorgaengerId = dokument.vorgaenger?.id
                versandDatum = dokument.versandDatum
                gebucht = dokument.isGebucht()
                gebuchtAm = dokument.gebuchtAm
                storniert = dokument.isStorniert()
                storniertAm = dokument.storniertAm
                digitalAngenommen = dokument.isDigitalAngenommen()
                inhaltHash = sha256(dokument.htmlInhalt)
                geaendertVon = bearbeiter
                geaendertAm = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
                this.aenderungsgrund = aenderungsgrund
                this.ipAdresse = ipAdresse
            }

        private fun emptyIfNull(value: Any?): String = value?.toString() ?: ""

        private fun plain(value: BigDecimal?): String = value?.toPlainString() ?: ""

        @JvmStatic
        fun sha256(input: String?): String? {
            if (input == null) return null
            return try {
                val md = MessageDigest.getInstance("SHA-256")
                val hash = md.digest(input.toByteArray(StandardCharsets.UTF_8))
                buildString(hash.size * 2) {
                    for (byte in hash) {
                        append("%02x".format(byte))
                    }
                }
            } catch (_: NoSuchAlgorithmException) {
                null
            }
        }
    }
}
