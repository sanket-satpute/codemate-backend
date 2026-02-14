package com.codescope.backend.controller;

import com.codescope.backend.dto.auth.AuthResponse;
import com.codescope.backend.dto.auth.LoginRequest;
import com.codescope.backend.dto.auth.RegisterRequest;
import com.codescope.backend.security.jwt.JwtTokenProvider;
import com.codescope.backend.service.auth.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for AuthController.
 */
@WebFluxTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("Should register a new user successfully")
    void registerUser_success() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123");
        AuthResponse mockAuthResponse = AuthResponse.builder().token("mockJwtToken").build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(Mono.just(mockAuthResponse));

        webTestClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .isEqualTo(mockAuthResponse);
    }

    @Test
    @DisplayName("Should return 400 Bad Request for invalid registration data")
    void registerUser_badRequest() {
        RegisterRequest request = new RegisterRequest("", "invalid-email", "");

        webTestClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Should authenticate user and return JWT token")
    void authenticateUser_success() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        AuthResponse mockAuthResponse = AuthResponse.builder().token("mockJwtToken").build();
        when(authService.login(any(LoginRequest.class))).thenReturn(Mono.just(mockAuthResponse));

        webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class).isEqualTo(mockAuthResponse);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized for invalid login credentials")
    void authenticateUser_unauthorized() {
        LoginRequest request = new LoginRequest("invalid@example.com", "wrongpassword");
        // For login, authService.login throws CustomException if credentials are bad
        // Since we are mocking, we can simulate this by making the service return null or throw an exception.
        // Or, more accurately for a WebFluxTest, the controller handles the exception and returns appropriate status.
        // Here, we'll simulate the underlying authentication failure.
        when(authService.login(any(LoginRequest.class))).thenReturn(Mono.error(new RuntimeException("Bad credentials")));

        webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
