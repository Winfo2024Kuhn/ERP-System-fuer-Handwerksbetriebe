package org.example.kalkulationsprogramm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Uhr für die Telefon-Anbindung. Die FRITZ!Box liefert Ortszeit ohne Zone,
 * deshalb rechnet das ERP hier fest in Europe/Berlin. In Tests austauschbar.
 */
@Configuration
public class TelefonKonfiguration {

    public static final ZoneId ZEITZONE = ZoneId.of("Europe/Berlin");

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.system(ZEITZONE);
    }
}
