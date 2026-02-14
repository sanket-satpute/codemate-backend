package com.codescope.backend.security;

import com.codescope.backend.model.Role;
import com.codescope.backend.model.User;
import com.codescope.backend.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64; // Import Base64

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        // Generate a Base64 encoded secret key for testing (at least 32 bytes for HS256)
        String testSecret = Base64.getEncoder().encodeToString("supersecretkeyforexampletests1234567890".getBytes());
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", testSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", 3600000L); // 1 hour expiration
    }

    // --------------------------------------------------------
    // ✅ TEST 1: Generate token and validate it
    // --------------------------------------------------------
    @Test
    void generateToken_shouldCreateValidToken() {
        User mockUser = User.builder()
                .id("1")
                .email("user@example.com")
                .role(Role.USER)
                .password("encoded_password")
                .name("testuser")
                .build();

        String token = jwtTokenProvider.generateToken(mockUser);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token, mockUser));

        Claims extracted = jwtTokenProvider.extractAllClaims(token);
        assertEquals(mockUser.getEmail(), extracted.getSubject());
        assertEquals(mockUser.getRole().name(), extracted.get("roles"));
    }

    // --------------------------------------------------------
    // ✅ TEST 2: Token should be invalid when tampered
    // --------------------------------------------------------
    @Test
    void validateToken_shouldReturnFalseForTamperedToken() {
        User mockUser = User.builder()
                .id("1")
                .email("user@example.com")
                .role(Role.USER)
                .password("encoded_password")
                .name("testuser")
                .build();
        String token = jwtTokenProvider.generateToken(mockUser);
        // Modify last few chars to break signature
        String tampered = token.substring(0, token.length() - 5) + "abcde";

        assertFalse(jwtTokenProvider.validateToken(tampered, mockUser));
    }

    // --------------------------------------------------------
    // ✅ TEST 3: Should extract correct claims
    // --------------------------------------------------------
    @Test
    void getClaims_shouldReturnClaimsData() {
        User mockUser = User.builder()
                .id("2")
                .email("sanket@codescope.ai")
                .role(Role.ADMIN)
                .password("encoded_password")
                .name("sanket")
                .build();

        String token = jwtTokenProvider.generateToken(mockUser);
        Claims body = jwtTokenProvider.extractAllClaims(token);

        assertEquals(mockUser.getEmail(), body.getSubject());
        assertEquals(mockUser.getRole().name(), body.get("roles"));
    }

    // --------------------------------------------------------
    // ✅ TEST 4: ValidateToken should handle invalid input (not covered by validateToken(token, userDetails))
    // --------------------------------------------------------
    @Test
    void validateToken_shouldReturnFalseForGarbageToken() {
        assertFalse(jwtTokenProvider.validateToken("not_a_real_token", null));
    }
}
