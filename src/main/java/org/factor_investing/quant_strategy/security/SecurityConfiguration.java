package org.factor_investing.quant_strategy.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import java.time.Duration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    public static final String ISSUER = "quant-strategy";
    public static final String AUDIENCE = "quant-strategy-api";

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecretKey jwtSigningKey(SigningKeyStore store) {
        return store.loadOrCreate();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        OAuth2TokenValidator<Jwt> expiry = jwt -> jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing expiry", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(ISSUER), audience, expiry));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, DatabaseJwtAuthenticationConverter authentication) throws Exception {
        return http
                // Authentication is exclusively an explicit Authorization header, never a cookie.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(access -> access
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/momentum/executions",
                                "/api/momentum/executions/{assetDataType}/{strategyRunDate}").permitAll()
                        .requestMatchers("/api/auth/me").hasAnyRole("SUPERADMIN", "ADMIN", "USER")
                        .requestMatchers(HttpMethod.GET, "/api/market-breadth/latest", "/api/market-breadth/history",
                                "/api/market-breadth/sectors", "/api/market-breadth/config", "/api/market-breadth/export",
                                "/api/market-breadth/alerts", "/api/market-breadth/reference-data/check").hasAnyRole("SUPERADMIN", "ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST, "/api/market-breadth/recalculate").hasAnyRole("SUPERADMIN", "ADMIN", "USER")
                        .requestMatchers(HttpMethod.GET, "/api/momentum-backtest/**", "/api/risk-adjusted-momentum/**",
                                "/api/risk-adjusted-momentum-backtest/**", "/api/breadth-backtest/**",
                                "/api/technical-indicator/**").hasAnyRole("SUPERADMIN", "ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST, "/api/momentum/calculate-and-rank/*",
                                "/api/momentum-backtest/**", "/api/momentum-risk-overlay-backtest/**",
                                "/api/risk-adjusted-momentum/calculate-and-rank/*",
                                "/api/risk-adjusted-momentum-backtest/**", "/api/breadth-backtest/**").hasAnyRole("SUPERADMIN", "ADMIN", "USER")
                        .requestMatchers("/api/**", "/read-csv/**", "/nifty-index/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/*.js", "/*.css", "/*.ico",
                                "/*.svg", "/*.png", "/assets/**", "/media/**",
                                "/login", "/overview/**", "/analyze/**", "/backtest/**", "/data/**",
                                "/administration/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication)))
                .build();
    }
}
