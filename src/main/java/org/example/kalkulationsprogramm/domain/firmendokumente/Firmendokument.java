package org.example.kalkulationsprogramm.domain.firmendokumente;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Ein abgelegtes Firmendokument (beliebiges Format außer Programmdateien). Die Datei liegt unter
 * {@code uploads/firmendokumente/} mit einem vom ERP erzeugten Namen ({@link #gespeicherterDateiname},
 * {@code <UUID>.bin}, ältere PDFs {@code <UUID>.pdf}); der Originalname dient der Anzeige und bestimmt die Dateiart.
 */
@Getter
@Setter
@Entity
@Table(name = "firmendokument")
public class Firmendokument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordner_id", nullable = false)
    private FirmendokumentOrdner ordner;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "original_dateiname", nullable = false, length = 255)
    private String originalDateiname;

    @Column(name = "gespeicherter_dateiname", nullable = false, unique = true, length = 60)
    private String gespeicherterDateiname;

    @Column(name = "groesse_bytes", nullable = false)
    private long groesseBytes;

    @Column(name = "hochgeladen_am", nullable = false)
    private LocalDateTime hochgeladenAm;

    @Column(name = "hochgeladen_von", length = 255)
    private String hochgeladenVon;

    /** Optional, z. B. nächste Torprüfung. */
    @Column(name = "faellig_am")
    private LocalDate faelligAm;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
