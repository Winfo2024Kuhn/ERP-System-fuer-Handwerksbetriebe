package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AngebotDokument;
import org.example.kalkulationsprogramm.domain.AngebotGeschaeftsdokument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AngebotDokumentRepository extends JpaRepository<AngebotDokument, Long> {
    List<AngebotDokument> findByAngebotId(Long angebotId);

    @Query("SELECT g FROM AngebotGeschaeftsdokument g")
    List<AngebotGeschaeftsdokument> findAllGeschaeftsdokumente();

    @Query("SELECT g FROM AngebotGeschaeftsdokument g WHERE g.geschaeftsdokumentart = 'Rechnung'")
    List<AngebotGeschaeftsdokument> findOffeneGeschaeftsdokumente();

    Optional<AngebotDokument> findByGespeicherterDateiname(String gespeicherterDateiname);

    Optional<AngebotDokument> findByGespeicherterDateinameIgnoreCase(String gespeicherterDateiname);
}
