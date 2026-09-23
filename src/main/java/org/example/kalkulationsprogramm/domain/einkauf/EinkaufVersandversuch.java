package org.example.kalkulationsprogramm.domain.einkauf;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "einkauf_versandversuch", indexes = @Index(name = "ix_einkauf_versuch_auftrag", columnList = "auftrag_id, id"))
@Getter
@NoArgsConstructor
public class EinkaufVersandversuch {
    public enum Ergebnis { GESTARTET, ANGENOMMEN, SICHER_FEHLGESCHLAGEN, UNKLAR, ARCHIVIERT, ARCHIV_FEHLER }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auftrag_id", nullable = false)
    private EinkaufVersandauftrag auftrag;
    @Column(name = "nummer", nullable = false)
    private int nummer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "ENUM('GESTARTET','ANGENOMMEN','SICHER_FEHLGESCHLAGEN','UNKLAR','ARCHIVIERT','ARCHIV_FEHLER')")
    private Ergebnis ergebnis;
    @Column(name = "fehler_code", length = 80)
    private String fehlerCode;
    @Column(name = "gestartet_am", nullable = false)
    private Instant gestartetAm;
    @Column(name = "beendet_am")
    private Instant beendetAm;
    @Column(name = "akteur_id")
    private Long akteurId;

    public EinkaufVersandversuch(EinkaufVersandauftrag auftrag, int nummer, Long akteurId, Instant zeit) {
        this.auftrag = auftrag; this.nummer = nummer; this.gestartetAm = zeit;
        this.akteurId = akteurId;
        this.ergebnis = Ergebnis.GESTARTET;
    }

    public void beendet(Ergebnis wert, String code, Instant zeit) {
        this.ergebnis = wert; this.fehlerCode = code; this.beendetAm = zeit;
    }
}
