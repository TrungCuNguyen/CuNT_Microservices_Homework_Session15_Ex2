package com.example.gatewayservice.filter;

import com.example.gatewayservice.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Chặn và xác thực các request gửi tới /api/courses/**
        if (path.startsWith("/api/courses")) {
            // 1. Kiểm tra sự tồn tại của Header Authorization
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Missing Authorization Header");
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid Authorization Header format. Expected Bearer token");
            }

            String token = authHeader.substring(7);

            // 2. Giải mã và kiểm tra tính hợp lệ của Token
            try {
                // Trích xuất role từ Payload
                String role = jwtUtil.extractRole(token);

                // 3. Gắn vào Header mới X-User-Role trước khi forward xuống Course-Service
                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header("X-User-Role", role)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());

            } catch (ExpiredJwtException e) {
                // Token đã hết hạn
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Token has expired");
            } catch (SignatureException e) {
                // Sai chữ ký (Secret Key không khớp hoặc token bị can thiệp)
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid token signature");
            } catch (JwtException | IllegalArgumentException e) {
                // Token không hợp lệ
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid token: " + e.getMessage());
            }
        }

        // Đối với các route công khai khác (như /api/auth/**), cho phép đi tiếp
        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String jsonBody = String.format("{\"status\":%d,\"error\":\"Unauthorized\",\"message\":\"%s\"}",
                status.value(), message);
        DataBuffer buffer = response.bufferFactory().wrap(jsonBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // Ưu tiên xử lý trước khi forward request
    }
}
