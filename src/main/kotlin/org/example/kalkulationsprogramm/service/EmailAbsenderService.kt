package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.EmailAbsender
import org.example.kalkulationsprogramm.dto.EmailAbsenderDto
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
class EmailAbsenderService(
    private val repository: EmailAbsenderRepository,
) {
    @Transactional(readOnly = true)
    fun findAll(): List<EmailAbsenderDto> =
        repository.findAllByOrderBySortierungAscIdAsc()
            .mapNotNull(EmailAbsenderDto::fromEntity)

    @Transactional(readOnly = true)
    fun findActiveEmailAddresses(): List<String> =
        repository.findByAktivTrueOrderBySortierungAscIdAsc()
            .mapNotNull { it.emailAdresse }
            .filter { it.isNotBlank() }

    @Transactional(readOnly = true)
    fun findFirstActive(): Optional<EmailAbsender> =
        repository.findFirstByAktivTrueOrderBySortierungAscIdAsc()

    @Transactional(readOnly = true)
    fun findById(id: Long?): Optional<EmailAbsender> =
        if (id == null) Optional.empty() else repository.findById(id)

    @Transactional
    fun save(dto: EmailAbsenderDto?): EmailAbsenderDto {
        if (dto == null) {
            throw IllegalArgumentException("Daten fehlen.")
        }
        val adresse = dto.emailAdresse?.trim()
        if (adresse.isNullOrEmpty()) {
            throw IllegalArgumentException("E-Mail-Adresse darf nicht leer sein.")
        }
        if (!adresse.matches(Regex("^[^@\\s]+@[^@\\s.]+(?:\\.[^@\\s.]+)+$"))) {
            throw IllegalArgumentException("Ungueltige E-Mail-Adresse: $adresse")
        }

        val entity = if (dto.id != null) {
            repository.findById(dto.id!!)
                .orElseThrow { IllegalArgumentException("Absender nicht gefunden: ${dto.id}") }
        } else {
            EmailAbsender()
        }

        repository.findByEmailAdresseIgnoreCase(adresse).ifPresent { existing ->
            if (entity.id == null || existing.id != entity.id) {
                throw IllegalArgumentException("Diese E-Mail-Adresse ist bereits angelegt.")
            }
        }

        entity.emailAdresse = adresse
        entity.anzeigename = dto.anzeigename?.trim()
        entity.aktiv = dto.isAktiv
        entity.sortierung = dto.sortierung

        return EmailAbsenderDto.fromEntity(repository.save(entity))!!
    }

    @Transactional
    fun delete(id: Long?) {
        if (id == null) {
            return
        }
        repository.deleteById(id)
    }
}
