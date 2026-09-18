package com.decksuggester.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContexts;
    private final UserAccountService accountService;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContexts,
                          UserAccountService accountService) {
        this.authenticationManager = authenticationManager;
        this.securityContexts = securityContexts;
        this.accountService = accountService;
    }

    /**
     * Reports the CSRF header name the client must echo back, rather than
     * letting the client hardcode a guess: the repository's configured header
     * name (and the fetch client's assumption about it) can drift out of sync,
     * silently turning every POST/PUT/DELETE after login into a 403.
     */
    @GetMapping("/session")
    public SessionResponse session(Authentication authentication, CsrfToken csrfToken) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return new SessionResponse(true, principal.getUsername(), principal.libraryId(),
                    csrfToken.getHeaderName(), csrfToken.getToken());
        }
        return new SessionResponse(false, null, null,
                csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest login,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            login.username().trim(), login.password()));
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContexts.saveContext(context, request, response);
            AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
            return ResponseEntity.ok(new LoginResponse(principal.getUsername(),
                    principal.libraryId()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Username or password is invalid"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserAccount account = accountService.register(request.username(), request.password(),
                request.email());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LoginResponse(account.getUsername(), account.getLibraryId()));
    }

    @PostMapping("/password-reset/request")
    public MessageResponse requestPasswordReset(@Valid @RequestBody ResetRequest request) {
        accountService.requestPasswordReset(request.account());
        return new MessageResponse(
                "If the account exists, its password reset URL was written to the application logs");
    }

    @PostMapping("/password-reset/confirm")
    public MessageResponse resetPassword(@Valid @RequestBody ResetConfirmation request) {
        accountService.resetPassword(request.token(), request.password());
        return new MessageResponse("Password updated. You can now sign in");
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 256) String password) {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64)
            @Pattern(regexp = "[A-Za-z0-9_.-]+",
                    message = "must contain only letters, numbers, dots, dashes, or underscores")
            String username,
            @NotBlank @Size(min = 12, max = 256) String password,
            @NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetRequest(@NotBlank @Size(max = 254) String account) {
    }

    public record ResetConfirmation(
            @NotBlank @Size(max = 256) String token,
            @NotBlank @Size(min = 12, max = 256) String password) {
    }

    public record SessionResponse(boolean authenticated, String username, String libraryId,
                                  String csrfHeaderName, String csrfToken) {
    }

    public record LoginResponse(String username, String libraryId) {
    }

    public record MessageResponse(String message) {
    }
}
