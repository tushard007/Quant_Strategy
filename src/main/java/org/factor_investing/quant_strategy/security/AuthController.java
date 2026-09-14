package org.factor_investing.quant_strategy.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder encoder;

    public AuthController(AuthenticationManager authenticationManager, JwtEncoder encoder) {
        this.authenticationManager = authenticationManager;
        this.encoder = encoder;
    }

    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(900);
        var user = currentUser(authentication);
        var claims = JwtClaimsSet.builder().issuer(SecurityConfiguration.ISSUER)
                .audience(List.of(SecurityConfiguration.AUDIENCE)).subject(user.username())
                .issuedAt(now).expiresAt(expiresAt).claim("roles", user.roles())
                .claim("auth_version", ((AccountPrincipal) authentication.getPrincipal()).tokenVersion()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new LoginResponse(token, "Bearer", expiresAt, user));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(currentUser(authentication));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Invalid email or password"));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return new CurrentUser(authentication.getName(), authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).filter(role -> role.startsWith("ROLE_"))
                .map(role -> role.substring(5)).toList());
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String username,
                               @NotBlank @Size(max = 72) String password) {
        public LoginRequest { username = UserAccount.normalizeEmail(username); }
        @Override public String toString() { return "LoginRequest[username=" + username + "]"; }
    }
    public record CurrentUser(String username, List<String> roles) {}
    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, CurrentUser user) {}
}
