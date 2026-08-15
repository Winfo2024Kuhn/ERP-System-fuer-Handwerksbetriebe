package org.example.kalkulationsprogramm.dto.Artikel;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.ArtikelDokumentTyp;

import lombok.Getter;
import lombok.Setter;

/**
 * Sicht auf ein {@link org.example.kalkulationsprogramm.domain.ArtikelDokument}
 * fuer die REST-Antwort. Die Entity wird nie direkt exponiert - insbesondere
 * fehlt hier der intern vergebene {@code gespeicherterDateiname}, der nur der
 * Service kennen muss.
 */
@Getter
@Setter
public class ArtikelDokumentDto {
    private Long id;
    private String originalDateiname;
    private ArtikelDokumentTyp typ;
    private String beschreibung;
    private LocalDateTime erstelltAm;
    private Long dateigroesseBytes;
    /** Pfad auf den Datei-Endpoint, z.B. {@code /api/artikel/dokumente/42/datei}. */
    private String url;
}
