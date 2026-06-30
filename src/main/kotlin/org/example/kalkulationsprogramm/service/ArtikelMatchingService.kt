package org.example.kalkulationsprogramm.service

import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.repository.ArtikelRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
class ArtikelMatchingService(
    private val artikelRepository: ArtikelRepository,
    private val lieferantenRepository: LieferantenRepository,
) {
    private val jaro = JaroWinklerSimilarity()

    @Transactional(readOnly = true)
    fun findeBesteTreffer(produktname: String?, produktlinie: String?): List<Artikel> =
        artikelRepository.findAll()
            .sortedByDescending { artikel ->
                score(produktname, produktlinie, readString(artikel, "getProduktname"), readString(artikel, "getProduktlinie"))
            }
            .take(5)

    @Transactional(readOnly = true)
    fun findeLieferantFuerEmail(email: String?): Optional<Lieferanten> {
        if (email == null || !email.contains("@")) {
            return Optional.empty()
        }
        val domain = email.substring(email.indexOf('@') + 1).trim().lowercase()
        return lieferantenRepository.findAll().stream()
            .filter { lieferant ->
                lieferant.kundenEmails
                    .map { it.trim().lowercase() }
                    .any { it.endsWith(domain) }
            }
            .findFirst()
    }

    @Transactional
    fun merkeLieferantenEmail(lieferant: Lieferanten?, email: String?) {
        if (lieferant == null || email == null) {
            return
        }
        val cleaned = email.trim()
        if (lieferant.kundenEmails.map { it.trim() }.none { cleaned.equals(it, ignoreCase = true) }) {
            lieferant.kundenEmails.add(cleaned)
            lieferantenRepository.save(lieferant)
        }
    }

    private fun score(name: String?, line: String?, artName: String?, artLine: String?): Double =
        similarity(name, artName) * 0.7 + similarity(line, artLine) * 0.3

    private fun similarity(a: String?, b: String?): Double {
        if (a == null || b == null) {
            return 0.0
        }
        return jaro.apply(a.lowercase(), b.lowercase())
    }

    private fun readString(target: Any, getterName: String): String? =
        runCatching { target.javaClass.getMethod(getterName).invoke(target) as? String }.getOrNull()
}
