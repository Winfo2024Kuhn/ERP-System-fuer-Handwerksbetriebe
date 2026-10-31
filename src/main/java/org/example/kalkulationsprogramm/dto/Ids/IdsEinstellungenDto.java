package org.example.kalkulationsprogramm.dto.Ids;

import java.util.List;

/** Einstellungen der Shop-Anbindung für Admins. */
public record IdsEinstellungenDto(boolean aktiv, boolean verschluesselungEingerichtet, List<IdsZugangDto> zugaenge) {
}
