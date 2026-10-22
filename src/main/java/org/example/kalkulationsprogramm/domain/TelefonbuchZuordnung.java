package org.example.kalkulationsprogramm.domain;

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
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Merkt sich je Kontakt, welcher Eintrag im FRITZ!Box-Telefonbuch zu ihm gehört
 * ({@link #eintragId}) und was zuletzt gesendet wurde ({@link #inhaltHash}).
 * Bewusst ohne Fremdschlüssel auf den Kontakt: Wird er gelöscht, muss die Zeile
 * bleiben, bis sein Eintrag auch in der Box gelöscht ist.
 */
@Getter
@Setter
@Entity
@Table(name = "telefonbuch_zuordnung",
        uniqueConstraints = @UniqueConstraint(name = "telefonbuch_zuordnung__quelle", columnNames = {"quelle_typ", "quelle_id"}))
public class TelefonbuchZuordnung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "quelle_typ", nullable = false, length = 40)
    private TelefonbuchQuelle quelleTyp;

    @Column(name = "quelle_id", nullable = false)
    private Long quelleId;

    @Column(name = "eintrag_id", nullable = false, length = 20)
    private String eintragId;

    @Column(name = "inhalt_hash", nullable = false, length = 64)
    private String inhaltHash;

    @Column(name = "gesendet_am", nullable = false)
    private LocalDateTime gesendetAm;
}
