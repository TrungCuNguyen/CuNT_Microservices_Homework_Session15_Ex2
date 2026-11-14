package com.example.gatewayservice.filter;

import com.example.gatewayservice.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private final String SECRET = "vs6K5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2z";
    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET);
        filter = new JwtAuthenticationFilter(jwtUtil);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private String createToken(String username, String role, long durationMs, Key signingKey) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + durationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("Should return 401 when Authorization header is missing on /api/courses")
    void filter_MissingAuthHeader_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Should return 401 when Authorization header does not start with Bearer")
    void filter_InvalidAuthHeaderFormat_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Basic 123456")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Should return 401 when JWT token is expired")
    void filter_ExpiredToken_Returns401() {
        // -10000ms expiration means expired 10 seconds ago
        String expiredToken = createToken("student1", "STUDENT", -10000L, jwtUtil.getSigningKey());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Should return 401 when JWT token signature is invalid")
    void filter_InvalidSignature_Returns401() {
        Key wrongKey = Keys.hmacShaKeyFor("differentSecretKeyForUnitTestingPurposesOnly1234567890".getBytes(StandardCharsets.UTF_8));
        String invalidToken = createToken("student1", "STUDENT", 60000L, wrongKey);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Should extract role, attach X-User-Role header and forward when JWT token is valid")
    void filter_ValidToken_AttachesHeaderAndForwards() {
        String validToken = createToken("teacher_john", "INSTRUCTOR", 600000L, jwtUtil.getSigningKey());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain, times(1)).filter(exchangeCaptor.capture());

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        String addedHeader = capturedExchange.getRequest().getHeaders().getFirst("X-User-Role");

        assertNotNull(addedHeader);
        assertEquals("INSTRUCTOR", addedHeader, "Header X-User-Role must contain role from token payload");
    }

    @Test
    @DisplayName("Should not block non-course paths (e.g. /api/auth/login)")
    void filter_NonCoursePath_PassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
    }
}
