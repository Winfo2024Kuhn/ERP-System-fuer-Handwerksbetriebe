package org.example.kalkulationsprogramm.service.einkauf;

import org.example.kalkulationsprogramm.service.EmailImportService.EinkaufEmailImportiert;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/** Defers linking until the email insert has committed; the service opens a separate write transaction. */
@Component
public class EinkaufImportMailListener {
    private final EinkaufImportZuordnungsService consumer;

    public EinkaufImportMailListener(EinkaufImportZuordnungsService consumer) {
        this.consumer = consumer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void nachImport(EinkaufEmailImportiert event) {
        if (event != null) consumer.verarbeiteImportZuordnung(event.emailId());
    }
}
