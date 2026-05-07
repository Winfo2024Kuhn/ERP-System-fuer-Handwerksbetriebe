package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnfrageDokumentRepository extends JpaRepository<AnfrageDokument, Long> {
    List<AnfrageDokument> findByAnfrageId(Long anfrageId);

    @Query("SELECT g FROM AnfrageGeschaeftsdokument g")
    List<AnfrageGeschaeftsdokument> findAllGeschaeftsdokumente();

    @Query("SELECT g FROM AnfrageGeschaeftsdokument g WHERE g.geschaeftsdokumentart = 'Rechnung'")
    List<AnfrageGeschaeftsdokument> findOffeneGeschaeftsdokumente();

    Optional<AnfrageDokument> findByGespeicherterDateiname(String gespeicherterDateiname);

    Optional<AnfrageDokument> findByGespeicherterDateinameIgnoreCase(String gespeicherterDateiname);

    /**
     * Liefert für eine Liste von Anfrage-IDs ein Mapping
     * [AnfrageGeschaeftsdokument.id, Anfrage.id], damit der DokumentFreigabeService
     * den jüngsten Freigabe-Status pro Anfrage finden kann.
     */
    @Query("SELECT g.id, g.anfrage.id FROM AnfrageGeschaeftsdokument g WHERE g.anfrage.id IN :anfrageIds")
    List<Object[]> findGeschaeftsdokumentIdMappingByAnfrageIds(@org.springframework.data.repository.query.Param("anfrageIds") List<Long> anfrageIds);
}
