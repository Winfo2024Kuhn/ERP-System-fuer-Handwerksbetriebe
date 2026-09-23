package org.example.kalkulationsprogramm.service.einkauf;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EinkaufAnalyseWorker {
    public record AnalyseAngefordert(Long jobId) {}
    private final EinkaufAngebotsAnalyseService analyse;
    public EinkaufAnalyseWorker(EinkaufAngebotsAnalyseService analyse) { this.analyse = analyse; }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void nachCommit(AnalyseAngefordert event) {
        if (event == null || event.jobId() == null || event.jobId() <= 0) return;
        analyse.analysiere(event.jobId());
    }
}
