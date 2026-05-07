package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * IDS-Connect-Konfiguration eines Lieferanten.
 *
 * <p>Pro Lieferant existiert maximal eine Konfig (1:1). Das Passwort
 * wird AES-GCM-verschlüsselt persistiert; der Klartext-Wert verlässt
 * niemals den Server.</p>
 *
 * <p>Standard-Protokoll: {@link IdsProtokoll#IDS_CONNECT_2_5}, der
 * vom ZVSHK definierte Standard, den die meisten deutschen Groß-
 * händler unterstützen (Würth, Berner, Reyher, Sonepar, …).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "lieferant_ids_konfig")
public class LieferantIdsKonfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieferant_id", nullable = false, unique = true)
    private Lieferanten lieferant;

    @Column(name = "aktiviert", nullable = false)
    private boolean aktiviert = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "protokoll", nullable = false, length = 40)
    private IdsProtokoll protokoll = IdsProtokoll.IDS_CONNECT_2_5;

    @Column(name = "punchout_url", length = 500)
    private String punchoutUrl;

    @Column(name = "kundennummer", length = 100)
    private String kundennummer;

    @Column(name = "login_name", length = 100)
    private String loginName;

    /** AES-GCM-verschlüsselter Passwort-Wert. Niemals direkt anzeigen. */
    @Column(name = "passwort_verschluesselt", length = 500)
    private String passwortVerschluesselt;

    @Column(name = "notizen", columnDefinition = "TEXT")
    private String notizen;

    @Column(name = "geaendert_am")
    private LocalDateTime geaendertAm;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geaendert_von_id")
    private Mitarbeiter geaendertVon;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    @PrePersist
    protected void onCreate() {
        if (erstelltAm == null) {
            erstelltAm = LocalDateTime.now();
        }
    }
}
