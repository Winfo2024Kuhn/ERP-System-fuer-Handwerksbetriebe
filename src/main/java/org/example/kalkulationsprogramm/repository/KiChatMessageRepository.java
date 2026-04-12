package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.KiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KiChatMessageRepository extends JpaRepository<KiChatMessage, Long> {
}
