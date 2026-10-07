package com.personal.finance.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security behaviour at the HTTP layer (SEC-03, SEC-06, single user policy).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAllInBatch();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"username":"user","password":"Str0ng!Pass","displayName":"User"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyOneUserCanRegister() throws Exception {
        register("user");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"other","password":"Str0ng!Pass","displayName":"Other"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void fiveFailedLoginsLockTheAccount() throws Exception {
        register("user");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .param("username", "user")
                            .param("password", "wrong-password"))
                    .andExpect(status().isUnauthorized());
        }

        // Even the correct password is rejected while the lock lasts.
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", "user")
                        .param("password", "Str0ng!Pass"))
                .andExpect(status().isUnauthorized());
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"Str0ng!Pass","displayName":"User"}
                                """.formatted(username)))
                .andExpect(status().isCreated());
    }
}
