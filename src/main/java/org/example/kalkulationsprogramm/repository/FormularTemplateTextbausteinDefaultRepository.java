package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.FormularTemplateTextbausteinDefault;
import org.example.kalkulationsprogramm.domain.TextbausteinPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FormularTemplateTextbausteinDefaultRepository
        extends JpaRepository<FormularTemplateTextbausteinDefault, Long> {

    List<FormularTemplateTextbausteinDefault>
            findByTemplateNameIgnoreCaseOrderByDokumenttypAscPositionAscSortOrderAsc(String templateName);

    List<FormularTemplateTextbausteinDefault>
            findByTemplateNameIgnoreCaseAndDokumenttypAndPositionOrderBySortOrderAsc(
                    String templateName, Dokumenttyp dokumenttyp, TextbausteinPosition position);

    List<FormularTemplateTextbausteinDefault>
            findByTemplateNameIgnoreCaseAndDokumenttypOrderByPositionAscSortOrderAsc(
                    String templateName, Dokumenttyp dokumenttyp);

    @Transactional
    void deleteByTemplateNameIgnoreCaseAndDokumenttyp(String templateName, Dokumenttyp dokumenttyp);

    @Transactional
    void deleteByTemplateNameIgnoreCase(String templateName);
}
