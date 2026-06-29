package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.Optional

class InquiryDetectionServiceTest {

    private lateinit var service: InquiryDetectionService

    @Mock
    private lateinit var lieferantenRepository: LieferantenRepository

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = InquiryDetectionService(lieferantenRepository)
        `when`(lieferantenRepository.findByEmail(anyString())).thenReturn(Optional.empty())
        `when`(lieferantenRepository.existsByEmailDomain(anyString())).thenReturn(false)
    }

    @Test
    fun testCalculateInquiryScoreSubjectAnfrage() {
        val email = Email().apply {
            subject = "Anfrage"
            body = "Hallo, hier ist Text."
            fromAddress = "test@example.com"
        }

        service.calculateInquiryScore(email)
        service.analyzeAndMarkInquiry(email)

        assertFalse(email.isPotentialInquiry, "Expected 'Anfrage' in subject alone to NOT be enough yet")
    }

    @Test
    fun testCalculateInquiryScoreSubjectStrongKeyword() {
        val email = Email().apply {
            subject = "Anfrage bezüglich Projekt"
            body = "Hallo."
            fromAddress = "test@example.com"
        }

        service.calculateInquiryScore(email)
        service.analyzeAndMarkInquiry(email)

        assertTrue(email.isPotentialInquiry, "Expected strong keyword to trigger inquiry")
    }
}
