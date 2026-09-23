package org.example.kalkulationsprogramm.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailkonto;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Update;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Verschluesselung;
import org.example.kalkulationsprogramm.repository.EinkaufMailkontoRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(MailkontoService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MailkontoPersistenzTest {
    @Autowired private MailkontoService service;
    @Autowired private EinkaufMailkontoRepository repository;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @MockBean private SystemSettingsService settings;
    @MockBean private MailSecretService secrets;
    @MockBean private EinkaufBerechtigungService berechtigungen;

    @BeforeEach
    void leereMailkonten() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void antwortversionBleibtNachUpdateAktuellUndWirdBeimNaechstenPutAkzeptiert() {
        when(berechtigungen.verlangeAktivenAdmin(any())).thenReturn(70L);
        var auth = auth();
        var initial = service.speichern(auth, update(0L, "Erster Name"));
        var firstPut = service.speichern(auth, update(initial.version(), "Zweiter Name"));

        assertThat(firstPut.version()).isEqualTo(1L);
        assertThat(repository.findById("EINKAUF").orElseThrow().getVersion()).isEqualTo(firstPut.version());

        var secondPut = service.speichern(auth, update(firstPut.version(), "Dritter Name"));

        assertThat(secondPut.version()).isEqualTo(2L);
        assertThat(repository.findById("EINKAUF").orElseThrow().getFromName()).isEqualTo("Dritter Name");
    }

    @Test
    void echteOptimisticLockKollisionWirdBeimFlushAusgeloest() {
        when(berechtigungen.verlangeAktivenAdmin(any())).thenReturn(70L);
        var auth = auth();
        var created = service.speichern(auth, update(0L, "Ursprung"));
        EinkaufMailkonto staleCopy = repository.findById("EINKAUF").orElseThrow();
        entityManager.detach(staleCopy);

        service.speichern(auth, update(created.version(), "Neu gespeichert"));

        assertThatThrownBy(() -> repository.saveAndFlush(staleCopy))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    private Update update(long version, String fromName) {
        return new Update(version, false, "einkauf@example.com", fromName,
                "smtp.test.invalid", 465, "smtp-user", Verschluesselung.TLS,
                "imap.test.invalid", 993, "imap-user", Verschluesselung.STARTTLS,
                "INBOX", "Sent", null, null);
    }

    private UsernamePasswordAuthenticationToken auth() {
        var principal = new FrontendUserPrincipal(70L, "test@example.com", "Max Mustermann", "", true,
                Set.of(org.example.kalkulationsprogramm.domain.FrontendUserRole.ADMIN));
        return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
    }
}
