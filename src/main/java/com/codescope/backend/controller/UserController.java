package com.codescope.backend.controller;

import com.codescope.backend.dto.BaseResponse;
import com.codescope.backend.dto.auth.UserDTO;
import com.codescope.backend.dto.user.ChangeEmailRequest;
import com.codescope.backend.dto.user.ChangePasswordRequest;
import com.codescope.backend.dto.user.DeleteAccountRequest;
import com.codescope.backend.dto.user.DisableAccountRequest;
import com.codescope.backend.dto.user.UpdateProfileRequest;
import com.codescope.backend.exception.CustomException;
import com.codescope.backend.model.AccountStatus;
import com.codescope.backend.model.User;
import com.codescope.backend.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * GET /api/users/me — Get the current authenticated user's profile.
     * Used by frontend SettingsService.getCurrentUser().
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<BaseResponse<UserDTO>>> getCurrentUser() {
        return getCurrentAuthenticatedUser()
                .map(user -> {
                    UserDTO userDTO = UserDTO.builder()
                            .id(user.getId())
                            .name(user.getName())
                            .email(user.getEmail())
                            .role(user.getRole())
                            .build();
                    return ResponseEntity.ok(BaseResponse.success(userDTO, "User profile retrieved successfully"));
                })
                .onErrorResume(e -> {
                    log.error("Error fetching current user: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to retrieve user profile: " + e.getMessage())));
                });
    }

    /**
     * PUT /api/users/me — Update the current user's profile (name, email,
     * profilePictureUrl).
     * Used by frontend SettingsService.updateProfile().
     */
    @PutMapping("/me")
    public Mono<ResponseEntity<BaseResponse<UserDTO>>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return getCurrentAuthenticatedUser()
                .flatMap(user -> {
                    if (request.getName() != null) {
                        user.setName(request.getName());
                    }
                    // If email is changing, check uniqueness first
                    if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
                        return userRepository.findByEmail(request.getEmail())
                                .hasElement()
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new CustomException(
                                                "This email is already in use by another account",
                                                HttpStatus.CONFLICT));
                                    }
                                    user.setEmail(request.getEmail());
                                    user.setUpdatedAt(LocalDateTime.now());
                                    return userRepository.save(user);
                                });
                    }
                    user.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(user);
                })
                .map(savedUser -> {
                    UserDTO userDTO = UserDTO.builder()
                            .id(savedUser.getId())
                            .name(savedUser.getName())
                            .email(savedUser.getEmail())
                            .role(savedUser.getRole())
                            .build();
                    return ResponseEntity.ok(BaseResponse.success(userDTO, "Profile updated successfully"));
                })
                .onErrorResume(CustomException.class, e -> Mono.just(ResponseEntity.status(e.getStatus())
                        .body(BaseResponse.error(e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error updating profile: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to update profile: " + e.getMessage())));
                });
    }

    /**
     * POST /api/users/change-password — Change the current user's password.
     * Used by frontend SettingsService.changePassword().
     *
     * Fetches the user FRESH from the database to ensure the password hash
     * is always up-to-date (the security context principal may be stale).
     */
    @PostMapping("/change-password")
    public Mono<ResponseEntity<BaseResponse<Void>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        return getCurrentAuthenticatedUser()
                // Re-fetch from DB to guarantee we have the actual stored password hash
                .flatMap(contextUser -> userRepository.findByEmail(contextUser.getEmail())
                        .switchIfEmpty(Mono.error(new CustomException("User not found", HttpStatus.NOT_FOUND))))
                .flatMap(user -> {
                    // Guard against null/empty password (e.g., OAuth-only accounts)
                    if (user.getPassword() == null || user.getPassword().isBlank()) {
                        log.warn("Change-password attempt for user {} but stored password is null/blank",
                                user.getEmail());
                        return Mono.error(new CustomException(
                                "No password set for this account. Please contact support.",
                                HttpStatus.BAD_REQUEST));
                    }

                    // Validate current password
                    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                        log.debug("Password mismatch for user {}", user.getEmail());
                        return Mono.error(new CustomException("Current password is incorrect", HttpStatus.BAD_REQUEST));
                    }
                    // Validate new password matches confirmation
                    if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
                        return Mono.error(new CustomException("New password and confirmation do not match",
                                HttpStatus.BAD_REQUEST));
                    }
                    // Encode and save new password
                    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                    return userRepository.save(user);
                })
                .map(savedUser -> ResponseEntity.ok(BaseResponse.<Void>success(null, "Password changed successfully")))
                .onErrorResume(CustomException.class, e -> Mono.just(ResponseEntity.status(e.getStatus())
                        .body(BaseResponse.<Void>error(e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error changing password: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.<Void>error("Failed to change password: " + e.getMessage())));
                });
    }

    /**
     * POST /api/users/change-email — Change the current user's email.
     * Requires current password for identity verification.
     * Checks that the new email is not already in use and that both email fields match.
     */
    @PostMapping("/change-email")
    public Mono<ResponseEntity<BaseResponse<UserDTO>>> changeEmail(
            @Valid @RequestBody ChangeEmailRequest request) {
        return getCurrentAuthenticatedUser()
                .flatMap(contextUser -> userRepository.findByEmail(contextUser.getEmail())
                        .switchIfEmpty(Mono.error(new CustomException("User not found", HttpStatus.NOT_FOUND))))
                .flatMap(user -> {
                    // Verify current password
                    if (user.getPassword() == null || user.getPassword().isBlank()) {
                        return Mono.<User>error(new CustomException(
                                "No password set for this account. Please contact support.",
                                HttpStatus.BAD_REQUEST));
                    }
                    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                        return Mono.<User>error(new CustomException("Incorrect password", HttpStatus.BAD_REQUEST));
                    }
                    // Verify new email and confirmation match
                    if (!request.getNewEmail().equalsIgnoreCase(request.getConfirmNewEmail())) {
                        return Mono.<User>error(new CustomException(
                                "New email and confirmation do not match", HttpStatus.BAD_REQUEST));
                    }
                    // Check if new email is the same as current
                    if (request.getNewEmail().equalsIgnoreCase(user.getEmail())) {
                        return Mono.<User>error(new CustomException(
                                "New email is the same as your current email", HttpStatus.BAD_REQUEST));
                    }
                    // Check email uniqueness
                    return userRepository.findByEmail(request.getNewEmail())
                            .hasElement()
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.<User>error(new CustomException(
                                            "This email is already in use by another account",
                                            HttpStatus.CONFLICT));
                                }
                                user.setEmail(request.getNewEmail());
                                user.setUpdatedAt(LocalDateTime.now());
                                return userRepository.save(user);
                            });
                })
                .map(savedUser -> {
                    UserDTO userDTO = UserDTO.builder()
                            .id(savedUser.getId())
                            .name(savedUser.getName())
                            .email(savedUser.getEmail())
                            .role(savedUser.getRole())
                            .build();
                    return ResponseEntity.ok(BaseResponse.success(userDTO, "Email changed successfully"));
                })
                .onErrorResume(CustomException.class, e -> Mono.just(ResponseEntity.status(e.getStatus())
                        .body(BaseResponse.<UserDTO>error(e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error changing email: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.<UserDTO>error("Failed to change email: " + e.getMessage())));
                });
    }

    /**
     * POST /api/users/disable-account — Temporarily disable the account for N days.
     * The user must provide their current password for verification.
     * While disabled, Spring Security's isAccountNonLocked() returns false and
     * login attempts will be rejected.
     */
    @PostMapping("/disable-account")
    public Mono<ResponseEntity<BaseResponse<Void>>> disableAccount(
            @Valid @RequestBody DisableAccountRequest request) {
        return getCurrentAuthenticatedUser()
                .flatMap(contextUser -> userRepository.findByEmail(contextUser.getEmail())
                        .switchIfEmpty(Mono.error(new CustomException("User not found", HttpStatus.NOT_FOUND))))
                .flatMap(user -> {
                    // Verify password
                    if (request.getPassword() == null || request.getPassword().isBlank()) {
                        return Mono.error(new CustomException("Password is required", HttpStatus.BAD_REQUEST));
                    }
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new CustomException("Incorrect password", HttpStatus.BAD_REQUEST));
                    }
                    // Disable for N days
                    user.setAccountStatus(AccountStatus.DISABLED);
                    user.setDisabledUntil(LocalDateTime.now().plusDays(request.getDays()));
                    user.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(user);
                })
                .map(saved -> ResponseEntity.ok(
                        BaseResponse.<Void>success(null,
                                "Account disabled for " + request.getDays() + " days. You will be logged out.")))
                .onErrorResume(CustomException.class, e -> Mono.just(ResponseEntity.status(e.getStatus())
                        .body(BaseResponse.<Void>error(e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error disabling account: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.<Void>error("Failed to disable account")));
                });
    }

    /**
     * POST /api/users/enable-account — Re-enable a disabled account.
     * (For admin use or self-service re-enable if the disable period hasn't expired yet.)
     */
    @PostMapping("/enable-account")
    public Mono<ResponseEntity<BaseResponse<Void>>> enableAccount() {
        return getCurrentAuthenticatedUser()
                .flatMap(contextUser -> userRepository.findByEmail(contextUser.getEmail())
                        .switchIfEmpty(Mono.error(new CustomException("User not found", HttpStatus.NOT_FOUND))))
                .flatMap(user -> {
                    user.setAccountStatus(AccountStatus.ACTIVE);
                    user.setDisabledUntil(null);
                    user.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(user);
                })
                .map(saved -> ResponseEntity.ok(
                        BaseResponse.<Void>success(null, "Account re-enabled successfully")))
                .onErrorResume(CustomException.class, e -> Mono.just(ResponseEntity.status(e.getStatus())
                        .body(BaseResponse.<Void>error(e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error enabling account: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.<Void>error("Failed to enable account")));
                });
    }

    /**
     * DELETE /api/users/me — Permanently delete the current user's account.
     * Requires current password for verification. This action is irreversible.
     */
    @DeleteMapping("/me")
    public Mono<ResponseEntity<BaseResponse<Void>>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request) {
        return getCurrentAuthenticatedUser()
                .flatMap(contextUser -> userRepository.findByEmail(contextUser.getEmail())
                        .switchIfEmpty(Mono.error(new CustomException("User not found", HttpStatus.NOT_FOUND))))
                .flatMap(user -> {
                    // Verify password
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new CustomException("Incorrect password", HttpStatus.BAD_REQUEST));
                    }
                    // Permanently delete from database
                    return userRepository.delete(user).then(Mono.just(user));
                })
                .map(deleted -> ResponseEntity.ok(
                        BaseResponse.<Void>success(null, "Account permanently deleted")))
                .onErrorResume(CustomException.class, e -> Mono.just(ResponseEntity.status(e.getStatus())
                        .body(BaseResponse.<Void>error(e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error deleting account: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.<Void>error("Failed to delete account")));
                });
    }

    /**
     * Helper to extract the current authenticated User from the reactive security
     * context.
     */
    private Mono<User> getCurrentAuthenticatedUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(User.class)
                .switchIfEmpty(Mono.error(new CustomException("No authenticated user found", HttpStatus.UNAUTHORIZED)));
    }
}
