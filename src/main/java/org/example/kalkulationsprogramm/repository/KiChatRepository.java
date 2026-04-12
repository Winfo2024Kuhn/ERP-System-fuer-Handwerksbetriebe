package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.KiChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KiChatRepository extends JpaRepository<KiChat, Long> {

    List<KiChat> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
