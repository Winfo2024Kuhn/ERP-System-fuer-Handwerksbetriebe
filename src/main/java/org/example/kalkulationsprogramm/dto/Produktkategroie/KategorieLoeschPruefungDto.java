package org.example.kalkulationsprogramm.dto.Produktkategroie;

import java.util.List;

/**
 * Was an einem Ordner (inkl. aller Unterordner) hängt, bevor er gelöscht wird.
 * Die Listen sind gekürzt; die Zähler nennen die volle Anzahl.
 */
public record KategorieLoeschPruefungDto(
        Long kategorieId,
        String bezeichnung,
        String pfad,
        boolean hatBezuege,
        long unterordnerAnzahl,
        long leistungAnzahl,
        long projektAnzahl,
        long zeitbuchungAnzahl,
        List<Unterordner> unterordner,
        List<LeistungEintrag> leistungen,
        List<ProjektEintrag> projekte) {

    public record Unterordner(Long id, String bezeichnung, String pfad) {
    }

    public record LeistungEintrag(Long id, String name, Long ordnerId) {
    }

    public record ProjektEintrag(Long id, String bauvorhaben, String auftragsnummer, String kunde) {
    }
}
