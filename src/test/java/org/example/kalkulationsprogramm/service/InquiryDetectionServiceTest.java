package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class InquiryDetectionServiceTest {

    private InquiryDetectionService service;

    @Mock
    private LieferantenRepository lieferantenRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new InquiryDetectionService(lieferantenRepository);
        when(lieferantenRepository.findByEmail(anyString())).thenReturn(java.util.Optional.empty());
        when(lieferantenRepository.existsByEmailDomain(anyString())).thenReturn(false);
    }

    @Test
    void testCalculateInquiryScore_SubjectAnfrage() {
        Email email = new Email();
        email.setSubject("Anfrage");
        email.setBody("Hallo, hier ist Text.");
        email.setFromAddress("test@example.com");

        int score = service.calculateInquiryScore(email);
        // Expecting: "anfrage" is medium keyword (15). Subject multiplier 1.3 -> 19.5
        // -> 19.
        // Threshold is 40.
        System.out.println("Score for 'Anfrage' in subject: " + score);

        service.analyzeAndMarkInquiry(email);
        // Should likely be false with current logic
        assertFalse(email.isPotentialInquiry(), "Expected 'Anfrage' in subject alone to NOT be enough yet");
    }

    @Test
    void testCalculateInquiryScore_SubjectStrongKeyword() {
        Email email = new Email();
        email.setSubject("Anfrage bezüglich Projekt");
        email.setBody("Hallo.");
        email.setFromAddress("test@example.com");

        // "anfrage bezüglich" is strong (35). Subject multiplier 1.5 -> 52.
        int score = service.calculateInquiryScore(email);
        System.out.println("Score for 'Anfrage bezüglich' in subject: " + score);

        service.analyzeAndMarkInquiry(email);
        assertTrue(email.isPotentialInquiry(), "Expected strong keyword to trigger inquiry");
    }
}
