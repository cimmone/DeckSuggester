package com.decksuggester;

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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import static org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;

@Configuration
public class SecurityConfig {

    /**
     * Spring Security's own PBKDF2 encoder: it already salts every hash with a
     * fresh random value per password, and this configured secret is mixed in
     * on top as an application-level pepper.
     */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${auth.password-salt}") String applicationSalt) {
        return new Pbkdf2PasswordEncoder(applicationSalt, 16, 310_000, PBKDF2WithHmacSHA256);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserAccountService accountService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(accountService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    SecurityContextRepository contexts)
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
                        .ignoringRequestMatchers("/auth/login", "/auth/register",
                                "/auth/password-reset/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/ui", "/ui/", "/ui/**", "/error",
                                "/auth/login", "/auth/register", "/auth/logout",
                                "/auth/session", "/auth/password-reset/**").permitAll()
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.disable())
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
