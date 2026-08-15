package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ArtikelDokument;
import org.example.kalkulationsprogramm.domain.ArtikelDokumentTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtikelDokumentRepository extends JpaRepository<ArtikelDokument, Long> {
    List<ArtikelDokument> findByArtikelIdOrderBySortierungAscIdAsc(Long artikelId);

    Optional<ArtikelDokument> findFirstByArtikelIdAndTyp(Long artikelId, ArtikelDokumentTyp typ);

    /**
     * Vorschaubilder mehrerer Artikel in einem Rutsch - Grundlage fuer die
     * Trefferliste, die sonst je Zeile eine eigene Abfrage bräuchte (N+1).
     */
    List<ArtikelDokument> findByArtikelIdInAndTyp(List<Long> artikelIds, ArtikelDokumentTyp typ);
}
