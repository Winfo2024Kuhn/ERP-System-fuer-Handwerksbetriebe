package org.example.kalkulationsprogramm.dto.Textbaustein;

import java.util.List;

/**
 * Dokumentarten, die in der Oberfläche unter einer gemeinsamen Überschrift stehen.
 *
 * @param gruppe         Überschrift, z. B. „Rechnungen“
 * @param dokumenttypen  Labels der Dokumentarten wie in {@link TextbausteinDto#getDokumenttypen()}
 */
public record DokumenttypGruppeDto(String gruppe, List<String> dokumenttypen) {
}
