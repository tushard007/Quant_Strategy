package org.factor_investing.quant_strategy.security;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final UserAccountService accounts;

    public UserAdminController(UserAccountService accounts) { this.accounts = accounts; }

    @GetMapping
    public ResponseEntity<List<UserAccountService.AccountView>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.list(jwt.getSubject(), ((Number) jwt.getClaims().get("auth_version")).longValue()));
    }

    @PostMapping
    public ResponseEntity<UserAccountService.AccountView> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserAccountService.CreateAccount request) {
        var account = accounts.create(jwt.getSubject(), ((Number) jwt.getClaims().get("auth_version")).longValue(), request);
        return ResponseEntity.created(URI.create("/api/admin/users/" + account.id()))
                .cacheControl(CacheControl.noStore()).body(account);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserAccountService.AccountView> update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody UserAccountService.UpdateAccount request) {
        var account = accounts.update(jwt.getSubject(), ((Number) jwt.getClaims().get("auth_version")).longValue(), id, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(account);
    }
}
