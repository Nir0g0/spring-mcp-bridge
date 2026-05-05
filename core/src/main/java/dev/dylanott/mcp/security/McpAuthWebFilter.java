package dev.dylanott.mcp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.protocol.JsonRpcResponse;
import dev.dylanott.mcp.server.AuthContext;
import dev.dylanott.mcp.server.McpException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public class McpAuthWebFilter implements WebFilter {

    private final JwtAuthenticator authenticator;
    private final ObjectMapper mapper;

    public McpAuthWebFilter(JwtAuthenticator authenticator, ObjectMapper mapper) {
        this.authenticator = authenticator;
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/mcp")) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        try {
            AuthContext ctx = authenticator.authenticate(header);
            exchange.getAttributes().put(AuthContext.class.getName(), ctx);
            return chain.filter(exchange);
        } catch (McpException e) {
            return writeError(exchange.getResponse(), e);
        }
    }

    private Mono<Void> writeError(ServerHttpResponse response, McpException e) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            JsonRpcResponse body = JsonRpcResponse.fail(null,
                    JsonRpcError.of(e.code(), e.getMessage()));
            byte[] bytes = mapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            DataBuffer buffer = response.bufferFactory()
                    .wrap(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }
    }
}
