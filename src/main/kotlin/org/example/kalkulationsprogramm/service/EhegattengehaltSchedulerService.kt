package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository
import org.example.kalkulationsprogramm.repository.SachkontoRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.jpa.JpaSystemException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Service
class EhegattengehaltSchedulerService(
    private val kasseEinstellungRepository: KasseEinstellungRepository,
    private val kasseShortcutService: KasseShortcutService,
    private val sachkontoRepository: SachkontoRepository,
) {
    @Scheduled(cron = "0 30 6 * * *", zone = "Europe/Berlin")
    fun monatlicheLohnBuchung() {
        try {
            tryLohnBuchung(LocalDate.now())
        } catch (e: DataIntegrityViolationException) {
            log.error("Ehegattengehalt-Scheduler: Konfigurationsproblem — bitte Einstellungen pruefen", e)
        } catch (e: JpaSystemException) {
            log.error("Ehegattengehalt-Scheduler: Konfigurationsproblem — bitte Einstellungen pruefen", e)
        } catch (e: Exception) {
            log.error("Ehegattengehalt-Scheduler fehlgeschlagen", e)
        }
    }

    fun tryLohnBuchung(heute: LocalDate): Boolean {
        val k = kasseEinstellungRepository.findSingleton().orElse(null) ?: return false
        if (k.value<Boolean>("isEhegattengehaltAktiv") != true) {
            return false
        }
        val betrag = k.value<BigDecimal>("getEhegattengehaltBetrag")
        if (betrag == null || betrag.signum() <= 0) {
            log.warn("Ehegattengehalt aktiv aber kein gueltiger Betrag konfiguriert")
            return false
        }
        val tag = k.value<Int>("getEhegattengehaltTag")
        if (tag == null) {
            log.warn("Ehegattengehalt aktiv aber kein Stichtag konfiguriert")
            return false
        }
        val lohnKonto = sachkontoRepository.findByNummer(LOHN_SACHKONTO_NUMMER).orElse(null)
        if (lohnKonto == null) {
            log.warn(
                "Ehegattengehalt: Sachkonto {} (Loehne & Gehaelter) nicht gefunden — bitte in Sachkonten anlegen",
                LOHN_SACHKONTO_NUMMER,
            )
            return false
        }

        if (heute.dayOfMonth < tag) {
            return false
        }

        val aktuellerJahrmonat = YearMonth.from(heute).format(YEAR_MONTH)
        if (aktuellerJahrmonat == k.value<String>("getLetzteBuchungJahrmonat")) {
            return false
        }

        markiereJahrmonatGebucht(k.value("getId"), aktuellerJahrmonat)

        val empfaengerName = k.value<String>("getEhegattengehaltEmpfaengerName")
        log.info(
            "Ehegattengehalt-Scheduler: buche {} EUR fuer {} (Empfaenger: {})",
            betrag,
            aktuellerJahrmonat,
            empfaengerName,
        )

        kasseShortcutService.lohnZahlung(
            betrag,
            heute,
            if (empfaengerName != null) "Auto: $empfaengerName" else "Auto: Ehegattengehalt",
            lohnKonto,
            null,
            null,
        )
        return true
    }

    internal fun markiereJahrmonatGebucht(einstellungsId: Long?, jahrmonat: String) {
        val id = einstellungsId ?: throw IllegalArgumentException("Kasse-Einstellung hat keine ID.")
        val k = kasseEinstellungRepository.findById(id).orElseThrow()
        k.setValue("setLetzteBuchungJahrmonat", String::class.java, jahrmonat)
        kasseEinstellungRepository.save(k)
    }

    private inline fun <reified T> Any.value(getter: String): T? =
        javaClass.getMethod(getter).invoke(this) as? T

    private fun Any.setValue(setter: String, parameterType: Class<*>, value: Any?) {
        javaClass.getMethod(setter, parameterType).invoke(this, value)
    }

    companion object {
        const val LOHN_SACHKONTO_NUMMER = "4120"
        private val YEAR_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
        private val log = LoggerFactory.getLogger(EhegattengehaltSchedulerService::class.java)
    }
}
