package com.decksuggester.auth;

import com.decksuggester.testsupport.EmbeddedMongoSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises remember-me end to end through the real {@code AuthController}
 * and security filter chain: a "remember me" login must let the browser
 * come back authenticated on a brand new HTTP session (standing in for the
 * application having restarted and dropped its in-memory sessions), while
 * an unchecked login must not leave any such cookie behind.
 */
@SpringBootTest(properties = "app.migrations.enabled=false")
class RememberMeAuthenticationTests {

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("mongodb.host", EmbeddedMongoSupport::connectionString);
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rememberMeCookieAuthenticatesARequestThatCarriesNoSession() throws Exception {
        register("remember-me-user", "a-very-long-password", "remember-me-user@example.com");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"remember-me-user","password":"a-very-long-password","rememberMe":true}"""))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rememberMeCookie = loginResult.getResponse().getCookie("remember-me");
        assertThat(rememberMeCookie).isNotNull();
        assertThat(rememberMeCookie.getMaxAge()).isEqualTo(24 * 60 * 60);

        // No JSESSIONID is sent, simulating the application having restarted
        // (and its in-memory session store with it) since the browser logged in.
        mockMvc.perform(get("/api/v1/auth/session").cookie(rememberMeCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("remember-me-user"));
    }

    @Test
    void uncheckingRememberMeLeavesNoCookieBehind() throws Exception {
        register("no-remember-user", "another-long-password", "no-remember-user@example.com");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"no-remember-user","password":"another-long-password","rememberMe":false}"""))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(loginResult.getResponse().getCookie("remember-me")).isNull();
    }

    @Test
    void omittingRememberMeDefaultsToOn() throws Exception {
        register("default-remember-user", "yet-another-long-password", "default-remember-user@example.com");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"default-remember-user","password":"yet-another-long-password"}"""))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(loginResult.getResponse().getCookie("remember-me")).isNotNull();
    }

    private void register(String username, String password, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","email":"%s"}"""
                                .formatted(username, password, email)))
                .andExpect(status().isCreated());
    }
}
