package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Zeitkonto
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.ZeitkontoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class ZeitkontoService(
    private val zeitkontoRepository: ZeitkontoRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val feiertagService: FeiertagService,
) {
    @Transactional
    fun getOrCreateZeitkonto(mitarbeiterId: Long?): Zeitkonto {
        val id = mitarbeiterId ?: throw IllegalArgumentException("Mitarbeiter-ID muss angegeben werden.")
        return zeitkontoRepository.findByMitarbeiterId(id)
            .orElseGet {
                val mitarbeiter = mitarbeiterRepository.findById(id)
                    .orElseThrow { IllegalArgumentException("Mitarbeiter nicht gefunden: $id") }
                zeitkontoRepository.save(Zeitkonto(mitarbeiter))
            }
    }

    fun getAlleZeitkonten(): List<Zeitkonto> =
        zeitkontoRepository.findAll()

    @Transactional
    fun speichereZeitkonto(zeitkonto: Zeitkonto): Zeitkonto =
        zeitkontoRepository.save(zeitkonto)

    @Transactional
    fun aktualisiereZeitkonto(
        mitarbeiterId: Long?,
        montag: BigDecimal?,
        dienstag: BigDecimal?,
        mittwoch: BigDecimal?,
        donnerstag: BigDecimal?,
        freitag: BigDecimal?,
        samstag: BigDecimal?,
        sonntag: BigDecimal?,
    ): Zeitkonto {
        val konto = getOrCreateZeitkonto(mitarbeiterId)
        konto.montagStunden = montag
        konto.dienstagStunden = dienstag
        konto.mittwochStunden = mittwoch
        konto.donnerstagStunden = donnerstag
        konto.freitagStunden = freitag
        konto.samstagStunden = samstag
        konto.sonntagStunden = sonntag
        return zeitkontoRepository.save(konto)
    }

    fun berechneSollstundenFuerMonat(mitarbeiterId: Long?, jahr: Int, monat: Int): BigDecimal {
        val konto = getOrCreateZeitkonto(mitarbeiterId)
        val ersterTag = LocalDate.of(jahr, monat, 1)
        val letzterTag = ersterTag.with(TemporalAdjusters.lastDayOfMonth())
        return berechneSollstundenFuerZeitraum(konto, ersterTag, letzterTag)
    }

    fun berechneSollstundenFuerMonatBisHeute(mitarbeiterId: Long?, jahr: Int, monat: Int): BigDecimal {
        val konto = getOrCreateZeitkonto(mitarbeiterId)
        val ersterTag = LocalDate.of(jahr, monat, 1)
        val heute = LocalDate.now()
        val letzterTag = if (monat == heute.monthValue && jahr == heute.year) {
            heute
        } else {
            ersterTag.with(TemporalAdjusters.lastDayOfMonth())
        }
        return berechneSollstundenFuerZeitraum(konto, ersterTag, letzterTag)
    }

    fun berechneSollstundenFuerZeitraum(konto: Zeitkonto, von: LocalDate, bis: LocalDate): BigDecimal {
        var summe = BigDecimal.ZERO
        var tag = von
        while (!tag.isAfter(bis)) {
            val wochentag = tag.dayOfWeek.value
            var tagesSoll = konto.getSollstundenFuerTag(wochentag)
            if (feiertagService.istHalberFeiertag(tag)) {
                tagesSoll = tagesSoll.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
            }
            summe = summe.add(tagesSoll)
            tag = tag.plusDays(1)
        }
        return summe
    }
}
