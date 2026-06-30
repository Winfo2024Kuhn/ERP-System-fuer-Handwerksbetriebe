package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.kalkulationsprogramm.domain.Feiertag
import org.example.kalkulationsprogramm.repository.FeiertagRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Service
open class FeiertagService(
    private val feiertagRepository: FeiertagRepository,
) {
    private val objectMapper = ObjectMapper()
    private val attemptedYears: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    @Autowired
    @Lazy
    private lateinit var self: FeiertagService

    open fun getFeiertageForJahr(jahr: Int): List<Feiertag> {
        var existingFeiertage = feiertagRepository.findByJahr(jahr)

        if (existingFeiertage.isEmpty() && !attemptedYears.contains(jahr)) {
            try {
                self.loadAndSaveFeiertage(jahr)
            } catch (e: Exception) {
                log.warn(
                    "Feiertage fuer {} konnten nicht gespeichert werden (vermutlich bereits vorhanden): {}",
                    jahr,
                    e.message,
                )
            }
            attemptedYears.add(jahr)
            existingFeiertage = feiertagRepository.findByJahr(jahr)
        }

        return existingFeiertage
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun loadAndSaveFeiertage(jahr: Int) {
        val existing = feiertagRepository.findByJahr(jahr)
        if (existing.isNotEmpty()) {
            return
        }

        var feiertage = ladeFeiertagenVonApi(jahr)
        if (feiertage.isEmpty()) {
            feiertage = generiereBayerischeFeiertage(jahr)
        }

        feiertagRepository.saveAll(feiertage)
    }

    open fun getFeiertageZwischen(von: LocalDate, bis: LocalDate): List<Feiertag> {
        for (jahr in von.year..bis.year) {
            self.getFeiertageForJahr(jahr)
        }
        return feiertagRepository.findByDatumBetween(von, bis)
    }

    open fun istFeiertag(datum: LocalDate): Boolean {
        self.getFeiertageForJahr(datum.year)
        return feiertagRepository.existsByDatumAndBundesland(datum, "BY")
    }

    open fun istHalberFeiertag(datum: LocalDate): Boolean {
        self.getFeiertageForJahr(datum.year)
        return feiertagRepository.findByDatumAndBundesland(datum, "BY")
            .map { it.isHalbTag() }
            .orElse(false)
    }

    open fun getFeiertagInfo(datum: LocalDate): Optional<Feiertag> {
        self.getFeiertageForJahr(datum.year)
        return feiertagRepository.findByDatumAndBundesland(datum, "BY")
    }

    private fun ladeFeiertagenVonApi(jahr: Int): List<Feiertag> {
        val feiertage = mutableListOf<Feiertag>()

        try {
            val restTemplate = RestTemplate()
            val url = API_URL.format(jahr)
            val response = restTemplate.getForObject(url, String::class.java)
            val root = objectMapper.readTree(response)

            if (root.has("status") && root.get("status").asText() == "success") {
                val feiertagsArray = root.get("feiertage")
                if (feiertagsArray != null && feiertagsArray.isArray) {
                    for (ft in feiertagsArray) {
                        val dateStr = ft.get("date").asText()
                        val name = ft.get("fname").asText()
                        val byValue = ft.get("by").asText()

                        if (byValue == "1") {
                            val datum = LocalDate.parse(dateStr)
                            feiertage.add(Feiertag(datum, name, "BY"))
                        }
                    }
                }

                log.info("Erfolgreich {} Feiertage fuer {} von API geladen", feiertage.size, jahr)
            }
        } catch (e: Exception) {
            log.error("Fehler beim Laden der Feiertage von API fuer Jahr {}: {}", jahr, e.message)
        }

        return feiertage
    }

    @Scheduled(cron = "0 0 8 30 12 *")
    @Transactional
    open fun ladeFeiertageFuerNaechstesJahr() {
        val naechstesJahr = LocalDate.now().year + 1
        log.info("Automatisches Laden der Feiertage fuer Jahr {} gestartet...", naechstesJahr)

        loescheFeiertageFuerJahr(naechstesJahr)

        val feiertage = getFeiertageForJahr(naechstesJahr)
        log.info(
            "Automatisches Laden abgeschlossen: {} Feiertage fuer {} geladen",
            feiertage.size,
            naechstesJahr,
        )
    }

    @Transactional
    open fun loescheFeiertageFuerJahr(jahr: Int) {
        val feiertage = feiertagRepository.findByJahr(jahr)
        feiertagRepository.deleteAll(feiertage)
    }

    @Transactional
    open fun regeneriereFeiertage(vonJahr: Int, bisJahr: Int): List<Feiertag> {
        val alleFeiertage = mutableListOf<Feiertag>()

        for (jahr in vonJahr..bisJahr) {
            loescheFeiertageFuerJahr(jahr)
            alleFeiertage.addAll(getFeiertageForJahr(jahr))
        }

        return alleFeiertage
    }

    private fun generiereBayerischeFeiertage(jahr: Int): List<Feiertag> {
        val feiertage = mutableListOf<Feiertag>()

        log.warn("API nicht erreichbar - generiere Feiertage lokal fuer Jahr {}", jahr)

        feiertage.add(Feiertag(LocalDate.of(jahr, 1, 1), "Neujahr"))
        feiertage.add(Feiertag(LocalDate.of(jahr, 1, 6), "Heilige Drei Könige"))
        feiertage.add(Feiertag(LocalDate.of(jahr, 5, 1), "Tag der Arbeit"))
        feiertage.add(Feiertag(LocalDate.of(jahr, 8, 15), "Mariä Himmelfahrt"))
        feiertage.add(Feiertag(LocalDate.of(jahr, 10, 3), "Tag der Deutschen Einheit"))
        feiertage.add(Feiertag(LocalDate.of(jahr, 11, 1), "Allerheiligen"))
        feiertage.add(Feiertag(LocalDate.of(jahr, 12, 25), "1. Weihnachtstag"))
        feiertage.add(Feiertag(LocalDate.of(jahr, 12, 26), "2. Weihnachtstag"))

        val ostersonntag = berechneOstersonntag(jahr)

        feiertage.add(Feiertag(ostersonntag.minusDays(2), "Karfreitag"))
        feiertage.add(Feiertag(ostersonntag.plusDays(1), "Ostermontag"))
        feiertage.add(Feiertag(ostersonntag.plusDays(39), "Christi Himmelfahrt"))
        feiertage.add(Feiertag(ostersonntag.plusDays(50), "Pfingstmontag"))
        feiertage.add(Feiertag(ostersonntag.plusDays(60), "Fronleichnam"))

        return feiertage
    }

    private fun berechneOstersonntag(jahr: Int): LocalDate {
        val a = jahr % 19
        val b = jahr / 100
        val c = jahr % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val monat = (h + l - 7 * m + 114) / 31
        val tag = ((h + l - 7 * m + 114) % 31) + 1

        return LocalDate.of(jahr, monat, tag)
    }

    companion object {
        private val log = LoggerFactory.getLogger(FeiertagService::class.java)
        private const val API_URL = "https://get.api-feiertage.de/?years=%d&states=by"
    }
}
