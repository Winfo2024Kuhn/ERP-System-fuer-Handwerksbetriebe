package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Ein potenzieller Duplikat-Treffer beim Anlegen eines Kunden.
 * Enthält die wichtigsten Anzeige-Felder des Bestandskunden plus die Liste der
 * Match-Gründe (z.B. "E-Mail gleich"), damit das Frontend dem User klar
 * anzeigen kann, warum dieser Kunde als möglicher Duplikat erkannt wurde.
 */
@Getter
@Setter
public class KundeDuplikatTrefferDto {
    private Long id;
    private String kundennummer;
    private String name;
    private String ansprechspartner;
    private String strasse;
    private String plz;
    private String ort;
    private String telefon;
    private String mobiltelefon;
    private List<String> kundenEmails;

    private int score;
    private List<KundeDuplikatGrund> gruende;
}
