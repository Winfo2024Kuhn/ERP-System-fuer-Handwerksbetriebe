package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.OooReplyLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OooReplyLogRepository extends JpaRepository<OooReplyLog, Long> {

    boolean existsByScheduleIdAndSenderAddressIgnoreCase(Long scheduleId, String senderAddress);
}
