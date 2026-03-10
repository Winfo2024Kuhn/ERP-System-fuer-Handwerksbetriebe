package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KundeEmailAttachmentDto {
    private Long id;
    private String filename;
    private String url;
}
