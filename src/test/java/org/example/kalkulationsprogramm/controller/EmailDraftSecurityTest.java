package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.service.EmailDraftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailDraftController.class)
@Import({SecurityConfig.class, EmailDraftSecurityTest.FilterBeans.class})
class EmailDraftSecurityTest {
    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }
    @Autowired private MockMvc mvc;
    @MockBean private EmailDraftService service;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;

    @Test
    void anonymousUsersCannotReadDraftsOrDownloadTheirFiles() throws Exception {
        mvc.perform(get("/api/emails/drafts/42")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/emails/drafts/42/attachments/7")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void csrfTokenAloneCannotAuthorizeAnUpload() throws Exception {
        mvc.perform(multipart("/api/emails/drafts")
                .file(new MockMultipartFile("dto", "", "application/json", "{}".getBytes())).with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void authenticatedWritesStillRequireCsrf() throws Exception {
        mvc.perform(multipart("/api/emails/drafts")
                .file(new MockMultipartFile("dto", "", "application/json", "{}".getBytes())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/emails/drafts/42")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void authenticatedUserCanDownloadTheirTeamsDraftAttachment() throws Exception {
        when(service.download(42L, 7L)).thenReturn(new EmailDraftService.Download("plan.pdf", "application/pdf", new byte[] {1, 2, 3}));
        mvc.perform(get("/api/emails/drafts/42/attachments/7"))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[] {1, 2, 3}));
    }
}
