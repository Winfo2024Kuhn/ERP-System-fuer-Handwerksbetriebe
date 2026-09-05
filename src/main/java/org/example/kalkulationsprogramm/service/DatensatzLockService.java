package org.example.kalkulationsprogramm.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.DatensatzLock;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.example.kalkulationsprogramm.dto.DatensatzLockDto;
import org.example.kalkulationsprogramm.repository.DatensatzLockRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Verwaltet Soft-Locks fuer sperrbare Datensaetze (siehe {@link SperrbarerTyp}),
 * nicht mehr nur fuer Geschaeftsdokumente wie zuvor {@code DokumentLockService}.
 *
 * Garantiert: Pro (entitaetTyp, entitaetId)-Paar haelt zu jedem Zeitpunkt
 * hoechstens ein User das Lock. Ein verwaistes Lock (Browser-Crash etc.)
 * darf nach STALE_AFTER von einem anderen User uebernommen werden.
 */
@Service
@RequiredArgsConstructor
public class DatensatzLockService {

    /** Lock gilt als verwaist, wenn so lange kein Heartbeat mehr kam. */
    static final Duration STALE_AFTER = Duration.ofSeconds(90);

    private final DatensatzLockRepository repository;

    /**
     * Versucht, das Lock fuer den User zu erwerben.
     * Erfolg, wenn entweder kein Lock existiert, der User selbst das Lock haelt
     * oder das bestehende Lock verwaist ist.
     */
    @Transactional
    public DatensatzLockDto acquire(SperrbarerTyp entitaetTyp, Long entitaetId, Long userId, String userDisplayName) {
        validateTyp(entitaetTyp);
        LocalDateTime now = LocalDateTime.now();

        Optional<DatensatzLock> existing = repository.findByEntitaetTypAndEntitaetId(entitaetTyp, entitaetId);
        if (existing.isPresent()) {
            DatensatzLock lock = existing.get();
            boolean sameUser = lock.getUserId().equals(userId);
            boolean stale = isStale(lock, now);
            if (sameUser || stale) {
                lock.setUserId(userId);
                lock.setUserDisplayName(safeDisplayName(userDisplayName));
                if (!sameUser) {
                    lock.setAcquiredAt(now);
                }
                lock.setLastHeartbeatAt(now);
                DatensatzLock saved = repository.save(lock);
                return acquired(saved);
            }
            return lockedByOther(lock);
        }

        DatensatzLock fresh = new DatensatzLock();
        fresh.setEntitaetTyp(entitaetTyp);
        fresh.setEntitaetId(entitaetId);
        fresh.setUserId(userId);
        fresh.setUserDisplayName(safeDisplayName(userDisplayName));
        fresh.setAcquiredAt(now);
        fresh.setLastHeartbeatAt(now);
        try {
            DatensatzLock saved = repository.saveAndFlush(fresh);
            return acquired(saved);
        } catch (DataIntegrityViolationException race) {
            // Konkurrierender Insert hat zwischen findBy und save den Lock geschrieben.
            // Den jetzt sichtbaren Eintrag wieder pruefen.
            DatensatzLock winner = repository.findByEntitaetTypAndEntitaetId(entitaetTyp, entitaetId)
                    .orElseThrow(() -> race);
            if (winner.getUserId().equals(userId)) {
                return acquired(winner);
            }
            return lockedByOther(winner);
        }
    }

    /**
     * Verlaengert das Lock des Users. Liefert ACQUIRED nur, wenn der Caller
     * tatsaechlich noch Owner ist; sonst LOCKED_BY_OTHER, damit der Frontend
     * den Editor schliessen kann.
     */
    @Transactional
    public DatensatzLockDto heartbeat(SperrbarerTyp entitaetTyp, Long entitaetId, Long userId, String userDisplayName) {
        validateTyp(entitaetTyp);
        Optional<DatensatzLock> existing = repository.findByEntitaetTypAndEntitaetId(entitaetTyp, entitaetId);
        if (existing.isEmpty()) {
            // Lock ist weg (Cleanup oder anderer Tab) — neu erwerben statt 404.
            return acquire(entitaetTyp, entitaetId, userId, userDisplayName);
        }
        DatensatzLock lock = existing.get();
        if (!lock.getUserId().equals(userId)) {
            if (isStale(lock, LocalDateTime.now())) {
                return acquire(entitaetTyp, entitaetId, userId, userDisplayName);
            }
            return lockedByOther(lock);
        }
        lock.setLastHeartbeatAt(LocalDateTime.now());
        if (userDisplayName != null && !userDisplayName.isBlank()) {
            lock.setUserDisplayName(userDisplayName);
        }
        return acquired(repository.save(lock));
    }

    /**
     * Gibt das Lock frei. No-op, wenn der Eintrag bereits weg ist oder einem
     * anderen User gehoert (z.B. weil ein verwaistes Lock zwischenzeitlich
     * uebernommen wurde).
     */
    @Transactional
    public void release(SperrbarerTyp entitaetTyp, Long entitaetId, Long userId) {
        validateTyp(entitaetTyp);
        repository.findByEntitaetTypAndEntitaetId(entitaetTyp, entitaetId)
                .filter(lock -> lock.getUserId().equals(userId))
                .ifPresent(repository::delete);
    }

    /**
     * Prueft, ob der User aktuell der Lock-Halter ist. Wird vor dem Speichern
     * verwendet, damit niemand am Lock vorbei schreibt.
     */
    @Transactional(readOnly = true)
    public boolean isHeldBy(SperrbarerTyp entitaetTyp, Long entitaetId, Long userId) {
        validateTyp(entitaetTyp);
        return repository.findByEntitaetTypAndEntitaetId(entitaetTyp, entitaetId)
                .map(lock -> lock.getUserId().equals(userId) && !isStale(lock, LocalDateTime.now()))
                .orElse(false);
    }

    private boolean isStale(DatensatzLock lock, LocalDateTime now) {
        return Duration.between(lock.getLastHeartbeatAt(), now).compareTo(STALE_AFTER) > 0;
    }

    private void validateTyp(SperrbarerTyp entitaetTyp) {
        if (entitaetTyp == null) {
            throw new IllegalArgumentException("Kein Datensatztyp angegeben.");
        }
    }

    private String safeDisplayName(String userDisplayName) {
        if (userDisplayName == null || userDisplayName.isBlank()) {
            return "Unbekannter Benutzer";
        }
        return userDisplayName.length() > 255 ? userDisplayName.substring(0, 255) : userDisplayName;
    }

    private DatensatzLockDto acquired(DatensatzLock lock) {
        return new DatensatzLockDto(
                DatensatzLockDto.ACQUIRED,
                lock.getUserId(),
                lock.getUserDisplayName(),
                lock.getAcquiredAt(),
                lock.getLastHeartbeatAt()
        );
    }

    private DatensatzLockDto lockedByOther(DatensatzLock lock) {
        return new DatensatzLockDto(
                DatensatzLockDto.LOCKED_BY_OTHER,
                lock.getUserId(),
                lock.getUserDisplayName(),
                lock.getAcquiredAt(),
                lock.getLastHeartbeatAt()
        );
    }
}
