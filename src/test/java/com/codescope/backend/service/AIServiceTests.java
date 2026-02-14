//package com.codescope.backend.service;
//
//import com.codescope.backend.ai.HuggingFaceService;
//import com.codescope.backend.ai.OpenAIService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.*;
//
///**
// * ✅ Unit tests for AIService
// * - Covers HuggingFace + OpenAI fallback logic
// * - Ensures non-null, structured responses
// */
//@ExtendWith(MockitoExtension.class)
//class AIServiceTests {
//
//    @Mock
//    private HuggingFaceService hfService;
//
//    @Mock
//    private OpenAIService openAIService;
//
//    @InjectMocks
//    private AIService aiService;
//
//    @BeforeEach
//    void setup() {
//        // any required setup before each test
//    }
//
//    @Test
//    @DisplayName("✅ Should return HuggingFace response when available")
//    void shouldReturnHuggingFaceResponse() {
//        // Arrange
//        String mockResponse = "{\"provider\":\"HuggingFace\",\"content\":\"Good code\"}";
//        when(hfService.queryDefaultModel(anyString())).thenReturn(mockResponse);
//        when(openAIService.chat(anyString())).thenReturn(null);
//
//        // Act
//        String result = aiService.analyzeCode("public class Test {}");
//
//        // Assert
//        assertNotNull(result);
//        assertTrue(result.contains("HuggingFace"));
//        verify(hfService, times(1)).queryDefaultModel(anyString());
//        verify(openAIService, never()).chat(anyString());
//    }
//
//    @Test
//    @DisplayName("✅ Should fallback to OpenAI when HuggingFace fails")
//    void shouldFallbackToOpenAI() {
//        // Arrange
//        when(hfService.queryDefaultModel(anyString())).thenReturn(null);
//        String mockResponse = "{\"provider\":\"OpenAI\",\"content\":\"Fallback analysis\"}";
//        when(openAIService.chat(anyString())).thenReturn(mockResponse);
//
//        // Act
//        String result = aiService.analyzeCode("code sample");
//
//        // Assert
//        assertNotNull(result);
//        assertTrue(result.contains("OpenAI"));
//        verify(hfService, times(1)).queryDefaultModel(anyString());
//        verify(openAIService, times(1)).chat(anyString());
//    }
//
//    @Test
//    @DisplayName("⚠️ Should return Mock response when all providers fail")
//    void shouldReturnMockResponseWhenAllProvidersFail() {
//        // Arrange
//        when(hfService.queryDefaultModel(anyString())).thenReturn(null);
//        when(openAIService.chat(anyString())).thenReturn(null);
//
//        // Act
//        String result = aiService.analyzeCode("some code");
//
//        // Assert
//        assertNotNull(result);
//        assertTrue(result.contains("\"provider\":\"Mock\""));
//        assertTrue(result.contains("No AI provider available"));
//    }
//
//    @Test
//    @DisplayName("🚫 Should handle null or empty input gracefully")
//    void shouldHandleEmptyInputGracefully() {
//        // Act
//        String result = aiService.analyzeCode("");
//
//        // Assert
//        assertNotNull(result);
//        assertTrue(result.contains("Invalid input"));
//    }
//}
