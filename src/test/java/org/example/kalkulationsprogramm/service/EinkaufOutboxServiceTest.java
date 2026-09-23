package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto;
import org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufOutboxService;
import org.example.kalkulationsprogramm.service.mail.KontoMailTransport;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EinkaufOutboxServiceTest {
    @Test
    void localTestMailPolicySperrtVorKontoaufloesungUndMimeErstellung() {
        var repository = mock(EinkaufVersandauftragRepository.class);
        var konten = mock(MailkontoService.class);
        var policy = mock(LocalTestMailPolicy.class);
        var transport = mock(KontoMailTransport.class);
        var tx = transactionManager();
        var service = service(repository, konten, policy, transport, tx);
        UUID idempotenzKey = UUID.randomUUID();
        when(repository.findByIdempotenzKey(idempotenzKey)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Mailzugriff lokal gesperrt"))
                .when(policy).pruefeNetzwerkzugriff("EINKAUF");

        assertThrows(IllegalStateException.class, () -> service.einreihen(snapshot(), idempotenzKey, 1L));

        verify(policy).pruefeNetzwerkzugriff("EINKAUF");
        verifyNoInteractions(konten, transport);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void vorhandenerIdempotenzschluesselLiefertGleichenAuftragOhneKontozugriff() throws Exception {
        var repository = mock(EinkaufVersandauftragRepository.class);
        var konten = mock(MailkontoService.class);
        var policy = mock(LocalTestMailPolicy.class);
        var transport = mock(KontoMailTransport.class);
        var mapper = new ObjectMapper();
        var tx = transactionManager();
        UUID key = UUID.randomUUID();
        var request = snapshot();
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(request)));
        var existing = new EinkaufVersandauftrag("ANFRAGE", 19L, 4L, 8L, "EINKAUF", key,
                hash, "mimehash", request.freigabeHash(), mapper.writeValueAsBytes(request),
                new byte[] {1, 2}, "<fixed@erp.local>", 1L);
        when(repository.findByIdempotenzKey(key)).thenReturn(Optional.of(existing));
        var service = new EinkaufOutboxService(repository, konten, policy, transport, mapper, tx, eventPublisher());

        var result = service.einreihen(request, key, 99L);

        assertEquals("VORBEREITET", result.status());
        assertEquals("<fixed@erp.local>", result.messageId());
        verifyNoInteractions(policy, konten, transport);
    }

    @Test
    void nurSicherFehlgeschlagenerVersandDarfGezieltErneutVersuchtWerden() {
        var repository = mock(EinkaufVersandauftragRepository.class);
        var order = newOrder(UUID.randomUUID());
        order.starte(1L);
        order.sicherFehlgeschlagen("SMTP_VERBINDUNG");
        when(repository.sperreById(42L)).thenReturn(Optional.of(order));
        var service = service(repository, mock(MailkontoService.class), mock(LocalTestMailPolicy.class),
                mock(KontoMailTransport.class), transactionManager());

        var result = service.erneutVersuchen(42L, 0, 2L);

        assertEquals("VORBEREITET", result.status());
        assertEquals(1, order.getVersuche().size());
        assertFalse(service.beanspruche(42L).archivRetry());
        assertEquals(2, order.getVersuche().size());
        assertEquals(EinkaufVersandauftrag.Status.LAEUFT, order.getStatus());

        order.unklar("SMTP_ANTWORT_UNKLAR");
        assertThrows(IllegalStateException.class, () -> service.erneutVersuchen(42L, 0, 2L));
        assertEquals(EinkaufVersandauftrag.Status.UNKLAR, order.getStatus());
    }

    @Test
    void aufklaerungSpeichertBelegUndNimmtKeinenZweitenSmtpVersandAn() {
        var repository = mock(EinkaufVersandauftragRepository.class);
        var order = newOrder(UUID.randomUUID());
        order.starte(1L);
        order.unklar("SMTP_ANTWORT_UNKLAR");
        when(repository.sperreById(42L)).thenReturn(Optional.of(order));
        var service = service(repository, mock(MailkontoService.class), mock(LocalTestMailPolicy.class),
                mock(KontoMailTransport.class), transactionManager());

        service.klaeren(42L, new EinkaufVersandDto.Klaerung(0, EinkaufVersandDto.Entscheidung.BEREITS_ANGENOMMEN,
                "Antwort im Testpostfach"), 3L);

        assertEquals(EinkaufVersandauftrag.Status.ANGENOMMEN, order.getStatus());
        assertEquals("Antwort im Testpostfach", order.getKlaerungBeleg());
        assertEquals("BEREITS_ANGENOMMEN", order.getKlaerungEntscheidung());
        assertEquals(3L, order.getKlaerungAkteurId());
    }

    private EinkaufOutboxService service(EinkaufVersandauftragRepository repository, MailkontoService konten,
            LocalTestMailPolicy policy, KontoMailTransport transport, PlatformTransactionManager tm) {
        return new EinkaufOutboxService(repository, konten, policy, transport, new ObjectMapper(), tm, eventPublisher());
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        return tx;
    }

    private org.springframework.context.ApplicationEventPublisher eventPublisher() {
        return mock(org.springframework.context.ApplicationEventPublisher.class);
    }

    private EinkaufVersandDto.VersandSnapshot snapshot() {
        return new EinkaufVersandDto.VersandSnapshot("ANFRAGE", 19L, 4L, 8L,
                new EinkaufVersandDto.KontoZugangReferenz("EINKAUF"),
                new MailTransportDto.Nachricht("<fixed@erp.local>", "test@example.com", "Anfrage", "<p>Test</p>",
                        null, List.of(), List.of()), "freigabe-hash");
    }

    private EinkaufVersandauftrag newOrder(UUID key) {
        return new EinkaufVersandauftrag("ANFRAGE", 19L, 4L, 8L, "EINKAUF", key,
                "payloadhash", "mimehash", "freigabehash", new byte[] {1}, new byte[] {2},
                "<test@erp.local>", 1L);
    }
}
