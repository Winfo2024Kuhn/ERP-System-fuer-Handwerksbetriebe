package org.example.kalkulationsprogramm.dto.Ids;

import java.util.Map;

/** Was der Abgleich mit dem Artikelstamm geändert hat; {@code artikelIds}: Zeilen-Index → Artikel. */
public record IdsArtikelstammErgebnisDto(int aktualisiert, int angelegt, int unveraendert, Map<Integer, Long> artikelIds) {
}
