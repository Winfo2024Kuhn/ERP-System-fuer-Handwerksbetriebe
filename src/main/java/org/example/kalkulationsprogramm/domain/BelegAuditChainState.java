package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Singleton-Zeile (id = 1) mit dem Kopf der Beleg-Hash-Kette.
 *
 * <p>Wird beim Anhaengen eines Audit-Eintrags per {@code SELECT ... FOR UPDATE}
 * gelockt. Ohne diesen Lock koennten zwei gleichzeitige Aktionen denselben
 * {@code previousHash} ziehen -- die Kette waere dann eine Verzweigung statt
 * einer Linie und liesse sich nicht mehr eindeutig nachrechnen.</p>
 *
 * <p>{@link #lastLaufendeNummer} haengt bewusst hier mit drin: der
 * Monatsabschluss braucht im selben Moment einen neuen Kettenplatz UND die
 * naechste Belegnummer. Beides unter einem einzigen Lock zu ziehen ist der
 * einfachste Weg, Nummernluecken durch parallele Abschluesse auszuschliessen.</p>
 *
 * <p>Getrennt von {@link AuditChainState} (Ausgangsrechnungen), weil dort ein
 * {@code CHECK (id = 1)} genau eine Kette erzwingt.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "beleg_audit_chain_state")
public class BelegAuditChainState {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "last_chain_index", nullable = false)
    private Long lastChainIndex;

    @Column(name = "last_entry_hash", columnDefinition = "CHAR(64)")
    private String lastEntryHash;

    /** Zuletzt vergebene laufende Belegnummer. Startet bei 0, erste Nummer ist also 1. */
    @Column(name = "last_laufende_nummer", nullable = false)
    private Long lastLaufendeNummer;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
