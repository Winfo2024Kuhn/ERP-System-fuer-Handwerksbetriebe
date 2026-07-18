package org.example.kalkulationsprogramm.repository

import org.example.kalkulationsprogramm.domain.Dokumenttyp
import org.example.kalkulationsprogramm.domain.FormularTemplateTextbausteinDefault
import org.example.kalkulationsprogramm.domain.TextbausteinPosition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface FormularTemplateTextbausteinDefaultRepository : JpaRepository<FormularTemplateTextbausteinDefault, Long> {
    fun findByTemplateNameIgnoreCaseOrderByDokumenttypAscPositionAscSortOrderAsc(
        templateName: String,
    ): List<FormularTemplateTextbausteinDefault>

    @Query(
        """
            SELECT f FROM FormularTemplateTextbausteinDefault f
            JOIN FETCH f.textbaustein
            WHERE LOWER(f.templateName) = LOWER(:templateName)
              AND f.dokumenttyp = :dokumenttyp
              AND f.position = :position
            ORDER BY f.sortOrder ASC
            """,
    )
    fun findByTemplateNameIgnoreCaseAndDokumenttypAndPositionOrderBySortOrderAsc(
        @Param("templateName") templateName: String,
        @Param("dokumenttyp") dokumenttyp: Dokumenttyp,
        @Param("position") position: TextbausteinPosition,
    ): List<FormularTemplateTextbausteinDefault>

    fun findByTemplateNameIgnoreCaseAndDokumenttypOrderByPositionAscSortOrderAsc(
        templateName: String,
        dokumenttyp: Dokumenttyp,
    ): List<FormularTemplateTextbausteinDefault>

    @Transactional
    fun deleteByTemplateNameIgnoreCaseAndDokumenttyp(templateName: String, dokumenttyp: Dokumenttyp)

    @Transactional
    fun deleteByTemplateNameIgnoreCase(templateName: String)
}
