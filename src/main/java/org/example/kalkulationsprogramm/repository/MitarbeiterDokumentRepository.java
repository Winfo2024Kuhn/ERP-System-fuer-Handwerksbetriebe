package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.MitarbeiterDokument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MitarbeiterDokumentRepository extends JpaRepository<MitarbeiterDokument, Long> {
    java.util.List<MitarbeiterDokument> findByMitarbeiterId(Long mitarbeiterId);
    
    // Für Dokument-Vorschau: Finde Dokument nach gespeichertem Dateinamen
    java.util.Optional<MitarbeiterDokument> findByGespeicherterDateiname(String gespeicherterDateiname);
}
