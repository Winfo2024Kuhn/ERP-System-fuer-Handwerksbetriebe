package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.EmailDraft;
import org.example.kalkulationsprogramm.dto.Email.EmailDraftDto;
import org.example.kalkulationsprogramm.service.EmailDraftService;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(EmailDraftService.class)
class EmailDraftPersistenceTest {
    @Autowired private EmailDraftService service;
    @Autowired private EmailDraftRepository drafts;
    @Autowired private EmailDraftAttachmentRepository attachments;
    @Autowired private EntityManager entityManager;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private EmailDraftDto content() {
        return new EmailDraftDto(null, "test@example.com", "kopie@example.com", "Plan", "<p>Bitte prüfen.</p>",
                "betrieb@example.com", 12L, 34L, null, true, null, null, null);
    }

    @Test
    void metadataAndBinaryRoundtripThenRemovalAndDeletion() {
        byte[] bytes = new byte[] {0, 1, 2, -1, 10, 13};
        var created = service.save(null, content(), List.of(new MockMultipartFile("attachments", "plan.pdf", "application/pdf", bytes)));
        Long id = created.id();
        entityManager.flush();
        entityManager.clear();

        var listed = service.list();
        assertThat(listed).hasSize(1);
        var listedDraft = listed.getFirst();
        assertThat(listedDraft.attachments()).hasSize(1);
        assertThat(listedDraft.attachments().getFirst().size()).isEqualTo(bytes.length);
        // A list of drafts must not initialize the collection containing all binary data.
        assertThat(Hibernate.isInitialized(entityManager.find(EmailDraft.class, id).getAttachments())).isFalse();
        var opened = service.get(id);
        assertThat(opened.cc()).isEqualTo("kopie@example.com");
        assertThat(opened.replyEmailId()).isEqualTo(12L);
        assertThat(opened.projektId()).isEqualTo(34L);
        assertThat(opened.geschaeftsdokument()).isTrue();
        assertThat(service.download(id, opened.attachments().getFirst().id()).data()).containsExactly(bytes);

        service.save(id, content(), List.of());
        entityManager.flush();
        entityManager.clear();
        assertThat(service.get(id).attachments()).isEmpty();
        assertThat(attachments.count()).isZero();

        service.save(id, content(), List.of(new MockMultipartFile("attachments", "neu.pdf", "application/pdf", bytes)));
        entityManager.flush();
        entityManager.clear();
        assertThat(service.get(id).attachments()).hasSize(1);
        service.delete(id);
        entityManager.flush();
        entityManager.clear();
        assertThat(drafts.existsById(id)).isFalse();
        assertThat(attachments.count()).isZero();
    }    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void successfulSendCleanupSurvivesRollbackOfLaterArchiving() {
        var created = service.save(null, content(), List.of(new MockMultipartFile("attachments", "plan.pdf", "application/pdf", new byte[] {1, 2})));
        Long id = created.id();
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                service.validateForSending(id, 12L);
                service.deleteAfterSuccessfulSend(id);
                transaction.setRollbackOnly(); // Simulates a later failure while archiving the sent email.
            });
            assertThat(drafts.existsById(id)).isFalse();
            assertThat(attachments.count()).isZero();
        } finally {
            if (drafts.existsById(id)) service.delete(id);
        }
    }

}
