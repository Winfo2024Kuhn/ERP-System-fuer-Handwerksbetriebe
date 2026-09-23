package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.einkauf.EmailImportIdentitaet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailImportIdentitaetRepository extends JpaRepository<EmailImportIdentitaet, Long> {
    boolean existsByKontoIdAndFolderAndUidValidityAndUid(String kontoId, String folder, long uidValidity, long uid);
}
