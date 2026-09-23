package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistiert eine selbst versandte (OUT) Email in einer separaten
 * Sub-Transaktion. Sinn: Aufrufer wie {@code AnfrageFunnelService} laufen
 * unter einer eigenen umschliessenden {@code @Transactional}, in der bereits
 * die fachlichen Daten (Anfrage, Notiz) angelegt wurden. Wuerde die
 * Email-Persistenz in derselben Transaktion scheitern (z.B.
 * {@code DataIntegrityViolationException} auf dem Unique-Index der
 * Message-ID, weil der IMAP-Sent-Scheduler die Mail Millisekunden vorher
 * importiert hat), markiert Hibernate die umfassende Transaktion als
 * rollback-only und der Funnel verliert den Lead — obwohl die SMTP-Mail
 * beim Kunden bereits angekommen ist.
 *
 * <p>Mit {@link Propagation#REQUIRES_NEW} laeuft jeder save in einer eigenen
 * Tx + Connection. {@code noRollbackFor=Exception.class} verhindert
 * Cross-Tx-Poisoning. Der Aufrufer schluckt den Fehler — der IMAP-Sent-Poll
 * legt die Mail spaeter ohnehin an (Sicherheitsnetz via Message-ID-Dedupe).
 */
@Service
@RequiredArgsConstructor
public class EmailOutboundPersistenceService {

    private final EmailRepository emailRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
    public void speichereOutEmail(Email email) {
        email.setKontoId("HAUPT");
        emailRepository.save(email);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
    public void speichereOutEmail(Email email, String kontoId) {
        email.setKontoId(kontoId == null || kontoId.isBlank() ? "HAUPT" : kontoId);
        emailRepository.save(email);
    }

    /**
     * Auch der Dedupe-Lookup laeuft in einer eigenen Sub-Tx — ein Connection-Loss
     * hier darf die umfassende Funnel-Tx genauso wenig poisonieren wie der save.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, noRollbackFor = Exception.class)
    public boolean existsByMessageId(String messageId) {
        return emailRepository.existsByKontoIdAndMessageId("HAUPT", messageId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, noRollbackFor = Exception.class)
    public boolean existsByMessageId(String kontoId, String messageId) {
        return emailRepository.existsByKontoIdAndMessageId(kontoId == null ? "HAUPT" : kontoId, messageId);
    }
}
