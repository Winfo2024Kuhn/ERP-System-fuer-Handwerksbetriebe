package org.example.kalkulationsprogramm.service.mail

import org.example.kalkulationsprogramm.service.SystemSettingsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class SmtpHtmlMailSenderTest {

    private lateinit var systemSettingsService: SystemSettingsService
    private lateinit var sender: SmtpHtmlMailSender

    @BeforeEach
    fun setup() {
        systemSettingsService = mock(SystemSettingsService::class.java)
        `when`(systemSettingsService.smtpHost).thenReturn("smtp.invalid.test")
        `when`(systemSettingsService.smtpPort).thenReturn(465)
        `when`(systemSettingsService.smtpUsername).thenReturn("info@example.com")
        `when`(systemSettingsService.smtpPassword).thenReturn("altes-passwort")

        sender = SmtpHtmlMailSender(systemSettingsService)
    }

    @Test
    fun liestZugangsdatenZurLaufzeitAusSystemSettingsService() {
        try {
            sender.send("info@example.com", "kunde@example.com", "Test", "<p>Hallo</p>", emptyMap())
        } catch (_: Exception) {
            // Verbindungsfehler erwartet.
        }

        verify(systemSettingsService).smtpHost
        verify(systemSettingsService).smtpPort
        verify(systemSettingsService).smtpUsername
        verify(systemSettingsService).smtpPassword
    }

    @Test
    fun liestPasswortBeiJedemSendAufrufNeu() {
        try {
            sender.send("info@example.com", "kunde@example.com", "Test 1", "<p>Erster Versand</p>", emptyMap())
        } catch (_: Exception) {
        }

        `when`(systemSettingsService.smtpPassword).thenReturn("neues-passwort")

        try {
            sender.send("info@example.com", "kunde@example.com", "Test 2", "<p>Zweiter Versand</p>", emptyMap())
        } catch (_: Exception) {
        }

        verify(systemSettingsService, times(2)).smtpPassword
    }

    @Test
    fun ueberspringtVersandWennEmpfaengerLeerIst() {
        sender.send("info@example.com", "", "Test", "<p>Hi</p>", emptyMap())
        sender.send("info@example.com", null, "Test", "<p>Hi</p>", emptyMap())

        verifyNoInteractions(systemSettingsService)
    }
}
