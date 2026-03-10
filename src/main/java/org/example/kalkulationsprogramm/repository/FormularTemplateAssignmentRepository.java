package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.FormularTemplateAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormularTemplateAssignmentRepository extends JpaRepository<FormularTemplateAssignment, Long> {
    List<FormularTemplateAssignment> findByTemplateNameIgnoreCaseAndUser_Id(String templateName, Long userId);

    List<FormularTemplateAssignment> findByTemplateNameIgnoreCaseAndUserIsNull(String templateName);

    List<FormularTemplateAssignment> findByTemplateNameIgnoreCase(String templateName);

    void deleteByTemplateNameIgnoreCase(String templateName);

    void deleteByTemplateNameIgnoreCaseAndUser_Id(String templateName, Long userId);

    void deleteByTemplateNameIgnoreCaseAndUserIsNull(String templateName);

    Optional<FormularTemplateAssignment> findFirstByDokumenttypAndUser_IdOrderByIdDesc(Dokumenttyp dokumenttyp, Long userId);

    Optional<FormularTemplateAssignment> findFirstByDokumenttypAndUserIsNullOrderByIdDesc(Dokumenttyp dokumenttyp);

    void deleteByDokumenttypAndUser_Id(Dokumenttyp dokumenttyp, Long userId);

    void deleteByDokumenttypAndUserIsNull(Dokumenttyp dokumenttyp);

    void deleteByDokumenttyp(Dokumenttyp dokumenttyp);

    List<FormularTemplateAssignment> findByDokumenttyp(Dokumenttyp dokumenttyp);
}
