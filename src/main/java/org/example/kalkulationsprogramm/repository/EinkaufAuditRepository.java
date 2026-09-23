package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufAudit;
import org.springframework.data.repository.Repository;

public interface EinkaufAuditRepository extends Repository<EinkaufAudit, Long> {
    EinkaufAudit save(EinkaufAudit audit);
}
