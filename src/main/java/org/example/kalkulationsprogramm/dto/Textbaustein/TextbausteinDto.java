package org.example.kalkulationsprogramm.dto.Textbaustein;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.domain.TextbausteinTyp;

@Getter
@Setter
@NoArgsConstructor
public class TextbausteinDto {
    private Long id;

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    @Size(max = 40)
    private String typ;

    @Size(max = 500)
    private String beschreibung;

    private String html;

    private List<String> placeholders = new ArrayList<>();

    private List<String> dokumenttypen = new ArrayList<>();

    private Integer sortOrder;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static TextbausteinDto fromEntity(Textbaustein entity) {
        TextbausteinDto dto = new TextbausteinDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setTyp(entity.getTyp() != null ? entity.getTyp().name() : TextbausteinTyp.FREITEXT.name());
        dto.setBeschreibung(entity.getBeschreibung());
        dto.setHtml(entity.getHtml());
        dto.setSortOrder(entity.getSortOrder());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setPlaceholders(new ArrayList<>(entity.getPlaceholders()));
        dto.setDokumenttypen(entity.getDokumenttypen().stream()
                .filter(Objects::nonNull)
                .map(Dokumenttyp::getLabel)
                .distinct()
                .toList());
        return dto;
    }

    public void applyToEntity(Textbaustein entity) {
        entity.setName(name != null ? name.trim() : "");
        entity.setTyp(TextbausteinTyp.fromString(typ));
        entity.setBeschreibung(beschreibung);
        entity.setHtml(html);
        entity.setSortOrder(sortOrder);
        Set<String> normalized = new LinkedHashSet<>();
        if (placeholders != null) {
            placeholders.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(String::trim)
                    .forEach(normalized::add);
        }
        entity.setPlaceholders(normalized);
    }
}
