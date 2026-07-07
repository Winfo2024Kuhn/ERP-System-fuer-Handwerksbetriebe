package org.example.kalkulationsprogramm.dto.Kunde

import java.time.LocalDateTime

data class KundeNotizDto(
    var id: Long? = null,
    var text: String? = null,
    var erstelltAm: LocalDateTime? = null,
)
