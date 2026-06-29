package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.example.kalkulationsprogramm.domain.EmailAbsender
import org.example.kalkulationsprogramm.dto.EmailAbsenderDto
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.Optional

class EmailAbsenderServiceTest {

    private lateinit var repository: EmailAbsenderRepository
    private lateinit var service: EmailAbsenderService

    @BeforeEach
    fun setUp() {
        repository = mock(EmailAbsenderRepository::class.java)
        service = EmailAbsenderService(repository)
        `when`(repository.save(any(EmailAbsender::class.java))).thenAnswer {
            it.getArgument<EmailAbsender>(0).apply {
                if (id == null) {
                    id = 42L
                }
            }
        }
    }

    @Test
    fun leereAdresseWirdAbgewiesen() {
        val dto = EmailAbsenderDto().apply { emailAdresse = "   " }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("E-Mail-Adresse")
    }

    @Test
    fun ungueltigeAdresseWirdAbgewiesen() {
        val dto = EmailAbsenderDto().apply { emailAdresse = "kein-at-zeichen" }

        assertThatThrownBy { service.save(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Ungueltig")
    }

    @Test
    fun duplikatBeiAnderemEintragWirdAbgewiesen() {
        val bestehend = EmailAbsender().apply {
            id = 7L
            emailAdresse = "max@mustermann.de"
        }
        `when`(repository.findByEmailAdresseIgnoreCase("MAX@mustermann.de"))
            .thenReturn(Optional.of(bestehend))

        val neu = EmailAbsenderDto().apply { emailAdresse = "MAX@mustermann.de" }

        assertThatThrownBy { service.save(neu) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("bereits angelegt")
    }

    @Test
    fun updateAufEigeneAdresseIstErlaubt() {
        val bestehend = EmailAbsender().apply {
            id = 7L
            emailAdresse = "max@mustermann.de"
            aktiv = true
        }
        `when`(repository.findById(7L)).thenReturn(Optional.of(bestehend))
        `when`(repository.findByEmailAdresseIgnoreCase("max@mustermann.de")).thenReturn(Optional.of(bestehend))

        val dto = EmailAbsenderDto().apply {
            id = 7L
            emailAdresse = "max@mustermann.de"
            anzeigename = "Max Mustermann"
        }

        val saved = service.save(dto)

        assertThat(saved.id).isEqualTo(7L)
        assertThat(saved.emailAdresse).isEqualTo("max@mustermann.de")
        assertThat(saved.anzeigename).isEqualTo("Max Mustermann")
    }

    @Test
    fun findActiveEmailAddressesGibtNurStringsZurueck() {
        val a1 = EmailAbsender().apply { emailAdresse = "erika@musterfrau.de" }
        val a2 = EmailAbsender().apply { emailAdresse = "max@mustermann.de" }
        `when`(repository.findByAktivTrueOrderBySortierungAscIdAsc()).thenReturn(listOf(a1, a2))

        val adressen = service.findActiveEmailAddresses()

        assertThat(adressen).containsExactly("erika@musterfrau.de", "max@mustermann.de")
    }
}
