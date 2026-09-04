package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.DatensatzLock;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifiziert das Persistenz-Verhalten von {@link DatensatzLockRepository}
 * gegen H2 -- insbesondere den Unique-Constraint auf
 * (entitaetTyp, entitaetId), der verhindert, dass zwei Lock-Eintraege
 * gleichzeitig fuer denselben Datensatz existieren.
 *
 * <p>Flyway ist im Testprofil deaktiviert (siehe
 * src/test/resources/application.properties), die Migration V363 laeuft
 * hier also nicht mit. Das Schema entsteht stattdessen ueber
 * ddl-auto=create-drop direkt aus den Entity-Annotationen -- das ist der
 * uebliche und hier erwartete Weg fuer Repository-Tests.
 */
@DataJpaTest
class DatensatzLockRepositoryTest {

    @Autowired
    private DatensatzLockRepository repository;

    @Test
    @DisplayName("findByEntitaetTypAndEntitaetId findet einen gespeicherten Eintrag")
    void findByEntitaetTypAndEntitaetId_findetGespeichertenEintrag() {
        repository.saveAndFlush(neuerLock(SperrbarerTyp.AUSGANG, 1L, "Max Mustermann"));

        Optional<DatensatzLock> gefunden =
                repository.findByEntitaetTypAndEntitaetId(SperrbarerTyp.AUSGANG, 1L);

        assertThat(gefunden).isPresent();
        assertThat(gefunden.get().getUserDisplayName()).isEqualTo("Max Mustermann");
        assertThat(gefunden.get().getEntitaetTyp()).isEqualTo(SperrbarerTyp.AUSGANG);
        assertThat(gefunden.get().getEntitaetId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findByEntitaetTypAndEntitaetId liefert leer, wenn kein Eintrag existiert")
    void findByEntitaetTypAndEntitaetId_leerWennNichtVorhanden() {
        assertThat(repository.findByEntitaetTypAndEntitaetId(SperrbarerTyp.AUSGANG, 999L)).isEmpty();
    }

    @Test
    @DisplayName("Unique-Constraint verhindert zweiten Lock auf denselben (Typ, Id)")
    void uniqueConstraint_verhindertZweitenLockAufSelbesTarget() {
        repository.saveAndFlush(neuerLock(SperrbarerTyp.AUSGANG, 1L, "Max Mustermann"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(neuerLock(SperrbarerTyp.AUSGANG, 1L, "Erika Mustermann")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AUSGANG/1 und EINGANG/1 koexistieren -- IDs ueberlappen zwischen den Typen")
    void unterschiedlicheTypenMitGleicherId_koexistieren() {
        repository.saveAndFlush(neuerLock(SperrbarerTyp.AUSGANG, 1L, "Max Mustermann"));
        repository.saveAndFlush(neuerLock(SperrbarerTyp.EINGANG, 1L, "Erika Mustermann"));

        assertThat(repository.findByEntitaetTypAndEntitaetId(SperrbarerTyp.AUSGANG, 1L)).isPresent();
        assertThat(repository.findByEntitaetTypAndEntitaetId(SperrbarerTyp.EINGANG, 1L)).isPresent();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("deleteByEntitaetTypAndEntitaetId entfernt genau den passenden Eintrag")
    void deleteByEntitaetTypAndEntitaetId_entferntNurDenPassendenEintrag() {
        repository.saveAndFlush(neuerLock(SperrbarerTyp.AUSGANG, 1L, "Max Mustermann"));
        repository.saveAndFlush(neuerLock(SperrbarerTyp.EINGANG, 1L, "Erika Mustermann"));

        repository.deleteByEntitaetTypAndEntitaetId(SperrbarerTyp.AUSGANG, 1L);
        repository.flush();

        assertThat(repository.findByEntitaetTypAndEntitaetId(SperrbarerTyp.AUSGANG, 1L)).isEmpty();
        assertThat(repository.findByEntitaetTypAndEntitaetId(SperrbarerTyp.EINGANG, 1L)).isPresent();
    }

    private DatensatzLock neuerLock(SperrbarerTyp typ, Long entitaetId, String anzeigename) {
        DatensatzLock lock = new DatensatzLock();
        lock.setEntitaetTyp(typ);
        lock.setEntitaetId(entitaetId);
        lock.setUserId(1L);
        lock.setUserDisplayName(anzeigename);
        LocalDateTime jetzt = LocalDateTime.now();
        lock.setAcquiredAt(jetzt);
        lock.setLastHeartbeatAt(jetzt);
        return lock;
    }
}
