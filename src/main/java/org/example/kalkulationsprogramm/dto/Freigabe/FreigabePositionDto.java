package org.example.kalkulationsprogramm.dto.Freigabe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kundensichere Darstellung einer einzelnen Position eines Geschäftsdokuments für die
 * öffentliche Freigabe-Seite. Abgeleitet aus dem {@code positionenJson} des Quelldokuments –
 * bewusst ohne interne Felder (leistungId, kategorieId, Textbaustein-Metadaten).
 *
 * <p>Optionale Positionen ({@code optional == true}) sind Alternativen, die der Kunde per
 * Checkbox mitbeauftragen kann. Die {@link #blockId} ist die stabile Referenz, die er dabei
 * zurückschickt.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreigabePositionDto
{
    /** Stabile ID des Blocks – wird bei der Annahme für die Alternativen-Auswahl zurückgesendet. */
    private String blockId;

    /** SERVICE | SECTION_HEADER | TEXT | SUBTOTAL | SEPARATOR | CLOSURE */
    private String typ;

    /** Positionsnummer (z.B. "1.2"), optional. */
    private String pos;

    /** Titel/Bezeichnung der Position. */
    private String bezeichnung;

    /** Beschreibung, kann HTML enthalten – im Frontend sicher (sanitized) rendern. */
    private String beschreibungHtml;

    private BigDecimal menge;

    private String einheit;

    private BigDecimal einzelpreisNetto;

    /** Rabatt in Prozent (0-100). */
    private BigDecimal rabattProzent;

    /** Zeilensumme nach Rabatt (netto). */
    private BigDecimal gesamtpreisNetto;

    /** true = Alternativposition, die der Kunde per Checkbox mitbeauftragen kann. */
    private boolean optional;

    /** Label eines SECTION_HEADER (Bauabschnitt). */
    private String sectionLabel;

    /** Verschachtelte SERVICE-Blöcke eines SECTION_HEADER. */
    private List<FreigabePositionDto> children;
}
