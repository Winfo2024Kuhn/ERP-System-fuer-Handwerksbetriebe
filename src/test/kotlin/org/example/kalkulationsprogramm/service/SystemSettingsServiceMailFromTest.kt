package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.SystemSetting
import org.example.kalkulationsprogramm.repository.SystemSettingRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.Optional

class SystemSettingsServiceMailFromTest {

    private lateinit var repository: SystemSettingRepository
    private lateinit var service: SystemSettingsService

    @BeforeEach
    fun setUp() {
        repository = mock(SystemSettingRepository::class.java)
        service = SystemSettingsService(repository)
    }

    @Test
    fun mailFromAddress_leerInDb_faelltAufSmtpUserZurueck() {
        stubSetting("mail.from-address", null)
        stubSetting("smtp.username", "info-firma@t-online.de")

        assertThat(service.mailFromAddress).isEqualTo("info-firma@t-online.de")
    }

    @Test
    fun mailFromAddress_konfiguriert_wirdZurueckgegeben() {
        stubSetting("mail.from-address", "kontakt@firma.de")
        stubSetting("smtp.username", "info-firma@t-online.de")

        assertThat(service.mailFromAddress).isEqualTo("kontakt@firma.de")
    }

    @Test
    fun mailFromAddress_ohneAtZeichen_faelltAufSmtpUserZurueck() {
        stubSetting("mail.from-address", "kein-at-zeichen")
        stubSetting("smtp.username", "info-firma@t-online.de")

        assertThat(service.mailFromAddress).isEqualTo("info-firma@t-online.de")
    }

    @Test
    fun mailFromAddress_mitWhitespace_wirdGetrimmt() {
        stubSetting("mail.from-address", "  kontakt@firma.de  ")
        stubSetting("smtp.username", "info-firma@t-online.de")

        assertThat(service.mailFromAddress).isEqualTo("kontakt@firma.de")
    }

    @Test
    fun mailFromAddress_platzhalter_faelltAufSmtpUserZurueck() {
        stubSetting("mail.from-address", "OVERRIDE_IN_LOCAL")
        stubSetting("smtp.username", "info-firma@t-online.de")

        assertThat(service.mailFromAddress).isEqualTo("info-firma@t-online.de")
    }

    private fun stubSetting(key: String, value: String?) {
        if (value == null) {
            `when`(repository.findById(eq(key))).thenReturn(Optional.empty())
        } else {
            `when`(repository.findById(eq(key))).thenReturn(Optional.of(SystemSetting(key, value, null)))
        }
        `when`(repository.save(any(SystemSetting::class.java))).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun saveMailFromAddress_trimmt_undSpeichertGetrimmtenWert() {
        `when`(repository.findById(anyString())).thenReturn(Optional.empty())

        service.saveMailFromAddress("  kontakt@firma.de  ")

        val captor = ArgumentCaptor.forClass(SystemSetting::class.java)
        verify(repository).save(captor.capture())
        assertThat(captor.value.key).isEqualTo("mail.from-address")
        assertThat(captor.value.value).isEqualTo("kontakt@firma.de")
    }

    @Test
    fun saveMailFromAddress_leer_speichertLeerstring() {
        `when`(repository.findById(anyString())).thenReturn(Optional.empty())

        service.saveMailFromAddress("")

        val captor = ArgumentCaptor.forClass(SystemSetting::class.java)
        verify(repository).save(captor.capture())
        assertThat(captor.value.value).isEmpty()
    }
}
