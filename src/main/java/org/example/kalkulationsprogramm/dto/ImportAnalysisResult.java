package org.example.kalkulationsprogramm.dto;

import lombok.Data;
import java.util.List;

@Data
public class ImportAnalysisResult {
    private int existingCount;
    private int newCount;
    private List<String> newArticleExamples;
}
