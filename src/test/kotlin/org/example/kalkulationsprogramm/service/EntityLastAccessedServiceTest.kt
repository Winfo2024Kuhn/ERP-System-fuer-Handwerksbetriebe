package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.EntityLastAccessed
import org.example.kalkulationsprogramm.repository.EntityLastAccessedRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class EntityLastAccessedServiceTest {

    @Mock
    private lateinit var repository: EntityLastAccessedRepository

    @InjectMocks
    private lateinit var service: EntityLastAccessedService

    @Test
    fun trackSpeichertEintragMitKorrektemCompositeKey() {
        service.track(42L, "PROJEKT", 7L)

        val captor = ArgumentCaptor.forClass(EntityLastAccessed::class.java)
        verify(repository).save(captor.capture())

        val saved = captor.value
        assertThat(saved.id!!.userId).isEqualTo(42L)
        assertThat(saved.id!!.entityType).isEqualTo("PROJEKT")
        assertThat(saved.id!!.entityId).isEqualTo(7L)
        assertThat(saved.zugegriffenAm).isNotNull()
    }

    @Test
    fun trackIgnoriertNullParameter() {
        service.track(null, "PROJEKT", 1L)
        service.track(1L, null, 1L)
        service.track(1L, "PROJEKT", null)

        verify(repository, never()).save(any())
    }

    @Test
    fun listForUserLiefertMapInDescReihenfolgeDesRepositories() {
        val alt = LocalDateTime.of(2026, 1, 1, 8, 0)
        val mittel = LocalDateTime.of(2026, 1, 2, 8, 0)
        val neu = LocalDateTime.of(2026, 1, 3, 8, 0)

        `when`(repository.findAllByUserAndType(eq(42L), eq("PROJEKT"))).thenReturn(
            listOf(
                EntityLastAccessed(42L, "PROJEKT", 30L, neu),
                EntityLastAccessed(42L, "PROJEKT", 20L, mittel),
                EntityLastAccessed(42L, "PROJEKT", 10L, alt),
            ),
        )

        val result = service.listForUser(42L, "PROJEKT")

        assertThat(result.keys).containsExactly(30L, 20L, 10L)
        assertThat(result[30L]).isGreaterThan(result[20L])
        assertThat(result[20L]).isGreaterThan(result[10L])
    }

    @Test
    fun listForUserLiefertLeereMapBeiNullParametern() {
        assertThat(service.listForUser(null, "PROJEKT")).isEmpty()
        assertThat(service.listForUser(1L, null)).isEmpty()
        verify(repository, never()).findAllByUserAndType(any(), any())
    }
}
