package org.example.kalkulationsprogramm.config;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.example.kalkulationsprogramm.service.PostfachUmzugService;
import org.junit.jupiter.api.Test;

class PostfachUmzugRunnerTest {

    @Test
    void ziehtBeimStartUm() {
        PostfachUmzugService umzug = mock(PostfachUmzugService.class);

        new PostfachUmzugRunner(umzug).run(null);

        verify(umzug).ziehUm();
    }

    @Test
    void fehlerVerhindertDenStartNicht() {
        PostfachUmzugService umzug = mock(PostfachUmzugService.class);
        doThrow(new IllegalStateException("Datenbank weg")).when(umzug).ziehUm();

        new PostfachUmzugRunner(umzug).run(null);

        verify(umzug).ziehUm();
    }
}
