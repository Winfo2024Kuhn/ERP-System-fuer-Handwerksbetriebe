package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.FormularTemplateTextbausteinDefault;
import org.example.kalkulationsprogramm.domain.TextbausteinPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FormularTemplateTextbausteinDefaultRepository
        extends JpaRepository<FormularTemplateTextbausteinDefault, Long> {

    List<FormularTemplateTextbausteinDefault>
            findByTemplateNameIgnoreCaseOrderByDokumenttypAscPositionAscSortOrderAsc(String templateName);

    /**
     * JOIN FETCH auf textbaustein: die Aufrufer (Auto-Mahn-/Auftragsbestaetigungs-Versand)
     * greifen ausserhalb der Transaktion auf Textbaustein-Felder zu (z.B. getHtml()).
     * Ohne Fetch liefert Hibernate wegen FetchType.LAZY nur einen Proxy zurueck,
     * dessen Zugriff dann mit LazyInitializationException scheitert.
     */
    @Query("SELECT f FROM FormularTemplateTextbausteinDefault f "
            + "JOIN FETCH f.textbaustein "
            + "WHERE LOWER(f.templateName) = LOWER(:templateName) "
            + "AND f.dokumenttyp = :dokumenttyp AND f.position = :position "
            + "ORDER BY f.sortOrder ASC")
    List<FormularTemplateTextbausteinDefault>
            findByTemplateNameIgnoreCaseAndDokumenttypAndPositionOrderBySortOrderAsc(
                    @Param("templateName") String templateName,
                    @Param("dokumenttyp") Dokumenttyp dokumenttyp,
                    @Param("position") TextbausteinPosition position);

    List<FormularTemplateTextbausteinDefault>
            findByTemplateNameIgnoreCaseAndDokumenttypOrderByPositionAscSortOrderAsc(
                    String templateName, Dokumenttyp dokumenttyp);

    @Transactional
    void deleteByTemplateNameIgnoreCaseAndDokumenttyp(String templateName, Dokumenttyp dokumenttyp);

    @Transactional
    void deleteByTemplateNameIgnoreCase(String templateName);
}
