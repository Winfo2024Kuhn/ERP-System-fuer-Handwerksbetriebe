package org.example.kalkulationsprogramm.dto.Formular;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FormularTemplateRenameRequest {
    @NotBlank(message = "Neuer Vorlagenname darf nicht leer sein.")
    @Size(max = 100, message = "Vorlagenname darf maximal 100 Zeichen lang sein.")
    private String newName;
}
