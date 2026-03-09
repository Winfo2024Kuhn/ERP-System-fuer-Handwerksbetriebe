package org.example.kalkulationsprogramm.dto.Email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailCenterAttachmentDto {
    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String contentId;
    private boolean inline;
}

