package org.example.kalkulationsprogramm.repository;

import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class SpamTokenCountRepositoryErweiterungImpl implements SpamTokenCountRepositoryErweiterung {

    private static final String UPSERT = "INSERT INTO spam_token_count (token, spam_count, ham_count) VALUES (:token, :spamInc, :hamInc) "
            + "ON CONFLICT (token) DO UPDATE SET spam_count = spam_token_count.spam_count + :spamInc, "
            + "ham_count = spam_token_count.ham_count + :hamInc";

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void upsertToken(String token, int spamIncrement, int hamIncrement) {
        entityManager.createNativeQuery(UPSERT)
                .setParameter("token", token)
                .setParameter("spamInc", spamIncrement)
                .setParameter("hamInc", hamIncrement)
                .executeUpdate();
    }
}
