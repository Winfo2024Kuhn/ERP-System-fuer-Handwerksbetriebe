package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import jakarta.persistence.EntityManagerFactory;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelefonbuchAenderungsBeobachterTest {

    @Mock EntityManagerFactory emf;
    @Mock TelefonbuchAbgleichPlaner planer;
    @Mock EntityPersister kundePersister;
    @Mock EntityPersister projektPersister;

    private TelefonbuchAenderungsBeobachter beobachter;

    @BeforeEach
    void setUp() {
        beobachter = new TelefonbuchAenderungsBeobachter(emf, planer);
        doReturn(Kunde.class).when(kundePersister).getMappedClass();
        when(kundePersister.getPropertyNames()).thenReturn(new String[]{"name", "strasse", "telefon"});
        doReturn(Projekt.class).when(projektPersister).getMappedClass();
    }

    private static PostUpdateEvent update(EntityPersister persister, int[] geaendert) {
        PostUpdateEvent e = mock(PostUpdateEvent.class);
        when(e.getPersister()).thenReturn(persister);
        when(e.getDirtyProperties()).thenReturn(geaendert);
        return e;
    }

    @Test
    @DisplayName("Nur beobachtete Entitäten brauchen Post-Commit-Behandlung")
    void nurBeobachtete() {
        assertThat(beobachter.requiresPostCommitHandling(kundePersister)).isTrue();
        assertThat(beobachter.requiresPostCommitHandling(projektPersister)).isFalse();
    }

    @Test
    @DisplayName("Anlegen und Löschen eines Kunden melden; andere Entitäten nicht")
    void anlegenLoeschen() {
        PostInsertEvent insert = mock(PostInsertEvent.class);
        when(insert.getPersister()).thenReturn(kundePersister);
        PostDeleteEvent delete = mock(PostDeleteEvent.class);
        when(delete.getPersister()).thenReturn(kundePersister);
        PostInsertEvent projekt = mock(PostInsertEvent.class);
        when(projekt.getPersister()).thenReturn(projektPersister);

        beobachter.onPostInsert(insert);
        beobachter.onPostDelete(delete);
        beobachter.onPostInsert(projekt);

        verify(planer, times(2)).aenderungGemeldet();
    }

    @Test
    @DisplayName("Änderung meldet nur, wenn ein Telefonbuch-Feld betroffen ist (oder unbekannt welches)")
    void nurRelevanteFelder() {
        beobachter.onPostUpdate(update(kundePersister, new int[]{1}));
        verify(planer, never()).aenderungGemeldet();

        beobachter.onPostUpdate(update(kundePersister, new int[]{1, 2}));
        verify(planer, times(1)).aenderungGemeldet();

        beobachter.onPostUpdate(update(kundePersister, null));
        verify(planer, times(2)).aenderungGemeldet();

        beobachter.onPostUpdate(update(kundePersister, new int[]{7}));
        beobachter.onPostUpdate(update(projektPersister, null));
        verify(planer, times(2)).aenderungGemeldet();
    }

    @Test
    @DisplayName("Rollback meldet nichts; ein Fehler beim Melden stört das Speichern nicht")
    void rollbackUndFehler() {
        beobachter.onPostInsertCommitFailed(mock(PostInsertEvent.class));
        beobachter.onPostUpdateCommitFailed(mock(PostUpdateEvent.class));
        beobachter.onPostDeleteCommitFailed(mock(PostDeleteEvent.class));
        verify(planer, never()).aenderungGemeldet();

        doThrow(new IllegalStateException("kaputt")).when(planer).aenderungGemeldet();
        PostInsertEvent insert = mock(PostInsertEvent.class);
        when(insert.getPersister()).thenReturn(kundePersister);
        assertThatCode(() -> beobachter.onPostInsert(insert)).doesNotThrowAnyException();
    }
}
