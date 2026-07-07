package org.example.kalkulationsprogramm.dto.Artikel

import org.example.kalkulationsprogramm.domain.LieferantRolle

data class KategorieResponseDto(
    var id: Int? = null,
    var bezeichnung: String? = null,
    var leaf: Boolean = false,
    var typischeRollen: Set<LieferantRolle>? = null,
)
