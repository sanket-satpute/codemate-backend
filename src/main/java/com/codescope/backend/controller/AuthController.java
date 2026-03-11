package com.codescope.backend.controller;

import com.codescope.backend.dto.BaseResponse;
import com.codescope.backend.dto.auth.AuthResponse;
import com.codescope.backend.dto.auth.LoginRequest;
import com.codescope.backend.dto.auth.RegisterRequest;
import com.codescope.backend.dto.auth.UserDTO;
import com.codescope.backend.exception.CustomException;
import com.codescope.backend.model.User;
import com.codescope.backend.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Mono<ResponseEntity<BaseResponse<AuthResponse>>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request)
                .map(authResponse -> ResponseEntity.ok(new BaseResponse<>(true, "Registration successful", authResponse)))
                .onErrorResume(WebExchangeBindException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new BaseResponse<>(false, "Validation failed", null))))
                .onErrorResume(CustomException.class, e ->
                        Mono.just(ResponseEntity.status(e.getStatus())
                                .body(new BaseResponse<>(false, e.getMessage(), null))));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<BaseResponse<AuthResponse>>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request)
                .map(authResponse -> ResponseEntity.ok(new BaseResponse<>(true, "Login successful", authResponse)))
                .onErrorResume(WebExchangeBindException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new BaseResponse<>(false, "Validation failed", null))))
                .onErrorResume(CustomException.class, e ->
                        Mono.just(ResponseEntity.status(e.getStatus())
                                .body(new BaseResponse<>(false, e.getMessage(), null))));
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<BaseResponse<UserDTO>>> getCurrentUser(Mono<Principal> principalMono) {
        return principalMono
                .flatMap(principal -> authService.getCurrentUser()
                        .map(userDTO -> ResponseEntity.ok(new BaseResponse<>(true, "Current user fetched successfully", userDTO))))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new BaseResponse<>(false, "No authenticated user found", null))))
                .onErrorResume(Exception.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new BaseResponse<>(false, "An unexpected error occurred while fetching current user: " + e.getMessage(), null))));
    }
}
