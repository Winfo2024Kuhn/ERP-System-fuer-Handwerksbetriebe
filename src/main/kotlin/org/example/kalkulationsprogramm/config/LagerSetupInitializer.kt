package org.example.kalkulationsprogramm.config

import org.example.kalkulationsprogramm.domain.Lagerort
import org.example.kalkulationsprogramm.repository.LagerortRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LagerSetupInitializer(
    private val lagerortRepository: LagerortRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (lagerortRepository.findByCodeIgnoreCase(DEFAULT_CODE) != null) return
        lagerortRepository.save(
            Lagerort().apply {
                code = DEFAULT_CODE
                name = "Hauptlager"
                regal = "HL"
                fach = "DEFAULT"
                aktiv = true
            },
        )
    }

    companion object {
        private const val DEFAULT_CODE = "HL-DEFAULT"
    }
}
