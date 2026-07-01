package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Abwesenheit
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp
import org.example.kalkulationsprogramm.domain.Urlaubsantrag
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.UrlaubsantragRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

@Service
class UrlaubsantragService(
    private val repository: UrlaubsantragRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val abwesenheitRepository: AbwesenheitRepository,
    private val feiertagService: FeiertagService,
    private val zeitkontoService: ZeitkontoService,
    private val monatsSaldoService: MonatsSaldoService,
    private val zeitkontoKorrekturService: ZeitkontoKorrekturService,
) {
    @Transactional
    fun createAntrag(
        mitarbeiterId: Long,
        von: LocalDate,
        bis: LocalDate,
        bemerkung: String?,
        typ: Urlaubsantrag.Typ?,
    ): Urlaubsantrag {
        val mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
            .orElseThrow { IllegalArgumentException("Mitarbeiter nicht gefunden") }

        if (typ == Urlaubsantrag.Typ.URLAUB) {
            val jahr = von.year
            val verbleibend = getResturlaub(mitarbeiterId, jahr)
            val beantragteTage = zaehleArbeitstage(von, bis)
            if (beantragteTage > verbleibend) {
                throw IllegalStateException(
                    String.format(
                        "Nicht genügend Urlaubstage. Verbleibend: %d, Beantragt: %d",
                        verbleibend,
                        beantragteTage,
                    ),
                )
            }
        }

        val ueberlappend = repository.findOverlapping(mitarbeiterId, von, bis)
        if (ueberlappend.isNotEmpty()) {
            val erster = ueberlappend.first()
            throw IllegalStateException(
                String.format(
                    "Es existiert bereits ein %s-Antrag vom %s bis %s in diesem Zeitraum",
                    erster.typ!!.name,
                    erster.vonDatum,
                    erster.bisDatum,
                ),
            )
        }

        val antrag = Urlaubsantrag()
        antrag.mitarbeiter = mitarbeiter
        antrag.vonDatum = von
        antrag.bisDatum = bis
        antrag.bemerkung = bemerkung
        antrag.typ = typ ?: Urlaubsantrag.Typ.URLAUB
        antrag.status = Urlaubsantrag.Status.OFFEN

        return repository.save(antrag)
    }

    private fun toAbwesenheitsTyp(typ: Urlaubsantrag.Typ?): AbwesenheitsTyp =
        when (typ) {
            Urlaubsantrag.Typ.URLAUB -> AbwesenheitsTyp.URLAUB
            Urlaubsantrag.Typ.KRANKHEIT -> AbwesenheitsTyp.KRANKHEIT
            Urlaubsantrag.Typ.FORTBILDUNG -> AbwesenheitsTyp.FORTBILDUNG
            Urlaubsantrag.Typ.ZEITAUSGLEICH -> AbwesenheitsTyp.ZEITAUSGLEICH
            else -> AbwesenheitsTyp.URLAUB
        }

    @Transactional
    fun approveAntrag(antragId: Long): Urlaubsantrag {
        val antrag = repository.findById(antragId)
            .orElseThrow { IllegalArgumentException("Antrag nicht gefunden") }

        if (antrag.status != Urlaubsantrag.Status.OFFEN) {
            throw IllegalStateException("Nur offene Anträge können genehmigt werden")
        }

        antrag.status = Urlaubsantrag.Status.GENEHMIGT
        val antragTyp = antrag.typ!!
        val mitarbeiter = antrag.mitarbeiter!!
        val vonDatum = antrag.vonDatum!!
        val bisDatum = antrag.bisDatum!!
        val abwesenheitsTyp = toAbwesenheitsTyp(antragTyp)

        var date = vonDatum
        while (!date.isAfter(bisDatum)) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY &&
                date.dayOfWeek != DayOfWeek.SUNDAY &&
                !feiertagService.istFeiertag(date)
            ) {
                val mitarbeiterId = mitarbeiter.id!!
                val sollStunden = zeitkontoService.getOrCreateZeitkonto(mitarbeiterId)
                    .getSollstundenFuerTag(date.dayOfWeek.value)

                if (sollStunden.compareTo(BigDecimal.ZERO) > 0 &&
                    !abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(mitarbeiterId, date, abwesenheitsTyp)
                ) {
                    val abwesenheit = Abwesenheit()
                    abwesenheit.mitarbeiter = mitarbeiter
                    abwesenheit.urlaubsantrag = antrag
                    abwesenheit.typ = abwesenheitsTyp
                    abwesenheit.datum = date
                    abwesenheit.stunden = sollStunden
                    abwesenheit.notiz = antragTyp.name + " (Antrag #" + antrag.id + ")"

                    abwesenheitRepository.save(abwesenheit)
                }
            }
            date = date.plusDays(1)
        }

        invalidiereBetroffeneMonate(mitarbeiter.id!!, vonDatum, bisDatum)

        return repository.save(antrag)
    }

    @Transactional
    fun rejectAntrag(antragId: Long): Urlaubsantrag {
        val antrag = repository.findById(antragId)
            .orElseThrow { IllegalArgumentException("Antrag nicht gefunden") }

        antrag.status = Urlaubsantrag.Status.ABGELEHNT
        return repository.save(antrag)
    }

    @Transactional
    fun stornoAntrag(antragId: Long): Urlaubsantrag {
        val antrag = repository.findById(antragId)
            .orElseThrow { IllegalArgumentException("Antrag nicht gefunden") }

        abwesenheitRepository.deleteByUrlaubsantragId(antragId)
        invalidiereBetroffeneMonate(antrag.mitarbeiter!!.id!!, antrag.vonDatum!!, antrag.bisDatum!!)

        antrag.status = Urlaubsantrag.Status.STORNIERT
        return repository.save(antrag)
    }

    private fun invalidiereBetroffeneMonate(mitarbeiterId: Long, von: LocalDate, bis: LocalDate) {
        var ym = YearMonth.from(von)
        val end = YearMonth.from(bis)
        while (!ym.isAfter(end)) {
            monatsSaldoService.invalidiereMonat(mitarbeiterId, ym.year, ym.monthValue)
            ym = ym.plusMonths(1)
        }
    }

    fun getOffeneAntraege(): List<Urlaubsantrag> =
        repository.findByStatus(Urlaubsantrag.Status.OFFEN)

    fun getAntraegeByStatus(status: Urlaubsantrag.Status): List<Urlaubsantrag> =
        repository.findByStatus(status)

    fun getAntraegeByMitarbeiter(mitarbeiterId: Long): List<Urlaubsantrag> =
        repository.findByMitarbeiterIdOrderByVonDatumDesc(mitarbeiterId)

    fun getAntraegeByMitarbeiterAndYear(mitarbeiterId: Long, jahr: Int): List<Urlaubsantrag> {
        val start = LocalDate.of(jahr, 1, 1)
        val end = LocalDate.of(jahr, 12, 31)
        return repository.findByMitarbeiterIdAndVonDatumBetweenOrderByVonDatumDesc(mitarbeiterId, start, end)
    }

    fun getAntraegeByMitarbeiterAndStatus(
        mitarbeiterId: Long,
        status: Urlaubsantrag.Status,
    ): List<Urlaubsantrag> =
        repository.findByMitarbeiterIdAndStatusOrderByVonDatumDesc(mitarbeiterId, status)

    @Transactional(readOnly = true)
    fun getResturlaub(mitarbeiterId: Long, jahr: Int): Int {
        val mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
            .orElseThrow { IllegalArgumentException("Mitarbeiter nicht gefunden") }

        val jahresUrlaub = mitarbeiter.jahresUrlaub ?: 30
        val jahresanfang = LocalDate.of(jahr, 1, 1)
        val jahresende = LocalDate.of(jahr, 12, 31)

        val abwesenheiten = abwesenheitRepository
            .findByMitarbeiterIdAndDatumBetween(mitarbeiterId, jahresanfang, jahresende)

        val genommen = abwesenheiten.count { it.typ == AbwesenheitsTyp.URLAUB }
        val korrektur = zeitkontoKorrekturService.summiereAktiveUrlaubsKorrekturen(mitarbeiterId, jahr)?.toInt() ?: 0

        return max(0, jahresUrlaub - genommen + korrektur)
    }

    private fun zaehleArbeitstage(von: LocalDate, bis: LocalDate): Long {
        var count = 0L
        var date = von
        while (!date.isAfter(bis)) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY &&
                date.dayOfWeek != DayOfWeek.SUNDAY &&
                !feiertagService.istFeiertag(date)
            ) {
                count++
            }
            date = date.plusDays(1)
        }
        return count
    }
}
