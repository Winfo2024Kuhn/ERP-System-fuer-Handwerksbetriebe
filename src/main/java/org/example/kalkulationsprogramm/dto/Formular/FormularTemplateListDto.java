package org.example.kalkulationsprogramm.dto.Formular;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormularTemplateListDto {
    private String name;
    private String created;
    private String modified;
    private List<String> assignedDokumenttypen;

}
