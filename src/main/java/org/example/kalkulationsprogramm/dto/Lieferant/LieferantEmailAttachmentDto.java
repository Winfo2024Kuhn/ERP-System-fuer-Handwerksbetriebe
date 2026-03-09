package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LieferantEmailAttachmentDto {
    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String contentId;
    private boolean inline;
}
