package org.example.kalkulationsprogramm.dto.Email;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import lombok.Data;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.domain.EmailTextTemplateKategorie;
import org.example.kalkulationsprogramm.service.EmailTextTemplateKategorien;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailTextTemplateDto {

    private Long id;

    @NotBlank
    private String dokumentTyp;

    /**
     * Fachliche Gruppierung im UI. Optional — wenn das Frontend keinen Wert
     * mitschickt, leitet {@link #applyToEntity(EmailTextTemplate)} die
     * Kategorie aus dem Dokumenttyp ab.
     */
    private EmailTextTemplateKategorie kategorie;

    @NotBlank
    private String name;

    @NotBlank
    private String subjectTemplate;

    @NotBlank
    private String htmlBody;

    private Boolean aktiv;

    private Boolean standard;
    private Long version;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static EmailTextTemplateDto fromEntity(EmailTextTemplate entity) {
        EmailTextTemplateDto dto = new EmailTextTemplateDto();
        dto.setId(entity.getId());
        dto.setDokumentTyp(entity.getDokumentTyp());
        dto.setKategorie(entity.getKategorie() != null
                ? entity.getKategorie()
                : EmailTextTemplateKategorien.kategorieFuer(entity.getDokumentTyp()));
        dto.setName(entity.getName());
        dto.setSubjectTemplate(entity.getSubjectTemplate());
        dto.setHtmlBody(entity.getHtmlBody());
        dto.setAktiv(entity.isAktiv());
        dto.setStandard(entity.isStandard());
        dto.setVersion(entity.getVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public void applyToEntity(EmailTextTemplate entity) {
        if (dokumentTyp != null) {
            entity.setDokumentTyp(dokumentTyp.trim().toUpperCase());
        }
        // Falls das Frontend keine Kategorie mitschickt (z.B. weil die Vorlage
        // mit einer aelteren Frontend-Version angelegt wird), leiten wir sie
        // aus dem Dokumenttyp ab — so bleibt die Gruppierung im UI konsistent
        // ohne dass DB-Zeilen unkategorisiert herumliegen.
        entity.setKategorie(
                kategorie != null
                        ? kategorie
                        : EmailTextTemplateKategorien.kategorieFuer(entity.getDokumentTyp()));
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
