package org.example.kalkulationsprogramm.dto.Formular;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FormularTemplateSaveRequest {
    @NotBlank(message = "Vorlagenname darf nicht leer sein.")
    @Size(max = 100, message = "Vorlagenname darf maximal 100 Zeichen lang sein.")
    private String name;

    @NotBlank(message = "Vorlageninhalt darf nicht leer sein.")
    private String html;

    private List<String> assignedDokumenttypen;

}
