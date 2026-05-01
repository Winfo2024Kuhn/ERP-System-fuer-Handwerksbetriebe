package org.example.kalkulationsprogramm.dto.Email;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import lombok.Data;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailTextTemplateDto {

    private Long id;

    @NotBlank
    private String dokumentTyp;

    @NotBlank
    private String name;

    @NotBlank
    private String subjectTemplate;

    @NotBlank
    private String htmlBody;

    private Boolean aktiv;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static EmailTextTemplateDto fromEntity(EmailTextTemplate entity) {
        EmailTextTemplateDto dto = new EmailTextTemplateDto();
        dto.setId(entity.getId());
        dto.setDokumentTyp(entity.getDokumentTyp());
        dto.setName(entity.getName());
        dto.setSubjectTemplate(entity.getSubjectTemplate());
        dto.setHtmlBody(entity.getHtmlBody());
        dto.setAktiv(entity.isAktiv());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public void applyToEntity(EmailTextTemplate entity) {
        if (dokumentTyp != null) {
            entity.setDokumentTyp(dokumentTyp.trim().toUpperCase());
        }
        if (name != null) {
            entity.setName(name.trim());
        }
        if (subjectTemplate != null) {
            entity.setSubjectTemplate(subjectTemplate);
        }
        if (htmlBody != null) {
            entity.setHtmlBody(htmlBody);
        }
        if (aktiv != null) {
            entity.setAktiv(aktiv);
        }
    }
}
