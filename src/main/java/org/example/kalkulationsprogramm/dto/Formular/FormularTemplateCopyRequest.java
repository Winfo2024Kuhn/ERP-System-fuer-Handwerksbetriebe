package org.example.kalkulationsprogramm.dto.Formular;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FormularTemplateCopyRequest {
    @NotBlank(message = "Neuer Vorlagenname darf nicht leer sein.")
    private String newName;
}
