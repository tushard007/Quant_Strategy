package org.factor_investing.quant_strategy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.factor_investing.quant_strategy.configuration.CorsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.factor_investing.quant_strategy.controller.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtSecurityTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:rbac;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.auth.allowed-origins=http://localhost:4200"
})
@AutoConfigureMockMvc
class JwtSecurityTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = UserAccount.class)
    @EnableJpaRepositories(basePackageClasses = UserAccountRepository.class)
    @Import({SecurityConfiguration.class, PasswordConfiguration.class, DatabaseUserDetailsService.class,
            DatabaseJwtAuthenticationConverter.class, SigningKeyStore.class, UserAccountService.class,
            AuthController.class, UserAdminController.class, ApiExceptionHandler.class,
            CorsConfig.class, ProbeController.class})
    static class TestApplication {}

    @Autowired UserAccountRepository accounts;
    @Autowired UserAccountService accountService;
    @Autowired SigningKeyStore signingKeys;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    private static final String ADMIN_HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("admin-test-password");
    private static final String USER_HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("user-test-password");

    @BeforeEach
    void prepareAccounts() {
        accounts.deleteAll();
        accounts.saveAndFlush(new UserAccount("admin@example.com", ADMIN_HASH, AccountRole.ADMIN));
        accounts.saveAndFlush(new UserAccount("superadmin@example.com", ADMIN_HASH, AccountRole.SUPERADMIN));
        accounts.saveAndFlush(new UserAccount("user@example.com", USER_HASH, AccountRole.USER));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;

    @RestController
    static class ProbeController {
        // The actual security filter chain runs; domain work is replaced with a harmless response.
        @RequestMapping({"/", "/api/momentum/executions", "/api/momentum/executions/STOCK/2026-09-14",
                "/api/momentum/calculate-and-rank/STOCK", "/api/momentum-backtest/run/stock",
                "/api/momentum-risk-overlay-backtest/run/stock", "/api/risk-adjusted-momentum/calculate-and-rank/STOCK",
                "/api/risk-adjusted-momentum-backtest/run/stock", "/api/breadth-backtest/run",
                "/api/technical-indicator/EMAIndicator/20", "/api/market-breadth/history",
                "/api/market-breadth/config", "/api/market-breadth/recalculate",
                "/api/stock-master", "/api/etf-master", "/api/index-master", "/api/nifty-index-stock/NIFTY50",
                "/api/price-data/jobs/stock-Price/DAILY", "/read-csv/row/example.csv", "/nifty-index/index-stock-data",
                "/api/llm-models/complete", "/api/future-endpoint"})
        String probe() { return "ok"; }
    }

    @Test
    void loginIssuesVerifiableJwtAndMeReturnsServerRoles() throws Exception {
        String token = login("user@example.com", "user-test-password");
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("user@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getAudience()).containsExactly(SecurityConfiguration.AUDIENCE);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now()).isBefore(Instant.now().plusSeconds(901));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("username").value("user@example.com"))
                .andExpect(jsonPath("roles[0]").value("USER"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void refreshIssuesReplacementJwtForAuthenticatedUser() throws Exception {
        String token = login("user@example.com", "user-test-password");
        var result = mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        Jwt refreshed = decoder.decode(mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText());
        assertThat(refreshed.getSubject()).isEqualTo("user@example.com");
        assertThat(refreshed.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(refreshed.getExpiresAt()).isAfter(Instant.now()).isBefore(Instant.now().plusSeconds(901));
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsInvalidAndMissingCredentialsAndIgnoresClientRole() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user@example.com\",\"password\":\"user-test-password\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).at("/user/roles/0").asText()).isEqualTo("USER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/api/momentum/executions", "/api/momentum/executions/STOCK/2026-09-14"})
    void dashboardIsPublic(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/momentum/calculate-and-rank/STOCK", "/api/momentum-backtest/run/stock",
            "/api/momentum-risk-overlay-backtest/run/stock", "/api/risk-adjusted-momentum/calculate-and-rank/STOCK",
            "/api/risk-adjusted-momentum-backtest/run/stock", "/api/breadth-backtest/run", "/api/market-breadth/recalculate"})
    void analysisCalculationsRequireLoginAndAllowBothRoles(String path) throws Exception {
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
        for (String role : List.of("USER", "ADMIN", "SUPERADMIN")) {
            mvc.perform(post(path).header("Authorization", "Bearer " + token(role, Instant.now().plusSeconds(900),
                    SecurityConfiguration.ISSUER, SecurityConfiguration.AUDIENCE))).andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/stock-master", "/api/etf-master", "/api/index-master", "/api/nifty-index-stock/NIFTY50",
            "/api/price-data/jobs/stock-Price/DAILY", "/read-csv/row/example.csv", "/nifty-index/index-stock-data",
            "/api/llm-models/complete", "/api/future-endpoint"})
    void masterAndOtherApisRequireAdminEvenWithDirectRequests(String path) throws Exception {
        String user = token("USER", Instant.now().plusSeconds(900), SecurityConfiguration.ISSUER, SecurityConfiguration.AUDIENCE);
        String admin = token("ADMIN", Instant.now().plusSeconds(900), SecurityConfiguration.ISSUER, SecurityConfiguration.AUDIENCE);
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (var method : List.of(org.springframework.http.HttpMethod.GET, org.springframework.http.HttpMethod.POST,
                org.springframework.http.HttpMethod.PUT, org.springframework.http.HttpMethod.DELETE)) {
            mvc.perform(request(method, path).header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
            mvc.perform(request(method, path).header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        }
    }

    @Test
    void analysisReadsWorkButConfigurationChangesAreAdminOnly() throws Exception {
        String user = login("user@example.com", "user-test-password");
        for (String path : List.of("/api/market-breadth/config", "/api/market-breadth/history", "/api/technical-indicator/EMAIndicator/20")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + user)).andExpect(status().isOk());
        }
        mvc.perform(put("/api/market-breadth/config").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        mvc.perform(put("/api/market-breadth/config").header("Authorization", "Bearer " + login("admin@example.com", "admin-test-password")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/momentum/executions")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidExpiredWrongIssuerAndWrongAudienceTokensAreRejected() throws Exception {
        String valid = login("admin@example.com", "admin-test-password");
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"admin\",\"roles\":[\"ADMIN\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
        for (String token : List.of("invalid", tampered,
                token("ADMIN", Instant.now().minusSeconds(1), SecurityConfiguration.ISSUER, SecurityConfiguration.AUDIENCE),
                token("ADMIN", Instant.now().plusSeconds(900), "wrong-issuer", SecurityConfiguration.AUDIENCE),
                token("ADMIN", Instant.now().plusSeconds(900), SecurityConfiguration.ISSUER, "wrong-audience"))) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void missingAudienceOrExpiryIsRejected() throws Exception {
        var noAudience = JwtClaimsSet.builder().subject("admin").issuer(SecurityConfiguration.ISSUER)
                .expiresAt(Instant.now().plusSeconds(900)).claim("roles", List.of("ADMIN")).build();
        var noExpiry = JwtClaimsSet.builder().subject("admin").issuer(SecurityConfiguration.ISSUER)
                .audience(List.of(SecurityConfiguration.AUDIENCE)).claim("roles", List.of("ADMIN")).build();
        for (var claims : List.of(noAudience, noExpiry)) {
            String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void corsOnlyAllowsConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/api/auth/login").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesMultipleEmailAccountsAndResponsesNeverExposeHashesOrKeys() throws Exception {
        String admin = login("superadmin@example.com", "admin-test-password");
        for (var entry : Map.of("alice@example.com", "USER", "bob@example.com", "ADMIN").entrySet()) {
            mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                            "email", "  " + entry.getKey().toUpperCase(java.util.Locale.ROOT) + "  ",
                            "password", "new-account-password", "role", entry.getValue()))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("email").value(entry.getKey()))
                    .andExpect(jsonPath("passwordHash").doesNotExist()).andExpect(jsonPath("secretBase64").doesNotExist());
            String newToken = login(entry.getKey().toUpperCase(java.util.Locale.ROOT), "new-account-password");
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("username").value(entry.getKey()));
        }
        assertThat(accounts.count()).isEqualTo(5);
        assertThat(accounts.findByEmail("alice@example.com").orElseThrow().getPasswordHash()).startsWith("$2");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "email", "ALICE@EXAMPLE.COM", "password", "another-password", "role", "USER"))))
                .andExpect(status().isConflict());
    }

    @Test
    void userCannotManageAccountsAndInvalidEmailOrPasswordIsRejected() throws Exception {
        String user = login("user@example.com", "user-test-password");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + user)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/1").header("Authorization", "Bearer " + user)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        String admin = login("superadmin@example.com", "admin-test-password");
        for (var request : List.of(
                Map.of("email", "invalid", "password", "valid-password", "role", "USER"),
                Map.of("email", "new@example.com", "password", "short", "role", "USER"),
                Map.of("email", "new@example.com", "password", "é".repeat(40), "role", "USER"))) {
            mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", "admin", "password", "admin-test-password"))))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "enabled", "password"})
    void accountChangesInvalidateExistingTokens(String change) throws Exception {
        String admin = login("superadmin@example.com", "admin-test-password");
        String user = login("user@example.com", "user-test-password");
        var account = accounts.findByEmail("user@example.com").orElseThrow();
        var request = new UserAccountService.UpdateAccount(change.equals("role") ? AccountRole.ADMIN : AccountRole.USER,
                !change.equals("enabled"), change.equals("password") ? "changed-user-password" : null, account.getVersion());
        mvc.perform(put("/api/admin/users/" + account.getId()).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + user)).andExpect(status().isUnauthorized());
        if (change.equals("password")) {
            login("user@example.com", "changed-user-password");
        }
        if (!change.equals("role")) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("username", "user@example.com", "password", "user-test-password"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void lastAdministratorCannotBeDisabledOrDemotedAndStaleEditsAreRejected() throws Exception {
        String admin = login("superadmin@example.com", "admin-test-password");
        var account = accounts.findByEmail("superadmin@example.com").orElseThrow();
        for (var request : List.of(new UserAccountService.UpdateAccount(AccountRole.USER, true, null, 0),
                new UserAccountService.UpdateAccount(AccountRole.ADMIN, false, null, 0),
                new UserAccountService.UpdateAccount(AccountRole.ADMIN, true, null, 999))) {
            mvc.perform(put("/api/admin/users/" + account.getId()).header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void simultaneousAdminDemotionsLeaveOneEnabledAdmin() throws Exception {
        var second = accounts.saveAndFlush(new UserAccount("second@example.com", ADMIN_HASH, AccountRole.SUPERADMIN));
        var first = accounts.findByEmail("superadmin@example.com").orElseThrow();
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = List.of(first, second).stream().map(account -> pool.submit(() -> {
                gate.await();
                try {
                    accountService.update(account.getEmail(), 0, account.getId(),
                            new UserAccountService.UpdateAccount(AccountRole.USER, true, null, 0));
                    return true;
                } catch (org.springframework.web.server.ResponseStatusException expected) {
                    assertThat(expected.getStatusCode().value()).isEqualTo(409);
                    return false;
                }
            })).toList();
            gate.countDown();
            int successCount = 0;
            for (var result : results) if (result.get(10, java.util.concurrent.TimeUnit.SECONDS)) successCount++;
            assertThat(successCount).isEqualTo(1);
        }
        assertThat(accounts.countByRoleAndEnabledTrue(AccountRole.SUPERADMIN)).isEqualTo(1);
    }

    @Test
    void cachedAdminCannotCreateAccountsAfterConcurrentDisable() {
        var second = accounts.saveAndFlush(new UserAccount("second@example.com", ADMIN_HASH, AccountRole.SUPERADMIN));
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.execute(status -> {
            accounts.findByEmail("second@example.com").orElseThrow();
            try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                worker.submit(() -> accountService.update("superadmin@example.com", 0, second.getId(),
                        new UserAccountService.UpdateAccount(AccountRole.ADMIN, false, null, 0)))
                        .get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return accountService.create("second@example.com", 0,
                    new UserAccountService.CreateAccount("unauthorized@example.com", "account-password", AccountRole.ADMIN));
        })).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(error -> assertThat(((org.springframework.web.server.ResponseStatusException) error).getStatusCode().value()).isEqualTo(403));
        assertThat(accounts.existsByEmail("unauthorized@example.com")).isFalse();
    }

    @Test
    void signingKeyPersistsAndBootstrapOnlyCreatesTheFirstAccount() {
        byte[] firstKey = signingKeys.loadOrCreate().getEncoded();
        assertThat(firstKey).hasSize(32);
        assertThat(signingKeys.loadOrCreate().getEncoded()).containsExactly(firstKey);
        assertThat(accountService.bootstrap(new UserAccountService.CreateAccount("first@example.com", "first-admin-password", AccountRole.SUPERADMIN))).isFalse();
        accounts.deleteAll();
        assertThat(accountService.bootstrap(new UserAccountService.CreateAccount("first@example.com", "first-admin-password", AccountRole.SUPERADMIN))).isTrue();
        assertThat(accounts.findByEmail("first@example.com")).isPresent();
        assertThat(accountService.bootstrap(new UserAccountService.CreateAccount("other@example.com", "other-admin-password", AccountRole.SUPERADMIN))).isFalse();
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void adminCanManageOnlyUsersAndCannotEscalatePrivileges() throws Exception {
        String admin = login("admin@example.com", "admin-test-password");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("user@example.com"));
        for (String role : List.of("ADMIN", "SUPERADMIN")) {
            mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                            "email", "elevated@example.com", "password", "account-password", "role", role))))
                    .andExpect(status().isForbidden());
        }
        for (String email : List.of("admin@example.com", "superadmin@example.com")) {
            var target = accounts.findByEmail(email).orElseThrow();
            mvc.perform(put("/api/admin/users/" + target.getId()).header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                            new UserAccountService.UpdateAccount(target.getRole(), true, "changed-password", target.getVersion()))))
                    .andExpect(status().isForbidden());
        }
        var user = accounts.findByEmail("user@example.com").orElseThrow();
        mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                        new UserAccountService.UpdateAccount(AccountRole.SUPERADMIN, true, null, user.getVersion()))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "email", "normal@example.com", "password", "account-password", "role", "USER"))))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                        new UserAccountService.UpdateAccount(AccountRole.USER, false, null, user.getVersion()))))
                .andExpect(status().isOk());
    }

    @Test
    void superadminManagesAdminsAndCanCreateAnotherSuperadmin() throws Exception {
        String token = login("superadmin@example.com", "admin-test-password");
        var admin = accounts.findByEmail("admin@example.com").orElseThrow();
        mvc.perform(put("/api/admin/users/" + admin.getId()).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                        new UserAccountService.UpdateAccount(AccountRole.USER, false, "changed-password", admin.getVersion()))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "email", "second-superadmin@example.com", "password", "account-password", "role", "SUPERADMIN"))))
                .andExpect(status().isCreated());
        String second = login("second-superadmin@example.com", "account-password");
        mvc.perform(get("/api/stock-master").header("Authorization", "Bearer " + second)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + second)).andExpect(status().isOk());
    }

    @Test
    void bootstrapAddsSuperadminToExistingDatabaseAndDoesNotOverwriteExistingEmail() {
        accounts.delete(accounts.findByEmail("superadmin@example.com").orElseThrow());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accountService.bootstrap(
                new UserAccountService.CreateAccount("admin@example.com", "replacement-password", AccountRole.SUPERADMIN)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(accountService.bootstrap(new UserAccountService.CreateAccount(
                "owner@example.com", "owner-password", AccountRole.SUPERADMIN))).isTrue();
        assertThat(accounts.findByEmail("admin@example.com").orElseThrow().getRole()).isEqualTo(AccountRole.ADMIN);
    }

    private String login(String username, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String token(String role, Instant expiresAt, String issuer, String audience) {
        var claims = JwtClaimsSet.builder().subject(role.toLowerCase() + "@example.com").issuer(issuer).audience(List.of(audience))
                .issuedAt(Instant.now().minusSeconds(60)).expiresAt(expiresAt).claim("roles", List.of(role)).claim("auth_version", 0L).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
