package org.example.kalkulationsprogramm.dto.AnfrageEmail;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AnfrageEmailAttachmentDto {
    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String contentId;
    private boolean inline;
}
