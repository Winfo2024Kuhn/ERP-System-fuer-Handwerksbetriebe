package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.AuditAktion
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle
import org.example.kalkulationsprogramm.domain.KorrekturTyp
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.ZeitkontoKorrektur
import org.example.kalkulationsprogramm.domain.ZeitkontoKorrekturAudit
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.ZeitkontoKorrekturAuditRepository
import org.example.kalkulationsprogramm.repository.ZeitkontoKorrekturRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.LinkedHashMap

@Service
class ZeitkontoKorrekturService(
    private val korrekturRepository: ZeitkontoKorrekturRepository,
    private val auditRepository: ZeitkontoKorrekturAuditRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val monatsSaldoService: MonatsSaldoService,
) {
    @Transactional
    fun erstelleKorrektur(
        mitarbeiterId: Long,
        stunden: BigDecimal?,
        datum: LocalDate,
        grund: String?,
        erstelltVonId: Long,
        quelle: ErfassungsQuelle?,
        typ: KorrekturTyp?,
    ): ZeitkontoKorrektur {
        if (grund.isNullOrBlank()) {
            throw IllegalArgumentException("Begründung ist ein Pflichtfeld für GoBD-Konformität")
        }
        if (stunden == null || stunden.compareTo(BigDecimal.ZERO) == 0) {
            throw IllegalArgumentException("Korrekturstunden dürfen nicht 0 sein")
        }

        val mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
            .orElseThrow { IllegalArgumentException("Mitarbeiter nicht gefunden: $mitarbeiterId") }

        val erstelltVon = mitarbeiterRepository.findById(erstelltVonId)
            .orElseThrow { IllegalArgumentException("Bearbeiter nicht gefunden: $erstelltVonId") }

        val korrektur = ZeitkontoKorrektur()
        korrektur.mitarbeiter = mitarbeiter
        korrektur.stunden = stunden
        korrektur.datum = datum
        korrektur.grund = grund
        korrektur.erstelltVon = erstelltVon
        korrektur.version = 1
        korrektur.typ = typ ?: KorrekturTyp.STUNDEN

        val gespeichert = korrekturRepository.save(korrektur)

        val audit = ZeitkontoKorrekturAudit.fromKorrektur(
            gespeichert,
            AuditAktion.ERSTELLT,
            erstelltVon,
            quelle ?: ErfassungsQuelle.DESKTOP,
            "Initiale Erfassung",
        )
        auditRepository.save(audit)

        monatsSaldoService.invalidiereFuerDatum(mitarbeiterId, datum)

        return gespeichert
    }

    @Transactional
    fun erstelleKorrektur(
        mitarbeiterId: Long,
        stunden: BigDecimal?,
        datum: LocalDate,
        grund: String?,
        erstelltVonId: Long,
        quelle: ErfassungsQuelle?,
    ): ZeitkontoKorrektur =
        erstelleKorrektur(mitarbeiterId, stunden, datum, grund, erstelltVonId, quelle, KorrekturTyp.STUNDEN)

    @Transactional
    fun aendereKorrektur(
        korrekturId: Long,
        stunden: BigDecimal?,
        grund: String?,
        bearbeiterId: Long,
        aenderungsgrund: String?,
        quelle: ErfassungsQuelle?,
    ): ZeitkontoKorrektur {
        if (aenderungsgrund.isNullOrBlank()) {
            throw IllegalArgumentException("Änderungsgrund ist ein Pflichtfeld für GoBD-Konformität")
        }

        val korrektur = korrekturRepository.findById(korrekturId)
            .orElseThrow { IllegalArgumentException("Korrektur nicht gefunden: $korrekturId") }

        if (korrektur.storniert == true) {
            throw IllegalStateException("Stornierte Korrekturen können nicht mehr geändert werden")
        }

        val bearbeiter = mitarbeiterRepository.findById(bearbeiterId)
            .orElseThrow { IllegalArgumentException("Bearbeiter nicht gefunden: $bearbeiterId") }

        korrektur.erhoeheVersion()

        if (stunden != null) {
            korrektur.stunden = stunden
        }
        if (!grund.isNullOrBlank()) {
            korrektur.grund = grund
        }

        val audit = ZeitkontoKorrekturAudit.fromKorrektur(
            korrektur,
            AuditAktion.GEAENDERT,
            bearbeiter,
            quelle ?: ErfassungsQuelle.DESKTOP,
            aenderungsgrund,
        )
        auditRepository.save(audit)

        val gespeicherteKorrektur = korrekturRepository.save(korrektur)
        monatsSaldoService.invalidiereFuerDatum(korrektur.mitarbeiter!!.id!!, korrektur.datum)

        return gespeicherteKorrektur
    }

    @Transactional
    fun storniereKorrektur(
        korrekturId: Long,
        bearbeiterId: Long,
        stornierungsgrund: String?,
        quelle: ErfassungsQuelle?,
    ) {
        if (stornierungsgrund.isNullOrBlank()) {
            throw IllegalArgumentException("Stornierungsgrund ist ein Pflichtfeld für GoBD-Konformität")
        }

        val korrektur = korrekturRepository.findById(korrekturId)
            .orElseThrow { IllegalArgumentException("Korrektur nicht gefunden: $korrekturId") }

        if (korrektur.storniert == true) {
            throw IllegalStateException("Korrektur ist bereits storniert")
        }

        val bearbeiter = mitarbeiterRepository.findById(bearbeiterId)
            .orElseThrow { IllegalArgumentException("Bearbeiter nicht gefunden: $bearbeiterId") }

        korrektur.erhoeheVersion()
        korrektur.storniert = true
        korrektur.storniertAm = LocalDateTime.now()
        korrektur.storniertVon = bearbeiter
        korrektur.stornierungsgrund = stornierungsgrund

        val audit = ZeitkontoKorrekturAudit.fromKorrektur(
            korrektur,
            AuditAktion.STORNIERT,
            bearbeiter,
            quelle ?: ErfassungsQuelle.DESKTOP,
            stornierungsgrund,
        )
        auditRepository.save(audit)

        korrekturRepository.save(korrektur)
        monatsSaldoService.invalidiereFuerDatum(korrektur.mitarbeiter!!.id!!, korrektur.datum)
    }

    fun getAktiveKorrekturenByMitarbeiter(mitarbeiterId: Long): List<ZeitkontoKorrektur> =
        korrekturRepository.findByMitarbeiterIdOrderByDatumDesc(mitarbeiterId)
            .filter { it.storniert != true }

    fun getAlleKorrekturenByMitarbeiter(mitarbeiterId: Long): List<ZeitkontoKorrektur> =
        korrekturRepository.findByMitarbeiterIdOrderByDatumDesc(mitarbeiterId)

    fun summiereAktiveKorrekturen(mitarbeiterId: Long, jahr: Int): BigDecimal {
        val von = LocalDate.of(jahr, 1, 1)
        val bis = LocalDate.of(jahr, 12, 31)
        return summiereAktiveKorrekturenImZeitraum(mitarbeiterId, von, bis)
    }

    fun summiereAktiveKorrekturenImZeitraum(mitarbeiterId: Long, von: LocalDate, bis: LocalDate): BigDecimal =
        korrekturRepository.findByMitarbeiterIdAndDatumBetween(mitarbeiterId, von, bis)
            .asSequence()
            .filter { it.storniert != true }
            .filter { it.typ == KorrekturTyp.STUNDEN }
            .map { it.stunden ?: BigDecimal.ZERO }
            .fold(BigDecimal.ZERO, BigDecimal::add)

    fun summiereAktiveUrlaubsKorrekturen(mitarbeiterId: Long, jahr: Int): BigDecimal {
        val von = LocalDate.of(jahr, 1, 1)
        val bis = LocalDate.of(jahr, 12, 31)

        return korrekturRepository.findByMitarbeiterIdAndDatumBetween(mitarbeiterId, von, bis)
            .asSequence()
            .filter { it.storniert != true }
            .filter { it.typ == KorrekturTyp.URLAUB }
            .map { it.stunden ?: BigDecimal.ZERO }
            .fold(BigDecimal.ZERO, BigDecimal::add)
    }

    @Transactional(readOnly = true)
    fun getAuditHistorie(korrekturId: Long): List<Map<String, Any?>> =
        auditRepository.findByZeitkontoKorrekturIdOrderByVersionDesc(korrekturId)
            .map(::auditToMap)

    private fun auditToMap(audit: ZeitkontoKorrekturAudit): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["id"] = audit.id
        map["version"] = audit.version
        map["aktion"] = audit.aktion?.name
        map["datum"] = audit.datum?.toString()
        map["stunden"] = audit.stunden
        map["grund"] = audit.grund
        val geaendertVon: Mitarbeiter? = audit.geaendertVon
        map["geaendertVon"] = if (geaendertVon != null) {
            geaendertVon.vorname + " " + geaendertVon.nachname
        } else {
            null
        }
        map["geaendertAm"] = audit.geaendertAm?.toString()
        map["geaendertVia"] = audit.geaendertVia?.name
        map["aenderungsgrund"] = audit.aenderungsgrund
        return map
    }
}
