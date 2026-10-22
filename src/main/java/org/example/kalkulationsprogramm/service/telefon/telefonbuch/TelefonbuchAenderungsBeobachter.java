package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SteuerberaterAnsprechpartner;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Meldet dem {@link TelefonbuchAbgleichPlaner} jede Änderung an Kontakten, die ins
 * FRITZ!Box-Telefonbuch gehören – egal, an welcher Stelle im Code gespeichert wird.
 * <p>
 * Dafür hängt er sich als Hibernate-„Post-Commit“-Beobachter ein: Er wird erst
 * nach erfolgreichem Commit aufgerufen, nie bei einem Rollback. Bei Änderungen
 * zählen nur Felder, die im Telefonbuch landen (Name, Nummern, aktiv,
 * gesperrt/anonymisiert, …) – ein neuer Login-Token eines Mitarbeiters löst
 * nichts aus. Massenänderungen per JPQL/SQL sieht Hibernate nicht; die fängt
 * der nächtliche Vollabgleich ab.
 * <p>
 * Alle Fehler werden geschluckt: Das Speichern im ERP ist zu diesem Zeitpunkt
 * schon abgeschlossen und darf durch den Abgleich nicht scheitern.
 */
@Slf4j
@Component
public class TelefonbuchAenderungsBeobachter
        implements PostCommitInsertEventListener, PostCommitUpdateEventListener, PostCommitDeleteEventListener {

    /** Beobachtete Entitäten → Felder, deren Änderung den Telefonbuch-Eintrag ändern kann. */
    static final Map<Class<?>, Set<String>> RELEVANTE_FELDER = Map.of(
            Kunde.class, Set.of("name", "anrede", "telefon", "mobiltelefon", "gesperrtAm", "anonymisiertAm"),
            Lieferanten.class, Set.of("lieferantenname", "telefon", "mobiltelefon", "istAktiv"),
            Mitarbeiter.class, Set.of("vorname", "nachname", "telefon", "festnetz", "aktiv", "art"),
            SteuerberaterKontakt.class, Set.of("name", "telefon", "aktiv", "gueltigBis"),
            SteuerberaterAnsprechpartner.class, Set.of("vorname", "nachname", "telefon", "steuerberater"),
            KontaktRufnummer.class, Set.of("nummerRoh", "kunde", "lieferant", "steuerberater"));

    private final EntityManagerFactory entityManagerFactory;
    private final TelefonbuchAbgleichPlaner planer;

    public TelefonbuchAenderungsBeobachter(EntityManagerFactory entityManagerFactory, TelefonbuchAbgleichPlaner planer) {
        this.entityManagerFactory = entityManagerFactory;
        this.planer = planer;
    }

    @PostConstruct
    void registriere() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_COMMIT_INSERT, this);
        registry.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        registry.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return RELEVANTE_FELDER.containsKey(persister.getMappedClass());
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (beobachtet(event.getPersister())) {
            melde();
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (beobachtet(event.getPersister()) && relevanteAenderung(event.getPersister(), event.getDirtyProperties())) {
            melde();
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (beobachtet(event.getPersister())) {
            melde();
        }
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
        // Rollback: im ERP hat sich nichts geändert.
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent event) {
        // Rollback: im ERP hat sich nichts geändert.
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent event) {
        // Rollback: im ERP hat sich nichts geändert.
    }

    private static boolean beobachtet(EntityPersister persister) {
        return persister != null && RELEVANTE_FELDER.containsKey(persister.getMappedClass());
    }

    /** Ohne Angabe der geänderten Felder lieber einmal zu viel abgleichen. */
    static boolean relevanteAenderung(EntityPersister persister, int[] geaenderteFelder) {
        if (geaenderteFelder == null) {
            return true;
        }
        Set<String> relevant = RELEVANTE_FELDER.get(persister.getMappedClass());
        String[] namen = persister.getPropertyNames();
        for (int index : geaenderteFelder) {
            if (index >= 0 && index < namen.length && relevant.contains(namen[index])) {
                return true;
            }
        }
        return false;
    }

    private void melde() {
        try {
            planer.aenderungGemeldet();
        } catch (RuntimeException e) {
            log.warn("Telefonbuch: Änderung konnte nicht gemeldet werden: {}", e.getClass().getSimpleName());
        }
    }
}
