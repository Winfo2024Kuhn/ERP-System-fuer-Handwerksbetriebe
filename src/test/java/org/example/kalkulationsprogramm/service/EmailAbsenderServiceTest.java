package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests fuer die Adresslisten aus EmailAbsenderService (Anlegen/Ändern siehe PostfachServiceTest).
 */
class EmailAbsenderServiceTest {

    private EmailAbsenderRepository repository;
    private EmailAbsenderService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailAbsenderRepository.class);
        service = new EmailAbsenderService(repository);
    }

    @Test
    void findActiveEmailAddressesGibtNurStringsZurueck() {
        EmailAbsender a1 = new EmailAbsender();
        a1.setEmailAdresse("erika@musterfrau.de");
        EmailAbsender a2 = new EmailAbsender();
        a2.setEmailAdresse("max@mustermann.de");

        when(repository.findByAktivTrueOrderBySortierungAscIdAsc())
                .thenReturn(List.of(a1, a2));

        List<String> adressen = service.findActiveEmailAddresses();

        assertThat(adressen).containsExactly("erika@musterfrau.de", "max@mustermann.de");
    }

    @Test
    void getPrioritizedFromAddresses_ohneUserLiefertAktiveAdressen() {
        EmailAbsender a1 = new EmailAbsender();
        a1.setEmailAdresse("info@handwerk.de");
        EmailAbsender a2 = new EmailAbsender();
        a2.setEmailAdresse("max@mustermann.de");

        when(repository.findByAktivTrueOrderBySortierungAscIdAsc())
                .thenReturn(List.of(a1, a2));

        List<String> result = service.getPrioritizedFromAddresses(null);

        assertThat(result).containsExactly("info@handwerk.de", "max@mustermann.de");
    }

    @Test
    void getPrioritizedFromAddresses_mitUserStelltAdresseAnDenAnfangUndDedupliziert() {
        var profileRepo = mock(org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository.class);
        EmailAbsenderService customService = new EmailAbsenderService(repository, profileRepo);

        EmailAbsender a1 = new EmailAbsender();
        a1.setEmailAdresse("info@handwerk.de");
        EmailAbsender a2 = new EmailAbsender();
        a2.setEmailAdresse("max@mustermann.de");
        EmailAbsender a3 = new EmailAbsender();
        a3.setEmailAdresse("kontakt@handwerk.de");

        when(repository.findByAktivTrueOrderBySortierungAscIdAsc())
                .thenReturn(List.of(a1, a2, a3));

        EmailAbsender userAbsender = new EmailAbsender();
        userAbsender.setEmailAdresse("max@mustermann.de");
        var profile = new org.example.kalkulationsprogramm.domain.FrontendUserProfile();
        profile.setEmailAbsender(userAbsender);

        when(profileRepo.findById(10L)).thenReturn(Optional.of(profile));

        List<String> result = customService.getPrioritizedFromAddresses(10L);

        assertThat(result).containsExactly("max@mustermann.de", "info@handwerk.de", "kontakt@handwerk.de");
    }

    @Test
    void getPrioritizedFromAddresses_mitUserOhneAbsenderBelassenReihenfolge() {
        var profileRepo = mock(org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository.class);
        EmailAbsenderService customService = new EmailAbsenderService(repository, profileRepo);

        EmailAbsender a1 = new EmailAbsender();
        a1.setEmailAdresse("info@handwerk.de");

        when(repository.findByAktivTrueOrderBySortierungAscIdAsc())
                .thenReturn(List.of(a1));

        var profile = new org.example.kalkulationsprogramm.domain.FrontendUserProfile();
        profile.setEmailAbsender(null);

        when(profileRepo.findById(10L)).thenReturn(Optional.of(profile));

        List<String> result = customService.getPrioritizedFromAddresses(10L);

        assertThat(result).containsExactly("info@handwerk.de");
    }
}
