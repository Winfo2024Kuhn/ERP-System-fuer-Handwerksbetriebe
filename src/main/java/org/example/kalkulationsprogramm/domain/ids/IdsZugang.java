package org.example.kalkulationsprogramm.domain.ids;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Lieferanten;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Shop-Zugang eines Lieferanten für IDS-Connect. Höchstens einer je Lieferant.
 * Das Passwort liegt nur verschlüsselt vor und verlässt den Server ausschließlich im
 * Anmeldeformular, das der Browser an den Shop schickt.
 */
@Getter
@Setter
@Entity
@Table(name = "ids_zugang")
public class IdsZugang {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieferant_id", nullable = false, unique = true)
    private Lieferanten lieferant;

    @Column(name = "aktiv", nullable = false)
    private boolean aktiv;

    @Enumerated(EnumType.STRING)
    @Column(name = "protokoll", nullable = false, length = 40)
    private IdsProtokoll protokoll = IdsProtokoll.IDS_CONNECT_2_5;

    @Column(name = "shop_url", length = 500)
    private String shopUrl;

    @Column(name = "kundennummer", length = 50)
    private String kundennummer;

    @Column(name = "benutzername", length = 50)
    private String benutzername;

    /** Mit {@code MailSecretService} verschlüsselt; nie anzeigen. */
    @Column(name = "passwort_verschluesselt", length = 500)
    private String passwortVerschluesselt;

    @Column(name = "geaendert_am")
    private LocalDateTime geaendertAm;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Bereit für einen Shop-Besuch: eingeschaltet und mit Shop-Adresse. */
    public boolean istBereit() {
        return aktiv && shopUrl != null && !shopUrl.isBlank();
    }
}
