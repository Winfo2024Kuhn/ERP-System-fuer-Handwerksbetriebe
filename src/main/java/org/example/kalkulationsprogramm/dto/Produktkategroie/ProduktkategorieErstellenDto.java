package org.example.kalkulationsprogramm.dto.Produktkategroie;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

@Getter
@Setter
public class ProduktkategorieErstellenDto {
    private String bezeichnung;
    private Verrechnungseinheit verrechnungseinheit;
    private Long parentId;
    private String beschreibung;
    /**
     * Nur beim Anlegen: Liegen im übergeordneten Ordner direkt Leistungen, wandern sie
     * in den neuen Unterordner (Leistungen gibt es nur in Ordnern ohne Unterordner).
     */
    private boolean leistungenMitnehmen;
    /** Nur beim Bearbeiten: {@code true} löscht das Ordnerbild. */
    private boolean bildEntfernen;
}
