package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AbteilungDokumentBerechtigung;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbteilungDokumentBerechtigungRepository extends JpaRepository<AbteilungDokumentBerechtigung, Long> {

    List<AbteilungDokumentBerechtigung> findByAbteilungId(Long abteilungId);

    Optional<AbteilungDokumentBerechtigung> findByAbteilungIdAndDokumentTyp(Long abteilungId, LieferantDokumentTyp dokumentTyp);

    @NativeQuery("SELECT dokument_typ FROM abteilung_dokument_berechtigung WHERE abteilung_id = :abteilungId AND darf_sehen = true")
    List<String> findSichtbareTypenByAbteilungId(@Param("abteilungId") Long abteilungId);

    @NativeQuery("SELECT dokument_typ FROM abteilung_dokument_berechtigung WHERE abteilung_id = :abteilungId AND darf_scannen = true")
    List<String> findScanbarTypenByAbteilungId(@Param("abteilungId") Long abteilungId);

    @NativeQuery("SELECT dokument_typ FROM abteilung_dokument_berechtigung WHERE abteilung_id IN :abteilungIds AND darf_sehen = true")
    List<String> findSichtbareTypenByAbteilungIds(@Param("abteilungIds") List<Long> abteilungIds);

    @NativeQuery("SELECT dokument_typ FROM abteilung_dokument_berechtigung WHERE abteilung_id IN :abteilungIds AND darf_scannen = true")
    List<String> findScanbarTypenByAbteilungIds(@Param("abteilungIds") List<Long> abteilungIds);
}
