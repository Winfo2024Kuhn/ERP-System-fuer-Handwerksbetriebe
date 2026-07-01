package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.BuchungsTyp
import org.example.kalkulationsprogramm.domain.KorrekturTyp
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.MonatsSaldo
import org.example.kalkulationsprogramm.domain.Zeitkonto
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.MonatsSaldoRepository
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository
import org.example.kalkulationsprogramm.repository.ZeitkontoKorrekturRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@Service
class MonatsSaldoService(
    private val monatsSaldoRepository: MonatsSaldoRepository,
    private val zeitbuchungRepository: ZeitbuchungRepository,
    private val abwesenheitRepository: AbwesenheitRepository,
    private val korrekturRepository: ZeitkontoKorrekturRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val zeitkontoService: ZeitkontoService,
    private val feiertagService: FeiertagService,
) {
    @Autowired
    @Lazy
    private lateinit var self: MonatsSaldoService

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun getOrBerechne(mitarbeiterId: Long, jahr: Int, monat: Int): MonatsSaldo {
        val heute = LocalDate.now()
        if (jahr == heute.year && monat == heute.monthValue) {
            return berechneMonatsSaldo(mitarbeiterId, jahr, monat)
        }

        val abfrage = YearMonth.of(jahr, monat)
        val aktuell = YearMonth.of(heute.year, heute.monthValue)
        if (abfrage.isAfter(aktuell)) {
            return berechneMonatsSaldo(mitarbeiterId, jahr, monat)
        }

        val cached = monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(mitarbeiterId, jahr, monat)
        if (cached.isPresent && cached.get().gueltig == true) {
            return cached.get()
        }

        val berechnet = berechneMonatsSaldo(mitarbeiterId, jahr, monat)
        return try {
            self.saveMonatsSaldoCache(mitarbeiterId, jahr, monat, berechnet)
        } catch (_: DataIntegrityViolationException) {
            log.debug(
                "MonatsSaldo-Cache concurrent insert für MA={}, {}/{} - verwende berechneten Wert",
                mitarbeiterId,
                jahr,
                monat,
            )
            berechnet
        }
    }

    private fun berechneMonatsSaldo(mitarbeiterId: Long, jahr: Int, monat: Int): MonatsSaldo {
        val ersterTag = LocalDate.of(jahr, monat, 1)
        val letzterTag = YearMonth.of(jahr, monat).atEndOfMonth()
        val startDt = ersterTag.atStartOfDay()
        val endDt = letzterTag.atTime(23, 59, 59)

        val buchungen = zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(mitarbeiterId, startDt, endDt)
        val istStunden = buchungen
            .asSequence()
            .filter { it.typ != BuchungsTyp.PAUSE }
            .mapNotNull { it.anzahlInStunden }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        val sollStunden = zeitkontoService.berechneSollstundenFuerMonat(mitarbeiterId, jahr, monat)
        val abwesenheitsStunden = abwesenheitRepository
            .sumStundenByMitarbeiterIdAndDatumBetween(mitarbeiterId, ersterTag, letzterTag)
            ?: BigDecimal.ZERO

        val zeitkonto = zeitkontoService.getOrCreateZeitkonto(mitarbeiterId)
        val feiertagsStunden = berechneFeiertagsStunden(zeitkonto, ersterTag, letzterTag)

        val korrekturStunden = korrekturRepository
            .findByMitarbeiterIdAndDatumBetween(mitarbeiterId, ersterTag, letzterTag)
            .asSequence()
            .filter { it.storniert != true }
            .filter { it.typ == KorrekturTyp.STUNDEN }
            .map { it.stunden ?: BigDecimal.ZERO }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        val saldo = MonatsSaldo()
        saldo.jahr = jahr
        saldo.monat = monat
        saldo.istStunden = istStunden
        saldo.sollStunden = sollStunden
        saldo.abwesenheitsStunden = abwesenheitsStunden
        saldo.feiertagsStunden = feiertagsStunden
        saldo.korrekturStunden = korrekturStunden
        saldo.gueltig = true
        saldo.berechnetAm = LocalDateTime.now()

        return saldo
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveMonatsSaldoCache(mitarbeiterId: Long, jahr: Int, monat: Int, berechnet: MonatsSaldo): MonatsSaldo {
        val existing = monatsSaldoRepository.findByMitarbeiterIdAndJahrAndMonat(mitarbeiterId, jahr, monat)

        val entity: MonatsSaldo = if (existing.isPresent) {
            existing.get()
        } else {
            val mitarbeiter: Mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow { IllegalArgumentException("Mitarbeiter nicht gefunden: $mitarbeiterId") }
            MonatsSaldo().also {
                it.mitarbeiter = mitarbeiter
                it.jahr = jahr
                it.monat = monat
            }
        }

        entity.istStunden = berechnet.istStunden
        entity.sollStunden = berechnet.sollStunden
        entity.abwesenheitsStunden = berechnet.abwesenheitsStunden
        entity.feiertagsStunden = berechnet.feiertagsStunden
        entity.korrekturStunden = berechnet.korrekturStunden
        entity.gueltig = true
        entity.berechnetAm = LocalDateTime.now()

        return monatsSaldoRepository.save(entity)
    }

    @Transactional
    fun invalidiereMonat(mitarbeiterId: Long, jahr: Int, monat: Int) {
        monatsSaldoRepository.invalidiere(mitarbeiterId, jahr, monat)
        log.debug("MonatsSaldo invalidiert: Mitarbeiter={}, {}/{}", mitarbeiterId, jahr, monat)
    }

    @Transactional
    fun invalidiereJahr(mitarbeiterId: Long, jahr: Int) {
        monatsSaldoRepository.invalidiereJahr(mitarbeiterId, jahr)
        log.debug("MonatsSaldo invalidiert (ganzes Jahr): Mitarbeiter={}, {}", mitarbeiterId, jahr)
    }

    @Transactional
    fun invalidiereAlle(mitarbeiterId: Long) {
        monatsSaldoRepository.invalidiereAlle(mitarbeiterId)
        log.debug("MonatsSaldo invalidiert (alle): Mitarbeiter={}", mitarbeiterId)
    }

    @Transactional
    fun invalidiereFuerDatum(mitarbeiterId: Long, datum: LocalDate?) {
        if (datum != null) {
            invalidiereMonat(mitarbeiterId, datum.year, datum.monthValue)
        }
    }

    @Transactional
    fun invalidiereFuerDateTime(mitarbeiterId: Long, dateTime: LocalDateTime?) {
        if (dateTime != null) {
            invalidiereFuerDatum(mitarbeiterId, dateTime.toLocalDate())
        }
    }

    private fun berechneFeiertagsStunden(zeitkonto: Zeitkonto, von: LocalDate, bis: LocalDate): BigDecimal {
        var summe = BigDecimal.ZERO
        var tag = von
        while (!tag.isAfter(bis)) {
            val wochentag = tag.dayOfWeek.value
            val tagesSoll = zeitkonto.getSollstundenFuerTag(wochentag)

            if (tagesSoll.compareTo(BigDecimal.ZERO) > 0 && feiertagService.istFeiertag(tag)) {
                summe = if (feiertagService.istHalberFeiertag(tag)) {
                    summe.add(tagesSoll.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP))
                } else {
                    summe.add(tagesSoll)
                }
            }
            tag = tag.plusDays(1)
        }
        return summe
    }

    companion object {
        private val log = LoggerFactory.getLogger(MonatsSaldoService::class.java)
    }
}
