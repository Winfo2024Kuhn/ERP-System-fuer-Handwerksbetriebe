package org.example.kalkulationsprogramm.dto.Formular;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FormularTemplateSelectionRequest {
    @NotBlank
    private String dokumenttyp;

    @NotBlank
    private String templateName;

    private Long userId;
    private java.util.List<Long> userIds;
}
