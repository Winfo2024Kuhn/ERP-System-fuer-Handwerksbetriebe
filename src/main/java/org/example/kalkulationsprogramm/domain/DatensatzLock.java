package org.example.kalkulationsprogramm.domain;

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
import lombok.Setter;

/**
 * Soft-Lock fuer sperrbare Datensaetze (siehe {@link SperrbarerTyp}), nicht
 * mehr nur fuer Geschaeftsdokumente wie zuvor {@code DokumentLock}. Genau ein
 * User darf ein (entitaetTyp, entitaetId)-Paar gleichzeitig geoeffnet halten.
 * Der Eintrag wird per Heartbeat am Leben gehalten und nach 90s
 * Heartbeat-Stille als verwaist behandelt (siehe {@code DatensatzLockService}).
 */
@Getter
@Setter
@Entity
@Table(
    name = "datensatz_lock",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_datensatz_lock_target",
        columnNames = {"entitaet_typ", "entitaet_id"}
    )
)
public class DatensatzLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entitaet_typ", nullable = false, length = 32)
    private SperrbarerTyp entitaetTyp;

    @Column(name = "entitaet_id", nullable = false)
    private Long entitaetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_display_name", nullable = false, length = 255)
    private String userDisplayName;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private LocalDateTime lastHeartbeatAt;
}
