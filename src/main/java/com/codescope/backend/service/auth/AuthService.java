package com.codescope.backend.service.auth;

import com.codescope.backend.dto.auth.AuthResponse;
import com.codescope.backend.dto.auth.LoginRequest;
import com.codescope.backend.dto.auth.RegisterRequest;
import com.codescope.backend.dto.auth.UserDTO;
import com.codescope.backend.exception.CustomException;
import com.codescope.backend.model.Role;
import com.codescope.backend.model.User;
import com.codescope.backend.repository.UserRepository;
import com.codescope.backend.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveAuthenticationManager authenticationManager;

    public Mono<AuthResponse> register(RegisterRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new CustomException("Email already registered", HttpStatus.CONFLICT));
                    }
                    User user = User.builder()
                            .name(request.getName())
                            .email(request.getEmail())
                            .password(passwordEncoder.encode(request.getPassword()))
                            .role(Role.USER)
                            .build();
                    return userRepository.save(user)
                            .map(savedUser -> {
                                String jwtToken = jwtTokenProvider.generateToken(savedUser);
                                return AuthResponse.builder()
                                        .token(jwtToken)
                                        .user(UserDTO.builder()
                                                .id(savedUser.getId())
                                                .name(savedUser.getName())
                                                .email(savedUser.getEmail())
                                                .role(savedUser.getRole())
                                                .build())
                                        .build();
                            });
                });
    }

    public Mono<AuthResponse> login(LoginRequest request) {
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        )
                .flatMap(authentication -> {
                    return Mono.just((User) authentication.getPrincipal());
                })
                .flatMap(user -> {
                    String jwtToken = jwtTokenProvider.generateToken(user);
                    return Mono.just(AuthResponse.builder()
                            .token(jwtToken)
                            .user(UserDTO.builder()
                                    .id(user.getId())
                                    .name(user.getName())
                                    .email(user.getEmail())
                                    .role(user.getRole())
                                    .build())
                            .build());
                });
    }

    public Mono<UserDTO> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(User.class)
                .map(user -> UserDTO.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .build());
    }
}
