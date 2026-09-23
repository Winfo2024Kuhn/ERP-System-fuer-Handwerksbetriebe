package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufOutboxService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandWorker;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAnnahmeereignisConsumer;
import org.example.kalkulationsprogramm.service.mail.KontoMailTransport;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.example.kalkulationsprogramm.service.mail.SentMailArchiver;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.inOrder;
import java.util.List;

class EinkaufVersandWorkerTest {
    @Test
    void unklarerVersandWirdDurchWiederholteVerarbeitungNichtErneutGesendet() {
        EinkaufOutboxService outbox = mock(EinkaufOutboxService.class);
        MailkontoService konten = mock(MailkontoService.class);
        KontoMailTransport transport = mock(KontoMailTransport.class);
        SentMailArchiver archiver = mock(SentMailArchiver.class);
        LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
        EinkaufVersandWorker worker = new EinkaufVersandWorker(outbox, konten, transport, archiver, policy, List.of(), mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandDispatchPublisher.class));

        when(outbox.beanspruche(42L)).thenReturn(null);
        worker.verarbeite(42L);
        worker.verarbeite(42L);

        verify(outbox, times(2)).beanspruche(42L);
        verifyNoInteractions(konten, transport, archiver);
    }

    @Test
    void archivFehlerFuehrtBeimNaechstenAufrufNurZuImapArchivRetry() {
        EinkaufOutboxService outbox = mock(EinkaufOutboxService.class);
        MailkontoService konten = mock(MailkontoService.class);
        KontoMailTransport transport = mock(KontoMailTransport.class);
        SentMailArchiver archiver = mock(SentMailArchiver.class);
        LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
        EinkaufVersandWorker worker = new EinkaufVersandWorker(outbox, konten, transport, archiver, policy, List.of(), mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandDispatchPublisher.class));
        byte[] mime = {1, 2, 3};
        var sending = new EinkaufOutboxService.Claim(42L, "EINKAUF", mime,
                org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag.Status.LAEUFT,
                1, false, "ANFRAGE", 8L, 9L);
        var accepted = new EinkaufOutboxService.Claim(42L, "EINKAUF", mime,
                org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag.Status.ANGENOMMEN,
                2, true, "ANFRAGE", 8L, 9L);
        var retry = new EinkaufOutboxService.Claim(42L, "EINKAUF", mime,
                org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag.Status.ANGENOMMEN,
                4, true, "ANFRAGE", 8L, 9L);
        when(outbox.beanspruche(42L)).thenReturn(sending, accepted, retry);
        when(konten.resolve("EINKAUF")).thenReturn(null);
        when(transport.sendenVorbereitet(null, mime)).thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Versandergebnis(
                org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.ANGENOMMEN,
                "<mail@erp.local>", null, mime));
        when(archiver.archiviere(null, mime)).thenReturn(
                new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.ArchivErgebnis(false, "IMAP_ARCHIV"),
                new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.ArchivErgebnis(false, "IMAP_ARCHIV"));

        worker.verarbeite(42L);
        worker.verarbeite(42L);

        verify(transport, times(1)).sendenVorbereitet(null, mime);
        verify(archiver, times(2)).archiviere(null, mime);
        verify(outbox, times(2)).archivErgebnis(42L,
                new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.ArchivErgebnis(false, "IMAP_ARCHIV"));
    }

    @Test
    void lokalePolicySperrtVorClaimUndKontozugriff() {
        EinkaufOutboxService outbox = mock(EinkaufOutboxService.class);
        MailkontoService konten = mock(MailkontoService.class);
        KontoMailTransport transport = mock(KontoMailTransport.class);
        SentMailArchiver archiver = mock(SentMailArchiver.class);
        LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
        doThrow(new IllegalStateException("gesperrt")).when(policy).pruefeNetzwerkzugriff("EINKAUF");
        EinkaufVersandWorker worker = new EinkaufVersandWorker(outbox, konten, transport, archiver, policy, List.of(), mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandDispatchPublisher.class));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> worker.verarbeite(42L));

        verifyNoInteractions(outbox, konten, transport, archiver);
    }

    @Test
    void unklarerDataAusgangWirdBeimNaechstenAufrufNichtNochmalGesendet() {
        EinkaufOutboxService outbox = mock(EinkaufOutboxService.class);
        MailkontoService konten = mock(MailkontoService.class);
        KontoMailTransport transport = mock(KontoMailTransport.class);
        SentMailArchiver archiver = mock(SentMailArchiver.class);
        LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
        var prepared = new EinkaufOutboxService.Claim(42L, "EINKAUF", new byte[] {4},
                org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag.Status.LAEUFT,
                1, false, "ANFRAGE", 8L, 9L);
        when(outbox.beanspruche(42L)).thenReturn(prepared, null);
        when(konten.resolve("EINKAUF")).thenReturn(null);
        when(transport.sendenVorbereitet(isNull(), any())).thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Versandergebnis(
                org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.UNKLAR,
                "<mail@erp.local>", "SMTP_ANTWORT_UNKLAR", new byte[] {4}));
        var worker = new EinkaufVersandWorker(outbox, konten, transport, archiver, policy, List.of(), mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandDispatchPublisher.class));

        worker.verarbeite(42L);
        worker.verarbeite(42L);

        verify(transport, times(1)).sendenVorbereitet(isNull(), any());
        verify(outbox).abgeschlossen(eq(42L), argThat(result -> result.status()
                == org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.UNKLAR));
        verifyNoInteractions(archiver);
    }

    @Test
    void datenbankfehlerNachSmtpAnnahmeFuehrtBeimErneutenWorkeraufrufNichtZumZweitversand() {
        EinkaufOutboxService outbox = mock(EinkaufOutboxService.class);
        MailkontoService konten = mock(MailkontoService.class);
        KontoMailTransport transport = mock(KontoMailTransport.class);
        SentMailArchiver archiver = mock(SentMailArchiver.class);
        LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
        var prepared = new EinkaufOutboxService.Claim(42L, "EINKAUF", new byte[] {4},
                org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag.Status.LAEUFT,
                1, false, "ANFRAGE", 8L, 9L);
        when(outbox.beanspruche(42L)).thenReturn(prepared, null);
        when(konten.resolve("EINKAUF")).thenReturn(null);
        when(transport.sendenVorbereitet(isNull(), any())).thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Versandergebnis(
                org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.ANGENOMMEN,
                "<mail@erp.local>", null, new byte[] {4}));
        doThrow(new IllegalStateException("Datenbank nicht verfügbar"))
                .when(outbox).abgeschlossen(eq(42L), any());
        var worker = new EinkaufVersandWorker(outbox, konten, transport, archiver, policy, List.of(), mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandDispatchPublisher.class));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> worker.verarbeite(42L));
        worker.verarbeite(42L);

        verify(transport, times(1)).sendenVorbereitet(isNull(), any());
        verifyNoInteractions(archiver);
    }

    @Test
    void angenommeneAnfrageVerarbeitetDauerhaftesFachereignisNachSmtpUndVorArchivierung() {
        EinkaufOutboxService outbox = mock(EinkaufOutboxService.class);
        MailkontoService konten = mock(MailkontoService.class);
        KontoMailTransport transport = mock(KontoMailTransport.class);
        SentMailArchiver archiver = mock(SentMailArchiver.class);
        LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
        EinkaufAnnahmeereignisConsumer consumer = mock(EinkaufAnnahmeereignisConsumer.class);
        var sending = new EinkaufOutboxService.Claim(42L, "EINKAUF", new byte[] {4},
                org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag.Status.LAEUFT,
                1, false, "ANFRAGE", 8L, 9L);
        var accepted = new EinkaufOutboxService.Claim(42L, "EINKAUF", new byte[] {4},
                org.example.kalkulationsprogramm.domain.einkauf.EinkaufVersandauftrag.Status.ANGENOMMEN,
                2, true, "ANFRAGE", 8L, 9L);
        when(outbox.beanspruche(42L)).thenReturn(sending, accepted);
        when(konten.resolve("EINKAUF")).thenReturn(null);
        when(transport.sendenVorbereitet(isNull(), any())).thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Versandergebnis(
                org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.ANGENOMMEN,
                "<mail@erp.local>", null, new byte[] {4}));
        var worker = new EinkaufVersandWorker(outbox, konten, transport, archiver, policy, List.of(consumer), mock(org.example.kalkulationsprogramm.service.einkauf.EinkaufVersandDispatchPublisher.class));

        worker.verarbeite(42L);

        var order = inOrder(outbox, consumer, archiver);
        order.verify(outbox).abgeschlossen(eq(42L), any());
        order.verify(outbox).verarbeiteOffeneAnnahmeereignisse(consumer, 100);
        order.verify(archiver).archiviere(null, new byte[] {4});
    }
}
