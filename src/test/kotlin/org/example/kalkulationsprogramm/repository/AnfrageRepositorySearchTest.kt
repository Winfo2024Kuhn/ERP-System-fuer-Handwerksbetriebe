package org.example.kalkulationsprogramm.repository

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.Kunde
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.LocalDate

@DataJpaTest
class AnfrageRepositorySearchTest {

    @Autowired
    private lateinit var anfrageRepository: AnfrageRepository

    @Autowired
    private lateinit var kundeRepository: KundeRepository

    @Test
    fun searchRespectsUmlautsAndAsciiSeparately() {
        val anfrage1 = Anfrage()
        val kunde1 = Kunde().apply {
            name = "Haentschel"
            kundennummer = "K1001"
        }
        kundeRepository.saveAndFlush(kunde1)
        anfrage1.kunde = kunde1
        anfrage1.bauvorhaben = "Testbau"
        anfrage1.anlegedatum = LocalDate.now()
        anfrageRepository.saveAndFlush(anfrage1)

        val anfrage2 = Anfrage()
        val kunde2 = Kunde().apply {
            name = "Häntschel"
            kundennummer = "K1002"
        }
        kundeRepository.saveAndFlush(kunde2)
        anfrage2.kunde = kunde2
        anfrage2.bauvorhaben = "Testbau 2"
        anfrage2.anlegedatum = LocalDate.now()
        anfrageRepository.saveAndFlush(anfrage2)

        val umlaut = anfrageRepository.search("Häntschel", null, null, null, null)
        assertThat(umlaut).extracting<String> { it.kunde!!.name }
            .containsExactlyInAnyOrder("Häntschel")

        val ascii = anfrageRepository.search("Haentschel", null, null, null, null)
        assertThat(ascii).extracting<String> { it.kunde!!.name }
            .containsExactlyInAnyOrder("Haentschel")
    }

    @Test
    fun searchBauvorhabenDoesNotCrossMapUmlauts() {
        val anfrage1 = Anfrage()
        val kunde1 = Kunde().apply {
            name = "Kunde"
            kundennummer = "K1003"
        }
        kundeRepository.saveAndFlush(kunde1)
        anfrage1.kunde = kunde1
        anfrage1.bauvorhaben = "Müllerstraße Dach"
        anfrage1.anlegedatum = LocalDate.now()
        anfrageRepository.saveAndFlush(anfrage1)

        val anfrage2 = Anfrage()
        val kunde2 = Kunde().apply {
            name = "Kunde"
            kundennummer = "K1004"
        }
        kundeRepository.saveAndFlush(kunde2)
        anfrage2.kunde = kunde2
        anfrage2.bauvorhaben = "Muellerbau"
        anfrage2.anlegedatum = LocalDate.now()
        anfrageRepository.saveAndFlush(anfrage2)

        val res1 = anfrageRepository.search(null, "Mueller", null, null, null)
        assertThat(res1).extracting<String> { it.bauvorhaben }
            .containsExactlyInAnyOrder("Muellerbau")

        val res2 = anfrageRepository.search(null, "Müller", null, null, null)
        assertThat(res2).extracting<String> { it.bauvorhaben }
            .containsExactlyInAnyOrder("Müllerstraße Dach")
    }
}
