package org.example.kalkulationsprogramm.service.mail.liste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.dto.SteuerberaterAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SteuerberaterOrdnerTest {

    @Mock private SteuerberaterKontaktService steuerberaterKontaktService;
    @Mock private EmailRepository emailRepository;
    @InjectMocks private SteuerberaterOrdner ordner;

    private static SteuerberaterKontaktDto kanzlei() {
        SteuerberaterKontaktDto k = new SteuerberaterKontaktDto();
        k.setEmail("Kanzlei Muster <info@Kanzlei-Muster.example.org>");
        k.setWeitereEmails(List.of("lohn@steuer-example.org", "kaputt"));
        SteuerberaterAnsprechpartnerDto person = new SteuerberaterAnsprechpartnerDto();
        person.setEmail("erika@kanzlei-muster.example.org");
        SteuerberaterAnsprechpartnerDto ohneMail = new SteuerberaterAnsprechpartnerDto();
        k.setAnsprechpartnerListe(List.of(person, ohneMail));
        return k;
    }

    private static Email mail(long id, String von, LocalDateTime gesendet) {
        Email e = new Email();
        e.setId(id);
        e.setFromAddress(von);
        e.setRecipient("info@example.com");
        e.setSentAt(gesendet);
        return e;
    }

    @Test
    void domainsAusAllenAdressen() {
        given(steuerberaterKontaktService.findAll()).willReturn(List.of(kanzlei()));

        assertThat(ordner.domains()).containsExactlyInAnyOrder("kanzlei-muster.example.org", "steuer-example.org");
    }

    @Test
    void ohneSteuerberaterKeineMails() {
        given(steuerberaterKontaktService.findAll()).willReturn(List.of());

        assertThat(ordner.emails()).isEmpty();
        verifyNoInteractions(emailRepository);
    }

    @Test
    void nurExakteDomainDoppelteEinmalNeuesteZuerst() {
        SteuerberaterKontaktDto k = new SteuerberaterKontaktDto();
        k.setEmail("info@kanzlei.example.org");
        given(steuerberaterKontaktService.findAll()).willReturn(List.of(k));
        Email treffer = mail(1, "Max <max@kanzlei.example.org>", LocalDateTime.of(2026, 10, 1, 9, 0));
        Email neuer = mail(2, "erika@KANZLEI.EXAMPLE.ORG", LocalDateTime.of(2026, 10, 2, 9, 0));
        Email ohneDatum = mail(3, "lohn@kanzlei.example.org", null);
        Email fremd = mail(4, "max@kanzlei.example.org.example.net", LocalDateTime.of(2026, 10, 3, 9, 0));
        given(emailRepository.findTaxAdvisorCandidates("@kanzlei.example.org"))
                .willReturn(List.of(treffer, fremd, neuer, ohneDatum, treffer));

        assertThat(ordner.emails()).extracting(Email::getId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void trefferAuchUeberEmpfaengerUndCc() {
        SteuerberaterKontaktDto k = new SteuerberaterKontaktDto();
        k.setEmail("info@kanzlei.example.org");
        given(steuerberaterKontaktService.findAll()).willReturn(List.of(k));
        Email gesendet = mail(1, "buero@example.com", LocalDateTime.of(2026, 10, 1, 9, 0));
        gesendet.setRecipient("info@kanzlei.example.org");
        Email kopie = mail(2, "buero@example.com", LocalDateTime.of(2026, 10, 2, 9, 0));
        kopie.setCc("Lohn <lohn@kanzlei.example.org>");
        given(emailRepository.findTaxAdvisorCandidates("@kanzlei.example.org")).willReturn(List.of(gesendet, kopie));

        assertThat(ordner.emails()).extracting(Email::getId).containsExactly(2L, 1L);
    }
}
