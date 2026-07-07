package org.example.kalkulationsprogramm.dto.Artikel

import org.example.kalkulationsprogramm.domain.LieferantRolle

data class KategorieCreateDto(
    var bezeichnung: String? = null,
    var parentId: Int? = null,
    var typischeRollen: Set<LieferantRolle>? = null,
)
