package org.example.kalkulationsprogramm.dto.Formular;

import lombok.Data;

import java.util.List;

@Data
public class FormularTemplateDto {
    private String html;
    private String lastModified;
    private List<String> placeholders;
    private List<String> assignedDokumenttypen;
    private List<Long> assignedUserIds;
    private String name;
    private String created;
    private String modified;
}
