package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine Handy-Buchung, die bei einer {@link ZeitbuchungUeberschneidung} Vorrang hatte – so, wie
 * das Handy sie damals geschickt hat. Die Meldung zeigt damit "Arbeit 11:27–12:00, Pause
 * 12:00–13:34" statt einer zusammengezogenen Spanne, auch wenn das Büro die Handy-Buchung
 * später ändert oder löscht.
 *
 * <p>Eine Zeile je Meldung und Handy-Buchung; kommt dieselbe Handy-Buchung wieder (Ende steht
 * fest), wird die Zeile aktualisiert. {@link #handyBuchungId} hat wie bei der Meldung keinen
 * Fremdschlüssel, die Zeile verschwindet nur mit ihrer Meldung.
 */
@Getter
@Setter
@Entity
@Table(name = "zeitbuchung_ueberschneidung_handy_zeit")
public class ZeitbuchungUeberschneidungHandyZeit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ueberschneidung_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ZeitbuchungUeberschneidung ueberschneidung;

    @Column(name = "handy_buchung_id", nullable = false)
    private Long handyBuchungId;

    /** Arbeit oder Pause (in der Meldung "Arbeit 11:27–12:00" bzw. "Pause …"). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BuchungsTyp art;

    @Column(nullable = false)
    private LocalDateTime von;

    /** {@code null}, solange die Handy-Buchung noch läuft ("Arbeit ab 07:00"). */
    private LocalDateTime bis;
}
