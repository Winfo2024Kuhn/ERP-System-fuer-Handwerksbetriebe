package org.example.kalkulationsprogramm.dto.Lieferant;

import java.util.List;

/**
 * Lieferant in der Liste der Handy-App ({@code GET /api/zeiterfassung/lieferanten}).
 * Feldnamen wie bisher (vorher eine Map), damit die App unverändert bleibt.
 *
 * @param firmenname      Name des Lieferanten
 * @param ansprechpartner Ansprechpartner, sortiert nach Nachname, Vorname; leer, wenn keiner
 */
public record ZeiterfassungLieferantDto(Long id,
                                        String firmenname,
                                        String aliasName,
                                        String strasse,
                                        String plz,
                                        String ort,
                                        String telefon,
                                        String mobiltelefon,
                                        List<String> kundenEmails,
                                        String lieferantenTyp,
                                        String eigeneKundennummer,
                                        List<LieferantAnsprechpartnerKurzDto> ansprechpartner) {
}
