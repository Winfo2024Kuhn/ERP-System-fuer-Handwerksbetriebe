package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.repository.ArtikelRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(ArtikelMatchingService::class)
class ArtikelMatchingServiceTest {

    @Autowired
    private lateinit var artikelRepository: ArtikelRepository

    @Autowired
    private lateinit var artikelMatchingService: ArtikelMatchingService

    @Autowired
    private lateinit var lieferantenRepository: LieferantenRepository

    @Test
    fun findetBestenTrefferNachAehnlichkeit() {
        val a1 = Artikel().apply {
            produktname = "Quadratrohr 50X3"
            produktlinie = "EN 10305-5"
        }
        artikelRepository.save(a1)

        val a2 = Artikel().apply {
            produktname = "Rundrohr 50X3"
            produktlinie = "EN 10219"
        }
        artikelRepository.save(a2)

        val treffer = artikelMatchingService.findeBesteTreffer("Quadratrohr 50X3", "EN 10305-5")

        assertThat(treffer).isNotEmpty()
        assertThat(treffer.first().produktname).isEqualTo("Quadratrohr 50X3")
    }

    @Test
    fun findetLieferantNachDomainUndSpeichertNeueAdresse() {
        val lieferant = Lieferanten().apply {
            lieferantenname = "Reinhard Stahl"
            kundenEmails.add("info@reinhard-stahl.de")
        }
        lieferantenRepository.save(lieferant)

        val found = artikelMatchingService.findeLieferantFuerEmail("benny.boettcher@reinhard-stahl.de")
            .orElse(null)
        assertThat(found).isNotNull()
        assertThat(found.id).isEqualTo(lieferant.id)

        artikelMatchingService.merkeLieferantenEmail(lieferant, "benny.boettcher@reinhard-stahl.de")
        val reloaded = lieferantenRepository.findById(lieferant.id).orElseThrow()
        assertThat(reloaded.kundenEmails).contains("info@reinhard-stahl.de", "benny.boettcher@reinhard-stahl.de")
    }

    @Test
    fun findetLieferantAuchBeiWhitespaceInAdresse() {
        val lieferant = Lieferanten().apply {
            lieferantenname = "Reinhard Stahl"
            kundenEmails.add(" info@reinhard-stahl.de ")
        }
        lieferantenRepository.save(lieferant)

        assertThat(artikelMatchingService.findeLieferantFuerEmail("user@reinhard-stahl.de")).isPresent()
    }

    @Test
    fun merkeLieferantenEmailTrimmtAdresse() {
        val lieferant = Lieferanten().apply {
            lieferantenname = "Reinhard Stahl"
        }
        lieferantenRepository.save(lieferant)

        artikelMatchingService.merkeLieferantenEmail(lieferant, " new@reinhard-stahl.de ")
        val reloaded = lieferantenRepository.findById(lieferant.id).orElseThrow()
        assertThat(reloaded.kundenEmails).containsExactly("new@reinhard-stahl.de")
    }

    @Test
    fun findeLieferantFuerUnbekannteDomainGibtLeer() {
        assertThat(artikelMatchingService.findeLieferantFuerEmail("user@unknown.com")).isEmpty()
    }
}
