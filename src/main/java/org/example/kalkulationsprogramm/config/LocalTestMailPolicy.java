package org.example.kalkulationsprogramm.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Fail-closed mail and vendor network policy for the local backup profile. */
@Component
public class LocalTestMailPolicy {
    private final boolean localTest;
    private final boolean manualMailEnabled;

    @Autowired
    public LocalTestMailPolicy(Environment environment) {
        this.localTest = environment.acceptsProfiles(Profiles.of("local-test"));
        this.manualMailEnabled = environment.getProperty("app.local-test.manual-mail.enabled", Boolean.class, false);
    }

    public void pruefeNetzwerkzugriff(String kontoId) {
        if (!localTest) {
            return;
        }
        if (!manualMailEnabled || !"EINKAUF".equals(kontoId)) {
            throw new IllegalStateException("Mail- und Anbieterzugriffe sind im lokalen Testprofil gesperrt.");
        }
    }
}
