package org.example.kalkulationsprogramm.dto.Projekt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjektEmailAttachmentDto {
    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String contentId;
    private boolean inline;
}
