package org.example.kalkulationsprogramm.dto.ProjektEmail;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjektEmailFileDto {
    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String contentId;
    private boolean inline;
    private String url; // Download-URL für das Attachment
}
