package org.example.kalkulationsprogramm.service.einkauf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.ArchivErgebnis;
import org.example.kalkulationsprogramm.service.mail.KontoMailTransport;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.example.kalkulationsprogramm.service.mail.SentMailArchiver;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EinkaufVersandWorker {
    private final EinkaufOutboxService outbox;
    private final MailkontoService konten;
    private final KontoMailTransport transport;
    private final SentMailArchiver archiver;
    private final LocalTestMailPolicy localTestMailPolicy;
    private final List<EinkaufAnnahmeereignisConsumer> acceptanceConsumers;
    private final EinkaufVersandDispatchPublisher dispatchPublisher;
    @Value("${app.background-jobs.enabled:true}")
    private boolean backgroundJobsEnabled;

    /** May be called immediately after the user has approved a prepared order. */
    @Async
    public void verarbeite(Long auftragId) {
        if (auftragId == null || auftragId <= 0) return;
        // EINKAUF is the only allowed account; check policy before claiming or resolving it.
        localTestMailPolicy.pruefeNetzwerkzugriff("EINKAUF");
        EinkaufOutboxService.Claim claim = outbox.beanspruche(auftragId);
        if (claim == null) return;
        org.example.kalkulationsprogramm.service.mail.MailkontoService.KontoZugang konto;
        try {
            konto = konten.resolve(claim.kontoId());
        } catch (RuntimeException ex) {
            if (claim.archivRetry()) {
                outbox.archivErgebnis(claim.id(), new ArchivErgebnis(false, "KONTO_NICHT_VERFUEGBAR"));
            } else {
                outbox.abgeschlossen(claim.id(), new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Versandergebnis(
                        org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.SICHER_FEHLGESCHLAGEN,
                        null, "KONTO_NICHT_VERFUEGBAR", claim.mime()));
            }
            log.info("[EinkaufOutbox] Versandauftrag {} vor SMTP sicher fehlgeschlagen", claim.id());
            return;
        }
        if (claim.archivRetry()) {
            ArchivErgebnis archiveResult = archiver.archiviere(konto, claim.mime());
            outbox.archivErgebnis(claim.id(), archiveResult);
            log.info("[EinkaufOutbox] Archiv-Retry {} Status {}", claim.id(), archiveResult.erfolgreich());
            return;
        }
        var result = transport.sendenVorbereitet(konto, claim.mime());
        outbox.abgeschlossen(claim.id(), result);
        if (result.status() == org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.ANGENOMMEN) {
            verarbeiteAnnahmeereignisse();
            EinkaufOutboxService.Claim archiveClaim = outbox.beanspruche(claim.id());
            if (archiveClaim != null && archiveClaim.archivRetry()) {
                ArchivErgebnis archiveResult = archiver.archiviere(konto, archiveClaim.mime());
                outbox.archivErgebnis(claim.id(), archiveResult);
            }
        }
        log.info("[EinkaufOutbox] Versandauftrag {} Status {}", claim.id(), result.status());
    }

    public void dispatchNachCommit(Long auftragId) {
        if (auftragId == null || auftragId <= 0) return;
        Runnable dispatch = () -> dispatchPublisher.publish(auftragId);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { dispatch.run(); }
                    });
        } else {
            dispatch.run();
        }
    }

    @Async
    @EventListener
    public void verarbeiteDispatch(VersandauftragDispatch event) {
        if (event != null) verarbeite(event.auftragId());
    }

    /** An interrupted SMTP DATA phase is uncertain after restart and needs documented human resolution. */
    @EventListener(ApplicationReadyEvent.class)
    public void markiereUnterbrocheneVersuche() {
        int count = outbox.markiereUnterbrocheneAlsUnklar();
        if (count > 0) log.warn("[EinkaufOutbox] {} unterbrochene Versandaufträge auf UNKLAR gesetzt", count);
        if (backgroundJobsEnabled) {
            verarbeiteVorbereiteteAuftraege();
            verarbeiteAnnahmeereignisse();
        }
    }

    private void verarbeiteAnnahmeereignisse() {
        for (EinkaufAnnahmeereignisConsumer consumer : acceptanceConsumers) {
            try {
                outbox.verarbeiteOffeneAnnahmeereignisse(consumer, 100);
            } catch (RuntimeException ex) {
                // SMTP ist bereits angenommen. A pending durable event is left for recovery; never retry SMTP here.
                log.error("[EinkaufOutbox] Annahmeereignis-Consumer {} fehlgeschlagen; bleibt zur Wiederverarbeitung offen",
                        consumer.getClass().getSimpleName(), ex);
            }
        }
    }

    /** Retries durable acceptance facts after transient database or application errors. */
    @Scheduled(fixedDelayString = "${einkauf.annahmeereignis.recovery-delay:30000}")
    public void wiederholeOffeneAnnahmeereignisse() {
        if (backgroundJobsEnabled) verarbeiteAnnahmeereignisse();
    }

    /** Dispatches durable VORBEREITET jobs on startup and after transient dispatch/process failures. */
    @Scheduled(fixedDelayString = "${einkauf.versand.recovery-delay:30000}")
    public void wiederholeVorbereiteteAuftraege() {
        if (backgroundJobsEnabled) verarbeiteVorbereiteteAuftraege();
    }

    private void verarbeiteVorbereiteteAuftraege() {
        try {
            for (Long id : outbox.findeVorbereiteteAuftraege(100)) dispatchNachCommit(id);
        } catch (RuntimeException ex) {
            log.error("[EinkaufOutbox] Recovery vorbereiteter Versandaufträge fehlgeschlagen", ex);
        }
    }

    public record VersandauftragDispatch(Long auftragId) {}
}
