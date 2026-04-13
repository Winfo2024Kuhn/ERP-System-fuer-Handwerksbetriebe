package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.SpamTokenCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpamTokenCountRepository extends JpaRepository<SpamTokenCount, Long> {

    Optional<SpamTokenCount> findByToken(String token);

    List<SpamTokenCount> findByTokenIn(Collection<String> tokens);

    @Modifying
    @Query(value = "INSERT INTO spam_token_count (token, spam_count, ham_count) VALUES (:token, :spamInc, :hamInc) "
            + "ON DUPLICATE KEY UPDATE spam_count = spam_count + :spamInc, ham_count = ham_count + :hamInc",
            nativeQuery = true)
    void upsertToken(@Param("token") String token,
                     @Param("spamInc") int spamIncrement,
                     @Param("hamInc") int hamIncrement);

    /** Dekrementiert Spam- oder Ham-Zähler, klemmt bei 0 (verhindert negative Werte). */
    @Modifying
    @Query(value = "UPDATE spam_token_count SET "
            + "spam_count = GREATEST(0, spam_count - :spamDec), "
            + "ham_count  = GREATEST(0, ham_count  - :hamDec) "
            + "WHERE token = :token",
            nativeQuery = true)
    void decrementToken(@Param("token") String token,
                        @Param("spamDec") int spamDecrement,
                        @Param("hamDec") int hamDecrement);
}
