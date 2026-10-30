package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.AuditChainState;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAuditAktion;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.repository.AuditChainStateRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.example.kalkulationsprogramm.testsupport.LogMitschnitt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AusgangsGeschaeftsDokumentAuditServiceTest {

    @Mock
    private AusgangsGeschaeftsDokumentAuditRepository auditRepository;
    @Mock
    private AuditChainStateRepository chainStateRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AusgangsGeschaeftsDokumentAuditService service;

    private AusgangsGeschaeftsDokument angebot;

    @BeforeEach
    void setUp() {
        AuditChainState state = new AuditChainState();
        state.setId(1);
        state.setLastChainIndex(4L);
        state.setUpdatedAt(LocalDateTime.now());
        when(chainStateRepository.lockState()).thenReturn(state);
        when(auditRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        angebot = new AusgangsGeschaeftsDokument();
        angebot.setId(123L);
        angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        angebot.setDokumentNummer("AG-2026-0021");
    }

    @Test
    void digitaleAnnahme_speichertNachweisInDerKette_aberNichtImLog() {
        String nachweis = "Digitale Annahme von Angebot AG-2026-0021 durch Max Mustermann.";

        List<String> log = LogMitschnitt.von(AusgangsGeschaeftsDokumentAuditService.class,
                () -> service.protokolliereDigitaleAnnahme(angebot, "192.0.2.10", nachweis));

        ArgumentCaptor<AusgangsGeschaeftsDokumentAudit> audit =
                ArgumentCaptor.forClass(AusgangsGeschaeftsDokumentAudit.class);
        verify(auditRepository, atLeastOnce()).saveAndFlush(audit.capture());
        assertThat(audit.getValue().getAktion()).isEqualTo(AusgangsGeschaeftsDokumentAuditAktion.DIGITAL_ANGENOMMEN);
        assertThat(audit.getValue().getAenderungsgrund()).isEqualTo(nachweis);
        assertThat(audit.getValue().getChainIndex()).isEqualTo(5L);
        assertThat(audit.getValue().getEntryHash()).isNotBlank();
        // DSGVO: Die Begründung kann Personendaten enthalten und gehört nicht ins Log.
        assertThat(log).isNotEmpty().noneMatch(zeile -> zeile.contains("Mustermann"));
    }

    @Test
    void digitaleAnnahme_ohneNachweis_nimmtStandardtext() {
        service.protokolliereDigitaleAnnahme(angebot, "192.0.2.10", " ");

        ArgumentCaptor<AusgangsGeschaeftsDokumentAudit> audit =
                ArgumentCaptor.forClass(AusgangsGeschaeftsDokumentAudit.class);
        verify(auditRepository, atLeastOnce()).saveAndFlush(audit.capture());
        assertThat(audit.getValue().getAenderungsgrund()).isEqualTo("Digitale Annahme durch Kunden");
    }
}
