package org.example.kalkulationsprogramm.domain;

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
import lombok.Getter;
import lombok.Setter;

/**
 * Digitale Freigabe einer {@link Wps} durch eine Schweißaufsichtsperson (SAP) –
 * ersetzt die handschriftliche „Unterschrift SAP" nach EN ISO 14731.
 *
 * <p>Der Name des Mitarbeiters wird als Snapshot mitgespeichert – damit bleibt
 * die Freigabe auch nach einem späteren Löschen oder Umbenennen des
 * Mitarbeiters lesbar und audit-tauglich.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wps_freigabe")
public class WpsFreigabe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wps_id", nullable = false)
    private Wps wps;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id")
    private Mitarbeiter mitarbeiter;

    @Column(name = "mitarbeiter_name", nullable = false, length = 255)
    private String mitarbeiterName;

    @Column(name = "zeitpunkt", nullable = false)
    private LocalDateTime zeitpunkt = LocalDateTime.now();
}
