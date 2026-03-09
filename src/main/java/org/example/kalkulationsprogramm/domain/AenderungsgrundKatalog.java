package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Katalog vordefinierter Änderungsgründe für Zeitbuchungs-Korrekturen.
 * Wird im Frontend als Dropdown angezeigt.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "aenderungsgrund_katalog")
public class AenderungsgrundKatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Technischer Code (z.B. VERGESSEN_AUSSTEMPELN) */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Anzeigename für Frontend (z.B. "Mitarbeiter hat Ausstempeln vergessen") */
    @Column(nullable = false)
    private String bezeichnung;

    /** Ob zusätzlicher Freitext erforderlich ist */
    @Column(name = "erfordert_freitext")
    private Boolean erfordertFreitext = false;
}
