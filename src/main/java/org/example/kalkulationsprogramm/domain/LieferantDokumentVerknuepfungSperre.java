package org.example.kalkulationsprogramm.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein von Hand gelöstes Paar zweier Lieferanten-Dokumente. Der automatische
 * Abgleich verknüpft gesperrte Paare nie wieder – sonst käme eine abgehängte
 * Rechnung beim nächsten Neu-Verknüpfen zurück.
 *
 * <p>Gespeichert in der Richtung der Verknüpfung (Nachfolger → Vorgänger);
 * geprüft wird in beide Richtungen.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lieferant_dokument_verknuepfung_gesperrt")
@IdClass(LieferantDokumentVerknuepfungSperre.Schluessel.class)
public class LieferantDokumentVerknuepfungSperre {

    @Id
    @Column(name = "dokument_id", nullable = false)
    private Long dokumentId;

    @Id
    @Column(name = "verknuepft_id", nullable = false)
    private Long verknuepftId;

    @Column(name = "gesperrt_am", nullable = false)
    private LocalDateTime gesperrtAm;

    /** Zusammengesetzter Primärschlüssel. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Schluessel implements Serializable {
        private Long dokumentId;
        private Long verknuepftId;
    }
}
