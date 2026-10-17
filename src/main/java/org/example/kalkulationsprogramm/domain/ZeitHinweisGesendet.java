package org.example.kalkulationsprogramm.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Merkt sich, welche Zeiterfassungs-Erinnerung ein Mitarbeiter an welchem Tag schon
 * aufs Handy bekommen hat. Verhindert, dass der Prüf-Job alle paar Minuten dieselbe
 * Pausen-Erinnerung schickt – auch über einen Neustart des Servers hinweg.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "zeit_hinweis_gesendet", uniqueConstraints = @UniqueConstraint(
        name = "uk_zeit_hinweis_gesendet", columnNames = {"mitarbeiter_id", "tag", "art"}))
public class ZeitHinweisGesendet {

    /** Welche Erinnerung verschickt wurde. */
    public enum Art {
        /** Kurz vor 6 Stunden Arbeit ohne 30 Minuten Pause. */
        PAUSE_6_STUNDEN,
        /** Kurz vor 9 Stunden Arbeit ohne 45 Minuten Pause. */
        PAUSE_9_STUNDEN,
        /** Kurz vor 6 Stunden Arbeit am Stück, obwohl die Pausen insgesamt reichen. */
        PAUSE_AM_STUECK,
        /** Der Auto-Stop hat eine Buchung beendet, weil kein Feierabend gestempelt wurde. */
        AUTO_BEENDET
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bewusst nur die ID: Der Eintrag ist reine Buchführung, kein Teil des Mitarbeiters. */
    @Column(name = "mitarbeiter_id", nullable = false)
    private Long mitarbeiterId;

    @Column(nullable = false)
    private LocalDate tag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Art art;

    @Column(nullable = false)
    private LocalDateTime gesendetAm;

    public ZeitHinweisGesendet(Long mitarbeiterId, LocalDate tag, Art art, LocalDateTime gesendetAm) {
        this.mitarbeiterId = mitarbeiterId;
        this.tag = tag;
        this.art = art;
        this.gesendetAm = gesendetAm;
    }
}
