package dev.dylanott.mcp.security;

import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.server.AuthContext;
import dev.dylanott.mcp.server.McpException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class JwtAuthenticator {

    private final SecretKey key;
    private final String issuer;
    private final String rolesClaim;

    public JwtAuthenticator(McpSecurityProperties.Jwt config) {
        if (config.getSecret() == null || config.getSecret().isBlank()) {
            throw new IllegalStateException("spring.mcp.security.jwt.secret is required when security is enabled");
        }
        byte[] secretBytes = config.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("spring.mcp.security.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = new SecretKeySpec(secretBytes, "HmacSHA256");
        this.issuer = config.getIssuer();
        this.rolesClaim = config.getRolesClaim();
    }

    public AuthContext authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new McpException(JsonRpcError.UNAUTHORIZED, "Missing Bearer token");
        }
        String token = authorizationHeader.substring(7).trim();
        try {
            var parserBuilder = Jwts.parser().verifyWith(key);
            if (issuer != null && !issuer.isBlank()) {
                parserBuilder.requireIssuer(issuer);
            }
            Claims claims = parserBuilder.build().parseSignedClaims(token).getPayload();
            String subject = claims.getSubject();
            Set<String> roles = extractRoles(claims.get(rolesClaim));
            return AuthContext.of(subject == null ? "anonymous" : subject, roles);
        } catch (JwtException e) {
            throw new McpException(JsonRpcError.UNAUTHORIZED, "Invalid JWT: " + e.getMessage());
        }
    }

    private Set<String> extractRoles(Object raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw instanceof Collection<?> coll) {
            for (Object o : coll) {
                if (o != null) {
                    result.add(o.toString());
                }
            }
        } else if (raw instanceof String s) {
            for (String role : s.split("[,\\s]+")) {
                if (!role.isEmpty()) {
                    result.add(role);
                }
            }
        }
        return result;
    }
}
