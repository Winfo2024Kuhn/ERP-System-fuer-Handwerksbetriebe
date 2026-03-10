package org.example.kalkulationsprogramm.tools;

import org.example.kalkulationsprogramm.KalkulationsprogrammApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class EmailHtmlBackfillMain {

    private EmailHtmlBackfillMain() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(KalkulationsprogrammApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        try {
            context.getBean(EmailHtmlBackfillRunner.class).run();
        } finally {
            context.close();
        }
    }
}
