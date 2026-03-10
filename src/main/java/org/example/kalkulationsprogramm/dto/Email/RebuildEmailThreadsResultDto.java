package org.example.kalkulationsprogramm.dto.Email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RebuildEmailThreadsResultDto {
    private int processed;
    private int relinked;
    private int cleared;
}
