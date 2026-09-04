package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.DatensatzLock;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.example.kalkulationsprogramm.dto.DatensatzLockDto;
import org.example.kalkulationsprogramm.repository.DatensatzLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Portiert von DokumentLockServiceTest: gleiche Logik, generalisiert auf
 * SperrbarerTyp statt String-Typen. Siehe DatensatzLockService.
 */
class DatensatzLockServiceTest {

    private static final SperrbarerTyp TYP = SperrbarerTyp.AUSGANG;
    private static final long ENTITAET_ID = 42L;
    private static final long USER_A = 1L;
    private static final long USER_B = 2L;

    private DatensatzLockRepository repository;
    private DatensatzLockService service;

    @BeforeEach
    void setUp() {
        repository = mock(DatensatzLockRepository.class);
        service = new DatensatzLockService(repository);
    }

    @ParameterizedTest
    @EnumSource(SperrbarerTyp.class)
    void acquire_freshLock_returnsAcquiredAndPersistsEntry_fuerJedenTyp(SperrbarerTyp typ) {
        when(repository.findByEntitaetTypAndEntitaetId(typ, ENTITAET_ID)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(DatensatzLock.class))).thenAnswer(inv -> inv.getArgument(0));

        DatensatzLockDto result = service.acquire(typ, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.ACQUIRED);
        assertThat(result.holderUserId()).isEqualTo(USER_A);
        assertThat(result.holderDisplayName()).isEqualTo("Max Mustermann");
        verify(repository).saveAndFlush(any(DatensatzLock.class));
    }

    @Test
    void acquire_existingLockSameUser_renewsAndReturnsAcquired() {
        DatensatzLock existing = lockHeldBy(USER_A, "Max Mustermann", LocalDateTime.now().minusSeconds(20));
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(DatensatzLock.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime originalAcquiredAt = existing.getAcquiredAt();
        DatensatzLockDto result = service.acquire(TYP, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.ACQUIRED);
        // Bei demselben User (zwei Tabs) darf der urspruengliche acquiredAt-
        // Zeitpunkt nicht ueberschrieben werden — nur lastHeartbeatAt aktualisiert sich.
        assertThat(existing.getAcquiredAt()).isEqualTo(originalAcquiredAt);
    }

    @Test
    void acquire_lockHeldByOtherButStale_takesOver() {
        LocalDateTime longAgo = LocalDateTime.now().minus(DatensatzLockService.STALE_AFTER).minusSeconds(10);
        DatensatzLock stale = lockHeldBy(USER_B, "Erika Mustermann", longAgo);
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(stale));
        when(repository.save(any(DatensatzLock.class))).thenAnswer(inv -> inv.getArgument(0));

        DatensatzLockDto result = service.acquire(TYP, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.ACQUIRED);
        assertThat(result.holderUserId()).isEqualTo(USER_A);
        assertThat(stale.getUserId()).isEqualTo(USER_A);
        // Bei Uebernahme wird auch acquiredAt frisch gesetzt.
        assertThat(stale.getAcquiredAt()).isAfter(longAgo);
    }

    @Test
    void acquire_lockHeldByOtherFresh_returnsLockedByOther() {
        DatensatzLock fresh = lockHeldBy(USER_B, "Erika Mustermann", LocalDateTime.now().minusSeconds(5));
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(fresh));

        DatensatzLockDto result = service.acquire(TYP, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.LOCKED_BY_OTHER);
        assertThat(result.holderUserId()).isEqualTo(USER_B);
        assertThat(result.holderDisplayName()).isEqualTo("Erika Mustermann");
        verify(repository, never()).save(any(DatensatzLock.class));
        verify(repository, never()).saveAndFlush(any(DatensatzLock.class));
    }

    @Test
    void acquire_concurrentInsertRace_returnsLockedByOtherForLoser() {
        // Erster findBy liefert nichts (Race-Setup), saveAndFlush schlaegt am
        // Unique-Constraint fehl, danach liefert findBy den Gewinner zurueck.
        DatensatzLock winner = lockHeldBy(USER_B, "Erika Mustermann", LocalDateTime.now());
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.saveAndFlush(any(DatensatzLock.class)))
                .thenThrow(new DataIntegrityViolationException("uk_datensatz_lock_target"));

        DatensatzLockDto result = service.acquire(TYP, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.LOCKED_BY_OTHER);
        assertThat(result.holderUserId()).isEqualTo(USER_B);
    }

    @Test
    void acquire_concurrentInsertRace_sameUserWins_returnsAcquired() {
        // Zwei Tabs desselben Nutzers oeffnen gleichzeitig -> der zweite Insert
        // verliert das Rennen, der nach dem Race sichtbare Eintrag gehoert aber
        // demselben User. Das muss weiterhin ACQUIRED liefern (sameUser-Zweig
        // gilt auch im Race-Fall), nicht LOCKED_BY_OTHER.
        DatensatzLock winner = lockHeldBy(USER_A, "Max Mustermann", LocalDateTime.now());
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.saveAndFlush(any(DatensatzLock.class)))
                .thenThrow(new DataIntegrityViolationException("uk_datensatz_lock_target"));

        DatensatzLockDto result = service.acquire(TYP, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.ACQUIRED);
        assertThat(result.holderUserId()).isEqualTo(USER_A);
    }

    @Test
    void heartbeat_byOwner_extendsAndStaysAcquired() {
        DatensatzLock existing = lockHeldBy(USER_A, "Max Mustermann", LocalDateTime.now().minusSeconds(25));
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(DatensatzLock.class))).thenAnswer(inv -> inv.getArgument(0));

        DatensatzLockDto result = service.heartbeat(TYP, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.ACQUIRED);
        verify(repository).save(any(DatensatzLock.class));
    }

    @Test
    void heartbeat_byNonOwnerWithFreshLock_returnsLockedByOther() {
        DatensatzLock fresh = lockHeldBy(USER_B, "Erika Mustermann", LocalDateTime.now().minusSeconds(5));
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(fresh));

        DatensatzLockDto result = service.heartbeat(TYP, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.LOCKED_BY_OTHER);
        assertThat(result.holderUserId()).isEqualTo(USER_B);
    }

    @Test
    void heartbeat_byNonOwnerOfStaleLock_takesOver() {
        LocalDateTime longAgo = LocalDateTime.now().minus(DatensatzLockService.STALE_AFTER).minusSeconds(30);
        DatensatzLock stale = lockHeldBy(USER_B, "Erika Mustermann", longAgo);
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(stale));
        when(repository.save(any(DatensatzLock.class))).thenAnswer(inv -> inv.getArgument(0));

        DatensatzLockDto result = service.heartbeat(TYP, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.ACQUIRED);
        assertThat(result.holderUserId()).isEqualTo(USER_A);
    }

    @Test
    void heartbeat_lockMissing_acquiresFresh() {
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(DatensatzLock.class))).thenAnswer(inv -> inv.getArgument(0));

        DatensatzLockDto result = service.heartbeat(TYP, ENTITAET_ID, USER_A, "Max Mustermann");

        assertThat(result.status()).isEqualTo(DatensatzLockDto.ACQUIRED);
    }

    @Test
    void release_byOwner_deletesLock() {
        DatensatzLock existing = lockHeldBy(USER_A, "Max Mustermann", LocalDateTime.now());
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(existing));

        service.release(TYP, ENTITAET_ID, USER_A);

        verify(repository, times(1)).delete(existing);
    }

    @Test
    void release_byNonOwner_keepsLock() {
        DatensatzLock existing = lockHeldBy(USER_B, "Erika Mustermann", LocalDateTime.now());
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(existing));

        service.release(TYP, ENTITAET_ID, USER_A);

        verify(repository, never()).delete(any(DatensatzLock.class));
    }

    @Test
    void isHeldBy_returnsTrueOnlyForOwnerWithFreshHeartbeat() {
        DatensatzLock fresh = lockHeldBy(USER_A, "Max Mustermann", LocalDateTime.now().minusSeconds(5));
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(fresh));

        assertThat(service.isHeldBy(TYP, ENTITAET_ID, USER_A)).isTrue();
        assertThat(service.isHeldBy(TYP, ENTITAET_ID, USER_B)).isFalse();
    }

    @Test
    void isHeldBy_staleLock_returnsFalseEvenForOwner() {
        LocalDateTime longAgo = LocalDateTime.now().minus(DatensatzLockService.STALE_AFTER).minusSeconds(10);
        DatensatzLock stale = lockHeldBy(USER_A, "Max Mustermann", longAgo);
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.of(stale));

        assertThat(service.isHeldBy(TYP, ENTITAET_ID, USER_A)).isFalse();
    }

    @Test
    void isHeldBy_noLockEntry_returnsFalse() {
        when(repository.findByEntitaetTypAndEntitaetId(TYP, ENTITAET_ID)).thenReturn(Optional.empty());

        assertThat(service.isHeldBy(TYP, ENTITAET_ID, USER_A)).isFalse();
    }

    @Test
    void acquire_nullTyp_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.acquire(null, ENTITAET_ID, USER_A, "Max Mustermann"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void heartbeat_nullTyp_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.heartbeat(null, ENTITAET_ID, USER_A, "Max Mustermann"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void release_nullTyp_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.release(null, ENTITAET_ID, USER_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isHeldBy_nullTyp_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.isHeldBy(null, ENTITAET_ID, USER_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DatensatzLock lockHeldBy(long userId, String displayName, LocalDateTime lastHeartbeat) {
        DatensatzLock lock = new DatensatzLock();
        lock.setId(7L);
        lock.setEntitaetTyp(TYP);
        lock.setEntitaetId(ENTITAET_ID);
        lock.setUserId(userId);
        lock.setUserDisplayName(displayName);
        lock.setAcquiredAt(lastHeartbeat);
        lock.setLastHeartbeatAt(lastHeartbeat);
        return lock;
    }
}
