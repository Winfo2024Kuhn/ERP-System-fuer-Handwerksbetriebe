package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAudit;
import org.springframework.data.repository.Repository;

public interface EinkaufAuditRepository extends Repository<EinkaufAudit, Long> {
    java.util.List<EinkaufAudit> findByVorgangTypAndVorgangIdAndAktion(String vorgangTyp, Long vorgangId, String aktion);
    EinkaufAudit save(EinkaufAudit audit);
}
