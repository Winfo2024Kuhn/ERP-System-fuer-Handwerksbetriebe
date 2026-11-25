package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

/**
 * Eingabe beim Anlegen und Ändern eines Lieferanten-Ansprechpartners. Geprüft und bereinigt
 * (trimmen, leer → {@code null}, Längen, E-Mail) wird in {@code LieferantAnsprechpartnerEingabe},
 * damit die Meldungen als ein verständlicher Satz zurückkommen.
 */
@Getter
@Setter
public class LieferantAnsprechpartnerRequestDto {
    private String anrede;
    private String vorname;
    private String nachname;
    private String aufgabe;
    private String telefon;
    private String mobiltelefon;
    private String email;
    private String notiz;
}
