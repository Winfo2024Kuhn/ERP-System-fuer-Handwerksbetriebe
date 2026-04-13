package org.example.kalkulationsprogramm.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gibt dem Frontend aktivierte Feature-Flags zurück.
 * Kein Auth erforderlich – liefert nur boolean-Flags.
 */
@RestController
@RequestMapping("/api/features")
public class FeatureFlagController {

    @Value("${en1090.features.enabled:false}")
    private boolean en1090Enabled;

    @Value("${echeck.features.enabled:false}")
    private boolean echeckEnabled;

    @Value("${email.features.enabled:true}")
    private boolean emailEnabled;

    @Value("${ai.rag.enabled:false}")
    private boolean ragEnabled;

    @GetMapping
    public Map<String, Boolean> getFeatures() {
        return Map.of(
                "en1090", en1090Enabled,
                "echeck", echeckEnabled || en1090Enabled, // echeck ist Teil von en1090
                "email", emailEnabled,
                "rag", ragEnabled
        );
    }
}
