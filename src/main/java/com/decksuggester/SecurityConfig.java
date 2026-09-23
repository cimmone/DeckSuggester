package com.decksuggester;

import com.decksuggester.auth.UserAccountService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.time.Duration;

import static org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;

@Configuration
public class SecurityConfig {

    /** PBKDF2 password hashing with an application-level pepper mixed into Spring Security's own salt. */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${auth.password-salt}") String applicationSalt) {
        return new Pbkdf2PasswordEncoder(applicationSalt, 16, 310_000, PBKDF2WithHmacSHA256);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserAccountService userAccountService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userAccountService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Hash-based (stateless) remember-me: the cookie is a signed
     * username/expiry/password-hash token, so no server-side token store is
     * needed and changing a password invalidates every outstanding cookie.
     */
    @Bean
    public RememberMeServices rememberMeServices(@Value("${auth.remember-me-key}") String rememberMeKey,
                                                  UserAccountService userAccountService) {
        TokenBasedRememberMeServices rememberMeServices =
                new TokenBasedRememberMeServices(rememberMeKey, userAccountService);
        rememberMeServices.setTokenValiditySeconds((int) Duration.ofHours(24).toSeconds());
        // AuthController decides whether to remember (from the request body, a
        // JSON POST with no query/form "remember-me" parameter for the default
        // servlet-parameter check to find), so skip that check and always set
        // the cookie when loginSuccess() is actually invoked.
        rememberMeServices.setAlwaysRemember(true);
        return rememberMeServices;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    SecurityContextRepository contexts,
                                                    RememberMeServices rememberMeServices)
            throws Exception {
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookiePath("/");
        csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));

        http
                .securityContext(context -> context
                        .securityContextRepository(contexts)
                        .requireExplicitSave(true))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .ignoringRequestMatchers("/api/v1/auth/login", "/api/v1/auth/register",
                                "/api/v1/auth/password-reset/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/ui", "/ui/", "/ui/**", "/error",
                                "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/logout",
                                "/api/v1/auth/session", "/api/v1/auth/password-reset/**").permitAll()
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.disable())
                .rememberMe(rememberMe -> rememberMe.rememberMeServices(rememberMeServices))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"status\":401,\"message\":\"Authentication is required\"}");
                        }).accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"status\":403,\"message\":\"The request was not authorized\"}");
                        }));
        return http.build();
    }
}
