package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.Zeitbuchung
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.MonatsSaldoRepository
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@Service
class MonatsSaldoWarmupService(
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val zeitbuchungRepository: ZeitbuchungRepository,
    private val monatsSaldoRepository: MonatsSaldoRepository,
    private val monatsSaldoService: MonatsSaldoService,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun warmupCache() {
        log.info("MonatsSaldo-Cache Warmup gestartet...")
        val startTime = System.currentTimeMillis()

        val aktiveMitarbeiter = mitarbeiterRepository.findByAktivTrue()
        var gesamtBerechnet = 0
        var gesamtUebersprungen = 0
        val aktuellerMonat = YearMonth.now()

        for (mitarbeiter in aktiveMitarbeiter) {
            try {
                val counts = warmupFuerMitarbeiter(mitarbeiter, aktuellerMonat)
                gesamtBerechnet += counts[0]
                gesamtUebersprungen += counts[1]
            } catch (e: Exception) {
                log.warn(
                    "MonatsSaldo-Warmup fehlgeschlagen für Mitarbeiter {} (ID={}): {}",
                    "${mitarbeiter.vorname} ${mitarbeiter.nachname}",
                    mitarbeiter.id,
                    e.message,
                )
            }
        }

        val dauer = System.currentTimeMillis() - startTime
        log.info(
            "MonatsSaldo-Cache Warmup abgeschlossen: {} Mitarbeiter, {} Monate berechnet, {} übersprungen, Dauer: {}ms",
            aktiveMitarbeiter.size,
            gesamtBerechnet,
            gesamtUebersprungen,
            dauer,
        )
    }

    private fun warmupFuerMitarbeiter(mitarbeiter: Mitarbeiter, aktuellerMonat: YearMonth): IntArray {
        var berechnet = 0
        var uebersprungen = 0

        val ersteBuchung = zeitbuchungRepository.findFirstByMitarbeiterIdOrderByStartZeitAsc(mitarbeiter.id)
        val startDatum: LocalDate = if (ersteBuchung.isPresent) {
            startZeit(ersteBuchung.get()).toLocalDate()
        } else if (mitarbeiter.eintrittsdatum != null) {
            mitarbeiter.eintrittsdatum!!
        } else {
            return intArrayOf(0, 0)
        }

        val startYM = YearMonth.from(startDatum)
        val endeYM = aktuellerMonat.minusMonths(1)
        if (startYM.isAfter(endeYM)) {
            return intArrayOf(0, 0)
        }

        var ym = startYM
        while (!ym.isAfter(endeYM)) {
            val istGueltig = monatsSaldoRepository
                .findByMitarbeiterIdAndJahrAndMonat(mitarbeiter.id, ym.year, ym.monthValue)
                .map { java.lang.Boolean.TRUE == it.value<Boolean>("getGueltig") }
                .orElse(false)

            if (istGueltig) {
                uebersprungen++
            } else {
                monatsSaldoService.getOrBerechne(mitarbeiter.id, ym.year, ym.monthValue)
                berechnet++
            }
            ym = ym.plusMonths(1)
        }

        if (berechnet > 0) {
            log.debug(
                "Warmup für {} {}: {} Monate berechnet, {} übersprungen",
                mitarbeiter.vorname,
                mitarbeiter.nachname,
                berechnet,
                uebersprungen,
            )
        }

        return intArrayOf(berechnet, uebersprungen)
    }

    private fun startZeit(zeitbuchung: Zeitbuchung): LocalDateTime =
        zeitbuchung.value("getStartZeit") ?: error("Zeitbuchung ohne StartZeit")

    private inline fun <reified T> Any.value(getter: String): T? =
        javaClass.getMethod(getter).invoke(this) as? T

    companion object {
        private val log = LoggerFactory.getLogger(MonatsSaldoWarmupService::class.java)
    }
}
