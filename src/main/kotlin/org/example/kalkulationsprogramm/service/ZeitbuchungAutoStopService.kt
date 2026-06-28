package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.Zeitbuchung
import org.example.kalkulationsprogramm.domain.Zeitkonto
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class ZeitbuchungAutoStopService(
    private val zeitbuchungRepository: ZeitbuchungRepository,
    private val zeitkontoRepository: ZeitkontoRepository,
    private val auditService: ZeitbuchungAuditService,
    private val monatsSaldoService: MonatsSaldoService,
) {
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    fun pruefUndStoppeOffeneBuchungen() {
        val zeitkonten = zeitkontoRepository.findAll()
        for (konto in zeitkonten) {
            val mitarbeiter = readField(konto, "mitarbeiter") ?: continue
            val mitarbeiterId = readLong(mitarbeiter, "id") ?: continue
            val offene = zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(mitarbeiterId)
            for (buchung in offene) {
                autoStoppeWennNoetig(buchung, konto)
            }
        }
    }

    @Transactional
    fun autoStoppeWennNoetig(buchung: Zeitbuchung, konto: Zeitkonto) {
        val jetzt = LocalDateTime.now()
        val startZeit = readLocalDateTime(buchung, "startZeit") ?: return
        val startDatum = startZeit.toLocalDate()
        val heute = jetzt.toLocalDate()

        if (heute.isAfter(startDatum)) {
            val endeZeit = startDatum.atTime(23, 59, 0)
            stopBuchung(buchung, endeZeit, "Automatisch beendet: Buchung lief über Mitternacht hinaus")
            log.info(
                "Auto-Stop (Mitternacht): Buchung {} von Mitarbeiter {} gestoppt bei {}",
                readLong(buchung, "id"),
                readLong(readField(konto, "mitarbeiter"), "id"),
                endeZeit,
            )
            return
        }

        val endeZeit = readField(konto, "buchungEndeZeit") as? LocalTime
        if (endeZeit != null && jetzt.toLocalTime().isAfter(endeZeit)) {
            val stopZeit = heute.atTime(endeZeit)
            if (startZeit.isBefore(stopZeit)) {
                stopBuchung(
                    buchung,
                    stopZeit,
                    "Automatisch beendet: Buchungszeitfenster überschritten (Ende: $endeZeit)",
                )
                log.info(
                    "Auto-Stop (Zeitfenster): Buchung {} von Mitarbeiter {} gestoppt bei {}",
                    readLong(buchung, "id"),
                    readLong(readField(konto, "mitarbeiter"), "id"),
                    stopZeit,
                )
            }
        }
    }

    private fun stopBuchung(buchung: Zeitbuchung, endeZeit: LocalDateTime, grund: String) {
        val startZeit = readLocalDateTime(buchung, "startZeit") ?: return
        writeField(buchung, "endeZeit", endeZeit)
        val dauer = Duration.between(startZeit, endeZeit)
        val stunden = BigDecimal.valueOf(dauer.toMinutes())
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
        writeField(buchung, "anzahlInStunden", stunden)

        val mitarbeiter = readField(buchung, "mitarbeiter") as? Mitarbeiter ?: return
        buchung.markiereAlsGeaendert(mitarbeiter)
        auditService.protokolliereAenderung(buchung, mitarbeiter, ErfassungsQuelle.SYSTEM, grund)

        zeitbuchungRepository.save(buchung)
        val mitarbeiterId = readLong(mitarbeiter, "id") ?: return
        monatsSaldoService.invalidiereFuerDateTime(mitarbeiterId, startZeit)
    }

    private fun readLong(target: Any?, fieldName: String): Long? =
        (readField(target, fieldName) as? Number)?.toLong()

    private fun readLocalDateTime(target: Any?, fieldName: String): LocalDateTime? =
        readField(target, fieldName) as? LocalDateTime

    private fun readField(target: Any?, fieldName: String): Any? {
        if (target == null) return null
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }

    private fun writeField(target: Any, fieldName: String, value: Any?) {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZeitbuchungAutoStopService::class.java)
    }
}
