package org.example.kalkulationsprogramm.dto.Produktkategroie;

/**
 * Wie ein Ordner mit Bezügen gelöscht wird.
 *
 * @param modus           {@code UMZIEHEN} (Inhalt und Projekte gehen auf den Ziel-Ordner über)
 *                        oder {@code ALLES} (Ordner samt Inhalt löschen)
 * @param zielKategorieId Ziel-Ordner, nur bei {@code UMZIEHEN}
 */
public record KategorieLoeschenDto(String modus, Long zielKategorieId) {
}
