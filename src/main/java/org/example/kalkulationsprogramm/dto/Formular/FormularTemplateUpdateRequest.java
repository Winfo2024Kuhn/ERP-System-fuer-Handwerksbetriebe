package org.example.kalkulationsprogramm.dto.Formular;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FormularTemplateUpdateRequest {

    @NotBlank(message = "Der Vorlageninhalt darf nicht leer sein.")
    private String html;
}
