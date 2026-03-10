package org.example.kalkulationsprogramm.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;

@Converter(autoApply = true)
public class LieferantDokumentTypConverter implements AttributeConverter<LieferantDokumentTyp, String> {

    @Override
    public String convertToDatabaseColumn(LieferantDokumentTyp attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public LieferantDokumentTyp convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        
        // Legacy-Mapping
        if ("EINGANGSRECHNUNG".equalsIgnoreCase(dbData.trim())) {
            return LieferantDokumentTyp.RECHNUNG;
        }

        try {
            return LieferantDokumentTyp.valueOf(dbData.trim());
        } catch (IllegalArgumentException e) {
            // Unbekannter Typ: Loggen oder null zurückgeben
            // Wir geben null zurück, damit die Anwendung nicht abstürzt
            return null;
        }
    }
}
