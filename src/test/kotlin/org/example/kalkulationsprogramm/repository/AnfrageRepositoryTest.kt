package org.example.kalkulationsprogramm.repository

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.Projekt
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.math.BigDecimal
import java.time.LocalDate

@DataJpaTest
class AnfrageRepositoryTest {

    @Autowired
    private lateinit var anfrageRepository: AnfrageRepository

    @Autowired
    private lateinit var projektRepository: ProjektRepository

    @Test
    fun findByProjektIdInReturnsMatchingAnfragen() {
        val projekt1 = createProjekt("AN1")
        val projekt2 = createProjekt("AN2")
        val projekt3 = createProjekt("AN3")
        projektRepository.saveAndFlush(projekt1)
        projektRepository.saveAndFlush(projekt2)
        projektRepository.saveAndFlush(projekt3)

        anfrageRepository.save(Anfrage().apply { projekt = projekt1 })
        anfrageRepository.save(Anfrage().apply { projekt = projekt2 })
        anfrageRepository.save(Anfrage().apply { projekt = projekt3 })
        anfrageRepository.flush()

        val result = anfrageRepository.findByProjektIdIn(listOf(projekt1.id, projekt2.id))

        assertThat(result)
            .extracting<Long> { it.projekt!!.id }
            .containsExactlyInAnyOrder(projekt1.id, projekt2.id)
    }

    private fun createProjekt(auftragsnummer: String): Projekt =
        Projekt().apply {
            bauvorhaben = "Bau"
            this.auftragsnummer = auftragsnummer
            anlegedatum = LocalDate.now()
            bruttoPreis = BigDecimal.ZERO
            setBezahlt(false)
        }
}
