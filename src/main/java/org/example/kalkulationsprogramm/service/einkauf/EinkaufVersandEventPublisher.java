package org.example.kalkulationsprogramm.service.einkauf;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class EinkaufVersandEventPublisher implements EinkaufVersandDispatchPublisher {
    private final ApplicationEventPublisher eventPublisher;

    public EinkaufVersandEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(Long versandauftragId) {
        eventPublisher.publishEvent(new EinkaufVersandWorker.VersandauftragDispatch(versandauftragId));
    }
}
