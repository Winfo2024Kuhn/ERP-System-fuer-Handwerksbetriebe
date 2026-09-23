package org.example.kalkulationsprogramm.service.einkauf;

/** Transactional API used by the after-commit listener and durable import recovery. */
public interface EinkaufImportZuordnungsService {
    void verarbeiteImportZuordnung(Long emailId);
    int verarbeiteOffeneImportZuordnungen(int limit);
}
