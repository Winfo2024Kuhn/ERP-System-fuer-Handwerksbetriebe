package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.SperrbarerTyp;

public interface DatensatzLockRepositoryErweiterung {

    /**
     * Legt das Lock an, wenn fuer den Datensatz noch keins existiert - ohne
     * Fehler, falls ein anderer User es gleichzeitig anlegt. Eine Dubletten-
     * Exception wuerde die laufende Transaktion unbrauchbar machen.
     *
     * @return true, wenn dieser Aufruf das Lock angelegt hat
     */
    boolean legeAnFallsFrei(SperrbarerTyp entitaetTyp, Long entitaetId, Long userId, String userDisplayName,
            LocalDateTime zeitpunkt);
}
