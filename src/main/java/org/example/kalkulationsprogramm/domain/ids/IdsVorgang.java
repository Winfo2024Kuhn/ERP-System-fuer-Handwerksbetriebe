package org.example.kalkulationsprogramm.domain.ids;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Lieferanten;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ein Shop-Besuch: gestartet von einem PC-Benutzer, beendet, wenn der Shop den Warenkorb
 * zurückschickt. Fachneutral – wer den Warenkorb weiterverarbeitet (Dokumenteditor, später
 * Beschaffung), entscheidet der Aufrufer.
 */
@Getter
@Setter
@Entity
@Table(name = "ids_vorgang")
public class IdsVorgang {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieferant_id", nullable = false)
    private Lieferanten lieferant;

    /** {@code FrontendUserProfile}-ID des Benutzers, der den Shop geöffnet hat. Nur er sieht den Warenkorb. */
    @Column(name = "frontend_user_id", nullable = false)
    private Long frontendUserId;

    /** Protokoll beim Start; der Rückweg wird damit gelesen, auch wenn der Zugang inzwischen geändert wurde. */
    @Enumerated(EnumType.STRING)
    @Column(name = "protokoll", nullable = false, length = 40)
    private IdsProtokoll protokoll = IdsProtokoll.IDS_CONNECT_2_5;

    /** SHA-256 (hex) des Rücksprung-Schlüssels; der Schlüssel selbst steht nur in der Rücksprung-Adresse. */
    @Column(name = "schluessel_hash", nullable = false, unique = true, length = 64)
    private String schluesselHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdsVorgangStatus status = IdsVorgangStatus.WARTET;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    @Column(name = "gueltig_bis", nullable = false)
    private LocalDateTime gueltigBis;

    @Column(name = "empfangen_am")
    private LocalDateTime empfangenAm;

    /** Der Shop meldet, dass der Warenkorb dort schon bestellt wurde. */
    @Column(name = "mit_bestellung", nullable = false)
    private boolean mitBestellung;

    @Column(name = "fehlertext", length = 500)
    private String fehlertext;

    /** Warenkorb, wie der Shop ihn geschickt hat – nur zur Fehlersuche, nie angezeigt. */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "rohdaten")
    private String rohdaten;

    @OneToMany(mappedBy = "vorgang", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("positionIndex ASC")
    private List<IdsWarenkorbPosition> positionen = new ArrayList<>();

    public void addPosition(IdsWarenkorbPosition position) {
        position.setVorgang(this);
        positionen.add(position);
    }
}
