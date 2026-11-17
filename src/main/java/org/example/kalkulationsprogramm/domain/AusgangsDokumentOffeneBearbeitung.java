package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Wer ein Ausgangsdokument seit dem letzten {@link AusgangsDokumentStand} bearbeitet hat – je
 * Dokument und Benutzer eine Zeile. Beim nächsten Stand wandern die Benutzer nach
 * {@code bearbeitetVonIds}, die Zeilen werden gelöscht.
 *
 * <p>Hat der Benutzer einen älteren Stand zurückgeholt, steht dessen Nummer in
 * {@code wiederhergestelltAusNummer}; der nächste Stand übernimmt die zuletzt zurückgeholte.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ausgangs_dokument_offene_bearbeitung", uniqueConstraints = @UniqueConstraint(
        name = "uk_ausgangs_dokument_offene_bearbeitung", columnNames = { "dokument_id", "benutzer_id" }))
public class AusgangsDokumentOffeneBearbeitung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dokument_id", nullable = false)
    private Long dokumentId;

    @Column(name = "benutzer_id", nullable = false)
    private Long benutzerId;

    @Column(nullable = false)
    private LocalDateTime seit;

    @Column(name = "wiederhergestellt_aus_nummer")
    private Integer wiederhergestelltAusNummer;

    @Column(name = "wiederhergestellt_am")
    private LocalDateTime wiederhergestelltAm;
}
