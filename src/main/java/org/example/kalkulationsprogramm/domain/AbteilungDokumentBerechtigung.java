package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Berechtigung einer Abteilung für einen Lieferanten-Dokumenttyp.
 * Definiert:
 * - darfSehen: Ob Mitarbeiter dieser Abteilung den Dokumenttyp sehen dürfen
 * - darfScannen: Ob Mitarbeiter dieser Abteilung den Dokumenttyp hochladen dürfen
 */
@Getter
@Setter
@Entity
@Table(name = "abteilung_dokument_berechtigung",
    uniqueConstraints = @UniqueConstraint(columnNames = {"abteilung_id", "dokument_typ"}))
public class AbteilungDokumentBerechtigung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "abteilung_id", nullable = false)
    private Abteilung abteilung;

    @Convert(converter = org.example.kalkulationsprogramm.domain.converter.LieferantDokumentTypConverter.class)
    @Column(name = "dokument_typ", nullable = false, length = 50)
    private LieferantDokumentTyp dokumentTyp;

    @Column(nullable = false)
    private Boolean darfSehen = false;

    @Column(nullable = false)
    private Boolean darfScannen = false;
}
