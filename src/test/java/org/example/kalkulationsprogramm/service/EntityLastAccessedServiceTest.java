package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.EntityLastAccessed;
import org.example.kalkulationsprogramm.repository.EntityLastAccessedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityLastAccessedServiceTest {

    @Mock
    private EntityLastAccessedRepository repository;

    @InjectMocks
    private EntityLastAccessedService service;

    @Test
    void track_speichert_Eintrag_mit_korrektem_Composite_Key() {
        service.track(42L, "PROJEKT", 7L);

        ArgumentCaptor<EntityLastAccessed> captor = ArgumentCaptor.forClass(EntityLastAccessed.class);
        verify(repository).save(captor.capture());

        EntityLastAccessed saved = captor.getValue();
        assertThat(saved.getId().getUserId()).isEqualTo(42L);
        assertThat(saved.getId().getEntityType()).isEqualTo("PROJEKT");
        assertThat(saved.getId().getEntityId()).isEqualTo(7L);
        assertThat(saved.getZugegriffenAm()).isNotNull();
    }

    @Test
    void track_ignoriert_null_Parameter() {
        service.track(null, "PROJEKT", 1L);
        service.track(1L, null, 1L);
        service.track(1L, "PROJEKT", null);

        verify(repository, never()).save(any());
    }

    @Test
    void listForUser_liefert_Map_in_DESC_Reihenfolge_des_Repositories() {
        LocalDateTime alt = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime mittel = LocalDateTime.of(2026, 1, 2, 8, 0);
        LocalDateTime neu = LocalDateTime.of(2026, 1, 3, 8, 0);

        when(repository.findAllByUserAndType(eq(42L), eq("PROJEKT"))).thenReturn(List.of(
                new EntityLastAccessed(42L, "PROJEKT", 30L, neu),
                new EntityLastAccessed(42L, "PROJEKT", 20L, mittel),
                new EntityLastAccessed(42L, "PROJEKT", 10L, alt)
        ));

        Map<Long, Long> result = service.listForUser(42L, "PROJEKT");

        assertThat(result.keySet()).containsExactly(30L, 20L, 10L);
        assertThat(result.get(30L)).isGreaterThan(result.get(20L));
        assertThat(result.get(20L)).isGreaterThan(result.get(10L));
    }

    @Test
    void listForUser_liefert_leere_Map_bei_null_Parametern() {
        assertThat(service.listForUser(null, "PROJEKT")).isEmpty();
        assertThat(service.listForUser(1L, null)).isEmpty();
        verify(repository, never()).findAllByUserAndType(any(), any());
    }
}
