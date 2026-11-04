package org.example.kalkulationsprogramm.service.mail.suche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.example.kalkulationsprogramm.repository.EmailSucheRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class EmailSuchtextNachpflegeTest {

    @AfterEach
    void unterbrechungZuruecksetzen() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("Unterbrochener Thread (Herunterfahren): kein weiterer Block, Unterbrechung bleibt stehen")
    void brichtBeiUnterbrechungAb() {
        EmailSucheRepository repository = mock(EmailSucheRepository.class);
        PlatformTransactionManager transaktionen = mock(PlatformTransactionManager.class);
        EmailSuchtextNachpflege nachpflege = new EmailSuchtextNachpflege(repository, transaktionen, true, 200);

        Thread.currentThread().interrupt();

        assertThat(nachpflege.nachpflegen()).isZero();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verifyNoInteractions(repository, transaktionen);
    }
}
