package org.example.kalkulationsprogramm.dto.Formular;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Antworten und Anfragen der bearbeitbaren Rechtstexte im Formularwesen. */
public final class RechtstextDtos
{
    private RechtstextDtos()
    {
    }

    /** Karte in der Übersicht. {@code fassung} 0 = mitgelieferter Standardtext. */
    public record Uebersicht(String art, String bezeichnung, int fassung, LocalDate stand, boolean standardtext,
            LocalDateTime geaendertAm)
    {
    }

    /** Ein Platzhalter mit Erklärung und Beispielwert für den Editor. */
    public record Platzhalter(String token, String beschreibung, String beispiel)
    {
    }

    /** Gültiger Text mit den Platzhaltern, die der Editor anbietet. */
    public record Detail(String art, String bezeichnung, int fassung, LocalDate stand, boolean standardtext,
            LocalDateTime geaendertAm, String html, List<Platzhalter> platzhalter)
    {
    }

    /** Eintrag in „Frühere Fassungen“. */
    public record FassungKurz(int fassung, LocalDate stand, LocalDateTime geaendertAm)
    {
    }

    /** Eine frühere Fassung zum Ansehen. */
    public record FassungVoll(int fassung, LocalDate stand, LocalDateTime geaendertAm, String html)
    {
    }

    /** Text zum Speichern oder für die Vorschau ({@code html} {@code null} = gespeicherter Text). */
    public record TextAnfrage(String html)
    {
    }
}
