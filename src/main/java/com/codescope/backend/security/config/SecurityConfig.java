package com.codescope.backend.security.config;

import com.codescope.backend.security.jwt.JwtTokenProvider; // Import JwtTokenProvider
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;
import org.springframework.context.annotation.Primary; // Moved import to correct location

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor // Use Lombok's @RequiredArgsConstructor for constructor injection
public class SecurityConfig {

    private final ReactiveUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider; // Inject JwtTokenProvider

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // Disable CSRF for API
                .cors(corsSpec -> corsSpec.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/register", "/api/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**", "/ws/chat/**").permitAll()
                        .pathMatchers("/api/auth/me").authenticated() // Secure /api/auth/me
                        .pathMatchers("/api/**").authenticated() // Secure all other /api paths
                        .anyExchange().permitAll() // Permit everything else by default
                )
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable) // Correctly disable logout
                .addFilterAt(jwtAuthenticationWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION);

        return http.build();
    }

    @Bean
    public AuthenticationWebFilter jwtAuthenticationWebFilter() {
        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(jwtReactiveAuthenticationManager());
        authenticationWebFilter.setServerAuthenticationConverter(exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(authHeader -> authHeader.startsWith("Bearer "))
                .map(authHeader -> authHeader.substring(7))
                .map(token -> new UsernamePasswordAuthenticationToken(token, null))); // Pass token as principal, manager will handle validation
        authenticationWebFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        return authenticationWebFilter;
    }

    @Primary // Mark this as the primary bean when multiple ReactiveAuthenticationManager beans are present
    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager() {
        // Custom ReactiveAuthenticationManager for username/password authentication
        return authentication -> Mono.justOrEmpty(authentication)
                .cast(UsernamePasswordAuthenticationToken.class)
                .flatMap(auth -> userDetailsService.findByUsername(auth.getName())
                        .flatMap(userDetails -> {
                            if (passwordEncoder.matches((CharSequence) auth.getCredentials(), userDetails.getPassword())) {
                                return Mono.just(new UsernamePasswordAuthenticationToken(userDetails, auth.getCredentials(), userDetails.getAuthorities()));
                            }
                            return Mono.error(new Exception("Invalid Credentials"));
                        })
                        .switchIfEmpty(Mono.error(new Exception("User not found"))));
    }

    // A dedicated ReactiveAuthenticationManager for JWT
    @Bean
    public ReactiveAuthenticationManager jwtReactiveAuthenticationManager() {
        return authentication -> {
            if (authentication.getPrincipal() instanceof String token) {
                String username = jwtTokenProvider.extractUsername(token);
                return userDetailsService.findByUsername(username)
                        .flatMap(userDetails -> {
                            if (jwtTokenProvider.validateToken(token, userDetails)) {
                                return Mono.<org.springframework.security.core.Authentication>just(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
                            }
                            return Mono.error(new Exception("Invalid JWT token for user: " + username)); // Specific error message
                        })
                        .switchIfEmpty(Mono.error(new Exception("User not found for token: " + username))); // If user not found
            }
            return Mono.empty(); // Not a JWT token or not a string principal
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200")); // Explicitly allow frontend origin
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
